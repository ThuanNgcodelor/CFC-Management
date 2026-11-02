import { lazy, Suspense, useMemo, useState } from 'react';
import { Eye, FlaskConical, RefreshCw } from 'lucide-react';
import toast from 'react-hot-toast';
import { Button } from '../../components/ui/Button';
import { HrEmpty, HrError, HrLoading, HrPageHeader, HrPageShell, HrPagination, HrStatusBadge } from '../../components/hr/HrUi';
import { HrAttendanceTabs } from '../../components/hr/HrAttendanceTabs';
import { HrAttendanceEmployeeOverview } from '../../components/hr/HrAttendanceEmployeeOverview';
import { HrAttendanceShiftDecisionDrawer } from '../../components/hr/HrAttendanceShiftDecisionDrawer';
import SEOHead from '../../components/SEOHead';
import { previewAttendanceFile, rematchAttendanceFile } from '../../api/hrAttendanceSandboxApi';
import { apiErrorMessage, formatHrDate, formatHrDateTime } from '../../utils/hr';
import { parseFactoryNightReward, summarizeSandbox } from '../../utils/attendanceSandbox';

const HrNightRewardReconcileModal = lazy(() => import('../../components/hr/HrNightRewardReconcileModal').then((module) => ({ default: module.HrNightRewardReconcileModal })));
const pageSize = 50;
const number = (value) => Number(value || 0).toLocaleString('vi-VN', { maximumFractionDigits: 2 });
const money = (value) => `${Number(value || 0).toLocaleString('vi-VN')} đ`;
const clock = (value) => value ? formatHrDateTime(value) : '—';
const dayId = (day) => `${day.employeeCode}:${day.workDate}`;
const policyLabel = (value) => ({ PRODUCTION_WORKER: 'Công nhân', KCS: 'KCS', OFFICE: 'Hành chính' }[value] || value || '—');
const filters = [['NEEDS_REVIEW', 'Cần xử lý'], ['AUTO_MATCHED', 'Sẵn sàng'], ['NO_PUNCH', 'Không chấm'], ['CONFIRMED', 'Đã xác nhận'], ['ALL', 'Tất cả']];

export default function HrAttendanceSandbox() {
  const [preview, setPreview] = useState(null);
  const [sourceFile, setSourceFile] = useState(null);
  const [decisions, setDecisions] = useState([]);
  const [days, setDays] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [employeePage, setEmployeePage] = useState(0);
  const [selectedCode, setSelectedCode] = useState('');
  const [view, setView] = useState('NEEDS_REVIEW');
  const [dayPage, setDayPage] = useState(0);
  const [decisionShift, setDecisionShift] = useState(null);
  const [reconcileOpen, setReconcileOpen] = useState(false);
  const [reconcileMounted, setReconcileMounted] = useState(false);

  const summaries = useMemo(() => summarizeSandbox(days), [days]);
  const filteredEmployees = useMemo(() => summaries.filter((item) =>
    `${item.employeeCode} ${item.employeeName}`.toLocaleLowerCase('vi').includes(keyword.trim().toLocaleLowerCase('vi'))), [summaries, keyword]);
  const employeeResult = useMemo(() => ({
    content: filteredEmployees.slice(employeePage * pageSize, (employeePage + 1) * pageSize),
    page: employeePage, totalPages: Math.ceil(filteredEmployees.length / pageSize), totalElements: filteredEmployees.length,
  }), [filteredEmployees, employeePage]);
  const visibleDays = useMemo(() => days.filter((day) =>
    (!selectedCode || day.employeeCode === selectedCode) && (view === 'ALL' || day.status === view)), [days, selectedCode, view]);
  const dayResult = useMemo(() => ({
    content: visibleDays.slice(dayPage * pageSize, (dayPage + 1) * pageSize),
    page: dayPage, totalPages: Math.ceil(visibleDays.length / pageSize), totalElements: visibleDays.length,
  }), [visibleDays, dayPage]);
  const systemEmployees = useMemo(() => summaries.map((item) => ({
    employeeCode: item.employeeCode, employeeName: item.employeeName, actualNightShiftCount: item.nightShifts,
  })), [summaries]);

  const loadAttendance = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setBusy(true); setError('');
    try {
      const next = await previewAttendanceFile(file);
      setPreview(next); setDays(next.days); setSourceFile(file); setDecisions([]);
      setSelectedCode(''); setView('NEEDS_REVIEW');
      setEmployeePage(0); setDayPage(0); setKeyword('');
    } catch (requestError) { setError(apiErrorMessage(requestError, 'Không đọc được file công thử nghiệm.')); }
    finally { setBusy(false); event.target.value = ''; }
  };

  const decideDraft = async (day, decision) => {
    const draftDecision = {
      employeeCode: day.employeeCode, workDate: day.workDate, action: decision.action,
      shiftCode: decision.shiftCode || day.shiftCode,
      checkInPunchId: decision.checkInPunchId, checkOutPunchId: decision.checkOutPunchId,
      workValue: decision.workValue, nightAllowanceAmount: decision.nightAllowanceAmount,
      reason: decision.reason.trim(),
    };
    const nextDecisions = [...decisions.filter((item) =>
      `${item.employeeCode}:${item.workDate}` !== dayId(day)), draftDecision];
    const next = await rematchAttendanceFile(sourceFile, nextDecisions);
    setPreview(next); setDecisions(nextDecisions); setDays(next.days);
    return next.days.find((item) => dayId(item) === dayId(day));
  };

  const reset = async () => {
    setBusy(true);
    try { const next = await previewAttendanceFile(sourceFile); setPreview(next); setDays(next.days); setDecisions([]); setSelectedCode(''); setView('NEEDS_REVIEW'); setDayPage(0); }
    catch (requestError) { toast.error(apiErrorMessage(requestError, 'Không thể đặt lại kết quả thử.')); }
    finally { setBusy(false); }
  };
  const parseFactory = async (file) => (await parseFactoryNightReward(file)).map((row, index) => ({
    stt: index + 1, maNV: row.employeeCode, hoTen: row.employeeName, caDem: row.nightShifts,
    tich12Rate: null, tich24Rate: null,
  }));

  return <HrPageShell>
    <SEOHead title="CFC Base | Chấm công ca sản xuất · Thử nghiệm" url="https://cfcbooking.io.vn/manager/hr/attendance/sandbox" />
    <HrPageHeader title="Chấm công ca sản xuất · Thử nghiệm" description="Xem và xử lý thử file công theo giao diện chấm công hiện tại, rồi đối soát ca đêm với file xí nghiệp." />
    <HrAttendanceTabs activeTab="sandbox" />
    <div className="mb-5 flex items-center gap-2 rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900"><FlaskConical className="h-4 w-4 shrink-0" />Dữ liệu thử chỉ tồn tại trong trang này. Không tạo import, không chốt công và không ghi thưởng vào hệ thống.</div>
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <label className="block text-sm font-medium text-gray-700">File công thử nghiệm (.xlsx)
        <input type="file" accept=".xlsx" disabled={busy} onChange={loadAttendance} className="mt-2 block w-full rounded-lg border border-gray-300 p-2 text-sm" />
      </label>
      {preview && <div className="mt-4 flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-gray-600">Tháng {preview.attendanceMonth} · {preview.totalRows} ngày công · {preview.totalPunches} dấu chấm</p><div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" onClick={reset}><RefreshCw className="h-4 w-4" />Đặt lại kết quả thử</Button><Button type="button" onClick={() => { setReconcileMounted(true); setReconcileOpen(true); }}>Đối soát thưởng ca đêm</Button></div></div>}
    </section>
    {busy && <div className="mt-5"><HrLoading label="Đang tính thử ca sản xuất..." /></div>}
    {error && <div className="mt-5"><HrError message={error} /></div>}
    {!preview && !busy && <div className="mt-5"><HrEmpty title="Chưa có file công" description="Chọn file Time Attendance để xem kết quả thử, không ghi dữ liệu thật." /></div>}
    {preview && <>
      <HrAttendanceEmployeeOverview result={employeeResult} keyword={keyword} onKeywordChange={(value) => { setKeyword(value); setEmployeePage(0); }} selectedCode={selectedCode} onPageChange={setEmployeePage} loading={false} onSelect={(item) => { setSelectedCode(item.employeeCode); setView(item.reviewShifts ? 'NEEDS_REVIEW' : 'ALL'); setDayPage(0); }} />
      <section className="mt-5 space-y-4"><div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm"><h2 className="font-semibold text-gray-900">Chi tiết ngày chấm công{selectedCode ? ` · ${selectedCode}` : ''}</h2><p className="mt-1 text-xs text-gray-500">Mặc định chỉ hiện việc cần xử lý. Chọn nhân viên ở bảng trên để xem riêng từng ngày.</p><div className="mt-4 flex flex-wrap items-center gap-2">{filters.map(([key, label]) => <button key={key} type="button" onClick={() => { setView(key); setDayPage(0); }} className={`rounded-lg px-3 py-2 text-sm font-semibold ${view === key ? 'bg-emerald-600 text-white' : 'bg-gray-50 text-gray-600 hover:bg-gray-100'}`}>{label}{key !== 'ALL' ? ` (${days.filter((day) => day.status === key).length})` : ''}</button>)}<input value={selectedCode} onChange={(event) => { setSelectedCode(event.target.value.toUpperCase()); setDayPage(0); }} placeholder="Tìm mã nhân viên" className="h-10 min-w-44 flex-1 rounded-lg border border-gray-300 px-3 text-sm" />{selectedCode && <Button type="button" size="sm" variant="secondary" onClick={() => { setSelectedCode(''); setDayPage(0); }}>Xem mọi người</Button>}</div></div>
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm"><div className="overflow-x-auto"><table className="min-w-[1080px] w-full text-left text-sm"><thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr><th className="px-3 py-3">Nhân viên</th><th className="px-3 py-3">Ngày</th><th className="px-3 py-3">Nhóm / ca</th><th className="px-3 py-3">Vào</th><th className="px-3 py-3">Ra</th><th className="px-3 py-3">Công</th><th className="px-3 py-3">Phụ cấp</th><th className="px-3 py-3">Trạng thái</th><th className="px-3 py-3">Thao tác</th></tr></thead><tbody className="divide-y divide-gray-100">{dayResult.content.map((day) => <tr key={dayId(day)} className={day.status === 'NEEDS_REVIEW' ? 'bg-amber-50/60' : ''}><td className="px-3 py-3"><b>{day.employeeCode}</b><p className="text-xs text-gray-500">{day.employeeName}</p></td><td className="px-3 py-3">{formatHrDate(day.workDate)}</td><td className="px-3 py-3">{policyLabel(day.policyGroup)}<p className="text-xs text-gray-500">{day.shiftCode || '—'}</p></td><td className="px-3 py-3">{clock(day.checkInAt)}</td><td className="px-3 py-3">{clock(day.checkOutAt)}</td><td className="px-3 py-3 font-semibold">{number(day.workValue)}</td><td className="px-3 py-3">{money(day.nightAllowanceAmount)}</td><td className="px-3 py-3">{day.status === 'NO_PUNCH' ? 'Không có dấu chấm – 0 công' : <HrStatusBadge status={day.status} />}</td><td className="px-3 py-3"><Button type="button" size="sm" variant="secondary" onClick={() => setDecisionShift(day)}><Eye className="h-4 w-4" />{day.status === 'NEEDS_REVIEW' ? 'Kiểm tra' : 'Chi tiết'}</Button></td></tr>)}</tbody></table></div>{!dayResult.content.length && <div className="p-6"><HrEmpty title={view === 'NEEDS_REVIEW' ? 'Đã xử lý hết ca cần kiểm tra' : 'Không có dữ liệu phù hợp'} /></div>}</div>
        <HrPagination page={dayResult.page} totalPages={dayResult.totalPages} totalElements={dayResult.totalElements} onPageChange={setDayPage} />
      </section>
    </>}
    <HrAttendanceShiftDecisionDrawer shift={decisionShift} policies={preview?.policies || []} readOnly={false} draft onDraftDecision={decideDraft} onClose={() => setDecisionShift(null)} onSaved={async (saved) => { setDecisionShift(null); toast.success(`Đã lưu quyết định thử ${saved.employeeCode} · ${formatHrDate(saved.workDate)}.`); }} />
    {reconcileMounted && preview && <Suspense fallback={<HrLoading label="Đang mở đối soát..." />}><HrNightRewardReconcileModal key={preview.attendanceMonth} isOpen={reconcileOpen} onClose={() => setReconcileOpen(false)} month={preview.attendanceMonth} systemEmployees={systemEmployees} parseFile={parseFactory} fileHint="Chọn file .xlsx từ xí nghiệp có cột MS, Họ và tên, Công ca đêm." /></Suspense>}
  </HrPageShell>;
}
