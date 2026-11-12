import { useEffect, useState } from 'react';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { healthApi } from '../../api/healthApi';
import { useHrListState, useHrPageBounds } from '../../hooks/useHrListState';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { normalizePage } from '../../api/hrApiUtils';
import { HrEmpty, HrError, HrLoading, HrPageHeader, HrPageShell, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import HealthEditor from './HealthEditor';
import HealthImports from './HealthImports';
import HealthRecords from './HealthRecords';
import HealthPublications from './HealthPublications';

const DEFAULTS = { tab: 'campaigns', keyword: '', page: 0, year: '', group: '' };
const ALLOWED = { tab: ['campaigns', 'indicators', 'imports', 'records', 'publications'], group: ['', 'DIRECT', 'INDIRECT', 'BOTH'] };
const GROUPS = { BOTH: 'Cả hai khối', DIRECT: 'Trực tiếp — Công nhân', INDIRECT: 'Gián tiếp — Văn phòng' };
const TYPES = { NUMBER: 'Số', TEXT: 'Văn bản', CODED: 'Định tính', COMPOSITE: 'Nhiều thành phần' };
const inputClass = 'rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm';

function HealthWorkspace() {
  const { values, setValues, setPage } = useHrListState(DEFAULTS, ALLOWED);
  const [search, setSearch] = useDebouncedHrSearchValue(values.keyword, (keyword) => setValues({ keyword }));
  const [editor, setEditor] = useState(null);
  const capabilities = useQuery({ queryKey: ['health', 'capabilities'], queryFn: ({ signal }) => healthApi.capabilities(signal) });
  const campaigns = values.tab === 'campaigns';
  const params = { keyword: values.keyword, page: values.page, size: 20,
    ...(campaigns ? { year: values.year || undefined, group: values.group || undefined } : {}) };
  const catalog = campaigns || values.tab === 'indicators';
  const query = useQuery({ queryKey: ['health', values.tab, params], enabled: capabilities.data?.enabled === true && catalog,
    queryFn: ({ signal }) => healthApi.list(values.tab, params, signal) });
  const result = normalizePage(query.data);
  useHrPageBounds(result, !catalog || query.isPending || query.isFetching, values.page, setPage);
  return <HrPageShell>
    <HrPageHeader title="Sổ sức khỏe" description="Quản lý đợt khám, chỉ tiêu và nhập kết quả sức khỏe nhân viên."
      actions={capabilities.data?.enabled && catalog && <Button onClick={() => setEditor({ item: null, type: values.tab })}>Thêm {campaigns ? 'đợt khám' : 'chỉ tiêu'}</Button>} />
    {capabilities.isPending ? <HrLoading /> : capabilities.isError ? <HrError message="Không thể kiểm tra quyền sử dụng sổ sức khỏe." onRetry={capabilities.refetch} />
      : !capabilities.data.enabled ? <HrEmpty title="Sổ sức khỏe chưa được bật" description="Liên hệ người quản trị để kiểm tra cấu hình môi trường." /> : <>
        <div className="mb-4 flex flex-wrap gap-2" role="tablist" aria-label="Quản lý sổ sức khỏe">
          {[['records', 'Hồ sơ sức khỏe'], ['publications', 'Công bố và phiên bản'], ['campaigns', 'Đợt khám'], ['indicators', 'Danh mục chỉ tiêu'], ['imports', 'Nhập dữ liệu']].map(([tab, label]) => <button key={tab} role="tab"
            aria-selected={values.tab === tab} className={`rounded-lg border px-3 py-2 text-sm ${values.tab === tab ? 'border-emerald-700 bg-emerald-700 text-white' : 'border-slate-300 bg-white text-slate-700'}`}
            onClick={() => { setEditor(null); setValues({ tab }); }}>{label}</button>)}
        </div>
        {values.tab === 'publications' ? <HealthPublications /> : values.tab === 'records' ? <HealthRecords /> : values.tab === 'imports' ? <HealthImports /> : <>
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
          <input className={inputClass} aria-label="Tìm mã hoặc tên" placeholder="Tìm mã hoặc tên" maxLength={100} value={search} onChange={(event) => setSearch(event.target.value)} />
          {campaigns && <>
            <input className={inputClass} aria-label="Năm báo cáo" placeholder="Năm báo cáo" type="number" min={1900} max={2200}
              value={values.year} onChange={(event) => setValues({ year: event.target.value })} />
            <select className={inputClass} aria-label="Lọc khối nhân sự" value={values.group} onChange={(event) => setValues({ group: event.target.value })}>
              <option value="">Tất cả khối</option>{Object.entries(GROUPS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </>}
          <Button variant="secondary" disabled={query.isFetching} onClick={() => query.refetch()}>Tải lại</Button>
        </div>
        {query.isPending ? <HrLoading /> : query.isError ? <HrError message={query.error?.response?.data?.message || 'Không thể tải danh sách.'} onRetry={query.refetch} />
          : result.content.length === 0 ? <HrEmpty title={campaigns ? 'Chưa có đợt khám phù hợp' : 'Chưa có chỉ tiêu phù hợp'} /> : <>
            <div className="mb-3 max-w-full overflow-x-auto rounded-xl border border-slate-200 bg-white" aria-busy={query.isFetching}>
              <table className="w-full min-w-[640px] text-left text-sm"><thead className="bg-slate-50 text-slate-600"><tr>
                <th className="p-3">Mã</th><th className="p-3">Tên</th><th className="p-3">{campaigns ? 'Năm / Cơ sở khám' : 'Nhóm / Kiểu'}</th>
                <th className="p-3">{campaigns ? 'Khối' : 'Tên cột tương đương'}</th><th className="p-3">Thao tác</th>
              </tr></thead><tbody>{result.content.map((item) => <tr key={item.id} className="border-t border-slate-100">
                <td className="p-3 font-medium">{item.code}</td><td className="p-3">{item.name}{!campaigns && item.status === 'INACTIVE' && <span className="block text-xs text-slate-500">Ngừng sử dụng</span>}</td>
                <td className="p-3">{campaigns ? `${item.reportYear} · ${item.provider}` : `${item.category} · ${TYPES[item.valueType]}`}</td>
                <td className="p-3">{campaigns ? GROUPS[item.workforceGroup] : item.aliases.join(', ') || '—'}</td>
                <td className="p-3"><Button variant="secondary" disabled={query.isFetching} onClick={() => setEditor({ item, type: values.tab })}>Sửa</Button></td>
              </tr>)}</tbody></table>
            </div>
            <HrPagination page={result.number} totalPages={result.totalPages} totalElements={result.totalElements} loading={query.isFetching} onPageChange={setPage} />
          </>}
        {editor && <HealthEditor key={`${editor.type}-${editor.item?.id || 'new'}`} type={editor.type} item={editor.item} onClose={() => setEditor(null)} />}
        </>}
      </>}
  </HrPageShell>;
}

export default function HrHealth() {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0, gcTime: 0,
    refetchOnWindowFocus: false, refetchOnReconnect: false }, mutations: { retry: false } } }));
  useEffect(() => () => client.clear(), [client]);
  return <QueryClientProvider client={client}><HealthWorkspace /></QueryClientProvider>;
}
