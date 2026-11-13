import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { healthPublicationApi } from '../../api/healthPublicationApi';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { HrDrawer, HrEmpty, HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';

export default function HealthPublicationHistory({ employeeId, visitId, onClose, onChanged }) {
  const [page, setPage] = useState(0); const [selected, setSelected] = useState(''); const [resultPage, setResultPage] = useState(0);
  const [reason, setReason] = useState(''); const [working, setWorking] = useState(''); const [error, setError] = useState('');
  const [controller, setController] = useState(null); const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useDebouncedHrSearchValue(keyword, (value) => { setKeyword(value); setResultPage(0); });
  const client = useQueryClient();
  useEffect(() => { const value = new AbortController(); setController(value); return () => value.abort(); }, []);
  const history = useQuery({ queryKey: ['health', 'publication-history', employeeId, visitId, page], queryFn: ({ signal }) => healthPublicationApi.history({ employeeId, visitId, page, size: 20 }, signal) });
  const results = useQuery({ queryKey: ['health', 'published-results', employeeId, selected, keyword, resultPage], enabled: Boolean(selected), queryFn: ({ signal }) => healthPublicationApi.results(selected, { employeeId, keyword, page: resultPage, size: 20 }, signal) });
  const revoke = async (release) => {
    setWorking('revoke'); setError('');
    try { await healthPublicationApi.revoke(release.id, { employeeId, rowVersion: release.rowVersion, reason: reason.trim() }); setSelected(''); await client.invalidateQueries({ queryKey: ['health'] }); await onChanged?.(); }
    catch (failure) { setError(healthError(failure)); } finally { setWorking(''); }
  };
  const download = async (release) => {
    setWorking('pdf'); setError('');
    try { await healthPublicationApi.pdf(release.id, employeeId, controller.signal); }
    catch (failure) { if (!controller.signal.aborted) setError(healthError(failure)); }
    finally { if (!controller.signal.aborted) setWorking(''); }
  };
  return <HrDrawer isOpen size="full" onClose={() => { if (working !== 'revoke') onClose(); }} title="Lịch sử phiên bản công bố">
    <div className="space-y-4 p-4 sm:p-6">
      <p className="text-sm text-slate-600">Nội dung công bố là snapshot cố định, tách khỏi bản biên tập. Chỉ phiên bản mới nhất đang công bố được xem/tải ở đây. Bản cũ và bản thu hồi giữ lịch sử, không dùng làm đường vòng tải nội dung. File đã tải về trước đó không thể thu hồi khỏi thiết bị.</p>
      <label className="block text-sm">Lý do thu hồi<textarea aria-label="Lý do thu hồi phiên bản" className={`${healthInput} mt-1 w-full`} rows={2} maxLength={500} value={reason} disabled={Boolean(working)} onChange={(e) => setReason(e.target.value)} /></label>
      {history.isPending ? <HrLoading /> : history.isError ? <HrError message={healthError(history.error)} onRetry={history.refetch} /> : <>
        {!history.data.content.length ? <HrEmpty title="Chưa có phiên bản công bố" /> : <div className="space-y-3">{history.data.content.map((release, index) => {
          const current = page === 0 && index === 0 && release.status === 'PUBLISHED';
          return <article key={release.id} className="rounded-xl border border-slate-200 p-4 text-sm"><strong>Phiên bản {release.revision} · {release.status === 'REVOKED' ? 'Đã thu hồi' : current ? 'Đang công bố' : 'Phiên bản trước'}</strong><p>{release.createdAt} · {release.createdByActor}</p><p className="whitespace-pre-wrap break-words">Lý do công bố: {release.publishReason || 'Chưa có từ dữ liệu cũ'}</p>{release.revokeReason && <p className="whitespace-pre-wrap break-words">Lý do thu hồi: {release.revokeReason}</p>}{current && <div className="mt-3 flex flex-wrap gap-3"><Button variant="secondary" disabled={Boolean(working)} onClick={() => { setSelected(release.id); setResultPage(0); }}>Xem bản công bố hiện tại</Button><Button disabled={!controller || Boolean(working)} onClick={() => download(release)}>Tải PDF bản công bố</Button><Button variant="secondary" disabled={Boolean(working) || !reason.trim()} onClick={() => revoke(release)}>Thu hồi phiên bản này</Button></div>}</article>;
        })}</div>}
        <HrPagination page={page} totalPages={history.data.totalPages} totalElements={history.data.totalElements} loading={history.isFetching} onPageChange={(value) => { setSelected(''); setPage(value); }} />
      </>}
      {error && <HrError message={error} />}
      {selected && <section className="space-y-3 rounded-xl border border-emerald-200 p-4">
        <h3 className="font-semibold">Kết quả trong phiên bản công bố</h3><input aria-label="Tìm chỉ tiêu đã công bố" className={healthInput} maxLength={100} placeholder="Tên hoặc mã chỉ tiêu" value={search} onChange={(e) => setSearch(e.target.value)} />
        {results.isPending ? <HrLoading /> : results.isError ? <HrError message={healthError(results.error)} onRetry={results.refetch} /> : <>
          <p className="text-sm">{results.data.person.code} · {results.data.person.name} · {results.data.visit.year} · SID {results.data.visit.sid} · Phân loại nguồn: {results.data.visit.classification || 'Chưa có dữ liệu'}</p>
          <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[700px] text-left text-sm"><thead><tr><th className="p-2">Chỉ tiêu</th><th className="p-2">Giá trị công bố</th><th className="p-2">Đơn vị / phương pháp</th><th className="p-2">Tham chiếu / nguồn</th></tr></thead><tbody>{results.data.results.content.map((v) => <tr key={v.key} className="border-t border-slate-200"><td className="p-2">{v.label}</td><td className="max-w-80 whitespace-pre-wrap break-words p-2">{v.raw}</td><td className="p-2">{v.unit || 'Chưa có'} · {v.method || 'Chưa xác minh'}</td><td className="p-2">{v.reference || 'Chưa có'}<small className="block">{v.sourceSheet} / {v.sourceRow} / {v.sourceColumn}</small></td></tr>)}</tbody></table></div>
          <HrPagination page={resultPage} totalPages={results.data.results.totalPages} totalElements={results.data.results.totalElements} loading={results.isFetching} onPageChange={setResultPage} />
        </>}
      </section>}
    </div>
  </HrDrawer>;
}
