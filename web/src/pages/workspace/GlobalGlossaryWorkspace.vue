<script lang="ts" setup>
import { h } from 'vue';
import {
  useMessage,
  NButton,
  NButtonGroup,
  NTag,
  useThemeVars,
} from 'naive-ui';
import {
  DeleteOutlineOutlined,
  EditOutlined,
  HistoryOutlined,
  AddOutlined,
} from '@vicons/material';
import { GlobalGlossaryApi } from '@/api/novel/GlobalGlossaryApi';
import { WebNovelApi } from '@/api/novel/WebNovelApi';
import { WenkuNovelApi } from '@/api/novel/WenkuNovelApi';
import type {
  GlobalGlossary,
  GlobalGlossaryRecord,
} from '@/model/GlobalGlossary';
import { useWhoamiStore } from '@/stores';
import { doAction, copyToClipBoard } from '../util';
import { Glossary } from '@/model/Glossary';
import { isEqual } from 'lodash-es';

const message = useMessage();
const whoamiStore = useWhoamiStore();
const themeVars = useThemeVars();

const glossaries = ref<GlobalGlossary[]>([]);
const loading = ref(false);

const loadGlossaries = async () => {
  loading.value = true;
  try {
    glossaries.value = await GlobalGlossaryApi.listGlobalGlossaries();
  } catch (e: any) {
    message.error(`加载全域术语表失败: ${e.message || e}`);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadGlossaries();
});

// Create / Edit modal state
const showEditModal = ref(false);
const isEditing = ref(false);
const formModel = ref({
  uid: '',
  name: '',
  content: {} as Glossary,
  tagRaw: '',
});

const originalFormModel = ref<any>(null);

const openCreateModal = () => {
  isEditing.value = false;
  formModel.value = {
    uid: '',
    name: '',
    content: {},
    tagRaw: '',
  };
  originalFormModel.value = JSON.parse(JSON.stringify(formModel.value));
  showEditModal.value = true;
};

const openEditModal = async (gg: GlobalGlossary) => {
  isEditing.value = true;
  try {
    const fullGg = await GlobalGlossaryApi.getGlobalGlossary(gg.uid);
    formModel.value = {
      uid: fullGg.uid,
      name: fullGg.name,
      content: { ...fullGg.content },
      tagRaw: (fullGg.tag || []).join(', '),
    };
    originalFormModel.value = JSON.parse(JSON.stringify(formModel.value));
    showEditModal.value = true;
  } catch (e: any) {
    message.error(`获取术语表详情失败: ${e.message || e}`);
  }
};

const handleUpdateShow = async (value: boolean) => {
  if (value === false) {
    const hasChanges = !isEqual(
      toRaw(formModel.value),
      originalFormModel.value,
    );
    if (hasChanges) {
      if (window.confirm('术语表有未保存的修改，是否保存？')) {
        await saveGlossary();
      } else {
        showEditModal.value = false;
      }
    } else {
      showEditModal.value = false;
    }
  } else {
    showEditModal.value = true;
  }
};

const saveGlossary = () => {
  const tag = formModel.value.tagRaw
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);

  const action = isEditing.value
    ? GlobalGlossaryApi.updateGlobalGlossary(formModel.value.uid, {
        name: formModel.value.name,
        content: formModel.value.content,
        tag,
      })
    : GlobalGlossaryApi.createGlobalGlossary({
        name: formModel.value.name,
        content: formModel.value.content,
        tag,
      });

  return doAction(
    action.then(() => {
      showEditModal.value = false;
      loadGlossaries();
    }),
    isEditing.value ? '修改全域术语表' : '创建全域术语表',
    message,
  );
};

const deleteGlossary = (uid: string) => {
  if (window.confirm(`确定要删除全域术语表吗？此操作不可恢复。`)) {
    doAction(
      GlobalGlossaryApi.deleteGlobalGlossary(uid).then(() => {
        loadGlossaries();
      }),
      '删除全域术语表',
      message,
    );
  }
};

// History modal state
const showHistoryModal = ref(false);
const selectedGlossary = ref<GlobalGlossary | null>(null);

const viewHistory = async (gg: GlobalGlossary) => {
  try {
    selectedGlossary.value = await GlobalGlossaryApi.getGlobalGlossary(gg.uid);
    showHistoryModal.value = true;
  } catch (e: any) {
    message.error(`获取修改历史失败: ${e.message || e}`);
  }
};

const formatDate = (dateSeconds: number) => {
  return new Date(dateSeconds * 1000).toLocaleString('zh-CN');
};

// Rollback Logic
const rollbackToRecord = (targetIndex: number) => {
  if (!selectedGlossary.value) return {};
  const currentContent = { ...selectedGlossary.value.content };
  const records = selectedGlossary.value.record;

  // We need to apply diffs in reverse order from the last record down to (and including) targetIndex + 1
  for (let j = records.length - 1; j > targetIndex; j--) {
    const rec = records[j];
    for (const key in rec.diff) {
      const item = rec.diff[key];
      if (item.old === null || item.old === '') {
        delete currentContent[key];
      } else {
        currentContent[key] = item.old;
      }
    }
  }
  return currentContent;
};

const handleRollback = (targetIndex: number) => {
  if (!selectedGlossary.value) return;
  if (
    !window.confirm(
      '确定要将术语表还原到该历史状态吗？这将生成一条新的修改记录。',
    )
  )
    return;

  const rolledBackContent = rollbackToRecord(targetIndex);

  doAction(
    GlobalGlossaryApi.updateGlobalGlossary(selectedGlossary.value.uid, {
      name: selectedGlossary.value.name,
      content: rolledBackContent,
      tag: selectedGlossary.value.tag,
    }).then(() => {
      showHistoryModal.value = false;
      loadGlossaries();
    }),
    '回滚全域术语表',
    message,
  );
};

const deleteHistoryRecord = (targetIndex: number) => {
  if (!selectedGlossary.value) return;
  if (!window.confirm('确定要彻底删除这条历史修改记录吗？此操作不可恢复。'))
    return;

  doAction(
    GlobalGlossaryApi.deleteGlobalGlossaryRecord(
      selectedGlossary.value.uid,
      targetIndex,
    ).then(async () => {
      if (selectedGlossary.value) {
        const latest = await GlobalGlossaryApi.getGlobalGlossary(
          selectedGlossary.value.uid,
        );
        selectedGlossary.value = latest;
      }
      loadGlossaries();
    }),
    '删除历史记录',
    message,
  );
};

// Used Novels modal state (lazy loaded)
const showUsedModal = ref(false);
const usedLoading = ref(false);
const selectedGlossaryName = ref('');

interface UsedNovelItem {
  url: string;
  title: string;
}
const lazyUsedNovels = ref<UsedNovelItem[]>([]);

const viewUsedNovels = async (uid: string, name: string) => {
  selectedGlossaryName.value = name;
  lazyUsedNovels.value = [];
  showUsedModal.value = true;
  usedLoading.value = true;
  try {
    const detail = await GlobalGlossaryApi.getGlobalGlossary(uid);
    const urls = detail.used || [];

    // Fetch titles in parallel
    const fetchedNovels = await Promise.all(
      urls.map(async (url) => {
        let title = url;
        try {
          if (url.startsWith('/novel/')) {
            const parts = url.split('/');
            const providerId = parts[2];
            const novelId = parts[3];
            const novel = await WebNovelApi.getNovel(providerId, novelId);
            title = novel.titleZh || novel.titleJp || url;
          } else if (url.startsWith('/wenku/')) {
            const parts = url.split('/');
            const novelId = parts[2];
            const novel = await WenkuNovelApi.getNovel(novelId);
            title = novel.titleZh || novel.title || url;
          }
        } catch {
          // Fallback to url if request fails
        }
        return { url, title };
      }),
    );
    lazyUsedNovels.value = fetchedNovels;
  } catch (e: any) {
    message.error(`获取引用小说列表失败: ${e.message || e}`);
  } finally {
    usedLoading.value = false;
  }
};

const getRecordTimelineType = (rec: GlobalGlossaryRecord) => {
  const items = Object.values(rec.diff);
  if (items.length === 0) return 'info';

  const isAllAdd = items.every((it) => !it.old || it.old.trim() === '');
  if (isAllAdd) return 'success';

  const isAllDel = items.every((it) => !it.new || it.new.trim() === '');
  if (isAllDel) return 'error';

  return 'warning';
};

const getDiffType = (
  oldVal: string | null | undefined,
  newVal: string | null | undefined,
) => {
  if (!oldVal || oldVal.trim() === '') {
    return 'add';
  } else if (!newVal || newVal.trim() === '') {
    return 'delete';
  } else {
    return 'update';
  }
};

const getAddCount = (rec: GlobalGlossaryRecord) => {
  let count = 0;
  for (const key in rec.diff) {
    const item = rec.diff[key];
    const type = getDiffType(item.old, item.new);
    if (type === 'add' || type === 'update') {
      count++;
    }
  }
  return count;
};

const getDelCount = (rec: GlobalGlossaryRecord) => {
  let count = 0;
  for (const key in rec.diff) {
    const item = rec.diff[key];
    const type = getDiffType(item.old, item.new);
    if (type === 'delete' || type === 'update') {
      count++;
    }
  }
  return count;
};
</script>

<template>
  <div class="layout-content">
    <n-h1>全域术语表</n-h1>

    <n-space vertical size="large" style="margin-top: 16px">
      <c-button
        label="新建全域术语表"
        :icon="AddOutlined"
        @action="openCreateModal()"
      />
      <n-text depth="3">
        全域术语表可以被多本小说同时引用，并且独立术语表会优先覆盖全域术语表的同名项。
      </n-text>

      <n-data-table
        :columns="[
          { title: '名称', key: 'name' },
          {
            title: '标签',
            key: 'tag',
            render: (row: any) => {
              if (!row.tag || row.tag.length === 0)
                return h(
                  'span',
                  { style: { color: 'var(--text-color-3)' } },
                  '无',
                );
              return h(
                'div',
                {
                  style: {
                    display: 'flex',
                    gap: '6px',
                    flexWrap: 'wrap',
                    padding: '4px 0',
                  },
                },
                row.tag.map((t: string) =>
                  h(
                    NTag,
                    { type: 'info', size: 'small', round: true },
                    () => t,
                  ),
                ),
              );
            },
          },
          {
            title: '词条数量',
            key: 'termsCount',
            render: (row: any) => h('span', {}, row.termsCount ?? 0),
          },
          {
            title: '引用',
            key: 'used',
            render: (row: any) => {
              const count = row.used ? row.used.length : 0;
              return h(
                NButton,
                {
                  size: 'small',
                  onClick: () => viewUsedNovels(row.uid, row.name),
                },
                () => `查看 (${count})`,
              );
            },
          },
          {
            title: '更新日期',
            key: 'update',
            render: (row: any) => formatDate(row.update),
          },
          {
            title: '操作',
            key: 'actions',
            render: (row: any) =>
              h(NButtonGroup, { size: 'small' }, () =>
                [
                  h(
                    NButton,
                    { onClick: () => openEditModal(row) },
                    () => '编辑',
                  ),
                  h(
                    NButton,
                    { onClick: () => viewHistory(row) },
                    () => '修改历史',
                  ),
                  whoamiStore.whoami.isAdmin
                    ? h(
                        NButton,
                        {
                          type: 'error',
                          ghost: true,
                          onClick: () => deleteGlossary(row.uid),
                        },
                        () => '删除',
                      )
                    : null,
                ].filter(Boolean),
              ),
          },
        ]"
        :data="glossaries"
        :loading="loading"
      />

      <!-- Edit Modal (With rich inline terminology editing like GlossaryButton.vue) -->
      <c-modal
        :title="isEditing ? '编辑全域术语表' : '新建全域术语表'"
        :show="showEditModal"
        @update:show="handleUpdateShow"
        :max-height-percentage="85"
        :extra-height="120"
      >
        <n-flex vertical size="large" style="margin-bottom: 24px; width: 80%">
          <!-- IndieGlossaryEdit component handles terms editing -->
          <indie-glossary-edit v-model="formModel.content" />

          <!-- Form Details Placed BELOW the Terms Content -->
          <n-form
            label-placement="left"
            label-width="80"
            style="
              margin-top: 16px;
              border-top: 1px dashed var(--border-color);
              padding-top: 16px;
            "
          >
            <n-form-item label="名称">
              <n-input
                v-model:value="formModel.name"
                placeholder="例如: 蔚蓝档案全域术语表"
              />
            </n-form-item>
            <n-form-item label="标签">
              <n-input
                v-model:value="formModel.tagRaw"
                placeholder="逗号分隔: ブルーアーカイブ, 曇りせ, TS"
              />
            </n-form-item>
          </n-form>
        </n-flex>

        <template #action>
          <c-button label="提交" type="primary" @action="saveGlossary()" />
        </template>
      </c-modal>

      <!-- History Modal -->
      <c-modal
        title="全域术语表修改历史"
        v-model:show="showHistoryModal"
        :max-height-percentage="85"
        :extra-height="120"
      >
        <template v-if="selectedGlossary">
          <div
            v-if="selectedGlossary.record.length > 0"
            style="margin-bottom: 16px"
          >
            <n-table
              size="small"
              :bordered="false"
              style="table-layout: fixed; width: 100%"
            >
              <thead>
                <tr>
                  <th style="width: 80px; padding-left: 36px">类型</th>
                  <th style="width: 150px">词条</th>
                  <th>变更</th>
                </tr>
              </thead>
            </n-table>
          </div>

          <n-timeline v-if="selectedGlossary.record.length > 0">
            <n-timeline-item
              v-for="(rec, index) in [...selectedGlossary.record].reverse()"
              :key="index"
              :type="getRecordTimelineType(rec)"
            >
              <template #default>
                <n-collapse :default-expanded-names="[]">
                  <n-collapse-item :name="index.toString()">
                    <template #header>
                      <n-space align="center">
                        <span
                          v-if="getAddCount(rec) > 0"
                          :style="{
                            color: themeVars.successColor,
                            fontWeight: 'bold',
                          }"
                        >
                          +{{ getAddCount(rec) }}
                        </span>
                        <span
                          v-if="getDelCount(rec) > 0"
                          :style="{
                            color: themeVars.errorColor,
                            fontWeight: 'bold',
                          }"
                        >
                          -{{ getDelCount(rec) }}
                        </span>
                        <n-text depth="3">
                          {{ formatDate(rec.date) }}
                        </n-text>
                      </n-space>
                    </template>
                    <template #header-extra>
                      <n-button
                        size="tiny"
                        type="primary"
                        secondary
                        @click.stop="
                          handleRollback(
                            selectedGlossary.record.length - 1 - index,
                          )
                        "
                      >
                        回滚
                      </n-button>
                      <n-button
                        v-if="whoamiStore.whoami.isAdmin"
                        size="tiny"
                        type="error"
                        secondary
                        @click.stop="
                          deleteHistoryRecord(
                            selectedGlossary.record.length - 1 - index,
                          )
                        "
                      >
                        删除
                      </n-button>
                    </template>

                    <n-table
                      size="small"
                      striped
                      style="table-layout: fixed; width: 100%"
                    >
                      <tbody>
                        <tr v-for="(diffItem, key) in rec.diff" :key="key">
                          <td style="width: 80px">
                            <n-tag
                              v-if="
                                getDiffType(diffItem.old, diffItem.new) ===
                                'add'
                              "
                              type="success"
                              size="small"
                              :round="false"
                            >
                              新增
                            </n-tag>
                            <n-tag
                              v-else-if="
                                getDiffType(diffItem.old, diffItem.new) ===
                                'delete'
                              "
                              type="error"
                              size="small"
                              :round="false"
                            >
                              删除
                            </n-tag>
                            <n-tag
                              v-else
                              type="warning"
                              size="small"
                              :round="false"
                            >
                              修改
                            </n-tag>
                          </td>
                          <td style="font-weight: bold; width: 150px">
                            {{ key }}
                          </td>
                          <td>
                            <template
                              v-if="
                                getDiffType(diffItem.old, diffItem.new) ===
                                'add'
                              "
                            >
                              <span style="color: var(--n-text-color)">
                                {{ diffItem.new }}
                              </span>
                            </template>
                            <template
                              v-else-if="
                                getDiffType(diffItem.old, diffItem.new) ===
                                'delete'
                              "
                            >
                              <del style="color: var(--error-color)">
                                {{ diffItem.old }}
                              </del>
                            </template>
                            <template v-else>
                              <span style="color: var(--n-text-color)">
                                {{ diffItem.old }} => {{ diffItem.new }}
                              </span>
                            </template>
                          </td>
                        </tr>
                      </tbody>
                    </n-table>
                  </n-collapse-item>
                </n-collapse>
              </template>
            </n-timeline-item>
          </n-timeline>
          <n-empty v-else description="无修改记录" />
        </template>
      </c-modal>

      <!-- Used Novels Modal (Lazy loaded) -->
      <c-modal
        title="引用该术语表的小说列表"
        v-model:show="showUsedModal"
        :max-height-percentage="85"
        :extra-height="120"
      >
        <n-space vertical size="medium">
          <n-spin :show="usedLoading">
            <div v-if="lazyUsedNovels.length > 0">
              <div
                v-for="item in lazyUsedNovels"
                :key="item.url"
                style="
                  margin: 8px 0;
                  padding: 8px 0;
                  border-bottom: 1px solid var(--border-color);
                "
              >
                <a
                  :href="item.url"
                  target="_blank"
                  style="
                    color: var(--n-text-color);
                    text-decoration: none;
                    font-size: 14px;
                    font-weight: 500;
                  "
                >
                  {{ item.title }}
                </a>
              </div>
            </div>
            <n-empty
              v-else-if="!usedLoading"
              description="暂无小说引用该术语表"
            />
          </n-spin>
        </n-space>
      </c-modal>
    </n-space>
  </div>
</template>

<style scoped>
:deep(.n-timeline-item-timeline__circle) {
  border-color: #fff !important;
}
:deep(.n-timeline-item-timeline__icon) {
  color: #fff !important;
}
</style>
