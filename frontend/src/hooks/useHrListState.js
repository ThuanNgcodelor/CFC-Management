import { useCallback, useEffect, useLayoutEffect, useMemo, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { buildHrListParams, parseHrListState, safeHrPath, withHrReturnTo } from '../utils/hrListState';

const NO_RESTRICTIONS = {};

export function useHrListState(defaults, allowed = NO_RESTRICTIONS) {
  const location = useLocation();
  const navigate = useNavigate();
  const current = useRef({ location, navigate, defaults, allowed, search: location.search, active: true });
  const values = useMemo(() => parseHrListState(location.search, defaults, allowed), [location.search, defaults, allowed]);
  useLayoutEffect(() => {
    const state = current.current;
    if (state.location !== location) state.search = location.search;
    Object.assign(state, { location, navigate, defaults, allowed });
  });
  useLayoutEffect(() => {
    const state = current.current;
    state.active = true;
    return () => { state.active = false; };
  }, []);
  const getValues = useCallback(() => {
    const state = current.current;
    return parseHrListState(state.search, state.defaults, state.allowed);
  }, []);
  const setValues = useCallback((updates, options = {}) => {
    const state = current.current;
    if (!state.active) return;
    const patch = typeof updates === 'function' ? updates(getValues()) : updates;
    const next = buildHrListParams(state.search, state.defaults, patch, options.resetPage !== false).toString();
    if (next === new URLSearchParams(state.search).toString()) return;
    // Keep pending patches together even when two callers navigate before React renders.
    state.search = next;
    state.navigate({ pathname: state.location.pathname, search: next ? `?${next}` : '', hash: state.location.hash },
      { replace: options.replace === true });
  }, [getValues]);
  const setPage = useCallback((page) => setValues({ page }, { resetPage: false }), [setValues]);
  return { values, setValues, setPage, getValues };
}

export function useHrNavigation(fallback = '/manager/hr/employees') {
  const location = useLocation();
  const navigate = useNavigate();
  const source = `${location.pathname}${location.search}`;
  const returnTo = safeHrPath(new URLSearchParams(location.search).get('returnTo'), fallback);
  const open = useCallback((destination, options) => navigate(withHrReturnTo(destination, source), options), [navigate, source]);
  const back = useCallback((options) => navigate(returnTo, options), [navigate, returnTo]);
  return { open, back, returnTo, source };
}

export function useHrPageBounds(result, loading, page, setPage) {
  useEffect(() => {
    if (!loading && result.number === page && page > 0 && page >= result.totalPages) {
      setPage(Math.max(0, result.totalPages - 1));
      toast('Danh sách đã thay đổi. Đã chuyển về trang còn dữ liệu.', { id: 'hr-list-page-adjusted' });
    }
  }, [loading, page, result.number, result.totalPages, setPage]);
}
