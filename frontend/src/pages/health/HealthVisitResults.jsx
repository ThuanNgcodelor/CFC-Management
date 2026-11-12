import { useQuery } from '@tanstack/react-query';
import { healthRecordApi } from '../../api/healthRecordApi';
import { useHrListState } from '../../hooks/useHrListState';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { HrDrawer, HrEmpty, HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { healthError, healthInput } from './healthImportUi';

const DEFAULTS = { resultSearch: '', resultPage: 0 };
export default function HealthVisitResults({ employeeId, visitId, onClose }) {
  const { values, setValues } = useHrListState(DEFAULTS);
  const update = (patch) => setValues(patch, { resetPage: false });
  const [search, setSearch] = useDebouncedHrSearchValue(values.resultSearch, (resultSearch) => update({ resultSearch, resultPage: 0 }));
  const params = { keyword: values.resultSearch, page: values.resultPage, size: 20 };
  const query = useQuery({ queryKey: ['health', 'visit-results', employeeId, visitId, params], queryFn: ({ signal }) => healthRecordApi.results(employeeId, visitId, params, signal) });
  const result = query.data?.results;
  return <HrDrawer isOpen size="full" onClose={onClose} title="Kết quả lần khám — Nội bộ">
    <div className="space-y-4 p-4 sm:p-6">
      <input className={`${healthInput} w-full`} aria-label="Tìm chỉ tiêu lần khám" placeholder="Tìm tên hoặc mã chỉ tiêu" maxLength={100} value={search} onChange={(e) => setSearch(e.target.value)} />
      {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} /> : <>
        <p className="text-sm text-slate-600">{query.data.visit.year} · {query.data.visit.campaign} · {query.data.visit.provider} · Ngày {query.data.visit.examinedOn || 'Chưa xác nhận'} · SID {query.data.visit.sid}</p>
        <p className="text-sm text-slate-500">Giá trị và kết luận giữ theo nguồn. Thiếu khoảng tham chiếu không được coi là bình thường. Chưa có tệp kết quả cá nhân đính kèm trong bộ nguồn này.</p>
        {!result.content.length ? <HrEmpty title="Không có chỉ tiêu phù hợp" /> : <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead><tr><th className="p-2">Chỉ tiêu</th><th className="p-2">Kết quả gốc</th><th className="p-2">Đơn vị / phương pháp</th><th className="p-2">Tham chiếu từ nguồn</th><th className="p-2">Nguồn</th></tr></thead><tbody>{result.content.map((item) => <tr key={item.key} className="border-t border-slate-200"><td className="max-w-64 break-words p-2">{item.label}<small className="block text-slate-500">{item.category}</small></td><td className="max-w-80 whitespace-pre-wrap break-words p-2 font-medium">{item.raw || 'Không có dữ liệu'}</td><td className="p-2">{item.unit || 'Chưa có đơn vị'}<span className="block text-xs">{item.method || 'Chưa xác minh phương pháp'}</span></td><td className="max-w-64 whitespace-pre-wrap break-words p-2">{item.reference || 'Chưa có dữ liệu'}</td><td className="max-w-56 break-words p-2 text-xs text-slate-500">{item.sourceSheet} · dòng {item.sourceRow} · cột {item.sourceColumn}<span className="block">{item.sourceLabel}</span></td></tr>)}</tbody></table></div>}
        <HrPagination page={result.page} totalPages={result.totalPages} totalElements={result.totalElements} loading={query.isFetching} onPageChange={(resultPage) => update({ resultPage })} />
      </>}
    </div>
  </HrDrawer>;
}
