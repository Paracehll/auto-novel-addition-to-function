<script lang="ts" setup>
import { h } from 'vue';
import { useMessage, NButton, NButtonGroup, NTag } from 'naive-ui';
import { DeleteOutlineOutlined, EditOutlined, HistoryOutlined, AddOutlined } from '@vicons/material';
import { GlobalGlossaryApi } from '@/api/novel/GlobalGlossaryApi';
import { WebNovelApi } from '@/api/novel/WebNovelApi';
import { WenkuNovelApi } from '@/api/novel/WenkuNovelApi';
import type { GlobalGlossary, GlobalGlossaryRecord } from '@/model/GlobalGlossary';
import { useWhoamiStore } from '@/stores';
import { doAction } from '../util';
import { Glossary } from '@/model/Glossary';

const message = useMessage();
const whoamiStore = useWhoamiStore();

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
  contentRaw: '',
  tagRaw: '',
});

const openCreateModal = () => {
  isEditing.value = false;
  formModel.value = {
    uid: '',
    name: '',
    contentRaw: '',
    tagRaw: '',
  };
  showEditModal.value = true;
};

const openEditModal = (gg: GlobalGlossary) => {
  isEditing.value = true;
  formModel.value = {
    uid: gg.uid,
    name: gg.name,
    contentRaw: Glossary.toText(gg.content),
    tagRaw: (gg.tag || []).join(', '),
  };
  showEditModal.value = true;
};

const saveGlossary = () => {
  const content = Glossary.fromText(formModel.value.contentRaw);
  if (content === undefined) {
    message.error('术语表格式不正确，每行应为: 日文 => 中文');
    return;
  }

  const tag = formModel.value.tagRaw
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);

  const action = isEditing.value
    ? GlobalGlossaryApi.updateGlobalGlossary(formModel.value.uid, {
        name: formModel.value.name,
        content,
        tag,
      })
    : GlobalGlossaryApi.createGlobalGlossary({
        name: formModel.value.name,
        content,
        tag,
      });

  doAction(
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

const viewHistory = (gg: GlobalGlossary) => {
  selectedGlossary.value = gg;
  showHistoryModal.value = true;
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
      if (item.old === null) {
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
  if (!window.confirm('确定要将术语表还原到该历史状态吗？这将生成一条新的修改记录。')) return;

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
            title = novel.titleZh || url;
          }
        } catch {
          // Fallback to url if request fails
        }
        return { url, title };
      })
    );
    lazyUsedNovels.value = fetchedNovels;
  } catch (e: any) {
    message.error(`获取引用小说列表失败: ${e.message || e}`);
  } finally {
    usedLoading.value = false;
  }
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
              if (!row.tag || row.tag.length === 0) return h('span', { style: { color: 'var(--text-color-3)' } }, '无');
              return h(
                'div',
                { style: { display: 'flex', gap: '6px', flexWrap: 'wrap', padding: '4px 0' } },
                row.tag.map((t: string) =>
                  h(NTag, { type: 'info', size: 'small', round: true }, () => t)
                )
              );
            }
          },
          {
            title: '词条数量',
            key: 'termsCount',
            render: (row: any) => h('span', {}, Object.keys(row.content).length),
          },
          {
            title: '引用小說網址',
            key: 'used',
            render: (row: any) => {
              const count = row.used ? row.used.length : 0;
              return h(
                NButton,
                {
                  size: 'small',
                  onClick: () => viewUsedNovels(row.uid, row.name)
                },
                () => `查看 (${count})`
              );
            }
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
              h(
                NButtonGroup,
                { size: 'small' },
                () => [
                  h(
                    NButton,
                    { onClick: () => openEditModal(row) },
                    () => '编辑'
                  ),
                  h(
                    NButton,
                    { onClick: () => viewHistory(row) },
                    () => '修改历史'
                  ),
                  whoamiStore.whoami.isAdmin
                    ? h(
                        NButton,
                        { type: 'error', ghost: true, onClick: () => deleteGlossary(row.uid) },
                        () => '删除'
                      )
                    : null,
                ].filter(Boolean)
              ),
          },
        ]"
        :data="glossaries"
        :loading="loading"
      />

      <!-- Edit Modal -->
      <c-modal
        :title="isEditing ? '编辑全域术语表' : '新建全域术语表'"
        v-model:show="showEditModal"
        :extra-height="120"
      >
        <n-form label-placement="left" label-width="80">
          <n-form-item label="名称">
            <n-input
              v-model:value="formModel.name"
              placeholder="例如: 蔚蓝档案全域术语表"
            />
          </n-form-item>
          <n-form-item label="标签(tags)">
            <n-input
              v-model:value="formModel.tagRaw"
              placeholder="例如: 蔚蓝档案, 青春, 二次元 (逗号分隔)"
            />
          </n-form-item>
          <n-form-item label="术语内容">
            <n-input
              v-model:value="formModel.contentRaw"
              type="textarea"
              :rows="12"
              placeholder="格式: 日文 => 中文，每行一个词条。&#10;例如:&#10;先生 => 老师&#10;生徒 => 学生"
            />
          </n-form-item>
        </n-form>
        <template #action>
          <c-button label="提交" type="primary" @action="saveGlossary()" />
        </template>
      </c-modal>

      <!-- History Modal -->
      <c-modal
        title="全域术语表修改历史"
        v-model:show="showHistoryModal"
        :extra-height="120"
      >
        <template v-if="selectedGlossary">
          <n-scrollbar style="max-height: 50vh">
            <n-timeline v-if="selectedGlossary.record.length > 0">
              <n-timeline-item
                v-for="(rec, index) in [...selectedGlossary.record].reverse()"
                :key="index"
                type="info"
                :title="`修改历史 (${formatDate(rec.date)})`"
              >
                <n-space justify="space-between" align="center" style="margin-top: 4px; margin-bottom: 8px">
                  <n-text depth="3">修改词条详情：</n-text>
                  <n-button size="tiny" type="primary" secondary @click="handleRollback(selectedGlossary.record.length - 1 - index)">
                    回滚
                  </n-button>
                </n-space>
                <n-table size="small" striped>
                  <thead>
                    <tr>
                      <th>词条</th>
                      <th>变更 (原值 => 新值)</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(diffItem, key) in rec.diff" :key="key">
                      <td style="font-weight: bold">{{ key }}</td>
                      <td>
                        <template v-if="diffItem.old === null">
                          <n-tag type="success" size="small">新增</n-tag>
                          &nbsp;{{ diffItem.new }}
                        </template>
                        <template v-else-if="diffItem.new === null">
                          <n-tag type="error" size="small">删除</n-tag>
                          &nbsp;<del>{{ diffItem.old }}</del>
                        </template>
                        <template v-else>
                          <n-tag type="warning" size="small">修改</n-tag>
                          &nbsp;{{ diffItem.old }} => {{ diffItem.new }}
                        </template>
                      </td>
                    </tr>
                  </tbody>
                </n-table>
              </n-timeline-item>
            </n-timeline>
            <n-empty v-else description="无修改记录" />
          </n-scrollbar>
        </template>
      </c-modal>

      <!-- Used Novels Modal (Lazy loaded) -->
      <c-modal
        title="引用该术语表的小说列表"
        v-model:show="showUsedModal"
        :extra-height="120"
      >
        <n-space vertical size="medium">
          <n-spin :show="usedLoading">
            <n-scrollbar style="max-height: 50vh">
              <div v-if="lazyUsedNovels.length > 0">
                <div v-for="item in lazyUsedNovels" :key="item.url" style="margin: 8px 0; padding: 8px 0; border-bottom: 1px solid var(--border-color)">
                  <a
                    :href="item.url"
                    target="_blank"
                    style="color: var(--primary-color); text-decoration: none; font-size: 14px; font-weight: 500"
                  >
                    {{ item.title }}
                  </a>
                </div>
              </div>
              <n-empty v-else-if="!usedLoading" description="暂无小说引用该术语表" />
            </n-scrollbar>
          </n-spin>
        </n-space>
      </c-modal>
    </n-space>
  </div>
</template>
