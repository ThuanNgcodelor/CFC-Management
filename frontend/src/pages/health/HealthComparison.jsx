import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { healthRecordApi } from '../../api/healthRecordApi';
import { useHrListState } from '../../hooks/useHrListState';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { HrDrawer, HrEmpty, HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';
import HealthTrendChart from './HealthTrendChart';

const DEFAULTS = { compareSearch: '', comparePage: 0 };
export default function HealthComparison({ employeeId, visitIds, onClose }) {
  const { values, setValues } = useHrListState(DEFAULTS);
  const update = (patch) => setValues(patch, { resetPage: false });
  const [search, setSearch] = useDebouncedHrSearchValue(values.compareSearch, (compareSearch) => update({ compareSearch, comparePage: 0 }));
  const [chartKey, setChartKey] = useState('');
  const body = { visitIds, keyword: values.compareSearch, page: values.comparePage, size: 20 };
  const query = useQuery({ queryKey: ['health', 'comparison', employeeId, body], queryFn: ({ signal }) => healthRecordApi.compare(employeeId, body, signal) });
  const rows = query.data?.rows;
  const chart = rows?.content.find((row) => row.indicator.key === chartKey);
  return <HrDrawer isOpen size="full" onClose={onClose} title="So sánh kết quả sức khỏe — Nội bộ">
    <div className="space-y-4 p-4 sm:p-6">
      <p className="text-sm text-slate-600">Mỗi cột là một lượt khám. Các phương pháp/đơn vị khác nhau giữ riêng, ô thiếu không thay bằng 0; thay đổi số học không tự đánh giá sức khỏe tốt/xấu.</p>
      <input className={`${healthInput} w-full`} aria-label="Tìm chỉ tiêu so sánh" placeholder="Tìm tên hoặc mã chỉ tiêu" maxLength={100} value={search} onChange={(e) => setSearch(e.target.value)} />
      {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} /> : <>
        {!rows.content.length ? <HrEmpty title="Không có chỉ tiêu phù hợp để đặt cạnh nhau" /> : <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead><tr><th className="p-2">Chỉ tiêu / bối cảnh</th>{query.data.visits.map((visit) => <th className="min-w-44 p-2" key={visit.id}>{visit.year}<span className="block text-xs font-normal">{visit.examinedOn || 'Chưa xác nhận ngày'} · {visit.provider}</span><span className="block text-xs font-normal">SID {visit.sid}</span></th>)}<th className="p-2">Đối chiếu</th></tr></thead><tbody>{rows.content.map((row) => <tr key={row.indicator.key} className="border-t border-slate-200">
          <td className="max-w-64 break-words p-2 font-medium">{row.indicator.label}<small className="block font-normal text-slate-500">{row.indicator.unit || 'Chưa có đơn vị'} · {row.indicator.method || 'Chưa xác minh phương pháp'}</small></td>
          {row.cells.map((cell) => <td className="max-w-64 whitespace-pre-wrap break-words p-2" key={cell.visitId}>{cell.missing ? <span className="text-slate-400">Không có dữ liệu</span> : <>{cell.raw}<small className="mt-1 block text-slate-500">Tham chiếu: {cell.reference || 'Chưa có'}<br />{cell.sourceSheet} · {cell.sourceRow}/{cell.sourceColumn}</small></>}</td>)}
          <td className="min-w-52 max-w-80 p-2 text-xs text-slate-600">{row.note}{row.numericComparable && <><span className="my-2 block">Chênh lệch cuối − đầu: {row.difference}</span><Button variant="secondary" onClick={() => setChartKey(row.indicator.key)}>Xem xu hướng số học</Button></>}</td>
        </tr>)}</tbody></table></div>}
        {chart?.numericComparable && <HealthTrendChart row={chart} visits={query.data.visits} />}
        <HrPagination page={rows.page} totalPages={rows.totalPages} totalElements={rows.totalElements} loading={query.isFetching} onPageChange={(comparePage) => { setChartKey(''); update({ comparePage }); }} />
      </>}
    </div>
  </HrDrawer>;
}
