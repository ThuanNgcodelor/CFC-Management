import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

export function useDebouncedHrSearchValue(value, onCommit, delay = 350) {
  const [draft, setDraft] = useState(value);
  const location = useLocation();
  const navigationType = useNavigationType();
  const current = useRef({ value, draft: value, onCommit, delay, key: location.key, pending: [], timer: null });

  useLayoutEffect(() => {
    const state = current.current;
    Object.assign(state, { onCommit, delay });
    const historyNavigation = navigationType === 'POP' && state.key !== location.key;
    if (historyNavigation || state.value !== value) {
      const acknowledgement = historyNavigation ? -1 : state.pending.indexOf(value);
      if (acknowledgement >= 0) {
        state.pending.splice(0, acknowledgement + 1);
      } else {
        window.clearTimeout(state.timer);
        state.pending = [];
        state.draft = value;
        setDraft(value);
      }
      state.value = value;
    }
    state.key = location.key;
  }, [value, onCommit, delay, location.key, navigationType]);
  useLayoutEffect(() => {
    const state = current.current;
    return () => window.clearTimeout(state.timer);
  }, []);

  const changeDraft = useCallback((next) => {
    const state = current.current;
    state.draft = typeof next === 'function' ? next(state.draft) : next;
    setDraft(state.draft);
    window.clearTimeout(state.timer);
    if (state.draft.trim() === state.value) return;
    state.timer = window.setTimeout(() => {
      const committed = state.draft.trim();
      if (committed === state.value) return;
      state.pending.push(committed);
      state.onCommit(committed);
    }, state.delay);
  }, []);
  return [draft, changeDraft];
}
