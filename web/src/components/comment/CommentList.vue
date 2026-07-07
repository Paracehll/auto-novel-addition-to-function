<script lang="ts" setup>
import { CommentOutlined } from '@vicons/material';

import { CommentApi } from '@/api/novel/CommentApi';
import { CommentRepo } from '@/repos';
import { useDraftStore, useSettingStore, useWhoamiStore } from '@/stores';

const props = defineProps<{
  site: string;
  locked: boolean;
}>();

const settingStore = useSettingStore();
const { setting } = storeToRefs(settingStore);

const commentCount = ref<number | null>(null);

watch(
  () => [
    props.site,
    setting.value.showCommentCount,
    setting.value.commentCountUnique,
    setting.value.commentCountReply,
  ],
  async () => {
    if (!setting.value.showCommentCount) {
      commentCount.value = null;
      return;
    }
    try {
      const { total } = await CommentApi.countComment({
        site: props.site,
        unique: setting.value.commentCountUnique ? 1 : 0,
        reply: setting.value.commentCountReply ? 1 : 0,
      });
      commentCount.value = total;
    } catch (e) {
      console.error('Failed to fetch comment count:', e);
    }
  },
  { immediate: true },
);

const page = ref(1);
const { data: commentPage, error } = CommentRepo.useCommentList(
  page,
  () => props.site,
);

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const draftStore = useDraftStore();
const draftId = `comment-${props.site}`;

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

const canReply = computed(() => {
  const hasAccess = props.site.startsWith('article-')
    ? whoami.value.hasForumAccess
    : whoami.value.hasNovelAccess;
  return hasAccess && !props.locked;
});
</script>

<template>
  <div ref="anchor" />
  <SectionHeader
    title="评论"
    ref="commentSectionRef"
    style="margin-bottom: 32px"
  >
    <template #title-extra v-if="commentCount !== null">
      <n-text depth="3">💬 {{ commentCount }}</n-text>
    </template>
    <c-button
      v-if="canReply"
      label="发表评论"
      :icon="CommentOutlined"
      require-login
      @action="showInput = !showInput"
    />
  </SectionHeader>

  <n-p v-if="locked">评论区已锁定，不能再回复。</n-p>

  <template v-if="showInput">
    <CommentEditor
      :site="site"
      :draft-id="draftId"
      :placeholder="`发表回复`"
      @replied="onReplied()"
      @cancel="showInput = false"
    />
    <n-divider />
  </template>

  <CPage v-model:page="page" :page-number="commentPage?.pageNumber" disable-top>
    <template v-if="commentPage">
      <template v-for="comment in commentPage.items" :key="comment.id">
        <CommentThread :site="site" :comment="comment" :can-reply="canReply" />
        <n-divider />
      </template>
      <n-empty
        v-if="commentPage.items.length === 0 && !locked"
        description="暂无评论"
      />
    </template>

    <CResultX v-else :error="error" title="加载错误" />
  </CPage>
</template>
