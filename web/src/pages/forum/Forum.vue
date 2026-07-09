<script lang="ts" setup>
import { LockOutlined, PlusOutlined, PushPinOutlined, SearchOutlined, SettingsOutlined } from '@vicons/material';

import { ArticleRepo } from '@/repos';
import type { ArticleCategory, ArticleSimplified } from '@/model/Article';
import { doAction } from '@/pages/util';
import { useBlacklistStore, useForumSearchSettingStore, useWhoamiStore } from '@/stores';

const props = defineProps<{
  page: number;
  category: ArticleCategory;
  search?: string;
  sort?: string;
  sortDesc?: boolean;
}>();

const route = useRoute();
const router = useRouter();
const message = useMessage();

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const blacklistStore = useBlacklistStore();
const { setting: searchSetting } = storeToRefs(useForumSearchSettingStore());

const articleCategoryOptions = [
  { value: 'General', label: '小说交流' },
  { value: 'Guide', label: '使用指南' },
  { value: 'Support', label: '反馈与建议' },
];

const onUpdatePage = (page: number) => {
  const query = { ...route.query, page };
  router.push({ path: route.path, query });
};

const onUpdateCategory = (category: ArticleCategory) => {
  const query = { ...route.query, category, page: 1 };
  router.push({ path: route.path, query });
};

const searchQuery = ref('');
const activeTags = ref<{ type: 'a' | 't'; value: string }[]>([]);

const articleSortOptions = [
  { value: 'Default', label: '默认' },
  { value: 'CreateAt', label: '发布时间' },
  { value: 'Views', label: '点击量' },
  { value: 'Comments', label: '评论量' },
];

const currentSort = computed({
  get: () => ({
    value: props.sort ?? 'Default',
    desc: props.sortDesc ?? true,
  }),
  set: (val) => {
    const query = {
      ...route.query,
      sort: val.value,
      sortDesc: val.desc ? undefined : false,
      page: 1,
    };
    router.push({ path: route.path, query });
  },
});

watch(
  () => props.search,
  (newSearch) => {
    const search = newSearch ?? '';
    const tags: { type: 'a' | 't'; value: string }[] = [];

    const matches = search.matchAll(/([at]):"([^"]*)"/g);
    for (const match of matches) {
      tags.push({ type: match[1] as any, value: match[2] });
    }

    activeTags.value = tags;
    searchQuery.value = search
      .replace(/[at]:"[^"]*"/g, '')
      .trim();
  },
  { immediate: true },
);

const parseSearch = (search: string) => {
  const authorMatch = search.match(/a:"([^"]*)"/);
  const timeMatch = search.match(/t:"([^"]*)"/);

  let author = authorMatch ? authorMatch[1] : undefined;
  let startAt: number | undefined = undefined;
  let endAt: number | undefined = undefined;

  if (timeMatch) {
    const range = timeMatch[1].split('-');
    const parseDate = (s: string, isEnd: boolean) => {
      if (!s) {
        if (isEnd && searchSetting.value.autoFillToDate) {
          return Math.floor(new Date().getTime() / 1000);
        }
        return undefined;
      }
      const year = parseInt(s.substring(0, 4));
      const month = parseInt(s.substring(4, 6)) - 1;
      const day = parseInt(s.substring(6, 8));
      return Math.floor(new Date(year, month, day).getTime() / 1000);
    };
    if (range.length === 2) {
      startAt = parseDate(range[0], false);
      endAt = parseDate(range[1], true);
    } else if (!timeMatch[1].includes('-')) {
      startAt = parseDate(timeMatch[1], false);
    }
  }

  const query = search
    .replace(/[at]:"[^"]*"/g, '')
    .trim();

  return {
    author,
    query: query || undefined,
    startAt,
    endAt,
  };
};

const onSearch = () => {
  let searchStr = searchQuery.value.trim();
  activeTags.value.forEach((tag) => {
    searchStr += ` ${tag.type}:"${tag.value}"`;
  });

  const query = {
    ...route.query,
    search: searchStr.trim() || undefined,
    page: 1,
  };
  router.push({ path: route.path, query });
};

const removeTag = (index: number) => {
  activeTags.value.splice(index, 1);
  onSearch();
};

const searchInputInst = useTemplateRef<any>('searchInputInst');
const handleInput = (v: string) => {
  // 1. Auto-quote and cursor positioning
  const triggerRegex = /(?:^|\s)([at]):$/;
  const match = v.match(triggerRegex);
  if (match) {
    const type = match[1];
    let pos = v.length;
    if (type === 't' && searchSetting.value.autoFillToDate) {
      const today = new Date();
      const YYYY = today.getFullYear();
      const MM = String(today.getMonth() + 1).padStart(2, '0');
      const DD = String(today.getDate()).padStart(2, '0');
      searchQuery.value = v + `"-${YYYY}${MM}${DD}"`;
    } else {
      searchQuery.value = v + '""';
    }
    setTimeout(() => {
      const el = (searchInputInst.value?.elRef as HTMLElement) || document.querySelector('.search-bar');
      const input = el?.querySelector('input');
      if (input) {
        input.focus();
        input.setSelectionRange(pos + 1, pos + 1);
      }
    }, 10);
    return;
  }

  // 2. Tokenization: if user types a space after a completed tag, convert it to a badge
  const tokenMatch = v.match(/(?:^|\s)([at]):"([^"]*)"\s$/);
  if (tokenMatch) {
    const type = tokenMatch[1] as any;
    const value = tokenMatch[2];
    if (value) {
      activeTags.value.push({ type, value });
      searchQuery.value = v.replace(tokenMatch[0], ' ').trimStart();
      onSearch();
    }
  }
};

const searchParams = computed(() => parseSearch(props.search ?? ''));

const { data: articlePage, error } = ArticleRepo.useArticleList(
  () => props.page,
  () => props.category,
  () => searchParams.value.author,
  () => searchParams.value.query,
  () => searchSetting.value.exactAuthor,
  () => searchSetting.value.fuzzyTitle,
  () => searchParams.value.startAt,
  () => searchParams.value.endAt,
  undefined,
  undefined,
  undefined,
  undefined,
  () => props.sort,
  () => props.sortDesc,
);

const lockArticle = (article: ArticleSimplified) =>
  doAction(
    ArticleRepo.lockArticle(article.id).then(() => (article.locked = true)),
    '锁定',
    message,
  );

const unlockArticle = (article: ArticleSimplified) =>
  doAction(
    ArticleRepo.unlockArticle(article.id).then(() => (article.locked = false)),
    '解除锁定',
    message,
  );

const pinArticle = (article: ArticleSimplified) =>
  doAction(
    ArticleRepo.pinArticle(article.id).then(() => (article.pinned = true)),
    '置顶',
    message,
  );

const unpinArticle = (article: ArticleSimplified) =>
  doAction(
    ArticleRepo.unpinArticle(article.id).then(() => (article.pinned = false)),
    '解除置顶',
    message,
  );

const hideArticle = (article: ArticleSimplified) =>
  doAction(
    ArticleRepo.hideArticle(article.id).then(() => (article.hidden = true)),
    '隐藏',
    message,
  );

const unhideArticle = (article: ArticleSimplified) =>
  doAction(
    ArticleRepo.unhideArticle(article.id).then(() => (article.hidden = false)),
    '解除隐藏',
    message,
  );

const deleteArticle = (article: ArticleSimplified) =>
  doAction(ArticleRepo.deleteArticle(article.id), '删除', message);
</script>

<template>
  <div class="layout-content">
    <n-h1>论坛</n-h1>

    <router-link v-if="whoami.hasForumAccess" to="/forum-edit">
      <c-button
        label="发布文章"
        :icon="PlusOutlined"
        style="margin-bottom: 16px"
      />
    </router-link>

    <n-flex vertical>
      <c-action-wrapper title="搜索">
        <div style="position: relative; display: flex; align-items: center">
          <n-input
            ref="searchInputInst"
            v-model:value="searchQuery"
            placeholder="搜索标题、a:作者、t:时间..."
            clearable
            class="search-input search-bar"
            style="width: 260px"
            @update:value="handleInput"
            @keyup.enter="onSearch"
          >
            <template #prefix>
              <n-flex
                :size="4"
                align="center"
                style="margin-right: 4px"
                :wrap="false"
              >
                <n-tag
                  v-for="(tag, i) in activeTags"
                  :key="i"
                  size="small"
                  :type="tag.type === 'a' ? 'info' : 'success'"
                  closable
                  round
                  @close="removeTag(i)"
                >
                  {{ tag.type === 'a' ? '作者' : '时间' }}: {{ tag.value }}
                </n-tag>
              </n-flex>
            </template>
            <template #suffix>
              <n-icon
                :component="SearchOutlined"
                @click="onSearch"
                style="cursor: pointer"
              />
            </template>
          </n-input>
          <n-popover trigger="click" placement="bottom-end">
            <template #trigger>
              <c-icon-button :icon="SettingsOutlined" style="margin-left: 8px" />
            </template>
            <n-flex vertical>
              <n-flex
                align="center"
                justify="space-between"
                style="width: 200px"
              >
                <n-text>标题模糊搜索</n-text>
                <n-switch v-model:value="searchSetting.fuzzyTitle" />
              </n-flex>
              <n-flex align="center" justify="space-between">
                <n-text>精确作者匹配</n-text>
                <n-switch v-model:value="searchSetting.exactAuthor" />
              </n-flex>
              <n-flex align="center" justify="space-between">
                <n-text>自动填入结束日期</n-text>
                <n-switch v-model:value="searchSetting.autoFillToDate" />
              </n-flex>
            </n-flex>
          </n-popover>
        </div>
      </c-action-wrapper>

      <c-action-wrapper title="版块">
        <c-radio-group
          :value="category"
          @update-value="onUpdateCategory"
          :options="articleCategoryOptions"
        />
      </c-action-wrapper>

      <c-action-wrapper title="排序" align="center">
        <order-sort v-model:value="currentSort" :options="articleSortOptions" />
      </c-action-wrapper>
    </n-flex>

    <CPage
      :page="page"
      :page-number="articlePage?.pageNumber"
      @update:page="onUpdatePage"
    >
      <n-table
        v-if="articlePage"
        :bordered="false"
        style="margin-top: 24px; margin-bottom: 24px"
      >
        <thead>
          <tr>
            <th><b>标题</b></th>
            <th class="article-number"><b>查看/回复</b></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="article of articlePage.items" :key="article.id">
            <td>
              <n-flex :size="2" align="center" :wrap="false">
                <n-icon
                  v-if="article.pinned"
                  size="15"
                  :component="PushPinOutlined"
                />
                <n-icon
                  v-if="article.locked"
                  size="15"
                  :component="LockOutlined"
                />
                <c-a :to="`/forum/${article.id}`">
                  <n-text v-if="article.hidden" depth="3">[隐藏]</n-text>
                  <n-text
                    v-else-if="blacklistStore.isBlocked(article.user.username)"
                    depth="3"
                  >
                    [屏蔽]
                  </n-text>
                  <b v-else>{{ article.title }}</b>
                </c-a>
              </n-flex>
              <n-text style="font-size: 12px">
                {{ article.updateAt === article.createAt ? '发布' : '更新' }}于
                <n-time :time="article.updateAt * 1000" type="relative" />
                by {{ article.user.username }}
              </n-text>

              <n-flex v-if="whoami.asAdmin" style="margin-top: 4px">
                <c-button
                  v-if="article.locked"
                  size="tiny"
                  secondary
                  label="解除锁定"
                  @action="unlockArticle(article)"
                />
                <c-button
                  v-else
                  label="锁定"
                  size="tiny"
                  secondary
                  @action="lockArticle(article)"
                />

                <c-button
                  v-if="article.pinned"
                  label="解除置顶"
                  size="tiny"
                  secondary
                  @action="unpinArticle(article)"
                />
                <c-button
                  v-else
                  label="置顶"
                  size="tiny"
                  secondary
                  @action="pinArticle(article)"
                />

                <c-button
                  v-if="article.hidden"
                  label="解除隐藏"
                  secondary
                  size="tiny"
                  @action="unhideArticle(article)"
                />
                <c-button
                  v-else
                  label="隐藏"
                  secondary
                  size="tiny"
                  @action="hideArticle(article)"
                />

                <c-button
                  size="tiny"
                  secondary
                  label="删除"
                  type="error"
                  @action="deleteArticle(article)"
                />
              </n-flex>
            </td>
            <td class="article-number">
              {{ article.numViews }}/{{ article.numComments }}
            </td>
          </tr>
        </tbody>
      </n-table>

      <CResultX v-else :error="error" title="加载错误" />
    </CPage>
  </div>
</template>

<style scoped>
.article-number {
  width: 50px;
  text-align: center;
}
</style>
