import { useEffect, useMemo, useState } from 'react';
import { useHrListState, useHrNavigation } from '../../hooks/useHrListState';
import { hrReportApi } from '../../api/hrReportApi';
import { apiErrorMessage } from '../../utils/hr';
import { HrError, HrLoading, HrPageHeader, HrPageShell } from '../../components/hr/HrUi';

const CURRENT_YEAR = new Date().getFullYear();
const DEFAULTS = { year: CURRENT_YEAR, view: 'headcount', month: '', page: 0, group: '', contractClass: '', department: '', reason: '', detail: '', eventType: '', flow: '', uniqueOnly: '' };
const ALLOWED = { view: ['headcount', 'leavers'] };
const GROUPS = [['', 'Tất cả nhóm'], ['OFFICE', 'Văn phòng'], ['GENERAL_LABOR', 'Lao động phổ thông'], ['LEGACY_UNKNOWN', 'Chưa phân loại']];
const CONTRACTS = [['', 'Tất cả tình trạng HĐLĐ'], ['FORMAL', 'Có HĐLĐ hiệu lực'], ['UNKNOWN', 'Chưa ghi nhận HĐLĐ hiệu lực']];
const GROUP_LABELS = { OFFICE: 'Văn phòng', GENERAL_LABOR: 'Lao động phổ thông', LEGACY_UNKNOWN: 'Chưa phân loại' };
const MOVEMENT_LABELS = { INCREASE: 'Tăng', REHIRE: 'Tái tuyển', DECREASE: 'Giảm', TRANSFER: 'Điều chuyển' };

function MetricButton({ children, onClick, disabled }) {
  return <button disabled={disabled} className="font-semibold text-emerald-700 underline disabled:no-underline" onClick={onClick}>{children}</button>;
}

export default function HrReports() {
  const { values, setValues, setPage } = useHrListState(DEFAULTS, ALLOWED);
  const { open } = useHrNavigation('/manager/hr/reports');
  const [headcount, setHeadcount] = useState(null);
  const [leavers, setLeavers] = useState(null);
  const [movementSources, setMovementSources] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [exporting, setExporting] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [draftDepartment, setDraftDepartment] = useState(values.department);
  const [draftReason, setDraftReason] = useState(values.reason);
  const query = useMemo(() => ({
    year: values.year, ...(values.month ? { month: values.month } : {}),
    ...(values.group ? { group: values.group } : {}), ...(values.contractClass ? { contractClass: values.contractClass } : {}),
    ...(values.department ? { department: values.department } : {}), ...(values.reason ? { reason: values.reason } : {}),
  }), [values.year, values.month, values.group, values.contractClass, values.department, values.reason]);
  const maxEnding = Math.max(1, ...(headcount?.months || []).filter((row) => row.available).map((row) => row.ending));

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError('');
    const requests = [hrReportApi.headcount({ year: values.year, ...(values.group ? { group: values.group } : {}), ...(values.contractClass ? { contractClass: values.contractClass } : {}), ...(values.department ? { department: values.department } : {}) }, { signal: controller.signal })];
    if (values.view === 'leavers') requests.push(hrReportApi.leavers({ ...query, page: values.page, size: 20 }, { signal: controller.signal }));
    if (values.view === 'headcount' && values.detail === 'events') requests.push(hrReportApi.movementSources({
      ...query, month: values.month, type: values.eventType, flow: values.flow || undefined, uniqueOnly: values.uniqueOnly || undefined, page: values.page, size: 20,
    }, { signal: controller.signal }));
    Promise.all(requests).then(([headcountData, listData]) => {
      if (controller.signal.aborted) return;
      setHeadcount(headcountData);
      if (values.view === 'leavers') setLeavers(listData);
      if (values.view === 'headcount' && values.detail === 'events') setMovementSources(listData);
    }).catch((requestError) => {
      if (!controller.signal.aborted) setError(apiErrorMessage(requestError, 'Không tải được báo cáo nhân sự.'));
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [values.year, values.view, values.page, values.group, values.contractClass, values.department, values.detail, values.month, values.eventType, values.flow, values.uniqueOnly, query, reloadKey]);

  useEffect(() => { setDraftDepartment(values.department); setDraftReason(values.reason); }, [values.department, values.reason]);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (draftDepartment !== values.department || draftReason !== values.reason) {
        setValues({ department: draftDepartment, reason: draftReason }, { replace: true });
      }
    }, 300);
    return () => window.clearTimeout(timer);
  }, [draftDepartment, draftReason, values.department, values.reason, setValues]);

  const chooseView = (view) => setValues({ view, detail: '', eventType: '', flow: '', uniqueOnly: '', ...(view === 'headcount' ? { month: '', reason: '' } : {}) });
  const openMovementSource = (month, eventType, flow = '', uniqueOnly = false) => setValues({ view: 'headcount', detail: 'events', month: String(month), eventType, flow, uniqueOnly: uniqueOnly ? 'true' : '', page: 0 });
  const exportFile = async () => {
    setExporting(true);
    try { await hrReportApi.exportLeavers(query); }
    catch (requestError) { setError(apiErrorMessage(requestError, 'Không thể xuất Excel.')); }
    finally { setExporting(false); }
  };

  return <HrPageShell>
    <HrPageHeader eyebrow="QUẢN LÝ NHÂN SỰ" title="Báo cáo nhân sự" description="Theo dõi biến động quân số và tra cứu các lượt nghỉ việc theo kỳ." />
    <div className="mb-5 flex flex-wrap gap-2 rounded-xl border border-gray-200 bg-white p-2 shadow-sm">
      <button onClick={() => chooseView('headcount')} className={`rounded-lg px-4 py-2 text-sm font-medium ${values.view === 'headcount' ? 'bg-emerald-600 text-white' : 'text-gray-700 hover:bg-gray-100'}`}>Biến động quân số</button>
      <button onClick={() => chooseView('leavers')} className={`rounded-lg px-4 py-2 text-sm font-medium ${values.view === 'leavers' ? 'bg-emerald-600 text-white' : 'text-gray-700 hover:bg-gray-100'}`}>Danh sách nghỉ việc</button>
    </div>
    <div className="mb-5 grid gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm sm:grid-cols-2 xl:grid-cols-5">
      <label className="text-sm text-gray-600">Năm<select className="mt-1 w-full rounded-lg border-gray-300" value={values.year} onChange={(e) => setValues({ year: Number(e.target.value) })}>{Array.from({ length: 8 }, (_, i) => CURRENT_YEAR - i).map((year) => <option key={year}>{year}</option>)}</select></label>
      <label className="text-sm text-gray-600">Nhóm nhân sự<select className="mt-1 w-full rounded-lg border-gray-300" value={values.group} onChange={(e) => setValues({ group: e.target.value })}>{GROUPS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="text-sm text-gray-600">Tình trạng hợp đồng<select className="mt-1 w-full rounded-lg border-gray-300" value={values.contractClass} onChange={(e) => setValues({ contractClass: e.target.value })}>{CONTRACTS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      {values.view === 'leavers' && <>
        <label className="text-sm text-gray-600">Tháng<select className="mt-1 w-full rounded-lg border-gray-300" value={values.month} onChange={(e) => setValues({ month: e.target.value })}><option value="">Cả năm</option>{Array.from({ length: 12 }, (_, i) => i + 1).map((m) => <option key={m} value={m}>Tháng {m}</option>)}</select></label>
        <label className="text-sm text-gray-600">Lý do nghỉ<input className="mt-1 w-full rounded-lg border-gray-300" value={draftReason} onChange={(e) => setDraftReason(e.target.value)} placeholder="Tìm theo lý do" /></label>
      </>}
      <label className="text-sm text-gray-600">Phòng / xí nghiệp<input className="mt-1 w-full rounded-lg border-gray-300" value={draftDepartment} onChange={(e) => setDraftDepartment(e.target.value)} placeholder="Tìm theo đơn vị" /></label>
    </div>
    {error && <HrError message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
    {loading && !headcount ? <HrLoading /> : values.view === 'headcount' && values.detail === 'events' ? <>
      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 p-4"><div><button className="mb-2 text-sm font-medium text-emerald-700 hover:underline" onClick={() => setValues({ detail: '', month: '', eventType: '', flow: '', uniqueOnly: '' })}>← Quay lại báo cáo quân số</button><h2 className="text-lg font-semibold text-gray-900">Nguồn biến động · tháng {values.month}/{values.year}</h2><p className="text-sm text-gray-500">{movementSources?.totalElements ?? 0} {values.uniqueOnly === 'true' ? 'người' : 'lượt'} phù hợp bộ lọc</p></div></div>
        {loading ? <HrLoading /> : <div className="overflow-x-auto"><table className="w-full min-w-[850px] text-left text-sm"><thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr><th className="px-4 py-3">Nhân sự</th><th className="px-4 py-3">Ngày hiệu lực</th><th className="px-4 py-3">Loại</th><th className="px-4 py-3">Từ đơn vị</th><th className="px-4 py-3">Đến đơn vị</th><th className="px-4 py-3">Lý do</th><th className="px-4 py-3">Số quyết định</th></tr></thead><tbody className="divide-y divide-gray-100">{movementSources?.content?.map((row) => <tr key={row.movementId}><td className="px-4 py-3"><button className="text-left font-semibold text-emerald-700 hover:underline" onClick={() => open(`/manager/hr/employees/${row.employeeId}`)}>{row.employeeCode} · {row.fullName}</button></td><td className="px-4 py-3">{row.effectiveDate}</td><td className="px-4 py-3">{MOVEMENT_LABELS[row.movementType] || row.movementType}</td><td className="px-4 py-3">{row.fromDepartment || '—'}</td><td className="px-4 py-3">{row.toDepartment || '—'}</td><td className="px-4 py-3">{row.reason || '—'}</td><td className="px-4 py-3">{row.decisionNumber || '—'}</td></tr>)}</tbody></table>{!movementSources?.content?.length && <p className="p-8 text-center text-sm text-gray-500">Không có biến động nguồn phù hợp.</p>}</div>}
        <div className="flex items-center justify-between border-t border-gray-100 px-4 py-3 text-sm text-gray-500"><span>Trang {Number(movementSources?.page ?? 0) + 1}/{Math.max(1, movementSources?.totalPages ?? 0)}</span><div className="flex gap-2"><button className="rounded border px-3 py-1 disabled:opacity-40" disabled={!movementSources || movementSources.page <= 0} onClick={() => setPage(Math.max(0, values.page - 1))}>Trước</button><button className="rounded border px-3 py-1 disabled:opacity-40" disabled={!movementSources || movementSources.page + 1 >= movementSources.totalPages} onClick={() => setPage(values.page + 1)}>Sau</button></div></div>
      </section>
    </> : values.view === 'headcount' ? <>
      {headcount?.baselineFrom && <p className="mb-3 text-sm text-gray-500">Dữ liệu báo cáo từ baseline {headcount.baselineFrom.slice(0, 7)}. {headcount.workforceGroupBasis}</p>}
      <section className="mb-5 rounded-xl border border-gray-200 bg-white p-4 shadow-sm" aria-label="Biểu đồ quân số cuối kỳ theo tháng">
        <h2 className="mb-4 text-sm font-semibold text-gray-900">Quân số cuối kỳ theo tháng</h2>
        <div className="grid grid-cols-2 gap-x-5 gap-y-3 sm:grid-cols-3 xl:grid-cols-6">{headcount?.months?.filter((row) => row.available).map((row) => <div key={row.month} className="min-w-0"><div className="mb-1 flex justify-between text-xs text-gray-500"><span>T{row.month}</span><span>{row.ending}</span></div><div className="h-2 overflow-hidden rounded-full bg-gray-100"><div className="h-full rounded-full bg-emerald-600" style={{ width: `${Math.max(2, (row.ending / maxEnding) * 100)}%` }} /></div></div>)}</div>
      </section>
      <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
        <table className="w-full min-w-[900px] text-left text-sm"><thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr><th className="px-4 py-3">Tháng</th><th className="px-4 py-3">Đầu kỳ</th><th className="px-4 py-3">Tăng</th><th className="px-4 py-3">Chuyển đến</th><th className="px-4 py-3">Chuyển đi</th><th className="px-4 py-3">Lượt nghỉ</th><th className="px-4 py-3">Người nghỉ</th><th className="px-4 py-3">Cuối kỳ</th><th className="px-4 py-3">Ghi chú</th></tr></thead>
          <tbody className="divide-y divide-gray-100">{headcount?.months?.map((row) => <tr key={row.month} className={row.available ? '' : 'bg-gray-50 text-gray-400'}><td className="px-4 py-3 font-medium">Tháng {row.month}</td><td className="px-4 py-3">{row.opening ?? '—'}</td><td className="px-4 py-3"><MetricButton disabled={!row.available} onClick={() => openMovementSource(row.month, 'INCREASE')}>{row.available ? row.increases : '—'}</MetricButton></td><td className="px-4 py-3"><MetricButton disabled={!row.available} onClick={() => openMovementSource(row.month, 'TRANSFER', 'IN')}>{row.available ? row.movedIn : '—'}</MetricButton></td><td className="px-4 py-3"><MetricButton disabled={!row.available} onClick={() => openMovementSource(row.month, 'TRANSFER', 'OUT')}>{row.available ? row.movedOut : '—'}</MetricButton></td><td className="px-4 py-3"><MetricButton disabled={!row.available} onClick={() => openMovementSource(row.month, 'DECREASE')}>{row.available ? row.decreases : '—'}</MetricButton></td><td className="px-4 py-3"><MetricButton disabled={!row.available} onClick={() => openMovementSource(row.month, 'DECREASE', '', true)}>{row.available ? row.uniqueLeavers : '—'}</MetricButton></td><td className="px-4 py-3 font-semibold">{row.available ? row.ending : '—'}</td><td className="max-w-sm px-4 py-3 text-xs text-gray-500">{row.note}</td></tr>)}</tbody>
        </table>
      </div>
      <p className="mt-3 text-xs text-gray-500">Nhóm lấy theo hồ sơ hiện tại; hệ thống chưa lưu lịch sử đổi nhóm. “Người nghỉ” là số người riêng trong kỳ, “lượt nghỉ” tính từng biến động đã xác nhận.</p>
    </> : <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <div><p className="text-sm text-gray-500">Số lượt nghỉ trong bộ lọc</p><p className="text-2xl font-semibold text-gray-900">{leavers?.eventCount ?? 0} <span className="text-sm font-normal text-gray-500">lượt · {leavers?.uniqueEmployeeCount ?? 0} người</span></p></div>
        <button disabled={exporting || loading} onClick={exportFile} className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">{exporting ? 'Đang xuất…' : 'Xuất Excel theo bộ lọc'}</button>
      </div>
      {loading ? <HrLoading /> : <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm"><table className="w-full min-w-[900px] text-left text-sm"><thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr><th className="px-4 py-3">Nhân sự</th><th className="px-4 py-3">Ngày nghỉ hiệu lực</th><th className="px-4 py-3">Phòng / xí nghiệp</th><th className="px-4 py-3">Nhóm hiện tại</th><th className="px-4 py-3">HĐLĐ tại ngày nghỉ</th><th className="px-4 py-3">Lý do</th><th className="px-4 py-3">Số quyết định</th></tr></thead><tbody className="divide-y divide-gray-100">{leavers?.content?.map((row) => <tr key={row.movementId}><td className="px-4 py-3"><button className="text-left font-semibold text-emerald-700 hover:underline" onClick={() => open(`/manager/hr/employees/${row.employeeId}`)}>{row.employeeCode} · {row.fullName}</button></td><td className="px-4 py-3">{row.effectiveDate}</td><td className="px-4 py-3">{row.departmentName || '—'}</td><td className="px-4 py-3">{GROUP_LABELS[row.currentWorkforceGroup] || row.currentWorkforceGroup}</td><td className="px-4 py-3">{row.contractClassification === 'FORMAL' ? 'Có HĐLĐ hiệu lực' : 'Chưa ghi nhận HĐLĐ hiệu lực'}</td><td className="px-4 py-3">{row.reason || '—'}</td><td className="px-4 py-3">{row.decisionNumber || '—'}</td></tr>)}</tbody></table>
        {!leavers?.content?.length && <p className="p-8 text-center text-sm text-gray-500">Không có lượt nghỉ phù hợp bộ lọc.</p>}
        <div className="flex items-center justify-between border-t border-gray-100 px-4 py-3 text-sm text-gray-500"><span>{leavers?.totalElements ?? 0} lượt · trang {Number(leavers?.page ?? 0) + 1}/{Math.max(1, leavers?.totalPages ?? 0)}</span><div className="flex gap-2"><button className="rounded border px-3 py-1 disabled:opacity-40" disabled={!leavers || leavers.page <= 0} onClick={() => setPage(Math.max(0, values.page - 1))}>Trước</button><button className="rounded border px-3 py-1 disabled:opacity-40" disabled={!leavers || leavers.page + 1 >= leavers.totalPages} onClick={() => setPage(values.page + 1)}>Sau</button></div></div>
      </div>}
      <p className="mt-3 text-xs text-gray-500">Nhóm dùng thông tin hiện tại; lịch sử đổi nhóm chưa được lưu. “Chưa ghi nhận HĐLĐ hiệu lực” có thể gồm dữ liệu lịch sử chưa đầy đủ, không khẳng định người đó chưa từng ký hợp đồng.</p>
    </>}
  </HrPageShell>;
}
