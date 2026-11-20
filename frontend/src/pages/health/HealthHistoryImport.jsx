import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { healthHistoryApi } from '../../api/healthHistoryApi';
import { HrError, HrLoading } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError } from './healthImportUi';

export default function HealthHistoryImport({ onReview }) {
  const client = useQueryClient();
  const [file, setFile] = useState(null);
  const query = useQuery({ queryKey: ['health', 'history-import'], queryFn: ({ signal }) => healthHistoryApi.status(signal),
    refetchInterval: (current) => current.state.data?.state === 'RUNNING' ? 1500 : false });
  const complete = async () => { setFile(null); await client.invalidateQueries({ queryKey: ['health'] }); };
  const unlock = useMutation({ mutationFn: healthHistoryApi.unlock, onSuccess: complete });
  const retry = useMutation({ mutationFn: healthHistoryApi.run, onSuccess: complete });
  const status = query.data;
  if (query.isPending) return <HrLoading label="Đang kiểm tra gói dữ liệu lịch sử..." />;
  if (query.isError) return <HrError message="Không kiểm tra được dữ liệu lịch sử tự nhập." onRetry={query.refetch} />;
  if (!status || status.state === 'UNAVAILABLE') return null;
  const working = status.state === 'RUNNING' || unlock.isPending || retry.isPending;
  return <section className="space-y-3 rounded-xl border border-emerald-200 bg-emerald-50/60 p-4 sm:p-6" aria-label="Dữ liệu lịch sử tự nhập">
    <h2 className="text-lg font-semibold">Dữ liệu lịch sử 2023–2025</h2>
    <p className="text-sm text-slate-700" role="status">{status.message}</p>
    {status.state === 'LOCKED' ? <>
      <p className="text-sm text-slate-600">Chỉ cần cấp file khóa một lần trên máy chủ này. Những lần triển khai sau tự kiểm tra gói mới và chống nhập trùng; không cần chọn từng cột hoặc tải lại Excel.</p>
      <label className="block text-sm font-medium">File khóa dữ liệu lịch sử<input className="mt-2 block max-w-full text-sm" type="file" accept=".key" disabled={working} onChange={(event) => setFile(event.target.files?.[0] || null)} /></label>
      <Button disabled={!file || working} onClick={() => unlock.mutate(file)}>{unlock.isPending ? 'Đang kiểm tra khóa...' : 'Kích hoạt và tự nhập lịch sử'}</Button>
    </> : <>
      <p className="text-sm font-medium">Đã nhập {status.imported} dòng · Còn chờ rà soát {status.pending} dòng nguồn.</p>
      <p className="text-xs text-slate-600">Số dòng nguồn có thể gồm bản đối chiếu lặp giữa các sheet, không phải số nhân viên. Chưa tự công bố kết quả cho cá nhân.</p>
      {status.batches?.length > 0 && <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[600px] text-left text-sm"><thead><tr><th className="p-2">Năm / Nguồn</th><th className="p-2">Đã nhập</th><th className="p-2">Còn chờ</th><th className="p-2">Thao tác</th></tr></thead><tbody>
        {status.batches.map((batch) => <tr key={batch.id} className="border-t border-emerald-100"><td className="p-2">{batch.year} · {batch.sheet}</td><td className="p-2">{batch.imported}</td><td className="p-2">{batch.pending}</td><td className="p-2"><Button size="sm" variant="secondary" disabled={working} onClick={() => onReview(batch)}>Mở lô rà soát</Button></td></tr>)}
      </tbody></table></div>}
      <Button variant="secondary" disabled={working} onClick={() => retry.mutate()}>{working ? 'Đang xử lý lịch sử...' : 'Kiểm tra và tiếp tục nhập các dòng đủ điều kiện'}</Button>
    </>}
    {(unlock.isError || retry.isError) && <HrError message={healthError(unlock.error || retry.error)} />}
  </section>;
}
