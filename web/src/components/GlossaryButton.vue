<script lang="ts" setup>
import {
  FileDownloadOutlined,
  ContentCopyOutlined,
  DownloadOutlined,
  ArrowUpwardOutlined,
  ArrowDownwardOutlined,
} from '@vicons/material';
import { isEqual } from 'lodash-es';

import { WebNovelApi, WenkuNovelApi } from '@/api';
import { GlobalGlossaryApi } from '@/api/novel/GlobalGlossaryApi';
import { GenericNovelId } from '@/model/Common';
import { Glossary } from '@/model/Glossary';
import type { GlobalGlossaryFull } from '@/model/GlobalGlossary';
import { copyToClipBoard, doAction } from '@/pages/util';
import { useLocalVolumeStore, useWhoamiStore } from '@/stores';
import OrderSort from '@/components/OrderSort.vue';
import { checkIsMobile, downloadFile } from '@/util';
import { type CSSProperties } from 'vue';

const props = withDefaults(
  defineProps<{
    gnid?: GenericNovelId;
    value: Glossary;
    showGlobal?: boolean;
  }>(),
  {
    showGlobal: true,
  },
);

const isSaved = ref(false);

const message = useMessage();

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const glossary = ref<Glossary>({});
const linkedGlossaries = ref<string[]>([]);
const allGlobalGlossaries = ref<GlobalGlossaryFull[]>([]);
const novelKeywords = ref<string[]>([]);

const showGlossaryModal = ref(false);

const sortState = ref({
  value: 'default',
  desc: true,
});

const getMatchCount = (gg: GlobalGlossaryFull) => {
  if (!gg.tag || gg.tag.length === 0 || novelKeywords.value.length === 0)
    return 0;
  const ggTags = new Set(gg.tag);
  let count = 0;
  for (const k of novelKeywords.value) {
    if (ggTags.has(k)) {
      count++;
    }
  }
  return count;
};

const globalGlossariesOptions = computed(() => {
  const sorted = [...allGlobalGlossaries.value];
  const presentUids = new Set(sorted.map((gg) => gg.id));
  // 已刪除的全域術語表
  for (const _id of linkedGlossaries.value) {
    if (!presentUids.has(_id)) {
      sorted.push({
        id: _id,
        name: '[已删除的术语表]',
        terms: {},
        termsCount: 0,
        used: [],
        usedCount: 0,
        update: 0,
        tag: [],
        record: [],
        version: 1,
      });
    }
  }
  sorted.sort((a, b) => {
    let valA = 0;
    let valB = 0;

    if (sortState.value.value === 'default') {
      valA = getMatchCount(a);
      valB = getMatchCount(b);
    } else if (sortState.value.value === 'used') {
      valA = a.usedCount || 0;
      valB = b.usedCount || 0;
    } else if (sortState.value.value === 'update') {
      valA = new Date(a.update).getTime();
      valB = new Date(b.update).getTime();
    }

    if (valA === valB) {
      return a.id.localeCompare(b.id);
    }

    if (sortState.value.desc) {
      return valB - valA;
    } else {
      return valA - valB;
    }
  });

  return sorted.map((gg) => {
    const matchCount = getMatchCount(gg);
    const usedCount = gg.usedCount || 0;
    const matchLabel = matchCount > 0 ? ` (匹配标签: ${matchCount})` : '';
    return {
      label: `${gg.name} [ ${gg.termsCount ?? 0} ] [引用: ${usedCount}次]${matchLabel}`,
      value: gg.id,
    };
  });
});

const activeGlossariesMap = ref<Record<string, GlobalGlossaryFull>>({});
const fullyLoadedGlossaryIds = ref<Set<string>>(new Set());

const expandedActiveGlossaries = ref<string[]>([]);
const handleExpandedNamesChange = (
  names: string | number | (string | number)[],
) => {
  if (Array.isArray(names)) {
    expandedActiveGlossaries.value = names.map(String);
  } else if (names !== null && names !== undefined) {
    expandedActiveGlossaries.value = [String(names)];
  } else {
    expandedActiveGlossaries.value = [];
  }
};

const expandedGlobalConfig = ref<string[]>([]);

watch(
  linkedGlossaries,
  (newVal) => {
    const nextMap = { ...activeGlossariesMap.value };
    let updated = false;
    for (const id of newVal) {
      if (!nextMap[id]) {
        const metaGg = allGlobalGlossaries.value.find((gg) => gg.id === id);
        if (metaGg) {
          nextMap[id] = {
            ...metaGg,
            terms: {},
            used: [],
            record: [],
          } as GlobalGlossaryFull;
          updated = true;
        } else {
          nextMap[id] = {
            id,
            name: '[已删除的术语表]',
            terms: {},
            termsCount: 0,
            used: [],
            usedCount: 0,
            update: 0,
            tag: [],
            record: [],
            version: 1,
          };
          updated = true;
        }
      }
    }
    if (updated) {
      activeGlossariesMap.value = nextMap;
    }
  },
  { immediate: true, deep: true },
);

watch(
  expandedActiveGlossaries,
  async (newVal) => {
    for (const id of newVal) {
      if (!fullyLoadedGlossaryIds.value.has(id)) {
        try {
          const termsGg = await GlobalGlossaryApi.getGlobalGlossaryTerms(id);
          activeGlossariesMap.value = {
            ...activeGlossariesMap.value,
            [id]: {
              ...activeGlossariesMap.value[id],
              terms: termsGg.terms,
              version: termsGg.version,
            },
          };
          fullyLoadedGlossaryIds.value.add(id);
        } catch (e: any) {
          message.error(`获取全域术语表详情失败: ${e.message || e}`);
        }
      }
    }
  },
  { deep: true },
);

const ensureAllActiveGlossariesLoaded = async () => {
  const promises = linkedGlossaries.value.map(async (id) => {
    if (!fullyLoadedGlossaryIds.value.has(id)) {
      try {
        const termsGg = await GlobalGlossaryApi.getGlobalGlossaryTerms(id);
        activeGlossariesMap.value = {
          ...activeGlossariesMap.value,
          [id]: {
            ...activeGlossariesMap.value[id],
            terms: termsGg.terms,
            version: termsGg.version,
          },
        };
        fullyLoadedGlossaryIds.value.add(id);
      } catch (e) {
        console.error('Failed to load global glossary content', id, e);
      }
    }
  });
  await Promise.all(promises);
};

watch(
  expandedGlobalConfig,
  async (newVal) => {
    if (newVal.includes('global-config')) {
      await ensureAllActiveGlossariesLoaded();
    }
  },
  { deep: true },
);

const activeGlobalGlossaries = computed(() => {
  return linkedGlossaries.value
    .map((id) => activeGlossariesMap.value[id])
    .filter((gg): gg is GlobalGlossaryFull => gg !== undefined);
});

const totalGlobalTermsCount = computed(() => {
  let count = 0;
  for (const gg of activeGlobalGlossaries.value) {
    if (gg) {
      count += gg.termsCount ?? Object.keys(gg.terms || {}).length;
    }
  }
  return count;
});

const buttonLabel = computed(() => {
  const localCount = Object.keys(props.value).length;
  const globalCount = totalGlobalTermsCount.value;
  return `术语表[${localCount}]+[${globalCount}]`;
});

const moveGlossaryUp = (id: string) => {
  const idx = linkedGlossaries.value.indexOf(id);
  if (idx > 0) {
    const arr = [...linkedGlossaries.value];
    const temp = arr[idx];
    arr[idx] = arr[idx - 1];
    arr[idx - 1] = temp;
    linkedGlossaries.value = arr;
  }
};

const moveGlossaryDown = (id: string) => {
  const idx = linkedGlossaries.value.indexOf(id);
  if (idx !== -1 && idx < linkedGlossaries.value.length - 1) {
    const arr = [...linkedGlossaries.value];
    const temp = arr[idx];
    arr[idx] = arr[idx + 1];
    arr[idx + 1] = temp;
    linkedGlossaries.value = arr;
  }
};

const isConfigLoaded = ref(false);

// Fetch global glossaries and linked glossaries
const fetchData = async () => {
  if (isConfigLoaded.value) return;
  try {
    allGlobalGlossaries.value = await GlobalGlossaryApi.listGlobalGlossaries();
    const gnid = props.gnid;
    if (gnid !== undefined) {
      if (gnid.type === 'web') {
        const novel = await WebNovelApi.getNovel(gnid.providerId, gnid.novelId);
        linkedGlossaries.value = novel.linkedGlossaries || [];
        novelKeywords.value = novel.keywords || [];
      } else if (gnid.type === 'wenku') {
        const novel = await WenkuNovelApi.getNovel(gnid.novelId);
        linkedGlossaries.value = novel.linkedGlossaries || [];
        novelKeywords.value = novel.keywords || [];
      } else if (gnid.type === 'local') {
        const store = await useLocalVolumeStore();
        const volume = await store.getVolume(gnid.volumeId);
        if (volume) {
          linkedGlossaries.value = volume.linkedGlossaries || [];
        }
      }
    }
    isConfigLoaded.value = true;
  } catch (e: any) {
    message.error(`获取全域术语表配置失败: ${e.message || e}`);
  }
};

onMounted(fetchData);

watch(
  () => props.gnid,
  () => {
    isConfigLoaded.value = false;
    allGlobalGlossaries.value = [];
    linkedGlossaries.value = [];
    activeGlossariesMap.value = {};
    fullyLoadedGlossaryIds.value.clear();
    expandedGlobalConfig.value = [];
    fetchData();
  },
  { deep: true }
);

const skippedKeys = ref<Set<string>>(new Set());

const getGnidStorageKeys = () => {
  const gnid = props.gnid;
  if (!gnid) return null;
  let source = '';
  let id = '';
  if (gnid.type === 'web') {
    source = gnid.providerId;
    id = gnid.novelId;
  } else if (gnid.type === 'wenku') {
    source = 'wenku';
    id = gnid.novelId;
  } else {
    source = 'local';
    id = gnid.volumeId;
  }
  return { source, id };
};

const loadSkippedKeys = () => {
  const keys = getGnidStorageKeys();
  if (!keys) {
    skippedKeys.value = new Set();
    return;
  }
  const { source, id } = keys;
  const stored = localStorage.getItem('skipped-duplicates');
  if (stored) {
    try {
      const data = JSON.parse(stored);
      if (data && data[source] && data[source][id]) {
        skippedKeys.value = new Set(data[source][id]);
        return;
      }
    } catch {
      // ignore
    }
  }
  skippedKeys.value = new Set();
};

const saveSkippedKeys = () => {
  const keys = getGnidStorageKeys();
  if (!keys) return;
  const { source, id } = keys;
  const stored = localStorage.getItem('skipped-duplicates');
  let data: any = {};
  if (stored) {
    try {
      data = JSON.parse(stored);
      if (typeof data !== 'object' || data === null) {
        data = {};
      }
    } catch {
      data = {};
    }
  }
  if (!data[source]) {
    data[source] = {};
  }
  data[source][id] = Array.from(skippedKeys.value);
  localStorage.setItem('skipped-duplicates', JSON.stringify(data));
};

const originalGlossary = ref<Glossary>({});
const originalLinkedGlossaries = ref<string[]>([]);

const toggleGlossaryModal = async () => {
  if (showGlossaryModal.value === false) {
    glossary.value = { ...props.value };
    loadSkippedKeys();
    await fetchData();
    originalGlossary.value = { ...glossary.value };
    originalLinkedGlossaries.value = [...linkedGlossaries.value];
    isSaved.value = false;
    showGlossaryModal.value = true;
  } else {
    await handleUpdateShow(false);
  }
};

const handleUpdateShow = async (value: boolean) => {
  if (value === false) {
    if (isSaved.value) {
      showGlossaryModal.value = false;
      return;
    }
    const hasChanges =
      !isEqual(toRaw(glossary.value), toRaw(originalGlossary.value)) ||
      !isEqual(
        Object.keys(toRaw(glossary.value)),
        Object.keys(toRaw(originalGlossary.value)),
      ) ||
      !isEqual(
        toRaw(linkedGlossaries.value),
        toRaw(originalLinkedGlossaries.value),
      );
    if (hasChanges) {
      if (window.confirm('术语表有未保存的修改，是否保存？')) {
        await handleSaveConfirm();
      } else {
        handleDiscardConfirm();
      }
    } else {
      showGlossaryModal.value = false;
    }
  } else {
    showGlossaryModal.value = true;
  }
};

const handleSaveConfirm = async () => {
  try {
    await updateGlossary();
    for (const key in props.value) {
      delete props.value[key];
    }
    for (const key in glossary.value) {
      props.value[key] = glossary.value[key];
    }
    originalGlossary.value = { ...glossary.value };
    originalLinkedGlossaries.value = [...linkedGlossaries.value];
    message.success('保存成功');
    isSaved.value = true;
    showGlossaryModal.value = false;
  } catch (e: any) {
    message.error('保存失败:' + e);
  }
};

const handleDiscardConfirm = () => {
  glossary.value = { ...props.value };
  showGlossaryModal.value = false;
};

const gnidHint = computed(() => {
  const gnid = props.gnid;
  if (gnid === undefined) {
    return undefined;
  } else {
    return GenericNovelId.toString(gnid);
  }
});

const updateGlossary = async () => {
  const gnid = props.gnid;
  if (gnid === undefined) {
    return;
  }
  const glossaryValue = toRaw(glossary.value);
  const linkedList = toRaw(linkedGlossaries.value);

  if (gnid.type === 'web') {
    await WebNovelApi.updateGlossary(gnid.providerId, gnid.novelId, {
      glossary: glossaryValue,
      linkedGlossaries: linkedList,
    });
  } else if (gnid.type === 'wenku') {
    await WenkuNovelApi.updateGlossary(gnid.novelId, {
      glossary: glossaryValue,
      linkedGlossaries: linkedList,
    });
  } else {
    const repo = await useLocalVolumeStore();
    await repo.updateGlossary(gnid.volumeId, glossaryValue, linkedList);
  }
};

const submitGlossary = () =>
  doAction(
    updateGlossary().then(() => {
      // 触发组件外的术语表本体更新。
      for (const key in props.value) {
        delete props.value[key];
      }
      for (const key in glossary.value) {
        props.value[key] = glossary.value[key];
      }
      originalGlossary.value = { ...glossary.value };
      originalLinkedGlossaries.value = [...linkedGlossaries.value];
      isSaved.value = true;
      showGlossaryModal.value = false;
    }),
    '术语表提交',
    message,
  );

// Deduplication Logic
const duplicates = computed(() => {
  const dupes: {
    key: string;
    localVal: string;
    globalVal: string;
    globalName: string;
  }[] = [];

  const localKeys = Object.keys(glossary.value);
  for (const k of localKeys) {
    if (skippedKeys.value.has(k)) {
      continue;
    }
    // Search backwards (from last to first) because later loaded glossaries overwrite earlier ones.
    for (let i = linkedGlossaries.value.length - 1; i >= 0; i--) {
      const guid = linkedGlossaries.value[i];
      const gg = activeGlossariesMap.value[guid];
      if (gg && gg.terms && gg.terms[k] !== undefined) {
        dupes.push({
          key: k,
          localVal: glossary.value[k],
          globalVal: gg.terms[k],
          globalName: gg.name,
        });
        break;
      }
    }
  }
  return dupes;
});

watch(
  skippedKeys,
  () => {
    saveSkippedKeys();
  },
  { deep: true },
);

const keepLocal = (key: string) => {
  skippedKeys.value.add(key);
  skippedKeys.value = new Set(skippedKeys.value);
  saveSkippedKeys();
  message.info(`已保留独立词条并且对去重跳过: ${key}`);
};

const applyGlobal = (key: string) => {
  delete glossary.value[key];
  message.success(`已应用全域词条 (删除独立项): ${key}`);
};

const applyAllLocal = () => {
  const currentDupes = duplicates.value;
  if (currentDupes.length === 0) return;
  for (const dup of currentDupes) {
    skippedKeys.value.add(dup.key);
  }
  skippedKeys.value = new Set(skippedKeys.value);
  saveSkippedKeys();
  message.success(
    `已全部保留独立词条并且对去重跳过: 共 ${currentDupes.length} 个`,
  );
};

const applyAllGlobal = () => {
  const currentDupes = duplicates.value;
  if (currentDupes.length === 0) return;
  for (const dup of currentDupes) {
    delete glossary.value[dup.key];
  }
  message.success(
    `已全部应用全域词条 (删除了 ${currentDupes.length} 个独立项)`,
  );
};

const downloadMergedJson = async () => {
  await ensureAllActiveGlossariesLoaded();
  const merged: Glossary = {};
  for (const guid of linkedGlossaries.value) {
    const gg = activeGlossariesMap.value[guid];
    if (gg && gg.terms) {
      Object.assign(merged, gg.terms);
    }
  }
  Object.assign(merged, glossary.value);

  downloadFile(
    'glossary-merged.json',
    new Blob([Glossary.toJson(merged)], {
      type: 'text/plain',
    }),
  );
};

const exportMerged = async (ev?: MouseEvent) => {
  await ensureAllActiveGlossariesLoaded();
  const merged: Glossary = {};
  for (const guid of linkedGlossaries.value) {
    const gg = activeGlossariesMap.value[guid];
    if (gg && gg.terms) {
      Object.assign(merged, gg.terms);
    }
  }
  Object.assign(merged, glossary.value);

  const isSuccess = await copyToClipBoard(
    Glossary.toText(merged),
    ev?.target as HTMLElement,
  );
  if (isSuccess) {
    message.success('导出合并成功：已复制到剪贴板');
  } else {
    message.success('导出合并失败');
  }
};

const importGlobalToLocal = async () => {
  if (!window.confirm('此操作将解除所有全域术语表连结，是否合併？')) return;

  await ensureAllActiveGlossariesLoaded();
  let count = 0;
  for (const guid of linkedGlossaries.value) {
    const gg = activeGlossariesMap.value[guid];
    if (gg && gg.terms) {
      for (const jp in gg.terms) {
        glossary.value[jp] = gg.terms[jp];
        count++;
      }
    }
  }
  linkedGlossaries.value = [];
  message.success(`成功导入全域术语并解除链接 (合并了 ${count} 个词条)`);
};

const actionBarStyle = computed<CSSProperties>(() => ({
  display: 'flex',
  flexWrap: 'nowrap',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: '8px',
  width: '100%',
  overflowX: 'auto',
  flexDirection: checkIsMobile() ? 'row-reverse' : 'row',
}));

const exportMergedLabel = computed(() =>
  checkIsMobile() ? '合并' : '导出合并',
);
</script>

<template>
  <c-button
    :label="buttonLabel"
    v-bind="$attrs"
    @action="toggleGlossaryModal()"
  />

  <c-modal
    title="编辑术语表"
    :show="showGlossaryModal"
    @update:show="handleUpdateShow"
    :extra-height="120"
  >
    <template #header-extra>
      <n-flex
        vertical
        size="large"
        style="max-width: 400px; margin-bottom: 16px"
      >
        <template v-if="gnidHint">
          <n-text style="font-size: 12px">{{ gnidHint }}</n-text>

          <n-text>
            使用前务必先阅读
            <c-a to="/forum/660ab4da55001f583649a621">术语表使用指南</c-a>
            ，不要滥用术语表。
          </n-text>
        </template>

        <!-- Collapsible Global Glossary Configuration and Deduplication section -->
        <template
          v-if="
            props.showGlobal &&
            gnid &&
            (gnid.type === 'web' ||
              gnid.type === 'wenku' ||
              gnid.type === 'local')
          "
        >
          <n-collapse
            v-model:expanded-names="expandedGlobalConfig"
            style="margin: 4px 0"
          >
            <n-collapse-item
              :title="`全域术语表 [${totalGlobalTermsCount}]`"
              name="global-config"
            >
              <n-flex vertical size="medium">
                <!-- Sorting Controls using OrderSort component -->
                <n-space style="margin-bottom: 4px" align="center">
                  <n-text style="font-size: 11px" depth="3">排序方式：</n-text>
                  <OrderSort
                    v-model:value="sortState"
                    :options="[
                      { label: '标签符合度', value: 'default' },
                      { label: '引用次数', value: 'used' },
                      { label: '更新日期', value: 'update' },
                    ]"
                  />
                </n-space>

                <n-select
                  v-model:value="linkedGlossaries"
                  multiple
                  filterable
                  placeholder="引用全域术语表 (输入名称检索)"
                  :options="globalGlossariesOptions"
                  size="small"
                  style="margin-bottom: 8px"
                />

                <!-- Active Linked Global Glossaries Content Tables -->
                <template v-if="activeGlobalGlossaries.length > 0">
                  <n-collapse
                    @update:expanded-names="handleExpandedNamesChange"
                  >
                    <n-collapse-item
                      v-for="gg in activeGlobalGlossaries"
                      :key="gg.id"
                      :title="`${gg.name} (共 ${gg.termsCount ?? Object.keys(gg.terms || {}).length} 个词条)`"
                      :name="gg.id"
                      style="
                        border: 1px solid var(--border-color);
                        border-radius: 4px;
                        padding: 0px;
                        margin: 0 0 0 8px;
                      "
                    >
                      <template #header-extra>
                        <n-space size="small" :wrap="false">
                          <n-button
                            size="tiny"
                            secondary
                            circle
                            :disabled="linkedGlossaries.indexOf(gg.id) === 0"
                            @click.stop="moveGlossaryUp(gg.id)"
                          >
                            <template #icon>
                              <n-icon><ArrowUpwardOutlined /></n-icon>
                            </template>
                          </n-button>
                          <n-button
                            size="tiny"
                            secondary
                            circle
                            :disabled="
                              linkedGlossaries.indexOf(gg.id) ===
                              linkedGlossaries.length - 1
                            "
                            @click.stop="moveGlossaryDown(gg.id)"
                          >
                            <template #icon>
                              <n-icon><ArrowDownwardOutlined /></n-icon>
                            </template>
                          </n-button>
                        </n-space>
                      </template>
                      <template v-if="expandedActiveGlossaries.includes(gg.id)">
                        <n-scrollbar
                          v-if="Object.keys(gg.terms || {}).length > 0"
                          style="max-height: 150px"
                        >
                          <n-table size="small" striped style="font-size: 12px">
                            <thead>
                              <tr>
                                <th>原词</th>
                                <th>翻译</th>
                              </tr>
                            </thead>
                            <tbody>
                              <tr v-for="(zh, jp) in gg.terms" :key="jp">
                                <td style="font-weight: bold">{{ jp }}</td>
                                <td>{{ zh }}</td>
                              </tr>
                            </tbody>
                          </n-table>
                        </n-scrollbar>
                        <n-empty
                          v-else
                          description="暂无词条"
                          style="padding: 8px"
                        />
                      </template>
                    </n-collapse-item>
                  </n-collapse>
                </template>

                <!-- Deduplication UI placed inside the same collapse panel -->
                <template v-if="duplicates.length > 0">
                  <n-divider />
                  <div
                    style="
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      margin-bottom: 8px;
                    "
                  >
                    <n-text
                      type="warning"
                      style="font-weight: bold; font-size: 12px"
                    >
                      发现 {{ duplicates.length }} 個與全域重複
                    </n-text>
                    <n-space size="small">
                      <n-button
                        size="tiny"
                        type="primary"
                        secondary
                        @click="applyAllLocal"
                      >
                        套用独立
                      </n-button>
                      <n-button
                        size="tiny"
                        type="warning"
                        secondary
                        @click="applyAllGlobal"
                      >
                        套用全域
                      </n-button>
                    </n-space>
                  </div>

                  <n-table size="small" striped style="font-size: 12px">
                    <thead>
                      <tr>
                        <th>原词</th>
                        <th>独立</th>
                        <th>全域</th>
                        <th>使用</th>
                      </tr>
                    </thead>
                  </n-table>

                  <n-scrollbar
                    style="
                      max-height: 200px;
                      border: 1px solid var(--border-color);
                      border-radius: 4px;
                    "
                  >
                    <n-table size="small" striped style="font-size: 12px">
                      <tbody>
                        <tr v-for="dup in duplicates" :key="dup.key">
                          <td style="font-weight: bold">{{ dup.key }}</td>
                          <td>{{ dup.localVal }}</td>
                          <td>
                            {{ dup.globalVal }}
                          </td>
                          <td>
                            <n-space size="small">
                              <n-button
                                size="tiny"
                                type="primary"
                                secondary
                                @click="keepLocal(dup.key)"
                              >
                                独立
                              </n-button>
                              <n-button
                                size="tiny"
                                type="warning"
                                secondary
                                @click="applyGlobal(dup.key)"
                              >
                                全域
                              </n-button>
                            </n-space>
                          </td>
                        </tr>
                      </tbody>
                    </n-table>
                  </n-scrollbar>
                </template>
              </n-flex>
              <n-divider />
            </n-collapse-item>
          </n-collapse>
        </template>

        <indie-glossary-edit
          v-model="glossary"
          v-model:skippedKeys="skippedKeys"
        >
          <template #extra-edit-actions>
            <c-button
              :icon="DownloadOutlined"
              label="合併"
              :round="false"
              size="small"
              @action="importGlobalToLocal"
            />
          </template>
        </indie-glossary-edit>
      </n-flex>
    </template>

    <template #action>
      <div :style="actionBarStyle">
        <n-space :wrap="false" style="flex-shrink: 1; min-width: 0">
          <c-button
            :label="exportMergedLabel"
            :icon="ContentCopyOutlined"
            @action="exportMerged"
          />
          <c-button
            label="合并JSON"
            :icon="FileDownloadOutlined"
            @action="downloadMergedJson()"
          />
        </n-space>
        <c-button
          label="提交"
          type="primary"
          style="flex-shrink: 0"
          @action="submitGlossary()"
        />
      </div>
    </template>
  </c-modal>
</template>
