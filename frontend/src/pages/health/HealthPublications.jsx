import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { healthPublicationApi } from '../../api/healthPublicationApi';
import { normalizePage } from '../../api/hrApiUtils';
import { useHrListState, useHrPageBounds } from '../../hooks/useHrListState';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { HrEmpty, HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import HealthCatalogPicker from './HealthCatalogPicker';
import HealthPublicationPreview from './HealthPublicationPreview';
import HealthPublicationHistory from './HealthPublicationHistory';
import { healthError, healthInput } from './healthImportUi';

const DEFAULTS = { publicationSearch: '', publicationGroup: '', publicationPage: 0 };
const publicationState = { UNPUBLISHED: 'Chưa công bố', PUBLISHED: 'Đang công bố', CHANGED: 'Biên tập đã thay đổi', REVOKED: 'Đã thu hồi' };
export default function HealthPublications() {
  const [campaign, setCampaign] = useState(null); const [picker, setPicker] = useState(false);
  const [selection, setSelection] = useState([]); const [preview, setPreview] = useState(null); const [history, setHistory] = useState(null);
  const { values, setValues } = useHrListState(DEFAULTS); const client = useQueryClient();
  const update = (patch) => { setSelection([]); setValues(patch, { resetPage: false }); };
  const [search, setSearch] = useDebouncedHrSearchValue(values.publicationSearch, (publicationSearch) => update({ publicationSearch, publicationPage: 0 }));
  const params = { campaignId: campaign?.id, keyword: values.publicationSearch, group: values.publicationGroup || undefined, page: values.publicationPage, size: 20 };
  const query = useQuery({ queryKey: ['health', 'publication-candidates', params], enabled: Boolean(campaign), queryFn: ({ signal }) => healthPublicationApi.candidates(params, signal) });
  useHrPageBounds(normalizePage(query.data), !campaign || query.isPending || query.isFetching, values.publicationPage, (publicationPage) => update({ publicationPage }));
  const scope = { campaignId: campaign?.id, keyword: values.publicationSearch || null, group: values.publicationGroup || null };
  const toggle = (id) => setSelection((old) => old.includes(id) ? old.filter((v) => v !== id) : [...old, id]);
  const changed = async () => { await client.invalidateQueries({ queryKey: ['health'] }); };
  return <section className="space-y-4">
    <p className="text-sm text-slate-600">Bản biên tập và phiên bản công bố tách riêng. Chỉ kết quả đã nhập được đưa vào hàng đợi; dòng staging chưa ghép vẫn cần rà soát ở Nhập dữ liệu. Chưa mở cổng cá nhân ở Phase 6.</p>
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
      <p className="flex-1 text-sm">{campaign ? <><strong>{campaign.code} · {campaign.name}</strong><span className="block">Năm {campaign.reportYear} · {campaign.provider}</span></> : 'Chọn đợt khám để rà soát và công bố.'}</p>
      <Button variant="secondary" onClick={() => setPicker(true)}>Chọn đợt công bố</Button>
    </div>
    {campaign && <>
      <div className="flex flex-wrap gap-3"><input className={healthInput} aria-label="Tìm người trong hàng đợi công bố" maxLength={100} value={search} placeholder="Mã hoặc tên nhân viên" onChange={(e) => setSearch(e.target.value)} />
        <select className={healthInput} aria-label="Nhóm hiện tại khi công bố" value={values.publicationGroup} onChange={(e) => update({ publicationGroup: e.target.value, publicationPage: 0 })}><option value="">Tất cả nhóm hiện tại</option><option value="OFFICE">Văn phòng</option><option value="GENERAL_LABOR">Lao động phổ thông</option><option value="LEGACY_UNKNOWN">Chưa xác định</option></select>
        <Button variant="secondary" disabled={query.isFetching} onClick={() => query.refetch()}>Tải lại hàng đợi</Button>
        <Button disabled={!selection.length} onClick={() => setPreview({ ...scope, employeeIds: selection })}>Xem trước {selection.length} người đã chọn</Button>
        <Button variant="secondary" onClick={() => setPreview({ ...scope, employeeIds: null })}>Xem trước tất cả theo bộ lọc</Button>
      </div>
      {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} /> : !query.data.content.length ? <HrEmpty title="Chưa có lượt đã nhập phù hợp" /> : <>
        <label className="text-sm"><input type="checkbox" checked={query.data.content.every((p) => selection.includes(p.employeeId))} onChange={(e) => setSelection(e.target.checked ? query.data.content.map((p) => p.employeeId) : [])} /> Chọn trang này (tối đa 20 người)</label>
        <div className="max-w-full overflow-x-auto rounded-xl border border-slate-200 bg-white"><table className="w-full min-w-[800px] text-left text-sm"><thead><tr><th className="p-3">Chọn</th><th className="p-3">Nhân viên</th><th className="p-3">Lượt / phiên bản</th><th className="p-3">Rà soát</th><th className="p-3">Thao tác</th></tr></thead><tbody>{query.data.content.map((p) => <tr key={p.employeeId} className="border-t border-slate-200"><td className="p-3"><input type="checkbox" aria-label={`Chọn công bố ${p.code}`} checked={selection.includes(p.employeeId)} onChange={() => toggle(p.employeeId)} /></td><td className="p-3"><strong>{p.code}</strong><span className="block">{p.name}</span></td><td className="p-3">{p.visits.map((v) => <div key={v.visitId}>SID {v.sid} · {publicationState[v.state]}{v.latest ? ` · v${v.latest.revision}` : ''}</div>)}</td><td className="p-3">{p.visits.map((v) => <div key={v.visitId}>{v.reviewed ? 'Đã rà soát bản hiện tại' : 'Cần rà soát'} · {v.results} kết quả{v.blockers.length ? ` · ${v.blockers.length} lỗi chặn` : ''}</div>)}</td><td className="space-y-2 p-3"><Button variant="secondary" onClick={() => setPreview({ ...scope, employeeIds: [p.employeeId] })}>Rà soát / công bố người này</Button>{p.visits.map((v) => <Button key={v.visitId} variant="secondary" onClick={() => setHistory({ employeeId: p.employeeId, visitId: v.visitId })}>Phiên bản SID {v.sid}</Button>)}</td></tr>)}</tbody></table></div>
        <HrPagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} loading={query.isFetching} onPageChange={(publicationPage) => update({ publicationPage })} />
      </>}
    </>}
    {picker && <HealthCatalogPicker type="campaigns" allowClosed onClose={() => setPicker(false)} onSelect={(value) => { setCampaign(value); setSelection([]); setPreview(null); update({ publicationPage: 0 }); }} />}
    {preview && <HealthPublicationPreview scope={preview} onClose={() => setPreview(null)} onChanged={changed} />}
    {history && <HealthPublicationHistory {...history} onClose={() => setHistory(null)} onChanged={changed} />}
  </section>;
}
