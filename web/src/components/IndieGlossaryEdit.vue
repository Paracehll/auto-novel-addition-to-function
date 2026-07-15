<script lang="ts" setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useMessage } from 'naive-ui';
import {
  DeleteOutlineOutlined,
  RefreshOutlined,
  ContentCopyOutlined,
  DownloadOutlined,
  ContentPasteOutlined,
  FileDownloadOutlined,
} from '@vicons/material';
import { useWhoamiStore } from '@/stores';
import { Glossary } from '@/model/Glossary';
import { copyToClipBoard } from '@/pages/util';
import { downloadFile } from '@/util';

const glossary = defineModel<Glossary>({ required: true });
const skippedKeys = defineModel<Set<string>>('skippedKeys', {
  default: () => new Set(),
});

const message = useMessage();
const whoamiStore = useWhoamiStore();

const termsToAdd = ref<[string, string]>(['', '']);
const importGlossaryRaw = ref('');
const deletedTerms = ref<[string, string][]>([]);

const lastDeletedTerm = computed(() => {
  const last = deletedTerms.value[deletedTerms.value.length - 1];
  if (last === undefined) return undefined;
  return `${last[0]} => ${last[1]}`;
});

const jpKeys = computed(() => Object.keys(glossary.value).reverse());
const limit = ref(50);
const displayedKeys = computed(() => jpKeys.value.slice(0, limit.value));

// Reset limit when glossary changes
watch(
  () => glossary.value,
  () => {
    limit.value = 50;
  },
  { deep: false },
);

const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement;
  if (target.scrollTop + target.clientHeight >= target.scrollHeight - 20) {
    if (limit.value < jpKeys.value.length) {
      limit.value += 50;
    }
  }
};

const addTerm = () => {
  const [jp, zh] = termsToAdd.value;
  if (jp && zh) {
    glossary.value[jp.trim()] = zh.trim();
    termsToAdd.value = ['', ''];
  }
};

const deleteTerm = (jp: string) => {
  if (jp in glossary.value) {
    deletedTerms.value.push([jp, glossary.value[jp]]);
    delete glossary.value[jp];
  }
};

const undoDeleteTerm = () => {
  if (deletedTerms.value.length === 0) return;
  const [jp, zh] = deletedTerms.value.pop()!;
  glossary.value[jp] = zh;
};

const clearTerm = () => {
  glossary.value = {};
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

const exportGlossary = async (ev?: MouseEvent) => {
  const isSuccess = await copyToClipBoard(
    Glossary.toText(glossary.value),
    ev?.target as HTMLElement,
  );
  if (isSuccess) {
    message.success('导出成功：已复制到剪贴板');
  } else {
    message.success('导出失败');
  }
};

const downloadGlossaryAsJson = () => {
  downloadFile(
    'glossary.json',
    new Blob([Glossary.toJson(glossary.value)], {
      type: 'text/plain',
    }),
  );
};

const importGlossaryFromClipboard = async () => {
  try {
    const text = await navigator.clipboard.readText();
    let isValid = false;
    try {
      JSON.parse(text);
      isValid = true;
    } catch {
      // If not JSON, check if it's the valid human-readable glossary format
      const imported = Glossary.fromText(text);
      if (imported !== undefined) {
        isValid = true;
      }
    }

    if (!isValid) {
      message.error('检测到剪贴簿内容不是 JSON 格式');
      return;
    }

    importGlossaryRaw.value = text;
    message.success('从剪贴簿导入成功');
  } catch (err: any) {
    message.error('无法读取剪贴簿: ' + (err?.message ?? err));
  }
};

const isEditable = (el: Element | null): boolean => {
  if (!el) return false;
  const tagName = el.tagName.toUpperCase();
  if (tagName === 'INPUT' || tagName === 'TEXTAREA') {
    return true;
  }
  if (
    el.hasAttribute('contenteditable') &&
    el.getAttribute('contenteditable') !== 'false'
  ) {
    return true;
  }
  return false;
};

const handleKeyDown = (e: KeyboardEvent) => {
  if (isEditable(document.activeElement)) {
    return;
  }
  // 如果选取了文本（例如，在表格中选取的文字），不触发全局的复制（Ctrl+C/V）快捷键
  if (window.getSelection()?.toString()) {
    return;
  }
  const isCtrlOrCmd = e.ctrlKey || e.metaKey;
  if (isCtrlOrCmd) {
    if (e.key === 'c' || e.key === 'C') {
      e.preventDefault();
      exportGlossary();
    } else if (e.key === 'v' || e.key === 'V') {
      e.preventDefault();
      importGlossaryFromClipboard();
    }
  }
};

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown);
});

const revokeSkip = (jp: string) => {
  skippedKeys.value.delete(jp);
  skippedKeys.value = new Set(skippedKeys.value);
};
</script>

<template>
  <n-flex vertical size="large">
    <!-- Terms Editor Header Actions -->
    <n-input-group>
      <n-input
        pair
        v-model:value="termsToAdd"
        size="small"
        separator="=>"
        :placeholder="['日文', '中文']"
        :input-props="{ spellcheck: false }"
      />
      <c-button label="添加" :round="false" size="small" @action="addTerm" />
    </n-input-group>

    <n-input
      v-model:value="importGlossaryRaw"
      type="textarea"
      size="small"
      placeholder="批量导入术语表"
      :input-props="{ spellcheck: false }"
      :rows="1"
    />

    <c-action-wrapper align="center" title="编辑区">
      <n-flex align="center" :wrap="false">
        <c-button
          label="导入"
          :icon="DownloadOutlined"
          :round="false"
          size="small"
          @action="importGlossary"
        />
        <c-button
          label="下载JSON"
          :icon="FileDownloadOutlined"
          :round="false"
          size="small"
          @action="downloadGlossaryAsJson"
        />
        <c-button
          v-if="whoamiStore.whoami.isAdmin"
          secondary
          type="error"
          label="清空"
          :icon="DeleteOutlineOutlined"
          :round="false"
          size="small"
          @action="clearTerm"
        />
      </n-flex>
    </c-action-wrapper>

    <c-action-wrapper align="center" title="剪贴簿">
      <n-flex align="center" :wrap="false">
        <c-button
          label="导出"
          :icon="ContentCopyOutlined"
          :round="false"
          size="small"
          @action="exportGlossary"
        />
        <c-button
          class="clipboard-import-btn"
          label="剪贴"
          :icon="ContentPasteOutlined"
          :round="false"
          size="small"
          @action="importGlossaryFromClipboard"
        />
      </n-flex>
    </c-action-wrapper>

    <n-flex align="center" :wrap="false">
      <c-button
        :disabled="deletedTerms.length === 0"
        label="撤销删除"
        :round="false"
        size="small"
        @action="undoDeleteTerm"
      />
      <n-text
        v-if="lastDeletedTerm !== undefined"
        depth="3"
        style="font-size: 12px"
      >
        {{ lastDeletedTerm }}
      </n-text>
    </n-flex>

    <!-- Interactive Terms List Table with Dynamic Loading -->
    <n-scrollbar
      @scroll="handleScroll"
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
      >
        <tr v-for="wordJp in displayedKeys" :key="wordJp">
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
          <td nowrap="nowrap">=></td>
          <td style="padding-right: 16px">
            <div style="display: flex; align-items: center; gap: 4px">
              <n-input
                v-model:value="glossary[wordJp]"
                size="tiny"
                placeholder="请输入中文翻译"
                :theme-overrides="{
                  border: '0',
                  color: 'transparent',
                }"
              />
              <n-tooltip trigger="hover" v-if="skippedKeys.has(wordJp)">
                <template #trigger>
                  <n-button
                    size="tiny"
                    quaternary
                    circle
                    style="padding: 0; width: 18px; height: 18px"
                    @click="revokeSkip(wordJp)"
                  >
                    <template #icon>
                      <n-icon :component="RefreshOutlined" />
                    </template>
                  </n-button>
                </template>
                撤销去重跳过
              </n-tooltip>
            </div>
          </td>
        </tr>
      </n-table>
      <n-empty
        v-else
        description="暂无术语词条，请添加"
        style="padding: 16px"
      />

      <div
        v-if="limit < jpKeys.length"
        style="text-align: center; padding: 8px"
      >
        <n-button size="tiny" text @click="limit += 100">
          加载更多 (已显示 {{ limit }} / 共 {{ jpKeys.length }})
        </n-button>
      </div>
    </n-scrollbar>
  </n-flex>
</template>
