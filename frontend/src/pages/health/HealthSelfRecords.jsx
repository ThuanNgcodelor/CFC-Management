import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { HrEmpty, HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';
import HealthSelfResults from './HealthSelfResults';

const YEAR = new Date().getFullYear();

/** This component receives an already verified, owner-bound client; it never accepts an employee ID. */
export default function HealthSelfRecords({ api, onExit }) {
  const [range, setRange] = useState({ from: YEAR - 3, to: YEAR });
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState([]);
  const [view, setView] = useState(null);
  const [years, setYears] = useState({ from: YEAR - 3, to: YEAR });
  const profile = useQuery({ queryKey: ['health-self', 'profile', range, page], queryFn: ({ signal }) => api.profile({ ...range, page, size: 10 }, signal) });
  // An authorization error must clear both query data and every nested view on shared devices.
  useEffect(() => { if (profile.error?.status === 401) onExit(); }, [profile.error, onExit]);
  const rows = profile.data?.releases;
  const toggle = (id) => setSelected((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  return <main className="mx-auto min-h-screen max-w-5xl space-y-5 bg-slate-50 px-4 py-6 text-slate-900 sm:px-6">
    <header className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-emerald-100 bg-white p-5">
      <div><p className="text-xs font-semibold tracking-widest text-emerald-700">CFC · SỔ SỨC KHỎE</p><h1 className="mt-1 text-2xl font-semibold">Sức khỏe của tôi</h1></div>
      <Button variant="secondary" onClick={onExit}>Thoát tra cứu</Button>
    </header>
    <p className="text-sm text-slate-600">Chỉ hiển thị kết quả của bạn đã được công bố. Thiếu dữ liệu không đồng nghĩa chưa khám hoặc sức khỏe bình thường. Hãy thoát khi dùng xong trên máy chung.</p>
    <form className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4" onSubmit={(event) => { event.preventDefault(); setRange(years); setPage(0); setSelected([]); setView(null); }}>
      <label className="text-sm">Từ năm<input aria-label="Tra cứu từ năm" className={`${healthInput} mt-1 block w-28`} type="number" min={1900} max={2200} required value={years.from} onChange={(event) => setYears({ ...years, from: Number(event.target.value) })} /></label>
      <label className="text-sm">Đến năm<input aria-label="Tra cứu đến năm" className={`${healthInput} mt-1 block w-28`} type="number" min={years.from} max={Math.min(2200, years.from + 9)} required value={years.to} onChange={(event) => setYears({ ...years, to: Number(event.target.value) })} /></label>
      <Button type="submit">Xem lịch sử</Button><Button variant="secondary" onClick={() => { setView(null); profile.refetch(); }}>Tải lại</Button>
    </form>
    {profile.isPending ? <HrLoading /> : profile.isError ? <HrError message={healthError(profile.error)} onRetry={profile.refetch} /> : <>
      {profile.data.name && <section className="rounded-xl border border-slate-200 bg-white p-4"><h2 className="font-semibold">{profile.data.name}</h2><p className="mt-1 text-sm text-slate-600">Mã nhân viên: {profile.data.code} · Ngày sinh: {profile.data.birthDate || 'Chưa có dữ liệu'}</p></section>}
      {!rows.content.length ? <HrEmpty title="Chưa có kết quả được công bố trong khoảng năm này" description="Bạn có thể đổi khoảng năm hoặc liên hệ phòng Tổ chức – Hành chính – Nhân sự." /> : <>
        <div className="flex flex-wrap items-center gap-3"><Button disabled={selected.length < 2} onClick={() => setView({ ids: [...selected] })}>So sánh {selected.length} lượt đã chọn</Button><span className="text-sm text-slate-500">Chọn 2–6 lượt, có thể chọn qua nhiều trang.</span></div>
        <section className="space-y-3" aria-label="Lịch sử khám đã công bố">{rows.content.map((release) => <article key={release.id} className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex items-start gap-3"><input className="mt-1" type="checkbox" aria-label={`Chọn lượt ${release.year} ${release.campaign}`} checked={selected.includes(release.id)} disabled={!selected.includes(release.id) && selected.length >= 6} onChange={() => toggle(release.id)} />
            <div className="min-w-0 flex-1"><h2 className="break-words font-semibold">{release.year} · {release.campaign}</h2><p className="mt-1 break-words text-sm text-slate-600">{release.provider} · Ngày khám: {release.examinedOn || 'Chưa xác nhận ngày'}</p><p className="mt-2 text-sm">Phân loại theo cơ sở khám: {release.classification || 'Chưa có dữ liệu'}</p>{release.incomplete && <p className="mt-1 text-xs text-amber-800">Kết quả có thể chưa đầy đủ; liên hệ nhân sự nếu cần bổ sung.</p>}<p className="mt-1 text-xs text-slate-500">Phiên bản {release.revision} · Công bố {release.publishedAt}</p></div></div>
          <Button className="mt-3" variant="secondary" onClick={() => setView({ id: release.id })}>Xem kết quả {release.year}</Button>
        </article>)}</section>
        <HrPagination page={rows.page} totalPages={rows.totalPages} totalElements={rows.totalElements} loading={profile.isFetching} onPageChange={(next) => { setView(null); setPage(next); }} />
      </>}
    </>}
    {view && !profile.isError && <HealthSelfResults key={view.id || view.ids.join(',')} api={api} view={view} onClose={() => setView(null)} onExit={onExit} />}
  </main>;
}
