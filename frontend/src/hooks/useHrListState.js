import { useCallback, useEffect, useMemo } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { buildHrListParams, parseHrListState, safeHrPath, withHrReturnTo } from '../utils/hrListState';

const NO_RESTRICTIONS = {};

export function useHrListState(defaults, allowed = NO_RESTRICTIONS) {
  const [params, setParams] = useSearchParams();
  const key = params.toString();
  const values = useMemo(() => parseHrListState(key, defaults, allowed), [key, defaults, allowed]);
  const setValues = useCallback((updates, options = {}) => {
    setParams((current) => buildHrListParams(current, defaults, updates, options.resetPage !== false), { replace: options.replace === true });
  }, [defaults, setParams]);
  const setPage = useCallback((page) => setValues({ page }, { resetPage: false }), [setValues]);
  return { values, setValues, setPage };
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
