<script lang="ts" setup>
import {
  DeleteOutlineOutlined,
  ContentCopyOutlined,
  UploadOutlined,
  DownloadOutlined,
  ContentPasteOutlined,
  FileDownloadOutlined,
} from '@vicons/material';

import { isEqual } from 'lodash-es';

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

const showGlossaryModal = ref(false);

const toggleGlossaryModal = () => {
  if (showGlossaryModal.value === false) {
    glossary.value = { ...props.value };
    showGlossaryModal.value = true;
  } else {
    handleUpdateShow(false);
  }
};

const handleUpdateShow = async (value: boolean) => {
  if (value === false) {
    const hasChanges = !isEqual(toRaw(glossary.value), toRaw(props.value));
    if (hasChanges) {
      if (window.confirm('术语表有未保存的修改，是否保存？')) {
        await handleSaveConfirm();
      } else {
        handleDiscardConfirm();
      }
    } else {
      showGlossaryModal.value = false;
    }
  } else {
    showGlossaryModal.value = true;
  }
};

const handleSaveConfirm = async () => {
  try {
    await updateGlossary();
    for (const key in props.value) {
      delete props.value[key];
    }
    for (const key in glossary.value) {
      props.value[key] = glossary.value[key];
    }
    message.success('保存成功');
    showGlossaryModal.value = false;
  } catch (e: any) {
    message.error('保存失败:' + e);
  }
};

const handleDiscardConfirm = () => {
  glossary.value = { ...props.value };
  showGlossaryModal.value = false;
};

const gnidHint = computed(() => {
  const gnid = props.gnid;
  if (gnid === undefined) {
    return undefined;
  } else {
    return GenericNovelId.toString(gnid);
  }
});

const updateGlossary = async () => {
  const gnid = props.gnid;
  if (gnid === undefined) {
    return;
  }
  const glossaryValue = toRaw(glossary.value);
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

const submitGlossary = () =>
  doAction(
    updateGlossary().then(() => {
      // 触发组件外的术语表本体更新。有点傻，但够用。
      for (const key in props.value) {
        delete props.value[key];
      }
      for (const key in glossary.value) {
        props.value[key] = glossary.value[key];
      }
    }),
    '术语表提交',
    message,
  );

const importGlossaryRaw = ref('');
const termsToAdd = ref<[string, string]>(['', '']);

const deletedTerms = ref<[string, string][]>([]);

const lastDeletedTerm = computed(() => {
  const last = deletedTerms.value[deletedTerms.value.length - 1];
  if (last === undefined) return undefined;
  return `${last[0]} => ${last[1]}`;
});

const clearTerm = () => {
  glossary.value = {};
};

const undoDeleteTerm = () => {
  if (deletedTerms.value.length === 0) return;
  const [jp, zh] = deletedTerms.value.pop()!;
  glossary.value[jp] = zh;
};

const deleteTerm = (jp: string) => {
  if (jp in glossary.value) {
    deletedTerms.value.push([jp, glossary.value[jp]]);
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

watch(showGlossaryModal, (isOpen) => {
  if (isOpen) {
    window.addEventListener('keydown', handleKeyDown);
  } else {
    window.removeEventListener('keydown', handleKeyDown);
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown);
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
              @action="downloadGlossaryAsJsonFile"
            />
            <c-button
              v-if="whoami.isAdmin"
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
      </n-flex>
    </template>

    <n-table
      v-if="Object.keys(glossary).length !== 0"
      striped
      size="small"
      style="font-size: 12px; max-width: 400px"
    >
      <tr v-for="wordJp in Object.keys(glossary).reverse()" :key="wordJp">
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
          <n-input
            v-model:value="glossary[wordJp]"
            size="tiny"
            placeholder="请输入中文翻译"
            :theme-overrides="{
              border: '0',
              color: 'transprent',
            }"
          />
        </td>
      </tr>
    </n-table>

    <template #action>
      <c-button label="提交" type="primary" @action="submitGlossary()" />
    </template>
  </c-modal>
</template>
