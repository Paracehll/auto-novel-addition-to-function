import type { Ref } from 'vue';
import type { ScrollbarInst } from 'naive-ui';

const THRESHOLD = 40;
const MAX_SPEED = 15;

export function useAutoScroll(
  scrollbarInstRef: Ref<ScrollbarInst | null>,
  onScrollFrame?: () => void,
) {
  let frameId: number | null = null;
  let scrollSpeed = 0;
  let containerRect: DOMRect | null = null;

  const stop = () => {
    if (frameId !== null) {
      cancelAnimationFrame(frameId);
      frameId = null;
    }
    scrollSpeed = 0;
  };

  const handleBoundaryAutoScroll = (clientY: number) => {
    if (!containerRect) return;
    const relY = clientY - containerRect.top;
    const distTop = relY;
    const distBottom = containerRect.height - relY;

    if (distTop >= 0 && distTop < THRESHOLD) {
      scrollSpeed = -Math.pow((THRESHOLD - distTop) / THRESHOLD, 2) * MAX_SPEED;
    } else if (distBottom >= 0 && distBottom < THRESHOLD) {
      scrollSpeed =
        Math.pow((THRESHOLD - distBottom) / THRESHOLD, 2) * MAX_SPEED;
    } else {
      scrollSpeed = 0;
    }

    if (scrollSpeed !== 0 && frameId === null) {
      const loop = () => {
        if (scrollSpeed !== 0) {
          scrollbarInstRef.value?.scrollBy({ top: scrollSpeed });
          onScrollFrame?.();
          frameId = requestAnimationFrame(loop);
        } else {
          stop();
        }
      };
      frameId = requestAnimationFrame(loop);
    } else if (scrollSpeed === 0) {
      stop();
    }
  };

  return {
    onDragStart: (containerEl: HTMLElement) => {
      containerRect = containerEl.getBoundingClientRect();
    },
    handleBoundaryAutoScroll,
    stopAutoScroll: stop,
    cleanupAutoScroll: () => {
      stop();
      containerRect = null;
    },
  };
}
