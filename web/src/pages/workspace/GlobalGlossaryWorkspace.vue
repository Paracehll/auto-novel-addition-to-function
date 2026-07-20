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
  SearchOutlined,
} from '@vicons/material';
import OrderSort from '@/components/OrderSort.vue';
import { GlobalGlossaryApi } from '@/api/novel/GlobalGlossaryApi';
import type {
  GlobalGlossaryInfo,
  GlobalGlossaryHistory,
  GlobalGlossaryRecord,
} from '@/model/GlobalGlossary';
import { useWhoamiStore } from '@/stores';
import { doAction } from '../util';
import { Glossary } from '@/model/Glossary';
import { isEqual } from 'lodash-es';

const message = useMessage();
const whoamiStore = useWhoamiStore();
const themeVars = useThemeVars();

const glossaries = ref<GlobalGlossaryInfo[]>([]);
const loading = ref(false);

const searchQuery = ref('');
const sortState = ref({
  value: 'default',
  desc: true,
});

const loadGlossaries = async () => {
  loading.value = true;
  try {
    glossaries.value = await GlobalGlossaryApi.listGlobalGlossariesInfo(true);
  } catch (e: any) {
    message.error(`加载全域术语表失败: ${e.message || e}`);
  } finally {
    loading.value = false;
  }
};

const sortedAndFilteredGlossaries = computed(() => {
  let list = [...glossaries.value];

  // 1. Filter
  const query = searchQuery.value.trim();
  if (query) {
    const tokens = query.split(/\s+/).filter(Boolean);
    if (tokens.length > 0) {
      list = list.filter((gg) => {
        return tokens.some((token) => {
          if (token.endsWith('$')) {
            const tagQuery = token.slice(0, -1).toLowerCase();
            return (gg.tag || []).some((t: string) =>
              t.toLowerCase().includes(tagQuery),
            );
          } else {
            const nameQuery = token.toLowerCase();
            return (gg.name || '').toLowerCase().includes(nameQuery);
          }
        });
      });
    }
  }

  // 2. Sort
  list.sort((a, b) => {
    let cmp = 0;
    if (sortState.value.value === 'default') {
      cmp = a.id.localeCompare(b.id);
    } else if (sortState.value.value === 'update') {
      cmp = (a.update || 0) - (b.update || 0);
    } else if (sortState.value.value === 'termsCount') {
      const countA = a.termsCount ?? 0;
      const countB = b.termsCount ?? 0;
      cmp = countA - countB;
    } else if (sortState.value.value === 'used') {
      const usedA = a.usedCount || 0;
      const usedB = b.usedCount || 0;
      cmp = usedA - usedB;
    }

    if (cmp === 0) {
      cmp = a.id.localeCompare(b.id);
    }

    return sortState.value.desc ? -cmp : cmp;
  });

  return list;
});

onMounted(() => {
  loadGlossaries();
});

// Create / Edit modal state
const showEditModal = ref(false);
const isEditing = ref(false);
const formModel = ref({
  id: '',
  name: '',
  content: {} as Glossary,
  tagRaw: '',
});

const originalFormModel = ref<any>(null);
const isSaved = ref(false);

const openCreateModal = () => {
  isEditing.value = false;
  isSaved.value = false;
  formModel.value = {
    id: '',
    name: '',
    content: {},
    tagRaw: '',
  };
  originalFormModel.value = JSON.parse(JSON.stringify(formModel.value));
  showEditModal.value = true;
};

const openEditModal = async (gg: GlobalGlossaryInfo) => {
  isEditing.value = true;
  isSaved.value = false;
  try {
    const termsGg = await GlobalGlossaryApi.getGlobalGlossaryTerms(gg.id);
    formModel.value = {
      id: termsGg.id,
      name: gg.name,
      content: { ...termsGg.terms },
      tagRaw: (gg.tag || []).join(', '),
    };
    originalFormModel.value = JSON.parse(JSON.stringify(formModel.value));
    showEditModal.value = true;
  } catch (e: any) {
    message.error(`获取术语表详情失败: ${e.message || e}`);
  }
};

const handleUpdateShow = async (value: boolean) => {
  if (value === false) {
    if (isSaved.value) {
      showEditModal.value = false;
      return;
    }
    const hasChanges =
      !isEqual(toRaw(formModel.value), originalFormModel.value) ||
      !isEqual(
        Object.keys(toRaw(formModel.value.content || {})),
        Object.keys(originalFormModel.value.content || {}),
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
    ? GlobalGlossaryApi.updateGlobalGlossary(formModel.value.id, {
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
      isSaved.value = true;
      showEditModal.value = false;
      loadGlossaries();
    }),
    isEditing.value ? '修改全域术语表' : '创建全域术语表',
    message,
  );
};

const deleteGlossary = (id: string) => {
  if (window.confirm(`确定要删除全域术语表吗？此操作不可恢复。`)) {
    doAction(
      GlobalGlossaryApi.deleteGlobalGlossary(id).then(() => {
        loadGlossaries();
      }),
      '删除全域术语表',
      message,
    );
  }
};

// History modal state
const showHistoryModal = ref(false);
const selectedGlossary = ref<GlobalGlossaryHistory | null>(null);

const viewHistory = async (gg: GlobalGlossaryInfo) => {
  try {
    selectedGlossary.value = await GlobalGlossaryApi.getGlobalGlossaryHistory(gg.id);
    showHistoryModal.value = true;
  } catch (e: any) {
    message.error(`获取修改历史失败: ${e.message || e}`);
  }
};

const formatDate = (dateSeconds: number) => {
  return new Date(dateSeconds * 1000).toLocaleString('zh-CN');
};

const handleRollback = async (targetIndex: number) => {
  if (!selectedGlossary.value) return;
  if (
    !window.confirm(
      '确定要将术语表还原到该历史状态吗？这将生成一条新的修改记录。',
    )
  )
    return;

  try {
    const gg = glossaries.value.find((g) => g.id === selectedGlossary.value?.id);
    const name = gg?.name || '';
    const tag = gg?.tag || [];

    const termsGg = await GlobalGlossaryApi.getGlobalGlossaryTerms(selectedGlossary.value.id);
    const currentContent = { ...termsGg.terms };
    const records = selectedGlossary.value.record;

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

    doAction(
      GlobalGlossaryApi.updateGlobalGlossary(selectedGlossary.value.id, {
        name: name,
        content: currentContent,
        tag: tag,
      }).then(() => {
        showHistoryModal.value = false;
        loadGlossaries();
      }),
      '回滚全域术语表',
      message,
    );
  } catch (e: any) {
    message.error(`回滚全域术语表失败: ${e.message || e}`);
  }
};

const deleteHistoryRecord = (targetIndex: number) => {
  if (!selectedGlossary.value) return;
  if (!window.confirm('确定要彻底删除这条历史修改记录吗？此操作不可恢复。'))
    return;

  doAction(
    GlobalGlossaryApi.deleteGlobalGlossaryRecord(
      selectedGlossary.value.id,
      targetIndex,
    ).then(async () => {
      if (selectedGlossary.value) {
        const latest = await GlobalGlossaryApi.getGlobalGlossaryHistory(
          selectedGlossary.value.id,
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
const selectedGlossaryName = ref('');

const lazyUsedNovels = ref<{ url: string; label: string }[]>([]);

const viewUsedNovels = (name: string, usedMap?: Record<string, Record<string, string[]>>) => {
  selectedGlossaryName.value = name;
  const list: { url: string; label: string }[] = [];
  if (usedMap) {
    for (const type of Object.keys(usedMap)) {
      const providerMap = usedMap[type];
      for (const provider of Object.keys(providerMap)) {
        const idList = providerMap[provider];
        for (const id of idList) {
          const url = type === 'web' ? `/novel/${provider}/${id}` : `/wenku/${id}`;
          list.push({
            url,
            label: `[${type}] [${provider}] ${id}`,
          });
        }
      }
    }
  }
  lazyUsedNovels.value = list;
  showUsedModal.value = true;
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

    <n-space vertical size="large">
      <c-button
        label="新建全域术语表"
        :icon="AddOutlined"
        @action="openCreateModal()"
      />

      <bulletin>
        <n-p>
          全域术语表可以被多本小说同时引用，并且独立术语表会优先覆盖全域术语表的同名项。
        </n-p>
      </bulletin>

      <n-flex style="margin-top: 16px; margin-bottom: 8px">
        <n-input
          v-model:value="searchQuery"
          placeholder="搜索标题或标签 (例如: BA Re0$)"
          clearable
          size="small"
          style="width: 300px"
        >
          <template #suffix>
            <n-icon :component="SearchOutlined" />
          </template>
        </n-input>
        <OrderSort
          v-model:value="sortState"
          :options="[
            { label: '默认', value: 'default' },
            { label: '词条数', value: 'termsCount' },
            { label: '引用数', value: 'used' },
            { label: '更新时间', value: 'update' },
          ]"
        />
      </n-flex>

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
              const count = row.usedCount ?? 0;
              return h(
                NButton,
                {
                  size: 'small',
                  onClick: () => viewUsedNovels(row.name, row.used),
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
                          onClick: () => deleteGlossary(row.id),
                        },
                        () => '删除',
                      )
                    : null,
                ].filter(Boolean),
              ),
          },
        ]"
        :data="sortedAndFilteredGlossaries"
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
                          {{ formatDate(rec.date) }} &emsp; by {{ rec.by }}
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

      <!-- Used Novels Modal -->
      <c-modal
        title="引用该术语表的小说列表"
        v-model:show="showUsedModal"
        :max-height-percentage="85"
        :extra-height="120"
      >
        <n-space vertical size="medium">
          <div v-if="lazyUsedNovels.length > 0">
            <div
              v-for="item in lazyUsedNovels"
              :key="item.url"
              style="
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
                  font-family: monospace;
                  font-weight: 500;
                "
              >
                {{ item.label }}
              </a>
            </div>
          </div>
          <n-empty
            v-else
            description="暂无小说引用该术语表"
          />
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
