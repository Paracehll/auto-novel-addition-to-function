<script lang="ts" setup>
import { ref, computed } from 'vue';
import type { ScrollbarInst } from 'naive-ui';
import { DeleteOutlineOutlined } from '@vicons/material';
import { useGlossaryDrag } from './useGlossaryDrag';

import { WebNovelApi, WenkuNovelApi } from '@/api';
import { GenericNovelId } from '@/model/Common';
import { Glossary } from '@/model/Glossary';
import { copyToClipBoard, doAction } from '@/pages/util';
import { useLocalVolumeStore, useWhoamiStore } from '@/stores';
import { downloadFile } from '@/util';

const props = defineProps<{
  gnid?: GenericNovelId;
  value: Glossary;
}>();

const message = useMessage();

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const glossary = ref<Glossary>({});
const originalGlossary = ref<Glossary>({});

const showGlossaryModal = ref(false);
const showConfirmModal = ref(false);

const toggleGlossaryModal = () => {
  if (showGlossaryModal.value === false) {
    glossary.value = { ...props.value };
    originalGlossary.value = { ...props.value };
  }
  showGlossaryModal.value = !showGlossaryModal.value;
};

const isGlossaryChanged = () => {
  const cur = glossary.value;
  const orig = originalGlossary.value;
  const curKeys = Object.keys(cur);
  const origKeys = Object.keys(orig);
  if (curKeys.length !== origKeys.length) return true;
  for (let i = 0; i < curKeys.length; i++)
    if (curKeys[i] !== origKeys[i] || cur[curKeys[i]] !== orig[curKeys[i]])
      return true;
  return false;
};

const gnidHint = computed(() => {
  const gnid = props.gnid;
  if (gnid === undefined) {
    return undefined;
  } else {
    return GenericNovelId.toString(gnid);
  }
});

const updateGlossary = async (glossaryValue: Glossary) => {
  const gnid = props.gnid;
  if (gnid === undefined) {
    return;
  }
  if (gnid.type === 'web') {
    await WebNovelApi.updateGlossary(
      gnid.providerId,
      gnid.novelId,
      glossaryValue,
    );
  } else if (gnid.type === 'wenku') {
    await WenkuNovelApi.updateGlossary(gnid.novelId, glossaryValue);
  } else {
    const repo = await useLocalVolumeStore();
    await repo.updateGlossary(gnid.volumeId, glossaryValue);
  }
};

const submitGlossary = () => {
  const submittedGlossary = { ...toRaw(glossary.value) };
  return doAction(
    updateGlossary(submittedGlossary).then(() => {
      // 触发组件外的术语表本体更新。有点傻，但够用。
      for (const key in props.value) {
        delete props.value[key];
      }
      for (const key in submittedGlossary) {
        props.value[key] = submittedGlossary[key];
      }
      originalGlossary.value = { ...submittedGlossary };
    }),
    '术语表提交',
    message,
  );
};

const handleUpdateShow = (show: boolean) => {
  if (!show) {
    if (isGlossaryChanged()) {
      showConfirmModal.value = true;
      return;
    }
  }
  showGlossaryModal.value = show;
};

const handleConfirmClose = () => {
  showConfirmModal.value = false;
  showGlossaryModal.value = false;
};

const handleConfirmCancel = () => {
  showConfirmModal.value = false;
};

const importGlossaryRaw = ref('');
const termsToAdd = ref<[string, string]>(['', '']);

type UndoAction =
  | { type: 'delete'; item: [string, string] }
  | { type: 'reorder'; previousGlossary: Glossary };

const undoStack = ref<UndoAction[]>([]);

const lastUndoHint = computed(() => {
  const last = undoStack.value[undoStack.value.length - 1];
  if (last === undefined) return 'Tip: 按住 => 可以拖放';
  else if (last.type === 'delete') return `${last.item[0]} => ${last.item[1]}`;
  return '变更排序';
});

const clearTerm = () => {
  glossary.value = {};
  undoStack.value = [];
};

const undoLastAction = () => {
  if (undoStack.value.length === 0) return;
  const action = undoStack.value.pop()!;
  if (action.type === 'delete') {
    const [jp, zh] = action.item;
    glossary.value[jp] = zh;
  } else if (action.type === 'reorder') {
    glossary.value = action.previousGlossary;
  }
};

const deleteTerm = (jp: string) => {
  if (jp in glossary.value) {
    undoStack.value.push({ type: 'delete', item: [jp, glossary.value[jp]] });
    delete glossary.value[jp];
  }
};

const addTerm = () => {
  const [jp, zh] = termsToAdd.value;
  if (jp && zh) {
    glossary.value[jp.trim()] = zh.trim();
    termsToAdd.value = ['', ''];
  }
};

const exportGlossary = async (ev: MouseEvent) => {
  const isSuccess = await copyToClipBoard(
    Glossary.toText(glossary.value),
    ev.target as HTMLElement,
  );
  if (isSuccess) {
    message.success('导出成功：已复制到剪贴板');
  } else {
    message.success('导出失败');
  }
};

const importGlossary = () => {
  const importedGlossary = Glossary.fromText(importGlossaryRaw.value);
  if (importedGlossary === undefined) {
    message.error('导入失败：术语表格式不正确');
  } else {
    message.success('导入成功');
    for (const jp in importedGlossary) {
      const zh = importedGlossary[jp];
      glossary.value[jp] = zh;
    }
  }
};

const downloadGlossaryAsJsonFile = async (ev: MouseEvent) => {
  downloadFile(
    `${gnidHint.value ?? 'glossary'}.json`,
    new Blob([Glossary.toJson(glossary.value)], {
      type: 'text/plain',
    }),
  );
};

// --- 拖放邏輯 ---
const scrollbarInstRef = ref<ScrollbarInst | null>(null);
const scrollContainerRef = ref<HTMLDivElement | null>(null);
const jpKeys = computed(() => Object.keys(glossary.value).reverse());

const { draggedKey, dragOverKey, dragPosition, handleDragStart } =
  useGlossaryDrag({
    scrollContainerRef,
    scrollbarInstRef,
    jpKeys,
    onReorder: (sourceKey, targetKey, position) => {
      const keys = [...jpKeys.value];
      const fromIndex = keys.indexOf(sourceKey);
      let toIndex = keys.indexOf(targetKey);

      if (fromIndex !== -1 && toIndex !== -1) {
        if (position === 'on') {
          [keys[fromIndex], keys[toIndex]] = [keys[toIndex], keys[fromIndex]];
        } else {
          keys.splice(fromIndex, 1);
          toIndex = keys.indexOf(targetKey);
          if (toIndex !== -1) {
            if (position === 'below') toIndex++;
            keys.splice(toIndex, 0, sourceKey);
          }
        }

        undoStack.value.push({
          type: 'reorder',
          previousGlossary: { ...glossary.value },
        });

        const newGlossary: Glossary = {};
        for (const k of keys.reverse()) newGlossary[k] = glossary.value[k];

        glossary.value = newGlossary;
      }
    },
  });
</script>

<template>
  <c-button
    :label="`术语表[${Object.keys(value).length}]`"
    v-bind="$attrs"
    @action="toggleGlossaryModal()"
  />

  <c-modal
    title="编辑术语表"
    :show="showGlossaryModal"
    @update:show="handleUpdateShow"
    :extra-height="120"
  >
    <template #header-extra>
      <n-flex
        vertical
        size="large"
        style="max-width: 400px; margin-bottom: 16px"
      >
        <template v-if="gnidHint">
          <n-text style="font-size: 12px">{{ gnidHint }}</n-text>

          <n-text>
            使用前务必先阅读
            <c-a to="/forum/660ab4da55001f583649a621">术语表使用指南</c-a>
            ，不要滥用术语表。
          </n-text>
        </template>

        <n-input-group>
          <n-input
            pair
            v-model:value="termsToAdd"
            size="small"
            separator="=>"
            :placeholder="['日文', '中文']"
            :input-props="{ spellcheck: false }"
          />
          <c-button
            label="添加"
            :round="false"
            size="small"
            @action="addTerm"
          />
        </n-input-group>

        <n-input
          v-model:value="importGlossaryRaw"
          type="textarea"
          size="small"
          placeholder="批量导入术语表"
          :input-props="{ spellcheck: false }"
          :rows="1"
        />

        <n-flex align="center" :wrap="false">
          <c-button
            label="导出"
            :round="false"
            size="small"
            @action="exportGlossary"
          />
          <c-button
            label="导入"
            :round="false"
            size="small"
            @action="importGlossary"
          />
          <c-button
            label="下载json文件"
            :round="false"
            size="small"
            @action="downloadGlossaryAsJsonFile"
          />
          <c-button
            v-if="whoami.isAdmin"
            secondary
            type="error"
            label="清空"
            :round="false"
            size="small"
            @action="clearTerm"
          />
        </n-flex>
        <n-flex align="center" :wrap="false">
          <c-button
            :disabled="undoStack.length === 0"
            label="撤销"
            :round="false"
            size="small"
            @action="undoLastAction"
          />
          <n-text
            v-if="lastUndoHint !== undefined"
            depth="3"
            style="font-size: 12px"
          >
            {{ lastUndoHint }}
          </n-text>
        </n-flex>
      </n-flex>
    </template>

    <div ref="scrollContainerRef" style="position: relative; max-width: 400px">
      <n-scrollbar
        ref="scrollbarInstRef"
        style="
          max-height: 250px;
          border: 1px solid var(--border-color);
          border-radius: 4px;
          padding: 4px;
        "
      >
        <n-table
          v-if="jpKeys.length !== 0"
          striped
          size="small"
          style="font-size: 12px"
          :class="{ 'is-dragging': draggedKey !== null }"
        >
          <tbody>
            <tr
              v-for="wordJp in jpKeys"
              :key="wordJp"
              :data-key="wordJp"
              v-memo="[
                wordJp === draggedKey,
                wordJp === dragOverKey ? dragPosition : null,
                glossary[wordJp],
              ]"
              :class="{
                'dragged-row': wordJp === draggedKey,
                'drag-over-above':
                  wordJp === dragOverKey && dragPosition === 'above',
                'drag-over-below':
                  wordJp === dragOverKey && dragPosition === 'below',
                'drag-over-on': wordJp === dragOverKey && dragPosition === 'on',
              }"
            >
              <td>
                <c-button
                  :icon="DeleteOutlineOutlined"
                  text
                  type="error"
                  size="small"
                  @action="deleteTerm(wordJp)"
                />
              </td>
              <td>{{ wordJp }}</td>
              <td
                nowrap="nowrap"
                class="drag-handle"
                @pointerdown="handleDragStart($event, wordJp)"
              >
                =&gt;
              </td>
              <td style="padding-right: 16px">
                <n-input
                  v-model:value="glossary[wordJp]"
                  size="tiny"
                  placeholder="请输入中文翻译"
                  :theme-overrides="{
                    border: '0',
                    color: 'transparent',
                  }"
                />
              </td>
            </tr>
          </tbody>
        </n-table>
        <n-empty
          v-else
          description="暂无术语词条，请添加"
          style="padding: 16px"
        />
      </n-scrollbar>
    </div>

    <template #action>
      <c-button label="提交" type="primary" @action="submitGlossary()" />
    </template>
  </c-modal>

  <n-modal
    v-model:show="showConfirmModal"
    preset="card"
    title="提示"
    :bordered="false"
    size="small"
    transform-origin="center"
    style="
      position: fixed;
      top: 50px;
      left: 50%;
      transform: translateX(-50%);
      width: min(420px, calc(100% - 32px));
    "
  >
    <n-text>检测到未保存的修改，确认关闭吗？</n-text>
    <template #action>
      <n-flex justify="end">
        <c-button
          label="确认"
          type="warning"
          size="small"
          @action="handleConfirmClose"
        />
        <c-button
          label="取消"
          secondary
          size="small"
          @action="handleConfirmCancel"
        />
      </n-flex>
    </template>
  </n-modal>
</template>

<style scoped>
.drag-handle {
  cursor: grab;
  user-select: none;
  font-weight: bold;
  padding: 0 8px;
  text-align: center;
  touch-action: none;
}

.drag-handle:active {
  cursor: grabbing;
}

.dragged-row {
  opacity: 0.4;
}

.drag-over-above td {
  box-shadow: inset 0 2px 0 0 var(--primary-color, #ffffff) !important;
}

.drag-over-below td {
  box-shadow: inset 0 -2px 0 0 var(--primary-color, #ffffff) !important;
}

.drag-over-on td {
  background-color: rgba(255, 255, 255, 0.25) !important;
}

.is-dragging :deep(.n-input),
.is-dragging :deep(.n-input *) {
  pointer-events: none !important;
  user-select: none !important;
}

:global(.drag-preview-container) {
  position: fixed;
  pointer-events: none;
  z-index: 99999;
  opacity: 0.75;
  left: 0;
  top: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
  border-radius: 4px;
  overflow: hidden;
}

:global(.drag-preview-table) {
  width: 100%;
  background: var(--card-color, #18181c);
  border-collapse: collapse;
  font-size: 12px;
}
</style>
