<script lang="ts" setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useMessage, ScrollbarInst } from 'naive-ui';
import {
  DeleteOutlineOutlined,
  RefreshOutlined,
  ContentCopyOutlined,
  DownloadOutlined,
  ContentPasteOutlined,
  FileDownloadOutlined,
} from '@vicons/material';
import { useWhoamiStore } from '@/stores';
import { Glossary } from '@/model/Glossary';
import { copyToClipBoard } from '@/pages/util';
import { downloadFile } from '@/util';

const glossary = defineModel<Glossary>({ required: true });
const skippedKeys = defineModel<Set<string>>('skippedKeys', {
  default: () => new Set(),
});

const message = useMessage();
const whoamiStore = useWhoamiStore();

const scrollbarInstRef = ref<ScrollbarInst | null>(null);
const scrollContainerRef = ref<HTMLDivElement | null>(null);

const termsToAdd = ref<[string, string]>(['', '']);
const importGlossaryRaw = ref('');
const deletedTerms = ref<[string, string][]>([]);

const lastDeletedTerm = computed(() => {
  const last = deletedTerms.value[deletedTerms.value.length - 1];
  if (last === undefined) return undefined;
  return `${last[0]} => ${last[1]}`;
});

const jpKeys = computed(() => Object.keys(glossary.value).reverse());
const limit = ref(50);
const displayedKeys = computed(() => jpKeys.value.slice(0, limit.value));

const isReordering = ref(false);

watch(
  () => glossary.value,
  () => {
    if (isReordering.value) {
      isReordering.value = false;
      return;
    }
    limit.value = 50;
  },
  { deep: false },
);

const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement;
  if (target.scrollTop + target.clientHeight >= target.scrollHeight - 20) {
    if (limit.value < jpKeys.value.length) {
      limit.value += 50;
    }
  }
};

const addTerm = () => {
  const [jp, zh] = termsToAdd.value;
  if (jp && zh) {
    glossary.value[jp.trim()] = zh.trim();
    termsToAdd.value = ['', ''];
  }
};

const deleteTerm = (jp: string) => {
  if (jp in glossary.value) {
    deletedTerms.value.push([jp, glossary.value[jp]]);
    delete glossary.value[jp];
  }
};

const undoDeleteTerm = () => {
  if (deletedTerms.value.length === 0) return;
  const [jp, zh] = deletedTerms.value.pop()!;
  glossary.value[jp] = zh;
};

const clearTerm = () => {
  glossary.value = {};
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

const exportGlossary = async (ev?: MouseEvent) => {
  const isSuccess = await copyToClipBoard(
    Glossary.toText(glossary.value),
    ev?.target as HTMLElement,
  );
  if (isSuccess) {
    message.success('导出成功：已复制到剪贴板');
  } else {
    message.success('导出失败');
  }
};

const downloadGlossaryAsJson = () => {
  downloadFile(
    'glossary.json',
    new Blob([Glossary.toJson(glossary.value)], {
      type: 'text/plain',
    }),
  );
};

const importGlossaryFromClipboard = async () => {
  try {
    const text = await navigator.clipboard.readText();
    let isValid = false;
    try {
      JSON.parse(text);
      isValid = true;
    } catch {
      // If not JSON, 檢查是否為 [jp => zh]
      const imported = Glossary.fromText(text);
      if (imported !== undefined) {
        isValid = true;
      }
    }

    if (!isValid) {
      message.error('检测到剪贴簿内容不是 JSON 格式');
      return;
    }

    importGlossaryRaw.value = text;
    message.success('从剪贴簿导入成功');
  } catch (err: any) {
    message.error('无法读取剪贴簿: ' + (err?.message ?? err));
  }
};

const isEditable = (el: Element | null): boolean => {
  if (!el) return false;
  const tagName = el.tagName.toUpperCase();
  if (tagName === 'INPUT' || tagName === 'TEXTAREA') {
    return true;
  }
  if (
    el.hasAttribute('contenteditable') &&
    el.getAttribute('contenteditable') !== 'false'
  ) {
    return true;
  }
  return false;
};

const cancelDrag = () => {
  if (!isPointerDragging) return;

  isPointerDragging = false;
  pointerId = null;
  document.body.style.cursor = '';

  if (animationFrameId !== null) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }

  cleanupDrag();
};

const handleKeyDown = (e: KeyboardEvent) => {
  if (isPointerDragging && e.key === 'Escape') {
    e.preventDefault();
    cancelDrag();
    return;
  }

  if (isEditable(document.activeElement)) {
    return;
  }
  // 如果选取了文本（例如，在表格中选取的文字），不触发全局的复制（Ctrl+C/V）快捷键
  if (window.getSelection()?.toString()) {
    return;
  }
  const isCtrlOrCmd = e.ctrlKey || e.metaKey;
  if (isCtrlOrCmd) {
    if (e.key === 'c' || e.key === 'C') {
      e.preventDefault();
      exportGlossary();
    } else if (e.key === 'v' || e.key === 'V') {
      e.preventDefault();
      importGlossaryFromClipboard();
    }
  }
};

const handleWheel = (e: WheelEvent) => {
  if (draggedKey.value !== null) {
    e.preventDefault();
    scrollbarInstRef.value?.scrollBy({ top: e.deltaY });

    const elementUnderCursor = document.elementFromPoint(
      lastClientX,
      lastClientY,
    );
    const row = elementUnderCursor?.closest('tr');
    if (row) {
      const targetKey = row.getAttribute('data-key');
      if (targetKey && targetKey !== draggedKey.value) {
        dragOverKey.value = targetKey;
        const rect = row.getBoundingClientRect();
        const relativeY = lastClientY - rect.top;
        const ratio = relativeY / rect.height;
        const isLast =
          targetKey === displayedKeys.value[displayedKeys.value.length - 1];
        if (ratio < 0.3) {
          dragPosition.value = 'above';
        } else if (isLast && ratio > 0.7) {
          dragPosition.value = 'below';
        } else {
          dragPosition.value = 'on';
        }
      } else {
        dragOverKey.value = null;
        dragPosition.value = null;
      }
    } else {
      dragOverKey.value = null;
      dragPosition.value = null;
    }
  }
};

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown);
  if (scrollContainerRef.value) {
    scrollContainerRef.value.addEventListener('wheel', handleWheel, {
      passive: false,
    });
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown);
  if (scrollContainerRef.value) {
    scrollContainerRef.value.removeEventListener('wheel', handleWheel);
  }
  stopAutoScroll();
  removeBoundaryOverlay();
});

const revokeSkip = (jp: string) => {
  skippedKeys.value.delete(jp);
  skippedKeys.value = new Set(skippedKeys.value);
};

const draggedKey = ref<string | null>(null);
const dragOverKey = ref<string | null>(null);
const dragPosition = ref<'above' | 'below' | 'on' | null>(null);

let isPointerDragging = false;
let pointerId: number | null = null;
let animationFrameId: number | null = null;
let lastClientX = 0;
let lastClientY = 0;

let dragPreviewEl: HTMLElement | null = null;
let dragPreviewOffsetX = 0;
let dragPreviewOffsetY = 0;
let cachedContainerRect: DOMRect | null = null;

const THRESHOLD = 40;
const MAX_SPEED = 15;

let autoScrollFrameId: number | null = null;
let currentScrollSpeed = 0;
let lastDragOverTime = 0;

let topBoundaryEl: HTMLElement | null = null;
let bottomBoundaryEl: HTMLElement | null = null;

const createBoundaryOverlay = () => {
  topBoundaryEl = document.createElement('div');
  topBoundaryEl.style.cssText = `
    position: absolute;
    left: 0; right: 0; top: 0;
    height: ${THRESHOLD}px;
    pointer-events: none;
    z-index: 9998;
    background: linear-gradient(to bottom, #63e2b7, transparent);
    opacity: 0.15;
    transition: opacity 0.1s ease;
  `;
  bottomBoundaryEl = document.createElement('div');
  bottomBoundaryEl.style.cssText = `
    position: absolute;
    left: 0; right: 0; bottom: 0;
    height: ${THRESHOLD}px;
    pointer-events: none;
    z-index: 9998;
    background: linear-gradient(to top, #63e2b7, transparent);
    opacity: 0.15;
    transition: opacity 0.1s ease;
  `;
  scrollContainerRef.value?.appendChild(topBoundaryEl);
  scrollContainerRef.value?.appendChild(bottomBoundaryEl);
};

const removeBoundaryOverlay = () => {
  topBoundaryEl?.remove();
  bottomBoundaryEl?.remove();
  topBoundaryEl = null;
  bottomBoundaryEl = null;
};

const startAutoScroll = (speed: number) => {
  currentScrollSpeed = speed;
  if (autoScrollFrameId === null) {
    const scrollLoop = () => {
      if (scrollbarInstRef.value && currentScrollSpeed !== 0) {
        const elementUnderCursor = document.elementFromPoint(
          lastClientX,
          lastClientY,
        );
        const row = elementUnderCursor?.closest('tr');
        let targetKey: string | null = null;
        let rect: DOMRect | null = null;

        if (row) {
          targetKey = row.getAttribute('data-key');
          if (targetKey && targetKey !== draggedKey.value) {
            rect = row.getBoundingClientRect();
          }
        }

        scrollbarInstRef.value.scrollBy({ top: currentScrollSpeed });

        if (row && targetKey && targetKey !== draggedKey.value && rect) {
          dragOverKey.value = targetKey;
          const relativeY = lastClientY - rect.top;
          const ratio = relativeY / rect.height;
          const isLast =
            targetKey === displayedKeys.value[displayedKeys.value.length - 1];
          if (ratio < 0.3) {
            dragPosition.value = 'above';
          } else if (isLast && ratio > 0.7) {
            dragPosition.value = 'below';
          } else {
            dragPosition.value = 'on';
          }
        } else {
          dragOverKey.value = null;
          dragPosition.value = null;
        }

        autoScrollFrameId = requestAnimationFrame(scrollLoop);
      } else {
        stopAutoScroll();
      }
    };
    autoScrollFrameId = requestAnimationFrame(scrollLoop);
  }
};

const stopAutoScroll = () => {
  if (autoScrollFrameId !== null) {
    cancelAnimationFrame(autoScrollFrameId);
    autoScrollFrameId = null;
  }
  currentScrollSpeed = 0;
};

const handleBoundaryAutoScroll = (clientY: number) => {
  if (!cachedContainerRect) return;

  lastDragOverTime = Date.now();

  const rect = cachedContainerRect;
  const relativeY = clientY - rect.top;

  const distanceToTop = relativeY;
  const distanceToBottom = rect.height - relativeY;

  let speed = 0;
  let topRatio = 0;
  let bottomRatio = 0;

  if (distanceToTop >= 0 && distanceToTop < THRESHOLD) {
    const ratio = (THRESHOLD - distanceToTop) / THRESHOLD;
    speed = -Math.pow(ratio, 2) * MAX_SPEED;
    topRatio = ratio;
  } else if (distanceToBottom >= 0 && distanceToBottom < THRESHOLD) {
    const ratio = (THRESHOLD - distanceToBottom) / THRESHOLD;
    speed = Math.pow(ratio, 2) * MAX_SPEED;
    bottomRatio = ratio;
  }

  if (topBoundaryEl) {
    topBoundaryEl.style.opacity = String(0.15 + topRatio * 0.75);
  }
  if (bottomBoundaryEl) {
    bottomBoundaryEl.style.opacity = String(0.15 + bottomRatio * 0.75);
  }

  if (speed !== 0) {
    startAutoScroll(speed);
  } else {
    stopAutoScroll();
  }
};

const handleDragStart = (event: PointerEvent, key: string) => {
  if (event.button !== 0) return;

  draggedKey.value = key;
  isPointerDragging = true;
  pointerId = event.pointerId;
  lastClientX = event.clientX;
  lastClientY = event.clientY;

  const target = event.currentTarget as HTMLElement;
  if (target) {
    target.setPointerCapture(event.pointerId);
  }

  document.body.style.cursor = 'grabbing';

  if (scrollContainerRef.value) {
    cachedContainerRect = scrollContainerRef.value.getBoundingClientRect();
  }

  createBoundaryOverlay();

  const row = target.closest('tr');
  if (row) {
    const rect = row.getBoundingClientRect();
    dragPreviewOffsetX = event.clientX - rect.left;
    dragPreviewOffsetY = event.clientY - rect.top;

    dragPreviewEl = document.createElement('div');
    dragPreviewEl.style.cssText = `
      position: fixed;
      pointer-events: none;
      z-index: 99999;
      opacity: 0.7;
      left: 0;
      top: 0;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      border-radius: 4px;
      overflow: hidden;
      width: ${rect.width}px;
      transform: translate3d(${event.clientX - dragPreviewOffsetX}px, ${event.clientY - dragPreviewOffsetY}px, 0);
    `;

    const table = document.createElement('table');
    table.className = 'n-table n-table--striped';
    table.style.cssText = `
      width: 100%;
      background: var(--card-color, #18181c);
      border-collapse: collapse;
      font-size: 12px;
    `;

    const tbody = document.createElement('tbody');
    const clonedRow = row.cloneNode(true) as HTMLElement;

    const inputs = row.querySelectorAll('input');
    const clonedInputs = clonedRow.querySelectorAll('input');
    inputs.forEach((input, index) => {
      if (clonedInputs[index]) {
        clonedInputs[index].value = input.value;
        clonedInputs[index].setAttribute('disabled', 'true');
      }
    });

    tbody.appendChild(clonedRow);
    table.appendChild(tbody);
    dragPreviewEl.appendChild(table);
    document.body.appendChild(dragPreviewEl);
  }
};

const handleDragMove = (event: PointerEvent) => {
  if (!isPointerDragging || draggedKey.value === null) return;

  lastClientX = event.clientX;
  lastClientY = event.clientY;

  if (animationFrameId !== null) return;

  animationFrameId = requestAnimationFrame(() => {
    animationFrameId = null;

    const elementUnderCursor = document.elementFromPoint(
      lastClientX,
      lastClientY,
    );
    const row = elementUnderCursor?.closest('tr');
    let targetKey: string | null = null;
    let rect: DOMRect | null = null;

    if (row) {
      targetKey = row.getAttribute('data-key');
      if (targetKey && targetKey !== draggedKey.value) {
        rect = row.getBoundingClientRect();
      }
    }

    if (dragPreviewEl) {
      dragPreviewEl.style.transform = `translate3d(${lastClientX - dragPreviewOffsetX}px, ${lastClientY - dragPreviewOffsetY}px, 0)`;
    }

    if (row && targetKey && targetKey !== draggedKey.value && rect) {
      dragOverKey.value = targetKey;

      const relativeY = lastClientY - rect.top;
      const ratio = relativeY / rect.height;

      const isLast =
        targetKey === displayedKeys.value[displayedKeys.value.length - 1];

      if (ratio < 0.3) {
        dragPosition.value = 'above';
      } else if (isLast && ratio > 0.7) {
        dragPosition.value = 'below';
      } else {
        dragPosition.value = 'on';
      }
    } else {
      dragOverKey.value = null;
      dragPosition.value = null;
    }

    handleBoundaryAutoScroll(lastClientY);
  });
};

const handleDragEnd = (event: PointerEvent) => {
  if (!isPointerDragging) return;

  const target = event.currentTarget as HTMLElement;
  if (target && pointerId !== null) {
    try {
      target.releasePointerCapture(pointerId);
    } catch (err) {}
  }

  isPointerDragging = false;
  pointerId = null;
  document.body.style.cursor = '';

  if (animationFrameId !== null) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }

  const sourceKey = draggedKey.value;
  const targetKey = dragOverKey.value;

  if (sourceKey && targetKey && sourceKey !== targetKey) {
    const keys = [...jpKeys.value];
    const fromIndex = keys.indexOf(sourceKey);
    const toIndex = keys.indexOf(targetKey);

    if (fromIndex !== -1 && toIndex !== -1) {
      if (dragPosition.value === 'on') {
        keys[fromIndex] = targetKey;
        keys[toIndex] = sourceKey;
      } else {
        keys.splice(fromIndex, 1);
        let targetIndex = keys.indexOf(targetKey);
        if (targetIndex !== -1) {
          if (dragPosition.value === 'below') {
            targetIndex += 1;
          }
          keys.splice(targetIndex, 0, sourceKey);
        }
      }
    }

    const newGlossary: Glossary = {};
    const reversedKeys = [...keys].reverse();
    for (const k of reversedKeys) {
      newGlossary[k] = glossary.value[k];
    }
    isReordering.value = true;
    glossary.value = newGlossary;
  }

  cleanupDrag();
};

const cleanupDrag = () => {
  draggedKey.value = null;
  dragOverKey.value = null;
  dragPosition.value = null;
  stopAutoScroll();

  if (dragPreviewEl) {
    dragPreviewEl.remove();
    dragPreviewEl = null;
  }
  cachedContainerRect = null;
  removeBoundaryOverlay();
};
</script>

<template>
  <n-flex vertical size="large">
    <!-- Terms Editor Header Actions -->
    <n-input-group>
      <n-input
        pair
        v-model:value="termsToAdd"
        size="small"
        separator="=>"
        :placeholder="['日文', '中文']"
        :input-props="{ spellcheck: false }"
      />
      <c-button label="添加" :round="false" size="small" @action="addTerm" />
    </n-input-group>

    <n-input
      v-model:value="importGlossaryRaw"
      type="textarea"
      size="small"
      placeholder="批量导入术语表"
      :input-props="{ spellcheck: false }"
      :rows="1"
    />

    <div class="actions-wrapper">
      <n-flex vertical>
        <n-flex align="center" :wrap="true">
          <c-button
            class="clipboard-import-btn"
            label="剪贴"
            :icon="ContentPasteOutlined"
            :round="false"
            size="small"
            @action="importGlossaryFromClipboard"
          />
          <c-button
            label="JSON"
            :icon="FileDownloadOutlined"
            :round="false"
            size="small"
            @action="downloadGlossaryAsJson"
          />
          <slot name="extra-edit-actions" />
          <c-button
            v-if="whoamiStore.whoami.isAdmin"
            secondary
            type="error"
            label="清空"
            :icon="DeleteOutlineOutlined"
            :round="false"
            size="small"
            @action="clearTerm"
          />
        </n-flex>

        <n-flex align="center" :wrap="true">
          <c-button
            label="导出"
            :icon="ContentCopyOutlined"
            :round="false"
            size="small"
            @action="exportGlossary"
          />
          <c-button
            label="导入"
            :icon="DownloadOutlined"
            :round="false"
            size="small"
            @action="importGlossary"
          />
        </n-flex>
      </n-flex>
    </div>

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
      <n-text
        v-if="lastDeletedTerm === undefined"
        depth="3"
        style="font-size: 12px"
      >
        Tip: 拖动 => 可调整顺序
      </n-text>
    </n-flex>

    <!-- Interactive Terms List Table with Dynamic Loading -->
    <div ref="scrollContainerRef" style="position: relative">
      <n-scrollbar
        ref="scrollbarInstRef"
        @scroll="handleScroll"
        style="
          max-height: 250px;
          border: 1px solid var(--border-color);
          border-radius: 4px;
          padding: 4px;
        "
      >
        <n-table
          v-if="jpKeys.length !== 0"
          striped
          size="small"
          style="font-size: 12px"
        >
          <tr
            v-for="wordJp in displayedKeys"
            :key="wordJp"
            :data-key="wordJp"
            v-memo="[
              wordJp === draggedKey,
              wordJp === dragOverKey ? dragPosition : null,
              glossary[wordJp],
              skippedKeys.has(wordJp),
            ]"
            :class="{
              'dragged-row': wordJp === draggedKey,
              'drag-over-above':
                wordJp === dragOverKey && dragPosition === 'above',
              'drag-over-below':
                wordJp === dragOverKey && dragPosition === 'below',
              'drag-over-on': wordJp === dragOverKey && dragPosition === 'on',
            }"
          >
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
            <td
              nowrap="nowrap"
              class="drag-handle"
              @pointerdown="handleDragStart($event, wordJp)"
              @pointermove="handleDragMove($event)"
              @pointerup="handleDragEnd($event)"
              @pointercancel="cancelDrag"
              @lostpointercapture="cancelDrag"
              title="拖动调整顺序"
            >
              =&gt;
            </td>
            <td style="padding-right: 16px">
              <div style="display: flex; align-items: center; gap: 4px">
                <n-input
                  v-model:value="glossary[wordJp]"
                  size="tiny"
                  placeholder="请输入中文翻译"
                  :theme-overrides="{
                    border: '0',
                    color: 'transparent',
                  }"
                />
                <n-tooltip trigger="hover" v-if="skippedKeys.has(wordJp)">
                  <template #trigger>
                    <n-button
                      size="tiny"
                      quaternary
                      circle
                      style="padding: 0; width: 18px; height: 18px"
                      @click="revokeSkip(wordJp)"
                    >
                      <template #icon>
                        <n-icon :component="RefreshOutlined" />
                      </template>
                    </n-button>
                  </template>
                  撤销去重跳过
                </n-tooltip>
              </div>
            </td>
          </tr>
        </n-table>
        <n-empty
          v-else
          description="暂无术语词条，请添加"
          style="padding: 16px"
        />

        <div
          v-if="limit < jpKeys.length"
          style="text-align: center; padding: 8px"
        >
          <n-button size="tiny" text @click="limit += 100">
            加载更多 (已显示 {{ limit }} / 共 {{ jpKeys.length }})
          </n-button>
        </div>
      </n-scrollbar>
    </div>
  </n-flex>
</template>

<style scoped>
@media (max-width: 420px) {
  .actions-wrapper :deep(.n-button) {
    padding: 0 6px;
    font-size: 12px;
  }
}

.drag-handle {
  cursor: grab;
  user-select: none;
  font-weight: bold;
  padding: 0 8px;
  text-align: center;
  color: #ffffff;
  touch-action: none;
}

.drag-handle:active {
  cursor: grabbing;
}

.dragged-row {
  opacity: 0.4;
}

.drag-over-above td {
  box-shadow: inset 0 2px 0 0 var(--primary-color, #ffffff) !important;
}

.drag-over-below td {
  box-shadow: inset 0 -2px 0 0 var(--primary-color, #ffffff) !important;
}

.drag-over-on td {
  background-color: rgba(255, 255, 255, 0.25) !important;
}
</style>
