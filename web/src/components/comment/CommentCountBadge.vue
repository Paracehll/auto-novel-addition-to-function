<script lang="ts" setup>
import { useQuery } from '@pinia/colada';
import { CommentApi } from '@/api/novel/CommentApi';
import { useSettingStore } from '@/stores';

const props = defineProps<{
  site: string;
}>();

const settingStore = useSettingStore();
const { setting } = storeToRefs(settingStore);

const { data: commentCount } = useQuery({
  key: () => [
    'comment-count',
    props.site,
    setting.value.commentCountUnique,
    setting.value.commentCountReply,
  ],
  query: () =>
    CommentApi.countComment({
      site: props.site,
      unique: setting.value.commentCountUnique ? 1 : 0,
      reply: setting.value.commentCountReply ? 1 : 0,
    }).then((res) => res.total),
  enabled: () => setting.value.showCommentCount,
});
</script>

<template>
  <span v-if="commentCount !== undefined">💬 {{ commentCount }}</span>
</template>
