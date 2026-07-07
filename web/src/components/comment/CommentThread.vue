<script lang="ts" setup>
import { ChevronRightOutlined } from '@vicons/material';

import { CommentRepo } from '@/repos';
import type { Comment1 } from '@/model/Comment';
import { useDraftStore } from '@/stores';
import { useLocalStorage } from '@/util/useStorage';

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

const collapsedStore = useLocalStorage<Record<string, boolean>>(
  'collapsed-comments',
  {},
);
const collapsed = computed({
  get: () => collapsedStore.value[props.comment.id] ?? false,
  set: (v) => {
    if (v) {
      collapsedStore.value[props.comment.id] = true;
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
    v-if="comment.numReplies > 0"
    style="height: 0; overflow: visible; position: relative; z-index: 1"
  >
    <n-button quaternary size="tiny" @click="collapsed = !collapsed">
      <template #icon>
        <n-icon
          :component="ChevronRightOutlined"
          :style="{
            transition: 'transform 0.3s ease',
            transform: collapsed ? 'rotate(0deg)' : 'rotate(90deg)',
          }"
        />
      </template>
      {{ collapsed ? `展開回覆 (${comment.numReplies})` : '收起回覆' }}
    </n-button>
  </div>

  <n-collapse-transition :show="!collapsed">
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
