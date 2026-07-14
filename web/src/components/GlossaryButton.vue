<script lang="ts" setup>
import { DeleteOutlineOutlined, HelpOutlineOutlined } from '@vicons/material';

import { WebNovelApi, WenkuNovelApi } from '@/api';
import { GlobalGlossaryApi } from '@/api/novel/GlobalGlossaryApi';
import { GenericNovelId } from '@/model/Common';
import { Glossary } from '@/model/Glossary';
import type { GlobalGlossary } from '@/model/GlobalGlossary';
import { copyToClipBoard, doAction } from '@/pages/util';
import { useLocalVolumeStore, useWhoamiStore } from '@/stores';
import { downloadFile } from '@/util';
import OrderSort from '@/components/OrderSort.vue';

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
  if (!gg.tag || gg.tag.length === 0 || novelKeywords.value.length === 0) return 0;
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
      label: `${gg.name} (${gg.uid}) [${Object.keys(gg.content).length} 词条] [引用: ${usedCount}次]${matchLabel}`,
      value: gg.uid,
    };
  });
});

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

const toggleGlossaryModal = async () => {
  if (showGlossaryModal.value === false) {
    glossary.value = { ...props.value };
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
    await WebNovelApi.updateGlossary(
      gnid.providerId,
      gnid.novelId,
      {
        glossary: glossaryValue,
        linkedGlossaries: linkedList,
      },
    );
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

const importGlossaryRaw = ref('');
const termsToAdd = ref<[string, string]>(['', '']);

const deletedTerms = ref<[string, string][]>([]);

const lastDeletedTerm = computed(() => {
  const last = deletedTerms.value[deletedTerms.value.length - 1];
  if (last === undefined) return undefined;
  return `${last[0]} => ${last[1]}`;
});

const clearTerm = () => {
  glossary.value = {};
};

const undoDeleteTerm = () => {
  if (deletedTerms.value.length === 0) return;
  const [jp, zh] = deletedTerms.value.pop()!;
  glossary.value[jp] = zh;
};

const deleteTerm = (jp: string) => {
  if (jp in glossary.value) {
    deletedTerms.value.push([jp, glossary.value[jp]]);
    delete glossary.value[jp];
  }
};

const addTerm = () => {
  const [jp, zh] = termsToAdd.value;
  if (jp && zh) {
    glossary.value[jp.trim()] = zh.trim();
    termsToAdd.value = ['', ''];
  }
};

const exportGlossary = async (ev: MouseEvent) => {
  const isSuccess = await copyToClipBoard(
    Glossary.toText(glossary.value),
    ev.target as HTMLElement,
  );
  if (isSuccess) {
    message.success('导出成功：已复制到剪贴板');
  } else {
    message.success('导出失败');
  }
};

const importGlossary = () => {
  const importedGlossary = Glossary.fromText(importGlossaryRaw.value);
  if (importedGlossary === undefined) {
    message.error('导入失败：术语表格式不正确');
  } else {
    message.success('导入成功');
    for (const jp in importedGlossary) {
      const zh = importedGlossary[jp];
      glossary.value[jp] = zh;
    }
  }
};

const downloadGlossaryAsJsonFile = async (ev: MouseEvent) => {
  const gnid = props.gnid;
  try {
    let mergedGlossary: Glossary = { ...glossary.value };
    if (gnid !== undefined) {
      if (gnid.type === 'web') {
        mergedGlossary = await WebNovelApi.getGlossary(gnid.providerId, gnid.novelId);
      } else if (gnid.type === 'wenku') {
        mergedGlossary = await WenkuNovelApi.getGlossary(gnid.novelId);
      }
    }
    downloadFile(
      `${gnidHint.value ?? 'glossary'}.json`,
      new Blob([Glossary.toJson(mergedGlossary)], {
        type: 'text/plain',
      }),
    );
  } catch (e: any) {
    message.error(`下载合并术语表失败: ${e.message || e}`);
  }
};

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

const keepLocal = (key: string) => {
  message.info(`已保留独立词条: ${key}`);
};

const applyGlobal = (key: string) => {
  deleteTerm(key);
  message.success(`已应用全域词条 (删除独立项): ${key}`);
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
            <n-collapse-item title="配置全域术语表与去重" name="global-config">
              <n-flex vertical size="medium">
                <n-text style="font-size: 12px; font-weight: bold">链接全域术语表</n-text>

                <!-- Sorting Controls using OrderSort component -->
                <n-space style="margin-bottom: 4px" align="center">
                  <n-text style="font-size: 11px" depth="3">排序方式：</n-text>
                  <OrderSort
                    v-model:value="sortState"
                    :options="[
                      { label: '标签符合度', value: 'default' },
                      { label: '引用次数', value: 'used' },
                      { label: '更新日期', value: 'update' }
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

                <!-- Deduplication UI placed inside the same collapse panel -->
                <template v-if="duplicates.length > 0">
                  <div style="margin-top: 12px; border: 1px solid var(--border-color); border-radius: 4px; padding: 8px">
                    <n-text type="warning" style="font-weight: bold; font-size: 12px; display: block; margin-bottom: 6px">
                      去重警告: 发现 {{ duplicates.length }} 個與全域重複的詞條
                    </n-text>
                    <n-scrollbar style="max-height: 200px">
                      <n-table size="small" striped>
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
                            <td>{{ dup.globalVal }} <br/><span style="font-size:10px; color:gray">({{ dup.globalName }})</span></td>
                            <td>
                              <n-space size="small">
                                <n-button size="tiny" type="primary" secondary @click="keepLocal(dup.key)">
                                  使用独立
                                </n-button>
                                <n-button size="tiny" type="warning" secondary @click="applyGlobal(dup.key)">
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

        <n-input-group>
          <n-input
            pair
            v-model:value="termsToAdd"
            size="small"
            separator="=>"
            :placeholder="['日文', '中文']"
            :input-props="{ spellcheck: false }"
          />
          <c-button
            label="添加"
            :round="false"
            size="small"
            @action="addTerm"
          />
        </n-input-group>

        <n-input
          v-model:value="importGlossaryRaw"
          type="textarea"
          size="small"
          placeholder="批量导入术语表"
          :input-props="{ spellcheck: false }"
          :rows="1"
        />

        <n-flex align="center" :wrap="false">
          <c-button
            label="导出"
            :round="false"
            size="small"
            @action="exportGlossary"
          />
          <c-button
            label="导入"
            :round="false"
            size="small"
            @action="importGlossary"
          />
          <c-button
            label="下载json文件 (已合并)"
            :round="false"
            size="small"
            @action="downloadGlossaryAsJsonFile"
          />
          <c-button
            v-if="whoami.isAdmin"
            secondary
            type="error"
            label="清空"
            :round="false"
            size="small"
            @action="clearTerm"
          />
        </n-flex>
        <n-flex align="center" :wrap="false">
          <c-button
            :disabled="deletedTerms.length === 0"
            label="撤销删除"
            :round="false"
            size="small"
            @action="undoDeleteTerm"
          />
          <n-text
            v-if="lastDeletedTerm !== undefined"
            depth="3"
            style="font-size: 12px"
          >
            {{ lastDeletedTerm }}
          </n-text>
        </n-flex>
      </n-flex>
    </template>

    <n-table
      v-if="Object.keys(glossary).length !== 0"
      striped
      size="small"
      style="font-size: 12px; max-width: 400px"
    >
      <tr v-for="wordJp in Object.keys(glossary).reverse()" :key="wordJp">
        <td>
          <c-button
            :icon="DeleteOutlineOutlined"
            text
            type="error"
            size="small"
            @action="deleteTerm(wordJp)"
          />
        </td>
        <td>{{ wordJp }}</td>
        <td nowrap="nowrap">=></td>
        <td style="padding-right: 16px">
          <n-input
            v-model:value="glossary[wordJp]"
            size="tiny"
            placeholder="请输入中文翻译"
            :theme-overrides="{
              border: '0',
              color: 'transprent',
            }"
          />
        </td>
      </tr>
    </n-table>

    <template #action>
      <c-button label="提交" type="primary" @action="submitGlossary()" />
    </template>
  </c-modal>
</template>
