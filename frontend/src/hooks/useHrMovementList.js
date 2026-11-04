import { useEffect, useState } from 'react';
import { hrActivityApi } from '../api/hrActivityApi';
import { normalizePage } from '../api/hrApiUtils';
import { apiErrorMessage } from '../utils/hr';
import { MOVEMENT_ALLOWED, MOVEMENT_DEFAULTS } from '../utils/hrMovementSearch';
import { useHrListState, useHrPageBounds } from './useHrListState';
import { useHrListScroll } from './useHrListScroll';

export function useHrMovementList() {
  const { values, setValues, setPage } = useHrListState(MOVEMENT_DEFAULTS, MOVEMENT_ALLOWED);
  const [result, setResult] = useState(normalizePage(null));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    hrActivityApi.getMovements(values, { signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setResult(normalizePage(data)); })
      .catch((requestError) => { if (!controller.signal.aborted) setError(apiErrorMessage(requestError, 'Không thể tải lịch sử tăng/giảm.')); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [values, reloadKey]);
  useHrListScroll(loading);
  useHrPageBounds(result, loading, values.page, setPage);
  const [key, direction] = values.sort.split(',');
  const toggleSort = (field) => setValues({ sort: `${field},${key === field && direction === 'asc' ? 'desc' : 'asc'}` });
  return { filters: values, setFilters: setValues, page: values.page, setPage, result, loading, error,
    reloadKey, setReloadKey, sortConfig: { key, direction }, toggleSort };
}
