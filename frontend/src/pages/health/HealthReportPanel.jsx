import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { healthReportApi } from '../../api/healthReportApi';
import { HrDrawer, HrError, HrLoading } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';

export default function HealthReportPanel({ scope, onClose }) {
  const [controller, setController] = useState(null);
  const [working, setWorking] = useState('');
  const [error, setError] = useState('');
  const query = useQuery({ queryKey: ['health', 'report-summary', scope], queryFn: ({ signal }) => healthReportApi.preview(scope, signal) });
  useEffect(() => {
    const current = new AbortController();
    setController(current);
    return () => current.abort();
  }, []);
  const download = async (format) => {
    setWorking(format); setError('');
    try { await healthReportApi.download(scope, format, controller.signal); }
    catch (failure) { if (!controller.signal.aborted) setError(healthError(failure)); }
    finally { if (!controller.signal.aborted) setWorking(''); }
  };
  return <HrDrawer isOpen size="full" onClose={onClose} title="Báo cáo sức khỏe — Nội bộ">
    <div className="space-y-4 p-4 sm:p-6">
      <p className="text-sm">Khoảng năm <strong>{scope.fromYear}–{scope.toYear}</strong> · {scope.employeeId ? 'Một nhân viên' : 'Tập thể theo bộ lọc'} · {scope.visitIds?.length ? `${scope.visitIds.length} lượt đã chọn` : 'Tất cả lượt trong khoảng năm'}. Xuất toàn bộ phạm vi này, không chỉ trang đang xem.</p>
      {!scope.employeeId && <p className="break-words text-sm text-slate-600">Từ khóa: {scope.keyword || 'Tất cả'} · Nhóm hiện tại: {scope.group || 'Tất cả'} · Dữ liệu: {scope.data} · Sắp xếp: {scope.sort}.</p>}
      {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} /> : <>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">{[['Người trong phạm vi', query.data.people], ['Người có dữ liệu', query.data.peopleWithData], ['Lượt đã nhập', query.data.visits], ['Kết quả nguồn', query.data.results]].map(([label, value]) => <div key={label} className="rounded-xl border border-slate-200 p-4"><span className="text-sm text-slate-600">{label}</span><strong className="block text-2xl">{value}</strong></div>)}</div>
        <ul className="list-disc space-y-1 pl-5 text-sm text-slate-600">{query.data.notes.map((note) => <li key={note}>{note}</li>)}</ul>
        <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[600px] text-left text-sm"><thead><tr><th className="p-2">Chỉ số</th><th className="p-2">Số lượng / mẫu số</th><th className="p-2">Cơ sở tính</th></tr></thead><tbody>{query.data.counts.map((count) => <tr key={count.label} className="border-t border-slate-200"><td className="p-2">{count.label}</td><td className="p-2">{count.count} / {count.denominator}</td><td className="p-2">{count.basis}</td></tr>)}</tbody></table></div>
        <p className="text-xs text-slate-500">Giới hạn mỗi tệp: 500 người, 2.000 lượt, 20.000 kết quả, 10 năm. PDF cá nhân tối đa 1.000 kết quả/200 trang. Báo cáo được tạo lại lúc bấm xuất; nếu dữ liệu vừa thay đổi, hãy tải lại thống kê. Không gồm file gốc bệnh viện hoặc dữ liệu staging.</p>
        <div className="flex flex-wrap gap-3">
          <Button disabled={!controller || Boolean(working) || query.isFetching} onClick={() => download('excel')}>{working === 'excel' ? 'Đang tạo Excel…' : 'Tải Excel báo cáo'}</Button>
          {scope.employeeId && <Button variant="secondary" disabled={!controller || Boolean(working) || query.isFetching || query.data.results > 1000 || query.data.people !== 1} onClick={() => download('pdf')}>{working === 'pdf' ? 'Đang tạo PDF…' : 'Tải PDF cá nhân'}</Button>}
          <Button variant="secondary" disabled={Boolean(working) || query.isFetching} onClick={() => query.refetch()}>Tải lại thống kê</Button>
        </div>
        {error && <HrError message={error} onRetry={() => { setError(''); query.refetch(); }} />}
      </>}
    </div>
  </HrDrawer>;
}

export function HealthReportLauncher({ filters, employeeId, fromYear, toYear, visitIds, selectionOnly = false }) {
  const [open, setOpen] = useState(false);
  const [from, setFrom] = useState(String(filters?.year || new Date().getFullYear() - 3));
  const [to, setTo] = useState(String(filters?.year || new Date().getFullYear()));
  const start = Number(fromYear || from); const end = Number(toYear || to);
  const valid = start >= 1900 && end <= 2200 && end >= start && end - start <= 9;
  const scope = { employeeId: employeeId || null, fromYear: start, toYear: end, keyword: employeeId ? null : filters?.keyword || null, group: employeeId ? null : filters?.group || null, data: employeeId ? 'ALL' : filters?.data || 'WITH_DATA', sort: employeeId ? 'codeAsc' : filters?.sort || 'codeAsc', visitIds: visitIds?.length ? visitIds : null };
  return <div className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4">
    {!employeeId && <><label className="text-sm">Xuất từ năm<input aria-label="Báo cáo từ năm" className={`${healthInput} mt-1 block w-28`} type="number" min={1900} max={2200} value={from} onChange={(e) => setFrom(e.target.value)} /></label><label className="text-sm">Xuất đến năm<input aria-label="Báo cáo đến năm" className={`${healthInput} mt-1 block w-28`} type="number" min={1900} max={2200} value={to} onChange={(e) => setTo(e.target.value)} /></label></>}
    <Button variant="secondary" disabled={!valid || (selectionOnly && !visitIds?.length)} onClick={() => setOpen(true)}>{selectionOnly ? 'Báo cáo các lượt đã chọn' : employeeId ? 'Xuất báo cáo cá nhân' : 'Thống kê và xuất theo bộ lọc'}</Button>
    {!valid && <span className="text-sm text-amber-700">Chọn khoảng tối đa 10 năm.</span>}
    {open && <HealthReportPanel key={JSON.stringify(scope)} scope={scope} onClose={() => setOpen(false)} />}
  </div>;
}
