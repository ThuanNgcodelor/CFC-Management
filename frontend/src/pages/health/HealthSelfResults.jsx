import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { HrDrawer, HrEmpty, HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';
import HealthTrendChart from './HealthTrendChart';

export default function HealthSelfResults({ api, view, onClose, onExit }) {
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useDebouncedHrSearchValue(keyword, (value) => { setKeyword(value); setPage(0); });
  const [chartKey, setChartKey] = useState('');
  const [download, setDownload] = useState({ working: false, error: '' });
  const controller = useRef(null);
  useEffect(() => () => controller.current?.abort(), []);
  const query = useQuery({ queryKey: ['health-self', 'results', view, keyword, page], queryFn: ({ signal }) => view.id
    ? api.detail(view.id, { keyword, page, size: 20 }, signal) : api.compare({ visitIds: view.ids, keyword, page, size: 20 }, signal) });
  useEffect(() => { if (query.error?.status === 401) onExit(); }, [query.error, onExit]);
  const rows = view.id ? query.data?.results : query.data?.rows;
  const chart = !view.id && rows?.content.find((row) => row.indicator.key === chartKey);
  const pdf = async () => {
    setDownload({ working: true, error: '' });
    controller.current?.abort(); controller.current = new AbortController();
    try { await api.pdf(view.id, controller.current.signal); }
    catch (error) { if (!controller.current.signal.aborted) { if (error.status === 401) onExit(); else setDownload({ working: false, error: healthError(error) }); } }
    finally { if (!controller.current.signal.aborted) setDownload((value) => ({ ...value, working: false })); }
  };
  return <HrDrawer isOpen size="full" title={view.id ? 'Kết quả khám của tôi' : 'So sánh các lượt khám của tôi'} onClose={onClose}>
    <section className="space-y-4 p-4 sm:p-6">
      <p className="text-sm text-slate-600">Giữ nguyên kết quả và khoảng tham chiếu của cơ sở khám. So sánh số học không phải kết luận y khoa. File đã tải về vẫn còn trên thiết bị sau khi bạn thoát.</p>
      <input aria-label="Tìm chỉ tiêu cá nhân" className={`${healthInput} w-full`} maxLength={100} placeholder="Tìm tên hoặc mã chỉ tiêu" value={search} onChange={(event) => setSearch(event.target.value)} />
      {view.id && <Button disabled={download.working || query.isPending || query.isError} onClick={pdf}>{download.working ? 'Đang tạo phiếu…' : 'Tải phiếu PDF của tôi'}</Button>}
      {download.error && <HrError message={download.error} />}
      {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} /> : <>
        {view.id && <p className="text-sm">{query.data.release.year} · {query.data.release.provider} · Phân loại nguồn: {query.data.release.classification || 'Chưa có dữ liệu'}</p>}
        {!rows.content.length ? <HrEmpty title="Không có chỉ tiêu phù hợp" /> : view.id ? <div className="space-y-3">{rows.content.map((result) => <article key={result.key} className="rounded-xl border border-slate-200 p-4"><h3 className="font-semibold">{result.label}</h3><p className="mt-2 whitespace-pre-wrap break-words text-lg">{result.raw}</p><p className="mt-2 break-words text-sm text-slate-600">Đơn vị: {result.unit || 'Chưa có'} · Phương pháp: {result.method || 'Chưa xác minh'}</p><p className="break-words text-sm text-slate-600">Khoảng tham chiếu: {result.reference || 'Chưa có dữ liệu'}</p></article>)}</div>
          : <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[680px] text-left text-sm"><thead><tr><th className="p-3">Chỉ tiêu</th>{query.data.visits.map((visit) => <th key={visit.id} className="p-3">{visit.year}<span className="block text-xs font-normal">{visit.examinedOn || 'Chưa xác nhận ngày'} · {visit.provider}</span></th>)}<th className="p-3">Đối chiếu</th></tr></thead><tbody>{rows.content.map((row) => <tr key={row.indicator.key} className="border-t border-slate-200"><td className="p-3">{row.indicator.label}<small className="block">{row.indicator.unit || 'Chưa có đơn vị'} · {row.indicator.method || 'Chưa xác minh phương pháp'}</small></td>{row.cells.map((cell) => <td key={cell.visitId} className="max-w-64 whitespace-pre-wrap break-words p-3">{cell.missing ? 'Không có dữ liệu' : cell.raw}<small className="mt-1 block">Tham chiếu: {cell.reference || 'Chưa có'}</small></td>)}<td className="min-w-48 max-w-80 p-3 text-xs">{row.note}{row.numericComparable && <><p className="my-2">Chênh lệch cuối − đầu: {row.difference}</p><Button variant="secondary" onClick={() => setChartKey(row.indicator.key)}>Xem xu hướng</Button></>}</td></tr>)}</tbody></table></div>}
        {chart?.numericComparable && <HealthTrendChart row={chart} visits={query.data.visits} />}
        <HrPagination page={rows.page} totalPages={rows.totalPages} totalElements={rows.totalElements} loading={query.isFetching} onPageChange={(next) => { setPage(next); setChartKey(''); }} />
      </>}
    </section>
  </HrDrawer>;
}
