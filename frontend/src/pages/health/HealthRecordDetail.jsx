import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { healthRecordApi } from '../../api/healthRecordApi';
import { useHrListState } from '../../hooks/useHrListState';
import { HrEmpty, HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';
import HealthVisitResults from './HealthVisitResults';
import HealthComparison from './HealthComparison';
import HealthPublicationHistory from './HealthPublicationHistory';
import HealthPersonalAccessPanel from './HealthPersonalAccessPanel';
import { HealthReportLauncher } from './HealthReportPanel';

const CURRENT_YEAR = new Date().getFullYear();
const DEFAULTS = { fromYear: String(CURRENT_YEAR - 3), toYear: String(CURRENT_YEAR), timelinePage: 0, visit: '', selectedVisits: '', compare: '', publicationVisit: '' };
export default function HealthRecordDetail({ employeeId, onBack }) {
  const { values, setValues } = useHrListState(DEFAULTS);
  const update = (patch) => setValues(patch, { resetPage: false });
  const params = { fromYear: values.fromYear, toYear: values.toYear, page: values.timelinePage, size: 20 };
  const query = useQuery({ queryKey: ['health', 'timeline', employeeId, params], queryFn: ({ signal }) => healthRecordApi.timeline(employeeId, params, signal) });
  const selected = values.selectedVisits.split(',').filter(Boolean);
  const toggle = (id) => update({ selectedVisits: (selected.includes(id) ? selected.filter((value) => value !== id) : [...selected, id]).join(','), compare: '' });
  const visits = query.data?.visits;
  return <section className="space-y-4">
    <Button variant="secondary" onClick={onBack}>Quay lại danh sách hồ sơ</Button>
    <Button variant="secondary" disabled={query.isFetching} onClick={() => query.refetch()}>Tải lại hồ sơ</Button>
    {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} /> : <>
      <div className="rounded-xl border border-slate-200 bg-white p-5"><h2 className="text-xl font-semibold">{query.data.person.code} · {query.data.person.name}</h2><p className="mt-2 text-sm text-slate-600">Ngày sinh: {query.data.person.birthDate || 'Chưa có dữ liệu'} · {query.data.person.visitCount} lượt đã nhập nội bộ. Kết quả đang quản trị rà soát, chưa phải bản công bố cho cá nhân.</p></div>
      <HealthPersonalAccessPanel key={employeeId} employeeId={employeeId} />
      <HealthRecordRange key={`${values.fromYear}-${values.toYear}`} fromYear={values.fromYear} toYear={values.toYear} onApply={(fromYear, toYear) => update({ fromYear, toYear, timelinePage: 0, publicationVisit: '', visit: '', selectedVisits: '', compare: '' })} />
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-5">{query.data.years.map((year) => <div key={year.year} className="rounded-xl border border-slate-200 bg-white p-3"><strong>{year.year}</strong><span className="mt-1 block text-sm text-slate-600">{year.visits ? `${year.visits} lượt có dữ liệu` : 'Chưa có dữ liệu đã nhập'}</span></div>)}</div>
      <HealthReportLauncher employeeId={employeeId} fromYear={values.fromYear} toYear={values.toYear} />
      <HealthReportLauncher employeeId={employeeId} fromYear={values.fromYear} toYear={values.toYear} visitIds={selected} selectionOnly />
      <p className="text-sm text-slate-500">Không có dữ liệu không đồng nghĩa chưa khám hoặc sức khỏe bình thường. Chọn 2–6 lượt để so sánh; giữ riêng nhiều lượt trong cùng năm.</p>
      <Button disabled={selected.length < 2 || selected.length > 6} onClick={() => update({ compare: '1', visit: '', publicationVisit: '', comparePage: 0 })}>So sánh {selected.length} lượt đã chọn</Button>
      {!visits.content.length ? <HrEmpty title="Chưa có lượt khám đã nhập trong khoảng này" /> : <div className="space-y-3">{visits.content.map((visit) => <article key={visit.id} className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
        <input type="checkbox" aria-label={`Chọn lượt ${visit.sid}`} checked={selected.includes(visit.id)} disabled={!selected.includes(visit.id) && selected.length >= 6} onChange={() => toggle(visit.id)} />
        <div className="min-w-0 flex-1"><h3 className="font-semibold">{visit.year} · {visit.campaign}</h3><p className="break-words text-sm text-slate-600">{visit.provider} · Ngày khám: {visit.examinedOn || 'Chưa xác nhận ngày'}</p><p className="break-words text-sm">Phân loại theo nguồn: {visit.classification || 'Chưa có dữ liệu'}</p><p className="break-words text-xs text-slate-500">SID {visit.sid || 'Chưa có'} · {visit.resultCount} kết quả · Nguồn {visit.namespace}</p></div>
        <Button variant="secondary" onClick={() => update({ publicationVisit: visit.id, visit: '', compare: '' })}>Lịch sử công bố</Button>
        <Button variant="secondary" onClick={() => update({ visit: visit.id, compare: '', publicationVisit: '', resultPage: 0, resultSearch: '' })}>Xem kết quả lần khám</Button>
      </article>)}</div>}
      <HrPagination page={visits.page} totalPages={visits.totalPages} totalElements={visits.totalElements} loading={query.isFetching} onPageChange={(timelinePage) => update({ timelinePage })} />
    </>}
    {values.publicationVisit && <HealthPublicationHistory key={values.publicationVisit} employeeId={employeeId} visitId={values.publicationVisit} onClose={() => update({ publicationVisit: '' })} />}
    {values.visit && values.compare !== '1' && <HealthVisitResults key={`${employeeId}-${values.visit}`} employeeId={employeeId} visitId={values.visit} onClose={() => update({ visit: '' })} />}
    {values.compare === '1' && selected.length >= 2 && <HealthComparison key={`${employeeId}-${values.selectedVisits}`} employeeId={employeeId} visitIds={selected} onClose={() => update({ compare: '' })} />}
  </section>;
}

function HealthRecordRange({ fromYear, toYear, onApply }) {
  const [from, setFrom] = useState(fromYear);
  const [to, setTo] = useState(toYear);
  return <form className="flex flex-wrap items-end gap-3" onSubmit={(event) => { event.preventDefault(); onApply(from, to); }}>
    <label className="text-sm">Từ năm<input className={`${healthInput} mt-1 block w-32`} aria-label="Lịch sử từ năm" type="number" min={1900} max={2200} required value={from} onChange={(e) => setFrom(e.target.value)} /></label>
    <label className="text-sm">Đến năm<input className={`${healthInput} mt-1 block w-32`} aria-label="Lịch sử đến năm" type="number" min={1900} max={2200} required value={to} onChange={(e) => setTo(e.target.value)} /></label>
    <Button type="submit">Xem khoảng năm</Button>
  </form>;
}
