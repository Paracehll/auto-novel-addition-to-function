<script lang="ts">
import { useLocalStorage } from '@/util/useStorage';

const collapsedStore = useLocalStorage<Record<string, boolean>>(
  'collapsed-comments',
  {},
);
</script>

<script lang="ts" setup>
import { ChevronRightOutlined } from '@vicons/material';

import { CommentRepo } from '@/repos';
import type { Comment1 } from '@/model/Comment';
import { useDraftStore, useSettingStore } from '@/stores';

const props = defineProps<{
  site: string;
  comment: Comment1;
  canReply: boolean;
}>();

const settingStore = useSettingStore();
const { setting } = storeToRefs(settingStore);

const draftStore = useDraftStore();
const draftId = `comment-${props.site}`;

const page = ref(1);
const { data: commentPage, error } = CommentRepo.useCommentList(
  page,
  () => props.site,
  () => props.comment.id,
  {
    items: props.comment.replies,
    pageNumber: Math.floor((props.comment.numReplies + 9) / 10),
  },
);

const anchorEl = useTemplateRef('anchor');
watch(page, () => {
  anchorEl.value?.scrollIntoView();
  window.scrollBy({ top: -50, behavior: 'auto' });
});

const showInput = ref(false);

watch(showInput, (v) => {
  if (v) {
    collapsed.value = false;
  }
});

function onReplied() {
  showInput.value = false;
  draftStore.cancelAddDraft();
  draftStore.removeDraft(draftId);
}

const collapsed = computed({
  get: () => collapsedStore.value[props.comment.id] ?? false,
  set: (v) => {
    if (v) {
      collapsedStore.value[props.comment.id] = true;
      showInput.value = false;
    } else {
      delete collapsedStore.value[props.comment.id];
    }
  },
});
</script>

<template>
  <div ref="anchor" />
  <CommentItem
    :site="site"
    :comment="comment"
    :can-reply="canReply"
    @reply="showInput = !showInput"
  />

  <div
    v-if="setting.enableCommentCollapse && comment.numReplies > 0"
    style="margin-top: 8px"
  >
    <n-button
      quaternary
      size="small"
      :focusable="false"
      style="padding: 0 6px 0 0"
      @click="collapsed = !collapsed"
      @mouseup="(e: any) => (e.target as HTMLElement).blur()"
    >
      <template #icon>
        <n-icon
          :component="ChevronRightOutlined"
          :style="{
            transition: 'transform 0.3s ease',
            transform: collapsed ? 'rotate(0deg)' : 'rotate(90deg)',
          }"
        />
      </template>
      {{ collapsed ? `展开回复 (${comment.numReplies})` : '收起回复' }}
    </n-button>
  </div>

  <n-collapse-transition
    v-if="showInput || comment.numReplies > 0"
    :show="!setting.enableCommentCollapse || !collapsed || showInput"
  >
    <div
      :style="{
        display: 'flow-root',
        paddingBottom: showInput ? '48px' : '0',
        transition: 'padding-bottom 0.3s ease',
      }"
    >
      <div v-if="showInput" style="min-height: 200px">
        <CommentEditor
          :site="site"
          :draft-id="draftId"
          :parent="comment.id"
          :placeholder="`回复${comment.user.username}`"
          style="padding-top: 8px"
          @replied="onReplied()"
          @cancel="showInput = false"
        />
      </div>

      <div
        v-if="comment.numReplies > 0"
        style="margin-left: 32px; margin-top: 20px"
      >
        <CPage
          v-model:page="page"
          :page-number="commentPage?.pageNumber"
          disable-top
        >
          <template v-if="commentPage">
            <div
              v-for="replyComment in commentPage?.items"
              :key="replyComment.id"
              style="margin-top: 20px; margin-bottom: 20px"
            >
              <CommentItem
                :site="site"
                :parent-id="comment.id"
                :comment="replyComment"
                :can-reply="canReply"
              />
            </div>
          </template>
          <CResultX v-else :error="error" title="加载错误" />
        </CPage>
      </div>
    </div>
  </n-collapse-transition>
</template>
