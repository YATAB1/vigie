"use client";

import { useEffect, useRef } from "react";

/**
 * Calls ``tick`` every ``intervalMs`` while ``active`` is true, at most
 * ``maxTicks`` times, then calls ``onGiveUp``.
 */
export function usePolling(
  active: boolean,
  tick: () => void | Promise<void>,
  { intervalMs = 2000, maxTicks = 60, onGiveUp }: { intervalMs?: number; maxTicks?: number; onGiveUp?: () => void } = {},
) {
  const tickRef = useRef(tick);
  const giveUpRef = useRef(onGiveUp);

  useEffect(() => {
    tickRef.current = tick;
    giveUpRef.current = onGiveUp;
  });

  useEffect(() => {
    if (!active) return;
    let count = 0;
    const timer = setInterval(() => {
      count += 1;
      if (count > maxTicks) {
        clearInterval(timer);
        giveUpRef.current?.();
        return;
      }
      void tickRef.current();
    }, intervalMs);
    return () => clearInterval(timer);
  }, [active, intervalMs, maxTicks]);
}
