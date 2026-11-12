import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { healthImportApi } from '../../api/healthImportApi';
import { HrError, HrLoading, HrEmpty, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import HealthImportRowReview from './HealthImportRowReview';
import HealthPersonResolver from './HealthPersonResolver';
import { healthError, healthInput } from './healthImportUi';

export default function HealthImportReview({ batch, onChanged }) {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState({});
  const [decisions, setDecisions] = useState({});
  const [review, setReview] = useState(null);
  const [resolver, setResolver] = useState(null);
  const [cancelPreview, setCancelPreview] = useState(false);
  const [message, setMessage] = useState('');
  const [filter, setFilter] = useState('ALL');
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['health', 'import-rows', batch.id, page, filter], queryFn: ({ signal }) => healthImportApi.rows(batch.id, page, signal, filter) });
  const refresh = async (value) => {
    setSelected({}); setDecisions({}); setCancelPreview(false); onChanged(value);
    if(filter !== 'ALL')setPage(0);
    await client.invalidateQueries({ queryKey: ['health', 'import-rows', batch.id] });
  };
  const confirm = useMutation({ mutationFn: healthImportApi.confirm, onSuccess: async (result) => {
    setMessage(`Đã nhập ${result.importedRows} dòng; bỏ qua ${result.skippedRows} dòng đã nhập trước đó.`); await refresh(result.batch);
  } });
  const cancel = useMutation({ mutationFn: healthImportApi.cancel, onSuccess: refresh });
  const working = confirm.isPending || cancel.isPending;
  const decision = (row) => decisions[row.id] || { rowId: row.id, previewToken: row.previewToken, choices: {}, reason: '' };
  const ready = (row) => {
    const item = decision(row);
    return row.state === 'PENDING' && row.errors.length === 0 && item.previewToken === row.previewToken
      && row.cells.filter((cell) => cell.action === 'CONFLICT').every((cell) => item.choices[cell.key])
      && (!Object.values(item.choices).includes('REPLACE') || item.reason.trim());
  };
  return <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-4 sm:p-6">
    <h2 className="text-lg font-semibold">2. Rà soát và xác nhận lưu</h2>
    <p className="text-sm text-slate-600">Sheet {batch.sheet} · {batch.totalRows} dòng · Đã nhập {batch.importedRows} · Còn chờ {batch.pendingRows} · Đã hủy {batch.totalRows - batch.importedRows - batch.pendingRows}</p>
    <p className="text-sm text-slate-500">Kết quả được lưu để quản trị rà soát, chưa công bố cho nhân viên. Chỉ chọn các dòng đã kiểm tra. Ô bổ sung trống không xóa kết quả hoặc khoảng tham chiếu cũ.</p>
    <div className="flex flex-wrap gap-2">
      <select className={healthInput} aria-label="Lọc trạng thái dòng nhập" disabled={working} value={filter} onChange={(event) => { setFilter(event.target.value); setPage(0); setSelected({}); setDecisions({}); }}><option value="ALL">Tất cả dòng trong lô</option><option value="PENDING">Chỉ dòng còn chờ rà soát</option><option value="IMPORTED">Đã nhập</option><option value="CANCELLED">Đã hủy</option></select>
      <Button variant="secondary" disabled={working || query.isFetching} onClick={() => { setSelected({}); setDecisions({}); query.refetch(); }}>Tải lại xem trước</Button>
      <Button variant="secondary" disabled={working || !query.data} onClick={() => {
        setSelected(Object.fromEntries(query.data.content.filter(ready).map((row) => [row.id, decision(row)])));
      }}>Chọn dòng hợp lệ của trang này</Button>
      <Button disabled={working || Object.keys(selected).length === 0 || query.isFetching} onClick={() => confirm.mutate({ id: batch.id, rows: Object.values(selected) })}>
        {confirm.isPending ? 'Đang lưu...' : `Xác nhận ${Object.keys(selected).length} dòng`}
      </Button>
      {batch.pendingRows > 0 && <Button variant="secondary" disabled={working} onClick={() => setCancelPreview(true)}>Hủy phần chưa nhập</Button>}
    </div>
    {cancelPreview && <div className="space-y-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
      <p>Sẽ hủy {batch.pendingRows} dòng đang chờ. {batch.importedRows} dòng đã nhập được giữ nguyên.</p>
      <Button disabled={working} onClick={() => cancel.mutate({ id: batch.id, rowVersion: batch.rowVersion })}>Xác nhận hủy phần đang chờ</Button>
      <Button variant="secondary" disabled={working} onClick={() => setCancelPreview(false)}>Quay lại</Button>
    </div>}
    {message && <p role="status" className="text-sm text-emerald-800">{message}</p>}
    {(confirm.isError || cancel.isError) && <HrError message={healthError(confirm.error || cancel.error)} />}
    {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} />
      : !query.data.content.length ? <HrEmpty title="Không có dòng dữ liệu" /> : <>
        <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[800px] text-left text-sm"><thead><tr>
          <th className="p-2">Chọn</th><th className="p-2">Dòng / Người trong file</th><th className="p-2">Kết quả</th><th className="p-2">Trạng thái</th><th className="p-2">Thao tác</th>
        </tr></thead><tbody>{query.data.content.map((row) => <tr key={row.id} className="border-t border-slate-200">
          <td className="p-2"><input type="checkbox" aria-label={`Chọn dòng ${row.sourceRow}`} checked={Boolean(selected[row.id])} disabled={working || (!selected[row.id] && !ready(row))}
            onChange={(event) => setSelected((current) => { const next = { ...current }; if (event.target.checked) next[row.id] = decision(row); else delete next[row.id]; return next; })} /></td>
          <td className="p-2"><strong>{row.name}</strong><span className="block text-xs text-slate-500">Dòng {row.sourceRow} · {row.code || 'Chưa có mã'} · {row.sid || 'Chưa có SID'}</span></td>
          <td className="p-2">{row.cells.length} chỉ tiêu<span className="block text-xs text-amber-800">{row.cells.filter((cell) => cell.action === 'CONFLICT').length} chênh lệch</span></td>
          <td className="max-w-64 p-2">{row.state === 'IMPORTED' ? 'Đã nhập' : row.state === 'CANCELLED' ? 'Đã hủy' : row.errors.length ? <span className="text-red-700">{row.errors.join(' ')}</span> : ready(row) ? 'Có thể xác nhận' : 'Cần chọn cách xử lý chênh lệch'}</td>
          <td className="space-y-2 p-2"><Button variant="secondary" size="sm" disabled={working} onClick={() => setReview(row)}>Xem đối chiếu</Button>
            {row.state === 'PENDING' && <Button variant="secondary" size="sm" disabled={working} onClick={() => setResolver(row)}>Ghép nhân sự</Button>}</td>
        </tr>)}</tbody></table></div>
        <HrPagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} loading={query.isFetching || working} onPageChange={(value) => { setSelected({}); setPage(value); }} />
      </>}
    {review && <HealthImportRowReview key={review.id} row={review} batch={batch} onCorrected={refresh} saved={decisions[review.id]} onClose={() => setReview(null)} onSave={(item) => {
      setDecisions((current) => ({ ...current, [review.id]: item })); setSelected((current) => { const next = { ...current }; delete next[review.id]; return next; });
    }} />}
    {resolver && <HealthPersonResolver key={resolver.id} batch={batch} row={resolver} onClose={() => setResolver(null)} onChanged={refresh} />}
  </section>;
}
