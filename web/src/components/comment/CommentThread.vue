<script lang="ts" setup>
import { ChevronRightOutlined } from '@vicons/material';

import { CommentRepo } from '@/repos';
import type { Comment1 } from '@/model/Comment';
import { useDraftStore } from '@/stores';

const props = defineProps<{
  site: string;
  comment: Comment1;
  canReply: boolean;
}>();

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

function onReplied() {
  showInput.value = false;
  draftStore.cancelAddDraft();
  draftStore.removeDraft(draftId);
}
const showInput = ref(false);
const collapsed = ref(false);
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
    v-if="collapsed && comment.numReplies > 0"
    style="margin-left: 32px; margin-top: 20px"
  >
    <n-button quaternary size="tiny" @click="collapsed = false">
      <template #icon>
        <n-icon :component="ChevronRightOutlined" />
      </template>
      展開回覆 ({{ comment.numReplies }})
    </n-button>
  </div>

  <div
    :style="{
      display: 'grid',
      gridTemplateRows: !collapsed ? '1fr' : '0fr',
      transition: 'grid-template-rows 0.3s ease-in-out',
    }"
  >
    <div style="overflow: hidden">
      <div style="display: flow-root">
        <CommentEditor
          v-if="showInput"
          :site="site"
          :draft-id="draftId"
          :parent="comment.id"
          :placeholder="`回复${comment.user.username}`"
          style="padding-top: 8px"
          @replied="onReplied()"
          @cancel="showInput = false"
        />

        <div style="margin-left: 32px; margin-top: 20px">
          <CPage
            v-model:page="page"
            :page-number="commentPage?.pageNumber"
            disable-top
          >
            <template v-if="commentPage">
              <div
                v-for="(replyComment, index) in commentPage?.items"
                :key="replyComment.id"
                style="margin-top: 20px; margin-bottom: 20px"
              >
                <CommentItem
                  :site="site"
                  :parent-id="comment.id"
                  :comment="replyComment"
                  :can-reply="canReply"
                  :collapsed="collapsed"
                  :is-first-child="index === 0"
                  @toggle-collapse="collapsed = !collapsed"
                />
              </div>
            </template>
            <CResultX v-else :error="error" title="加载错误" />
          </CPage>
        </div>
      </div>
    </div>
  </div>
</template>
