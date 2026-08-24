import { ref, onMounted, onUnmounted, type Ref } from 'vue';
import type { ScrollbarInst } from 'naive-ui';
import { useAutoScroll } from './useAutoScroll';

export interface UseGlossaryDragOptions {
  scrollContainerRef: Ref<HTMLDivElement | null>;
  scrollbarInstRef: Ref<ScrollbarInst | null>;
  jpKeys: Ref<string[]>;
  onReorder: (
    sourceKey: string,
    targetKey: string,
    position: 'above' | 'below' | 'on',
  ) => void;
}

export function useGlossaryDrag(options: UseGlossaryDragOptions) {
  const { scrollContainerRef, scrollbarInstRef, jpKeys, onReorder } = options;

  const draggedKey = ref<string | null>(null);
  const dragOverKey = ref<string | null>(null);
  const dragPosition = ref<'above' | 'below' | 'on' | null>(null);

  let animationFrameId: number | null = null;
  let lastClientX = 0;
  let lastClientY = 0;

  let dragPreviewEl: HTMLElement | null = null;
  let dragPreviewOffsetX = 0;
  let dragPreviewOffsetY = 0;

  const updateDragOverTarget = (clientX: number, clientY: number) => {
    const row = document.elementFromPoint(clientX, clientY)?.closest('tr');
    const targetKey =
      row && scrollContainerRef.value?.contains(row)
        ? row.getAttribute('data-key')
        : null;

    if (targetKey && targetKey !== draggedKey.value) {
      const rect = row!.getBoundingClientRect();
      const ratio = (clientY - rect.top) / rect.height;
      const keys = jpKeys.value;
      const isLast = targetKey === keys[keys.length - 1];

      const newPos =
        ratio < 0.3 ? 'above' : isLast && ratio > 0.7 ? 'below' : 'on';
      if (dragOverKey.value !== targetKey || dragPosition.value !== newPos) {
        dragOverKey.value = targetKey;
        dragPosition.value = newPos;
      }
    } else if (dragOverKey.value || dragPosition.value) {
      dragOverKey.value = null;
      dragPosition.value = null;
    }
  };

  const autoScroll = useAutoScroll(scrollbarInstRef, () => {
    updateDragOverTarget(lastClientX, lastClientY);
  });

  const handlePointerMove = (e: PointerEvent) => {
    if (!draggedKey.value) return;
    lastClientX = e.clientX;
    lastClientY = e.clientY;

    autoScroll.handleBoundaryAutoScroll(lastClientY);

    if (animationFrameId === null) {
      animationFrameId = requestAnimationFrame(() => {
        animationFrameId = null;
        if (dragPreviewEl) {
          dragPreviewEl.style.transform = `translate3d(${lastClientX - dragPreviewOffsetX}px, ${lastClientY - dragPreviewOffsetY}px, 0)`;
        }
        updateDragOverTarget(lastClientX, lastClientY);
      });
    }
  };

  const handlePointerUp = () => {
    const sourceKey = draggedKey.value;
    const targetKey = dragOverKey.value;

    if (
      sourceKey &&
      targetKey &&
      sourceKey !== targetKey &&
      dragPosition.value
    ) {
      onReorder(sourceKey, targetKey, dragPosition.value);
    }
    cleanupDrag();
  };

  const preventClick = (e: MouseEvent) => {
    e.stopImmediatePropagation();
    e.preventDefault();
    window.removeEventListener('click', preventClick, true);
  };

  const cleanupDrag = () => {
    if (draggedKey.value) {
      window.addEventListener('click', preventClick, true);
      setTimeout(() => {
        window.removeEventListener('click', preventClick, true);
      }, 100);
    }

    document.body.style.cursor = '';
    document.body.style.userSelect = '';

    window.removeEventListener('pointermove', handlePointerMove);
    window.removeEventListener('pointerup', handlePointerUp);
    window.removeEventListener('pointercancel', cleanupDrag);

    if (animationFrameId !== null) {
      cancelAnimationFrame(animationFrameId);
      animationFrameId = null;
    }

    draggedKey.value = null;
    dragOverKey.value = null;
    dragPosition.value = null;

    if (dragPreviewEl) {
      dragPreviewEl.remove();
      dragPreviewEl = null;
    }

    autoScroll.cleanupAutoScroll();
  };

  const handleKeyDown = (e: KeyboardEvent) => {
    if (draggedKey.value && e.key === 'Escape') {
      e.preventDefault();
      cleanupDrag();
    }
  };

  const handleWheel = (e: WheelEvent) => {
    if (draggedKey.value) {
      e.preventDefault();
      scrollbarInstRef.value?.scrollBy({ top: e.deltaY });
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
    cleanupDrag();
  });

  const createDragPreview = (
    row: HTMLTableRowElement,
    clientX: number,
    clientY: number,
  ) => {
    const rect = row.getBoundingClientRect();
    const offsetX = clientX - rect.left;
    const offsetY = clientY - rect.top;

    const preview = document.createElement('div');
    preview.className = 'drag-preview-container';
    preview.style.width = `${rect.width}px`;
    preview.style.transform = `translate3d(${clientX - offsetX}px, ${clientY - offsetY}px, 0)`;

    const clonedRow = row.cloneNode(true) as HTMLTableRowElement;
    const tds = row.children;
    const clonedTds = clonedRow.children;

    if (clonedTds.length > 0) {
      const firstWidth = (tds[0] as HTMLElement)?.offsetWidth ?? 0;
      clonedTds[0].remove();
      if (clonedTds[0] && firstWidth) {
        (clonedTds[0] as HTMLElement).style.paddingLeft = `${firstWidth}px`;
      }
      for (let i = 1; i < tds.length; i++) {
        if (clonedTds[i - 1]) {
          (clonedTds[i - 1] as HTMLElement).style.width =
            `${(tds[i] as HTMLElement).offsetWidth}px`;
        }
      }
    }

    clonedRow.querySelectorAll('input').forEach((input, i) => {
      const orig = row.querySelectorAll<HTMLInputElement>('input')[i];
      if (orig) input.value = orig.value;
      input.disabled = true;
    });

    const table = document.createElement('table');
    table.className = 'n-table n-table--striped drag-preview-table';
    const tbody = document.createElement('tbody');
    tbody.appendChild(clonedRow);
    table.appendChild(tbody);
    preview.appendChild(table);
    document.body.appendChild(preview);

    return { preview, offsetX, offsetY };
  };

  const handleDragStart = (event: PointerEvent, key: string) => {
    if (event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();

    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }

    draggedKey.value = key;
    lastClientX = event.clientX;
    lastClientY = event.clientY;

    document.body.style.cursor = 'grabbing';
    document.body.style.userSelect = 'none';

    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', cleanupDrag);

    if (scrollContainerRef.value) {
      autoScroll.onDragStart(scrollContainerRef.value);
    }

    const row = (event.currentTarget as HTMLElement).closest('tr');
    if (row) {
      const res = createDragPreview(
        row as HTMLTableRowElement,
        event.clientX,
        event.clientY,
      );
      dragPreviewEl = res.preview;
      dragPreviewOffsetX = res.offsetX;
      dragPreviewOffsetY = res.offsetY;
    }
  };

  return {
    draggedKey,
    dragOverKey,
    dragPosition,
    handleDragStart,
  };
}
