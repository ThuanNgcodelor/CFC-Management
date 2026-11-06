import { useEffect, useState } from 'react';

export function useDebouncedHrSearchValue(value, onCommit, delay = 350) {
  const [draft, setDraft] = useState(value);

  useEffect(() => {
    setDraft(value);
  }, [value]);

  useEffect(() => {
    if (draft === value) return undefined;
    const timer = window.setTimeout(() => onCommit(draft), delay);
    return () => window.clearTimeout(timer);
  }, [delay, draft, onCommit, value]);

  return [draft, setDraft];
}
