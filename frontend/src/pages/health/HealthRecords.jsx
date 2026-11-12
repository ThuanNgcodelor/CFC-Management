import { useQuery } from '@tanstack/react-query';
import { healthRecordApi } from '../../api/healthRecordApi';
import { useHrListState, useHrPageBounds } from '../../hooks/useHrListState';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { normalizePage } from '../../api/hrApiUtils';
import { HrEmpty, HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';
import HealthRecordDetail from './HealthRecordDetail';
import { HealthReportLauncher } from './HealthReportPanel';

const DEFAULTS = { recordSearch: '', recordYear: '', recordGroup: '', recordData: 'WITH_DATA', recordSort: 'codeAsc', page: 0, employee: '' };
const ALLOWED = { recordData: ['ALL', 'WITH_DATA', 'WITHOUT_DATA'], recordSort: ['codeAsc', 'codeDesc', 'nameAsc', 'nameDesc'], recordGroup: ['', 'OFFICE', 'GENERAL_LABOR', 'LEGACY_UNKNOWN'] };
const healthGroups = { OFFICE: 'Văn phòng', GENERAL_LABOR: 'Lao động phổ thông', LEGACY_UNKNOWN: 'Chưa xác định' };

export default function HealthRecords() {
  const { values, setValues, setPage } = useHrListState(DEFAULTS, ALLOWED);
  const [search, setSearch] = useDebouncedHrSearchValue(values.recordSearch, (recordSearch) => setValues({ recordSearch }));
  const params = { keyword: values.recordSearch, year: values.recordYear || undefined, group: values.recordGroup || undefined, data: values.recordData, sort: values.recordSort, page: values.page, size: 20 };
  const query = useQuery({ queryKey: ['health', 'records', params], enabled: !values.employee, queryFn: ({ signal }) => healthRecordApi.people(params, signal) });
  const result = normalizePage(query.data);
  useHrPageBounds(result, Boolean(values.employee) || query.isPending || query.isFetching, values.page, setPage);
  if (values.employee) return <HealthRecordDetail key={values.employee} employeeId={values.employee} onBack={() => setValues({ employee: '' }, { resetPage: false })} />;
  return <section className="space-y-4">
    <p className="text-sm text-slate-600">Chỉ hiển thị kết quả đã nhập nội bộ; dòng staging chưa xác nhận không xuất hiện ở đây. Nhóm nhân sự là thông tin hiện tại, chưa phải nhóm tại lần khám.</p>
    <div className="flex flex-wrap gap-3 rounded-xl border border-slate-200 bg-white p-4">
      <input className={healthInput} aria-label="Tìm hồ sơ sức khỏe" placeholder="Mã hoặc tên nhân viên" maxLength={100} value={search} onChange={(e) => setSearch(e.target.value)} />
      <input className={`${healthInput} w-36`} aria-label="Lọc năm có kết quả" placeholder="Năm có kết quả" type="number" min={1900} max={2200} value={values.recordYear} onChange={(e) => setValues({ recordYear: e.target.value })} />
      <select className={healthInput} aria-label="Nhóm nhân sự hiện tại" value={values.recordGroup} onChange={(e) => setValues({ recordGroup: e.target.value })}><option value="">Tất cả nhóm hiện tại</option>{Object.entries(healthGroups).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
      <select className={healthInput} aria-label="Tình trạng dữ liệu sức khỏe" value={values.recordData} onChange={(e) => setValues({ recordData: e.target.value })}><option value="WITH_DATA">Có kết quả đã nhập</option><option value="WITHOUT_DATA">Chưa có kết quả trong phạm vi</option><option value="ALL">Tất cả hồ sơ trong phạm vi</option></select>
      <select className={healthInput} aria-label="Sắp xếp hồ sơ sức khỏe" value={values.recordSort} onChange={(e) => setValues({ recordSort: e.target.value })}><option value="codeAsc">Mã tăng dần</option><option value="codeDesc">Mã giảm dần</option><option value="nameAsc">Tên A–Z</option><option value="nameDesc">Tên Z–A</option></select>
      <Button variant="secondary" disabled={query.isFetching} onClick={() => query.refetch()}>Tải lại danh sách</Button>
    </div>
    <HealthReportLauncher key={JSON.stringify(params)} filters={params} />
    {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} /> : !result.content.length ? <HrEmpty title="Không có hồ sơ phù hợp" /> : <>
      <div className="max-w-full overflow-x-auto rounded-xl border border-slate-200 bg-white" aria-busy={query.isFetching}><table className="w-full min-w-[720px] text-left text-sm"><thead className="bg-slate-50"><tr><th className="p-3">Nhân viên</th><th className="p-3">Ngày sinh</th><th className="p-3">Nhóm hiện tại</th><th className="p-3">Lượt trong phạm vi</th><th className="p-3">Năm gần nhất</th><th className="p-3">Thao tác</th></tr></thead><tbody>{result.content.map((person) => <tr key={person.employeeId} className="border-t border-slate-100"><td className="p-3"><strong>{person.code}</strong><span className="block">{person.name}</span></td><td className="p-3">{person.birthDate || 'Chưa có'}</td><td className="p-3">{healthGroups[person.currentGroup] || 'Chưa xác định'}</td><td className="p-3">{person.visitCount}</td><td className="p-3">{person.lastYear || 'Chưa có dữ liệu'}</td><td className="p-3"><Button variant="secondary" onClick={() => setValues({ employee: person.employeeId, publicationVisit: '', visit: '', selectedVisits: '', compare: '', timelinePage: 0, resultPage: 0, comparePage: 0, resultSearch: '', compareSearch: '' }, { resetPage: false })}>Mở sổ sức khỏe</Button></td></tr>)}</tbody></table></div>
      <HrPagination page={result.number} totalPages={result.totalPages} totalElements={result.totalElements} loading={query.isFetching} onPageChange={setPage} />
    </>}
  </section>;
}
