<script lang="ts" setup>
import NovelGlossaryEditor from '@/components/NovelGlossaryEditor.vue';
import type { GenericNovelId } from '@/model/Common';
import type { Glossary } from '@/model/Glossary';

const props = defineProps<{
  gnid?: GenericNovelId;
  value: Glossary;
}>();

const editorRef = useTemplateRef('editorRef');

const showGlossaryModal = ref(false);
const showConfirmModal = ref(false);

const toggleGlossaryModal = () => {
  if (showGlossaryModal.value === false) {
    editorRef.value?.resetState();
  }
  showGlossaryModal.value = !showGlossaryModal.value;
};

const handleUpdateShow = (show: boolean) => {
  if (!show) {
    if (editorRef.value?.isGlossaryChanged()) {
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
    <NovelGlossaryEditor ref="editorRef" :gnid="gnid" :value="value" />
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
