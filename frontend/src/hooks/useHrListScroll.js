import { useEffect, useLayoutEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

const STORAGE_KEY = 'hr-list-scroll-v1';
const MAX_ENTRIES = 100;
const positions = readSavedPositions();

function readSavedPositions() {
  try {
    const entries = Object.entries(JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '{}'))
      .filter(([, top]) => Number.isFinite(top) && top >= 0)
      .slice(-MAX_ENTRIES);
    return new Map(entries);
  } catch (error) {
    // Browser storage may be disabled; navigation still works without persistence.
    console.warn('Không thể đọc vị trí cuộn đã lưu.', error);
    return new Map();
  }
}

function readPosition(key) {
  return positions.get(key) || 0;
}

function savePosition(keys, top) {
  for (const key of keys) {
    positions.delete(key);
    positions.set(key, top);
  }
  while (positions.size > MAX_ENTRIES) positions.delete(positions.keys().next().value);
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(Object.fromEntries(positions)));
  } catch (error) {
    console.warn('Không thể lưu vị trí cuộn; dùng bộ nhớ trong phiên.', error);
  }
}

export function useHrListScroll(loading) {
  const location = useLocation();
  const path = `${location.pathname}${location.search}`;
  const entry = `${location.key}:${path}`;
  const top = useRef(0);
  const restoring = useRef(true);

  useLayoutEffect(() => {
    const container = document.querySelector('main');
    if (!container) return undefined;
    restoring.current = true;
    top.current = readPosition(entry) || readPosition(path);
    const track = () => {
      if (!restoring.current) top.current = container.scrollTop;
    };
    container.addEventListener('scroll', track, { passive: true });
    return () => {
      container.removeEventListener('scroll', track);
      savePosition([entry, path], top.current);
    };
  }, [entry, path]);

  useEffect(() => {
    if (loading) {
      restoring.current = true;
      return undefined;
    }
    const frame = requestAnimationFrame(() => {
      const container = document.querySelector('main');
      if (container) container.scrollTop = top.current;
      restoring.current = false;
    });
    return () => cancelAnimationFrame(frame);
  }, [entry, path, loading]);
}
