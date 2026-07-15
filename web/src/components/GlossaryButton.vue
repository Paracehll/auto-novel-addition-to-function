<script lang="ts" setup>
import { DeleteOutlineOutlined, HelpOutlineOutlined } from '@vicons/material';

import { WebNovelApi, WenkuNovelApi } from '@/api';
import { GlobalGlossaryApi } from '@/api/novel/GlobalGlossaryApi';
import { GenericNovelId } from '@/model/Common';
import { Glossary } from '@/model/Glossary';
import type { GlobalGlossary } from '@/model/GlobalGlossary';
import { copyToClipBoard, doAction } from '@/pages/util';
import { useLocalVolumeStore, useWhoamiStore } from '@/stores';
import OrderSort from '@/components/OrderSort.vue';
import { downloadFile } from '@/util';

const props = defineProps<{
  gnid?: GenericNovelId;
  value: Glossary;
}>();

const message = useMessage();

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const glossary = ref<Glossary>({});
const linkedGlossaries = ref<string[]>([]);
const allGlobalGlossaries = ref<GlobalGlossary[]>([]);
const novelKeywords = ref<string[]>([]);

const showGlossaryModal = ref(false);

const sortState = ref({
  value: 'default',
  desc: true,
});

const getMatchCount = (gg: GlobalGlossary) => {
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
  sorted.sort((a, b) => {
    let valA = 0;
    let valB = 0;

    if (sortState.value.value === 'default') {
      valA = getMatchCount(a);
      valB = getMatchCount(b);
    } else if (sortState.value.value === 'used') {
      valA = (a.used || []).length;
      valB = (b.used || []).length;
    } else if (sortState.value.value === 'update') {
      valA = new Date(a.update).getTime();
      valB = new Date(b.update).getTime();
    }

    if (valA === valB) {
      return a.uid.localeCompare(b.uid);
    }

    if (sortState.value.desc) {
      return valB - valA;
    } else {
      return valA - valB;
    }
  });

  return sorted.map((gg) => {
    const matchCount = getMatchCount(gg);
    const usedCount = (gg.used || []).length;
    const matchLabel = matchCount > 0 ? ` (匹配标签数: ${matchCount})` : '';
    return {
      label: `${gg.name} [${Object.keys(gg.content).length} 词条] [引用: ${usedCount}次]${matchLabel}`,
      value: gg.uid,
    };
  });
});

const activeGlobalGlossaries = computed(() => {
  return allGlobalGlossaries.value.filter((gg) =>
    linkedGlossaries.value.includes(gg.uid),
  );
});

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

// Fetch global glossaries and linked glossaries
const fetchData = async () => {
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
      }
    }
  } catch (e: any) {
    message.error(`获取全域术语表配置失败: ${e.message || e}`);
  }
};

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

const toggleGlossaryModal = async () => {
  if (showGlossaryModal.value === false) {
    glossary.value = { ...props.value };
    loadSkippedKeys();
    await fetchData();
  }
  showGlossaryModal.value = !showGlossaryModal.value;
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
    await repo.updateGlossary(gnid.volumeId, glossaryValue);
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
    for (const guid of linkedGlossaries.value) {
      const gg = allGlobalGlossaries.value.find((g) => g.uid === guid);
      if (gg && gg.content[k] !== undefined) {
        dupes.push({
          key: k,
          localVal: glossary.value[k],
          globalVal: gg.content[k],
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

const downloadMergedJson = () => {
  const merged: Glossary = {};
  for (const guid of linkedGlossaries.value) {
    const gg = allGlobalGlossaries.value.find((g) => g.uid === guid);
    if (gg) {
      Object.assign(merged, gg.content);
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
</script>

<template>
  <c-button
    :label="`术语表[${Object.keys(value).length}]`"
    v-bind="$attrs"
    @action="toggleGlossaryModal()"
  />

  <c-modal
    title="编辑术语表"
    v-model:show="showGlossaryModal"
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
        <template v-if="gnid && (gnid.type === 'web' || gnid.type === 'wenku')">
          <n-collapse style="margin: 4px 0">
            <n-collapse-item title="全域术语表" name="global-config">
              <n-flex vertical size="medium">
                <!-- <n-text style="font-size: 12px; font-weight: bold">链接全域术语表</n-text> -->

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
                  placeholder="引用全域术语表 (可输入名称检索)"
                  :options="globalGlossariesOptions"
                  size="small"
                />

                <!-- Active Linked Global Glossaries Content Tables -->
                <template v-if="activeGlobalGlossaries.length > 0">
                  <n-collapse
                    @update:expanded-names="handleExpandedNamesChange"
                  >
                    <n-collapse-item
                      v-for="gg in activeGlobalGlossaries"
                      :key="gg.uid"
                      :title="`${gg.name} (共 ${Object.keys(gg.content).length} 个词条)`"
                      :name="gg.uid"
                      style="
                        border: 1px solid var(--border-color);
                        border-radius: 4px;
                        padding: 8px;
                        margin-left: 8px;
                        /* margin-bottom: 8px; */
                      "
                    >
                      <template
                        v-if="expandedActiveGlossaries.includes(gg.uid)"
                      >
                        <n-scrollbar
                          v-if="Object.keys(gg.content).length > 0"
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
                              <tr v-for="(zh, jp) in gg.content" :key="jp">
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
                  <div style="margin-top: 12px">
                    <n-text
                      type="warning"
                      style="
                        font-weight: bold;
                        font-size: 12px;
                        display: block;
                        margin-bottom: 6px;
                      "
                    >
                      去重警告: 发现 {{ duplicates.length }} 個與全域重複的詞條
                    </n-text>
                    <n-scrollbar
                      style="
                        max-height: 200px;
                        border: 1px solid var(--border-color);
                        border-radius: 4px;
                        padding: 4px;
                      "
                    >
                      <n-table size="small" striped style="font-size: 12px">
                        <thead>
                          <tr>
                            <th>原词</th>
                            <th>独立</th>
                            <th>全域</th>
                            <th>操作</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="dup in duplicates" :key="dup.key">
                            <td style="font-weight: bold">{{ dup.key }}</td>
                            <td>{{ dup.localVal }}</td>
                            <td>
                              {{ dup.globalVal }}
                              <!-- <br />
                              <span style="font-size: 10px; color: gray">
                                ({{ dup.globalName }})
                              </span> -->
                            </td>
                            <td>
                              <n-space size="small">
                                <n-button
                                  size="tiny"
                                  type="primary"
                                  secondary
                                  @click="keepLocal(dup.key)"
                                >
                                  使用独立
                                </n-button>
                                <n-button
                                  size="tiny"
                                  type="warning"
                                  secondary
                                  @click="applyGlobal(dup.key)"
                                >
                                  使用全域
                                </n-button>
                              </n-space>
                            </td>
                          </tr>
                        </tbody>
                      </n-table>
                    </n-scrollbar>
                  </div>
                </template>
              </n-flex>
            </n-collapse-item>
          </n-collapse>
        </template>

        <indie-glossary-edit
          v-model="glossary"
          v-model:skippedKeys="skippedKeys"
        />
      </n-flex>
    </template>

    <template #action>
      <n-space justify="space-between" style="width: 100%">
        <c-button label="下载合并JSON" @action="downloadMergedJson()" />
        <c-button label="提交" type="primary" @action="submitGlossary()" />
      </n-space>
    </template>
  </c-modal>
</template>
