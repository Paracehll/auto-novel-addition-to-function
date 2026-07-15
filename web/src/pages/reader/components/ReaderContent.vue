<script lang="ts" setup>
import { useOsTheme } from 'naive-ui';

import type { GenericNovelId } from '@/model/Common';
import type { ReaderChapter } from '../ReaderStore';
import { useReaderSettingStore } from '@/stores';
import { buildParagraphs } from './BuildParagraphs';
import { WebUtil } from '@/util/web';
import { ImageOutlined } from '@vicons/material';

const props = defineProps<{
  gnid: GenericNovelId;
  chapterId: string;
  chapter: ReaderChapter;
}>();

const osThemeRef = useOsTheme();

const paragraphs = computed(() => buildParagraphs(props.gnid, props.chapter));

const readerSettingStore = useReaderSettingStore();
const { readerSetting } = storeToRefs(readerSettingStore);

const fontColor = computed(() => {
  const theme = readerSetting.value.theme;
  if (theme.mode === 'custom') {
    return theme.fontColor;
  } else {
    let specificTheme: 'light' | 'dark' = 'light';
    if (theme.mode !== 'system') {
      specificTheme = theme.mode;
    } else if (osThemeRef.value) {
      specificTheme = osThemeRef.value;
    }
    return specificTheme === 'light' ? '#000000' : '#FFFFFF';
  }
});

const underlineColor = computed(() => `${fontColor.value}80`);

const textUnderlineOffset = computed(() => {
  const fontSize = readerSetting.value.fontSize;
  const offset = Math.round(fontSize / 4);
  return `${offset}px`;
});

const chapterHref = computed(() => {
  const chapterId = props.chapterId;
  const gnid = props.gnid;
  if (gnid.type === 'web') {
    return WebUtil.buildChapterUrl(gnid.providerId, gnid.novelId, chapterId);
  } else if (gnid.type === 'wenku') {
    throw '不支持文库';
  } else {
    return '/workspace';
  }
});

const collapsedState = ref<Record<string, boolean>>({});

const isImageCollapsed = (index: number) => {
  const key = `${props.chapterId}_${index}`;
  if (collapsedState.value[key] !== undefined) {
    return collapsedState.value[key];
  }
  return readerSetting.value.collapseIllustrationByDefault;
};

const setImageCollapsed = (index: number, val: boolean) => {
  const key = `${props.chapterId}_${index}`;
  collapsedState.value[key] = val;
};
</script>

<template>
  <div class="chapter" data-chapter :data-id="chapterId">
    <n-h4 class="chapter-title">
      <n-a :href="chapterHref">{{ chapter.titleJp }}</n-a>
      <br />
      <n-text depth="3">{{ chapter.titleZh }}</n-text>
      <br />
    </n-h4>
    <n-divider />

    <div class="chapter-content">
      <template
        v-for="(p, index) of paragraphs"
        :key="`${chapter.prevId}/${index}`"
      >
        <n-p v-if="p && 'text' in p" :aria-hidden="!p.needSpeak">
          <span
            v-if="readerSetting.enableSourceLabel && p.source"
            class="source"
          >
            {{ p.source }}
          </span>
          <span v-if="p.indent">
            {{ p.indent }}
          </span>
          <span :class="[p.secondary ? 'second' : 'first', 'text-content']">
            {{ p.text }}
          </span>
        </n-p>
        <br v-else-if="!p" />
        <div v-else class="illustration-wrapper">
          <div v-if="isImageCollapsed(index)" class="collapsed-placeholder">
            <n-button
              @click="setImageCollapsed(index, false)"
              size="small"
              secondary
              type="primary"
              class="expand-btn"
            >
              <template #icon>
                <n-icon><ImageOutlined /></n-icon>
              </template>
              展开插图
            </n-button>
          </div>
          <div v-else class="expanded-illustration">
            <div class="illustration-actions">
              <n-button
                @click="setImageCollapsed(index, true)"
                size="tiny"
                quaternary
                depth="3"
                class="collapse-btn"
              >
                收起插图
              </n-button>
            </div>
            <img
              :src="p.imageUrl"
              :alt="p.imageUrl"
              style="max-width: 100%; object-fit: scale-down"
              loading="lazy"
            />
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.chapter {
  font-size: v-bind('`${readerSetting.fontSize}px`');
  font-weight: v-bind('readerSetting.fontWeight');
}
.chapter-title {
  display: inline-block;
  padding: 24px 0 0 0;
  margin: 0;
  text-align: center;
  width: 100%;
}
.chapter-content {
  min-height: 65vh;
}
.illustration-wrapper {
  margin: 16px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}
.collapsed-placeholder {
  padding: 24px;
  border: 1px dashed rgba(128, 128, 128, 0.3);
  border-radius: 8px;
  background-color: rgba(128, 128, 128, 0.05);
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
}
.expanded-illustration {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}
.illustration-actions {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.chapter-content p {
  color: v-bind('fontColor');
  margin: v-bind('`${readerSetting.fontSize * readerSetting.lineSpace}px 0`');
  font-size: 1em;
}
.chapter-content p .source {
  display: inline-block;
  user-select: none;
  width: 1em;
  text-align: center;
  opacity: 0.4;
  font-size: 0.75em;
  margin-right: 0.5em;
}
.chapter-content p .first {
  opacity: v-bind('readerSetting.mixZhOpacity');
}
.chapter-content p .second {
  opacity: v-bind('readerSetting.mixJpOpacity');
}
.chapter-content p .text-content {
  text-decoration-line: v-bind(
    "readerSetting.textUnderline === 'none' ? 'none' : 'underline'"
  );
  text-decoration-style: v-bind('readerSetting.textUnderline');
  text-decoration-color: v-bind('underlineColor');
  text-decoration-thickness: 1px;
  text-underline-offset: v-bind('textUnderlineOffset');
}
</style>
