import { useEffect, useMemo, useState } from 'react';
import { ChevronRight, Search } from 'lucide-react';
import { Button } from '../ui/Button';
import { HrEmpty, HrPagination, HrStatusBadge } from './HrUi';
import { formatHrDate } from '../../utils/hr';

const pageSize = 50;
const statusText = {
  QUALIFIED: 'Đạt điều kiện', QUALIFIED_EXCEPTION: 'Đạt ngoại lệ', NOT_QUALIFIED: 'Chưa đạt',
  EXCLUDED: 'Không áp dụng', STALE: 'Cần chốt lại',
};
const normalized = (value) => String(value || '').toLocaleLowerCase('vi').normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');

function Progress({ progress, pendingMonth }) {
  if (!progress) return '—';
  return (
    <div className="space-y-1 text-xs">
      <p>Đã chốt · định kỳ: <b>{progress.periodicQualifiedMonths}/{progress.periodicRequiredMonths}</b> tháng</p>
      <p>Đã chốt · gắn bó: <b>{progress.loyaltyQualifiedMonths}/{progress.loyaltyRequiredMonths}</b> tháng</p>
      {pendingMonth && <p className="font-medium text-emerald-700">Tháng này dự kiến +1 sau khi chốt</p>}
    </div>
  );
}

export function HrNightRewardMonthTable({ employees = [], onOpenEmployee }) {
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [filter, setFilter] = useState('all');
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedKeyword(keyword), 250);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const filtered = useMemo(() => employees.filter((item) => {
    const status = item.finalizedStatus || item.calculatedStatus;
    if (filter === 'qualified' && !['QUALIFIED', 'QUALIFIED_EXCEPTION'].includes(status)) return false;
    if (filter === 'not-qualified' && status !== 'NOT_QUALIFIED') return false;
    if (filter === 'pending' && item.finalizedStatus && item.finalizedStatus !== 'STALE') return false;
    const needle = normalized(debouncedKeyword.trim());
    return !needle || normalized(`${item.employeeCode} ${item.employeeName}`).includes(needle);
  }), [employees, filter, debouncedKeyword]);
  const totalPages = Math.ceil(filtered.length / pageSize);
  const currentPage = Math.min(page, Math.max(0, totalPages - 1));
  const visible = filtered.slice(currentPage * pageSize, (currentPage + 1) * pageSize);

  return (
    <>
      <div className="flex flex-col gap-3 border-b border-gray-100 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <label className="relative block w-full sm:max-w-sm">
          <span className="sr-only">Tìm nhân viên trong tháng</span>
          <Search className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
          <input value={keyword} onChange={(event) => { setKeyword(event.target.value); setPage(0); }}
            placeholder="Tìm mã hoặc tên trong tháng" className="h-10 w-full rounded-lg border border-gray-300 pl-9 pr-3 text-sm" />
        </label>
        <select value={filter} onChange={(event) => { setFilter(event.target.value); setPage(0); }}
          aria-label="Lọc kết quả thưởng" className="h-10 rounded-lg border border-gray-300 bg-white px-3 text-sm">
          <option value="all">Tất cả trạng thái</option>
          <option value="qualified">Đạt điều kiện</option>
          <option value="not-qualified">Chưa đạt</option>
          <option value="pending">Chưa chốt / cần chốt lại</option>
        </select>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-[1020px] w-full text-left text-sm">
          <thead className="bg-gray-50 text-xs uppercase text-gray-500">
            <tr>
              <th className="px-5 py-3">Nhân viên</th><th className="px-4 py-3 text-center">Ca đêm hợp lệ</th>
              <th className="px-4 py-3">Kết quả tháng</th><th className="px-4 py-3">Tháng đã ghi nhận</th>
              <th className="px-4 py-3">Nguồn đối soát</th><th className="px-5 py-3 text-right">Chi tiết</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {visible.map((item) => (
              <tr key={item.employeeId} className="hover:bg-slate-50">
                <td className="px-5 py-4"><b>{item.employeeCode}</b><p className="text-xs text-gray-500">{item.employeeName}</p></td>
                <td className="px-4 py-4 text-center font-semibold">{item.actualNightShiftCount}/{item.requiredNightShiftCount}</td>
                <td className="px-4 py-4">
                  <HrStatusBadge status={item.finalizedStatus || item.calculatedStatus}
                    label={statusText[item.finalizedStatus || item.calculatedStatus]} />
                  <p className="mt-1 max-w-xs text-xs text-gray-500">{item.calculatedReason}</p>
                </td>
                <td className="px-4 py-4"><Progress progress={item.progress}
                  pendingMonth={['QUALIFIED', 'QUALIFIED_EXCEPTION'].includes(item.calculatedStatus) &&
                    (!item.finalizedStatus || item.finalizedStatus === 'STALE')} /></td>
                <td className="px-4 py-4 text-xs text-gray-600">
                  {item.sourceShifts?.length ? item.sourceShifts.map((source) => formatHrDate(source.workDate)).join(', ') : '—'}
                </td>
                <td className="px-5 py-4 text-right"><Button type="button" size="sm" variant="secondary"
                  onClick={() => onOpenEmployee(item.employeeCode)}>Theo nhân viên <ChevronRight className="h-3.5 w-3.5" /></Button></td>
              </tr>
            ))}
          </tbody>
        </table>
        {!visible.length && <div className="p-5"><HrEmpty title={employees.length ? 'Không có nhân viên phù hợp' : 'Chưa có dữ liệu công nhân để xét'}
          description={employees.length ? 'Thử từ khóa hoặc trạng thái khác.' : 'Hãy chốt file ca sản xuất trước khi xét thưởng.'} /></div>}
      </div>
      <HrPagination page={currentPage} totalPages={totalPages} totalElements={filtered.length} onPageChange={setPage} />
    </>
  );
}
