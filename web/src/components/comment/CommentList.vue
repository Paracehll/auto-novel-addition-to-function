<script lang="ts" setup>
import { CommentOutlined } from '@vicons/material';

import { CommentRepo } from '@/repos';
import { useDraftStore, useWhoamiStore, useSettingStore } from '@/stores';

const props = defineProps<{
  site: string;
  locked: boolean;
}>();

const { data: commentPage, error } = CommentRepo.useCommentList(
  1,
  () => props.site,
  undefined,
  undefined,
  100,
);

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const settingStore = useSettingStore();
const { setting } = storeToRefs(settingStore);

const draftStore = useDraftStore();
const draftId = `comment-${props.site}`;

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

const commentCount = computed(() => {
  if (!commentPage.value) return undefined;

  const items = commentPage.value.items;
  const unique = setting.value.commentCountUnique;
  const reply = setting.value.commentCountReply;

  if (unique) {
    const users = new Set<string>();
    for (const item of items) {
      if (item.user?.username) {
        users.add(item.user.username);
      }
      if (reply && item.replies) {
        for (const r of item.replies) {
          if (r.user?.username) {
            users.add(r.user.username);
          }
        }
      }
    }
    return users.size;
  } else {
    if (reply) {
      let count = 0;
      for (const item of items) {
        count += 1 + item.numReplies;
      }
      return count;
    } else {
      return items.length;
    }
  }
});
</script>

<template>
  <SectionHeader
    title="评论"
    ref="commentSectionRef"
    style="margin-bottom: 32px"
  >
    <template #title-extra>
      <n-text
        depth="3"
        v-if="setting.showCommentCount && commentCount !== undefined"
      >
        💬 {{ commentCount }}
      </n-text>
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

  <CResultX v-else-if="error" :error="error" title="加载错误" />
</template>
