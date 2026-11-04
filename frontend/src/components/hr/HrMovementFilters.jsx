import { useEffect, useState } from 'react';
import { ArrowUpDown } from 'lucide-react';
import { hrCatalogApi } from '../../api/hrCatalogApi';
import { apiErrorMessage, movementLabel, movementStatusLabel } from '../../utils/hr';
import { MOVEMENT_ALLOWED, MOVEMENT_DEFAULTS, MOVEMENT_SORT_FIELDS } from '../../utils/hrMovementSearch';
import { Button } from '../ui/Button';
import { HrError } from './HrUi';

const INPUT = 'mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm outline-none focus:border-emerald-500';

export function HrMovementSortHeading({ field, children, sort, onSort }) {
  const active = sort.key === field;
  return <th aria-sort={active ? (sort.direction === 'asc' ? 'ascending' : 'descending') : 'none'} className="px-5 py-4">
    <button type="button" onClick={() => onSort(field)} className="flex items-center gap-1.5 whitespace-nowrap text-left hover:text-emerald-700">
      {children}<ArrowUpDown className={`h-3.5 w-3.5 ${active ? 'text-emerald-700' : ''}`} />
      {active && (sort.direction === 'asc' ? '↑' : '↓')}
    </button>
  </th>;
}

export function HrMovementFilters({ filters, onApply, disabled }) {
  const [draft, setDraft] = useState(filters);
  const [departments, setDepartments] = useState([]);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  useEffect(() => setDraft(filters), [filters]);
  useEffect(() => {
    const controller = new AbortController();
    setError('');
    hrCatalogApi.getAllCatalogItems('departments', { sort: 'name,asc' }, { signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setDepartments(data); })
      .catch((requestError) => { if (!controller.signal.aborted) setError(apiErrorMessage(requestError, 'Không thể tải danh mục đơn vị.')); });
    return () => controller.abort();
  }, [reload]);
  const update = (key, value) => setDraft((current) => ({ ...current, [key]: value }));
  const select = (key, label, choices) => <label className="text-xs font-medium text-gray-600">{label}
    <select aria-label={label} value={draft[key]} onChange={(event) => update(key, key === 'size' ? Number(event.target.value) : event.target.value)} className={INPUT}>
      {choices.map(([value, text]) => <option key={value} value={value}>{text}</option>)}
    </select>
  </label>;
  return <form className="mb-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm" onSubmit={(event) => {
    event.preventDefault();
    onApply({ ...draft, page: 0 });
  }}>
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <label className="text-xs font-medium text-gray-600">Tìm nhân sự hoặc số chứng từ
        <input aria-label="Tìm nhân sự hoặc số chứng từ" value={draft.keyword} onChange={(event) => update('keyword', event.target.value)} placeholder="Mã, họ tên, số quyết định/hợp đồng" className={INPUT} />
      </label>
      {select('type', 'Loại biến động', [['', 'Tất cả loại'], ...MOVEMENT_ALLOWED.type.filter(Boolean).map((type) => [type, movementLabel(type)])])}
      {select('status', 'Trạng thái biến động', [['', 'Tất cả trạng thái'], ...MOVEMENT_ALLOWED.status.filter(Boolean).map((status) => [status, movementStatusLabel(status)])])}
      {select('workforceGroup', 'Nhóm nhân sự hiện tại', [['', 'Tất cả nhóm'], ['OFFICE', 'Văn phòng'], ['GENERAL_LABOR', 'Lao động phổ thông'], ['LEGACY_UNKNOWN', 'Chưa xác định']])}
      {select('departmentId', 'Phòng / xí nghiệp', [['', 'Tất cả đơn vị'], ...departments.map((item) => [item.id, item.name])])}
      <label className="text-xs font-medium text-gray-600">Hiệu lực từ ngày<input aria-label="Hiệu lực từ ngày" type="date" value={draft.from} max={draft.to || undefined} onChange={(event) => update('from', event.target.value)} className={INPUT} /></label>
      <label className="text-xs font-medium text-gray-600">Đến ngày<input aria-label="Đến ngày" type="date" value={draft.to} min={draft.from || undefined} onChange={(event) => update('to', event.target.value)} className={INPUT} /></label>
      {select('recordType', 'Loại bản ghi', [['ALL', 'Tất cả bản ghi'], ['ORIGINAL', 'Bản gốc'], ['ADJUSTMENT', 'Bản điều chỉnh'], ['SUPERSEDED', 'Đã được thay thế']])}
      {select('sort', 'Sắp xếp toàn bộ dữ liệu', MOVEMENT_ALLOWED.sort.map((sort) => {
        const [field, direction] = sort.split(',');
        return [sort, `${MOVEMENT_SORT_FIELDS[field]} · ${direction === 'asc' ? 'Tăng dần' : 'Giảm dần'}`];
      }))}
      {select('size', 'Số dòng mỗi trang', [[20, '20 dòng'], [50, '50 dòng']])}
    </div>
    {error && <div className="mt-3"><HrError message={error} onRetry={() => setReload((current) => current + 1)} /></div>}
    <div className="mt-3 flex justify-end gap-2">
      <Button type="button" variant="secondary" onClick={() => onApply(MOVEMENT_DEFAULTS)}>Xóa lọc</Button>
      <Button type="submit" disabled={disabled}>Áp dụng bộ lọc</Button>
    </div>
  </form>;
}
