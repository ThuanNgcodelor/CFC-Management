import { canApproveNightRewards } from '../../utils/hrPermissions';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  AlertTriangle,
  Building2,
  CheckCircle2,
  Clock3,
  FileSpreadsheet,
  Info,
  Loader2,
  RefreshCw,
  ShieldCheck,
  Unlock,
} from 'lucide-react';
import toast from 'react-hot-toast';
import SEOHead from '../../components/SEOHead';
import { Button } from '../../components/ui/Button';
import { HrAttendanceTabs } from '../../components/hr/HrAttendanceTabs';
import { HrDrawer, HrEmpty, HrError, HrLoading, HrPageHeader, HrPageShell, HrStatusBadge } from '../../components/hr/HrUi';
import { HrNightRewardReconcileModal } from '../../components/hr/HrNightRewardReconcileModal';
import { HrNightRewardMonthTable } from '../../components/hr/HrNightRewardMonthTable';
import { HrNightRewardEmployeeHistory } from '../../components/hr/HrNightRewardEmployeeHistory';
import { authApi } from '../../api/authApi';
import { hrNightRewardApi as api } from '../../api/hrNightRewardApi';
import { apiErrorMessage, formatHrDate, formatHrDateTime } from '../../utils/hr';

const currentMonth = () => new Date().toISOString().slice(0, 7);
const shadowMode = import.meta.env.VITE_HR_PRODUCTION_ATTENDANCE_SHADOW_MODE === 'true';
const statusText = {
  QUALIFIED: 'Đạt điều kiện',
  QUALIFIED_EXCEPTION: 'Đạt ngoại lệ',
  NOT_QUALIFIED: 'Chưa đạt',
  EXCLUDED: 'Không áp dụng',
  STALE: 'Cần chốt lại',
  DRAFT: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  CANCELLED: 'Đã hủy',
};
const typeText = {
  COMPANY_PRODUCTION_PLAN: 'Kế hoạch sản xuất',
  COMPANY_MAINTENANCE: 'Sửa chữa',
  COMPANY_REDUCED_ORDERS: 'Giảm đơn hàng',
  OTHER_COMPANY_APPROVED: 'Khác đã được duyệt',
};
const label = (value) => statusText[value] || value || '—';

function Metric({ label: title, value, description, tone = 'slate' }) {
  const tones = {
    slate: 'border-slate-200 bg-white',
    green: 'border-emerald-200 bg-emerald-50',
    blue: 'border-blue-200 bg-blue-50',
    amber: 'border-amber-200 bg-amber-50',
  };
  return (
    <article className={`rounded-xl border p-4 ${tones[tone]}`}>
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{title}</p>
      <p className="mt-1 text-2xl font-bold text-gray-900">{value}</p>
      <p className="mt-1 text-xs text-gray-600">{description}</p>
    </article>
  );
}

function TimelineDrawer({ value, onClose }) {
  return (
    <HrDrawer
      isOpen={Boolean(value)}
      onClose={onClose}
      title={value ? `${value.employeeCode} · ${value.employeeName}` : ''}
      description="Lịch sử xét thưởng được giữ theo từng tháng; tháng không đạt không làm mất số tháng đã tích lũy."
      size="wide"
    >
      {value && (
        <div className="space-y-5 p-5 sm:p-7">
          <section className="grid gap-3 sm:grid-cols-2">
            <Metric
              label="Chu kỳ định kỳ"
              value={`${value.progress.periodicQualifiedMonths}/${value.progress.periodicRequiredMonths}`}
              description="Đủ 12 tháng mới tạo quyền lợi nháp."
              tone="green"
            />
            <Metric
              label="Chu kỳ gắn bó"
              value={`${value.progress.loyaltyQualifiedMonths}/${value.progress.loyaltyRequiredMonths}`}
              description="Đủ 24 tháng mới tạo quyền lợi nháp."
              tone="blue"
            />
          </section>
          <section>
            <h3 className="font-semibold text-gray-900">Dòng thời gian tháng</h3>
            <div className="mt-3 space-y-3">
              {value.qualifications.map((item) => (
                <article key={item.id} className="rounded-xl border border-gray-200 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold">{item.attendanceMonth} · lần chốt {item.revision}</p>
                      <p className="mt-1 text-sm text-gray-600">
                        {item.actualNightShiftCount}/{item.requiredNightShiftCount} ca đêm · {item.qualificationReason}
                      </p>
                    </div>
                    <HrStatusBadge status={item.status} />
                  </div>
                  {item.sourceShifts?.length > 0 && (
                    <p className="mt-3 text-xs text-gray-500">
                      Nguồn: {item.sourceShifts.map((source) => `${formatHrDate(source.workDate)} (${source.shiftCode})`).join(', ')}
                    </p>
                  )}
                  {item.staleReason && (
                    <p className="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
                      Đã cần chốt lại: {item.staleReason}
                    </p>
                  )}
                </article>
              ))}
              {!value.qualifications.length && (
                <HrEmpty title="Chưa có tháng nào được chốt" description="Chốt tháng tại màn hình này để tạo lịch sử tích lũy." />
              )}
            </div>
          </section>
          {value.entitlements?.length > 0 && (
            <section>
              <h3 className="font-semibold text-gray-900">Quyền lợi đang chờ xử lý</h3>
              <div className="mt-2 space-y-2">
                {value.entitlements.map((item) => (
                  <p key={item.id} className="rounded-lg bg-blue-50 p-3 text-sm">
                    {item.track === 'PERIODIC_NIGHT' ? 'Thưởng ca đêm định kỳ' : 'Thưởng gắn bó ca đêm'} · đủ {item.qualifiedMonthsAtMaturity} tháng · {Number(item.amount).toLocaleString('vi-VN')} đ · <b>{label(item.status)}</b>
                  </p>
                ))}
              </div>
            </section>
          )}
        </div>
      )}
    </HrDrawer>
  );
}

// ──────────────────────────────────────────────────────────
// Main page
// ──────────────────────────────────────────────────────────
export default function HrNightRewards() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeView = searchParams.get('view') === 'person' ? 'person' : 'month';
  const selectedEmployeeCode = searchParams.get('employee') || '';
  const month = searchParams.get('month') || localStorage.getItem('hr-night-reward-month') || currentMonth();
  const setMonth = (nextMonth) => setSearchParams((current) => {
    const next = new URLSearchParams(current);
    if (nextMonth) next.set('month', nextMonth);
    else next.delete('month');
    return next;
  });
  const [data, setData] = useState(null);
  const [exceptions, setExceptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [timeline, setTimeline] = useState(null);
  const [exceptionForm, setExceptionForm] = useState({
    employeeCode: '',
    exceptionType: 'COMPANY_PRODUCTION_PLAN',
    reason: '',
    evidenceReference: '',
  });
  const [showReconcile, setShowReconcile] = useState(false);
  const loadSeq = useRef(0);
  const hasDataRef = useRef(false);
  const canApprove = canApproveNightRewards(authApi.getRole());

  useEffect(() => {
    hasDataRef.current = Boolean(data);
  }, [data]);

  const load = useCallback(async (silent = false) => {
    if (!silent && !hasDataRef.current) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }
    setError('');
    const seq = ++loadSeq.current;
    try {
      const [next, nextExceptions] = await Promise.all([api.month(month), api.exceptions(month)]);
      if (seq === loadSeq.current) {
        setData(next);
        setExceptions(nextExceptions || []);
        localStorage.setItem('hr-night-reward-month', month);
      }
    } catch (requestError) {
      if (seq === loadSeq.current) {
        setError(apiErrorMessage(requestError, 'Không tải được dữ liệu thưởng ca đêm.'));
      }
    } finally {
      if (seq === loadSeq.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [month]);

  useEffect(() => {
    if (activeView === 'month') load();
  }, [activeView, load]);

  const showPerson = (code) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set('view', 'person');
      if (code) next.set('employee', code);
      else next.delete('employee');
      return next;
    });
  };

  const showMonth = () => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete('view');
      next.delete('employee');
      return next;
    });
  };

  const openTimeline = useCallback(async (code) => {
    try {
      setTimeline(await api.timeline(code));
    } catch (requestError) {
      toast.error(apiErrorMessage(requestError, 'Không tải được lịch sử nhân viên.'));
    }
  }, []);

  const isFinalized = Boolean(
    data?.finalized ?? (
      data?.employees?.length > 0 &&
      data.employees.every((item) => item.finalizedStatus && item.finalizedStatus !== 'STALE')
    )
  );

  const finalize = async () => {
    const reason = window.prompt('Nhập lý do chốt tháng để đối soát:');
    if (!reason?.trim()) return;
    setBusy(true);
    try {
      const result = await api.finalizeMonth(month, { reason: reason.trim() });
      toast.success(`Đã chốt ${result.createdQualifications} hồ sơ tháng ${month}.`);
      await load(true);
    } catch (requestError) {
      toast.error(apiErrorMessage(requestError, 'Không thể chốt tháng.'));
    } finally {
      setBusy(false);
    }
  };

  const reopen = async () => {
    const reason = window.prompt(`Nhập lý do mở khóa tháng ${month} đã chốt:`);
    if (!reason?.trim()) return;
    setBusy(true);
    try {
      const result = await api.reopenMonth(month, { reason: reason.trim() });
      toast.success(`Đã mở khóa tháng ${month} (${result.staleQualifications} hồ sơ chuyển về chưa chốt).`);
      await load(true);
    } catch (requestError) {
      toast.error(apiErrorMessage(requestError, 'Không thể mở khóa tháng.'));
    } finally {
      setBusy(false);
    }
  };

  const createException = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      await api.createException({ ...exceptionForm, attendanceMonth: month });
      setExceptionForm({ employeeCode: '', exceptionType: 'COMPANY_PRODUCTION_PLAN', reason: '', evidenceReference: '' });
      toast.success('Đã tạo ngoại lệ, chờ người có quyền phê duyệt.');
      await load(true);
    } catch (requestError) {
      toast.error(apiErrorMessage(requestError, 'Không thể tạo ngoại lệ.'));
    } finally {
      setBusy(false);
    }
  };

  const review = async (item, action) => {
    const reason = window.prompt(action === 'approve' ? 'Lý do phê duyệt:' : action === 'reject' ? 'Lý do từ chối:' : 'Lý do hủy:');
    if (!reason?.trim()) return;
    setBusy(true);
    try {
      const method = action === 'approve' ? api.approveException : action === 'reject' ? api.rejectException : api.cancelException;
      await method(item.id, { reason: reason.trim() });
      toast.success('Đã cập nhật ngoại lệ.');
      await load(true);
    } catch (requestError) {
      toast.error(apiErrorMessage(requestError, 'Không thể cập nhật ngoại lệ.'));
    } finally {
      setBusy(false);
    }
  };

  if (activeView === 'month' && loading && !data) {
    return (
      <HrPageShell size="wide">
        <HrLoading label="Đang tổng hợp thưởng ca đêm..." />
      </HrPageShell>
    );
  }

  if (activeView === 'month' && error && !data) {
    return (
      <HrPageShell size="wide">
        <HrError message={error} onRetry={load} />
      </HrPageShell>
    );
  }

  return (
    <HrPageShell size="wide">
      <SEOHead title="Thưởng ca đêm | CFC Base" />
      <HrPageHeader
        eyebrow="QUẢN LÝ NHÂN SỰ"
        title="Thưởng gắn bó ca đêm"
        description="Theo dõi ca đêm đã chốt theo tháng, bảo lưu tháng đạt điều kiện và kiểm soát ngoại lệ trước khi chi trả."
        actions={activeView === 'month' ?
          <div className="flex gap-2">
            <input
              aria-label="Tháng"
              type="month"
              value={month}
              onChange={(event) => setMonth(event.target.value)}
              className="h-10 rounded-lg border border-gray-300 px-3 text-sm"
            />
            <Button
              type="button"
              variant="secondary"
              disabled={refreshing || busy}
              onClick={() => load(true)}
            >
              <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
              {refreshing ? 'Đang tải...' : 'Tải lại'}
            </Button>
          </div>
        : null}
      />

      {/* Unified Attendance Tabs */}
      <HrAttendanceTabs activeTab="night-rewards" />

      <nav aria-label="Cách xem thưởng ca đêm" className="mb-5 inline-flex max-w-full gap-1 rounded-xl border border-gray-200 bg-white p-1 shadow-sm">
        <button type="button" onClick={showMonth} aria-current={activeView === 'month' ? 'page' : undefined}
          className={`rounded-lg px-4 py-2 text-sm font-semibold ${activeView === 'month' ? 'bg-emerald-600 text-white' : 'text-gray-600 hover:bg-slate-50'}`}>
          Theo tháng
        </button>
        <button type="button" onClick={() => showPerson(selectedEmployeeCode)} aria-current={activeView === 'person' ? 'page' : undefined}
          className={`rounded-lg px-4 py-2 text-sm font-semibold ${activeView === 'person' ? 'bg-emerald-600 text-white' : 'text-gray-600 hover:bg-slate-50'}`}>
          Theo nhân viên
        </button>
      </nav>

      {activeView === 'person' ? (
        <HrNightRewardEmployeeHistory employeeCode={selectedEmployeeCode} initialMonth={month}
          onSelectEmployee={showPerson} onOpenRewardTimeline={openTimeline} />
      ) : (<>

      {/* Busy processing banner */}
      {busy && (
        <section className="mb-5 flex items-center gap-3 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm font-medium text-blue-900 shadow-sm animate-pulse">
          <Loader2 className="h-5 w-5 shrink-0 animate-spin text-blue-600" />
          <span>Đang xử lý dữ liệu và cập nhật trạng thái thưởng ca đêm... Vui lòng đợi trong giây lát.</span>
        </section>
      )}

      {/* Shadow mode notice */}
      {shadowMode && (
        <section className="mb-5 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950">
          <div className="flex gap-3">
            <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-blue-600" />
            <div>
              <b>Giai đoạn SHADOW / đối soát.</b> Màn hình này chỉ tạo lịch sử tích lũy và quyền lợi nháp. Chưa cộng vào bảng lương, PDF hoặc Telegram.
            </div>
          </div>
        </section>
      )}

      {error && <HrError message={error} onRetry={load} />}

      {/* Top summary metrics */}
      <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <Metric
          label="Người được xét"
          value={data?.eligibleEmployees || 0}
          description={`Ngưỡng ${data?.program?.qualifyingNightThreshold || 10} ca đêm hợp lệ/tháng`}
        />
        <Metric
          label="Đạt điều kiện"
          value={data?.qualifiedEmployees || 0}
          description="Mỗi tháng đạt được cộng tối đa 01 lần"
          tone="green"
        />
        <Metric
          label="Ngoại lệ đã duyệt"
          value={data?.exceptionEmployees || 0}
          description="Do nguyên nhân phía Công ty"
          tone="blue"
        />
        <Metric
          label="Chưa đạt"
          value={data?.notQualifiedEmployees || 0}
          description="Không mất số tháng đã tích lũy"
          tone="amber"
        />
      </section>

      {/* Main check section */}
      <section className="mt-5 rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 p-5">
          <div>
            <div className="flex items-center gap-2.5">
              <h2 className="font-semibold text-gray-900">
                {isFinalized ? `Kết quả tháng ${month}` : `Kiểm tra tháng ${month}`}
              </h2>
              {isFinalized ? (
                <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  Đã chốt tháng
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2.5 py-0.5 text-xs font-semibold text-amber-700">
                  Chưa chốt
                </span>
              )}
              {refreshing && (
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-blue-600">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  Đang cập nhật...
                </span>
              )}
            </div>
            <p className="mt-1 text-sm text-gray-500">
              {isFinalized
                ? `Kết quả thưởng ca đêm tháng ${month} đã được chốt và tích lũy vào chu kỳ cho nhân viên.`
                : 'Chỉ dùng ca có chính sách được đánh dấu "tính vào điều kiện thưởng ca đêm" và file chấm công đã chốt. Kết quả tháng chưa cộng vào chu kỳ cho đến khi chốt thưởng.'}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setShowReconcile(true)}
              title="Upload file Excel từ xí nghiệp để so sánh và xuất file đối soát"
            >
              <FileSpreadsheet className="h-4 w-4" />
              Rà soát Excel
            </Button>
            {isFinalized ? (
              canApprove && (
                <Button
                  type="button"
                  variant="secondary"
                  disabled={busy}
                  onClick={reopen}
                  className="border-red-200 text-red-700 hover:bg-red-50 hover:text-red-800"
                  title="Mở khóa tháng này để điều chỉnh hoặc tính lại"
                >
                  <Unlock className="h-4 w-4 text-red-600" />
                  Mở khóa
                </Button>
              )
            ) : (
              canApprove && (
                <Button
                  type="button"
                  disabled={busy || !data?.readyToFinalize}
                  title={!data?.readyToFinalize ? 'Xử lý các cảnh báo trước khi chốt.' : 'Chốt lịch sử tích lũy cho nhân viên trong file đã xác nhận'}
                  onClick={finalize}
                >
                  <CheckCircle2 className="h-4 w-4" />
                  Chốt tháng
                </Button>
              )
            )}
          </div>
        </div>

        {data?.blockers?.length > 0 && (
          <div className="m-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            <div className="flex gap-2">
              <AlertTriangle className="h-5 w-5 shrink-0" />
              <div>
                <b>Chưa thể chốt tháng</b>
                <ul className="mt-1 list-disc pl-5">
                  {data.blockers.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        )}

        {data?.notices?.length > 0 && (
          <div className="m-5 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
            <div className="flex gap-2">
              <Info className="h-5 w-5 shrink-0 text-blue-600" />
              <div>
                <b>Phạm vi chốt lần này</b>
                <ul className="mt-1 list-disc pl-5">
                  {data.notices.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        )}

        <HrNightRewardMonthTable employees={data?.employees || []} onOpenEmployee={showPerson} />
      </section>

      {/* Exception section */}
      <section className="mt-5 grid gap-5 xl:grid-cols-[0.9fr_1.6fr]">
        <form onSubmit={createException} className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
              <Building2 className="h-5 w-5" />
            </div>
            <div>
              <h2 className="font-semibold text-gray-900">Ngoại lệ phía Công ty</h2>
              <p className="text-xs text-gray-500">Tạo hồ sơ đề xuất, sau đó Quản trị viên duyệt để xét đủ điều kiện.</p>
            </div>
          </div>
          <div className="mt-4 space-y-3">
            <input
              required
              value={exceptionForm.employeeCode}
              onChange={(event) =>
                setExceptionForm({ ...exceptionForm, employeeCode: event.target.value.toUpperCase() })
              }
              placeholder="Mã nhân viên, ví dụ B124"
              className="h-10 w-full rounded-lg border border-gray-300 px-3 text-sm"
            />
            <select
              value={exceptionForm.exceptionType}
              onChange={(event) =>
                setExceptionForm({ ...exceptionForm, exceptionType: event.target.value })
              }
              className="h-10 w-full rounded-lg border border-gray-300 bg-white px-3 text-sm"
            >
              {Object.entries(typeText).map(([key, value]) => (
                <option key={key} value={key}>
                  {value}
                </option>
              ))}
            </select>
            <textarea
              required
              rows={3}
              value={exceptionForm.reason}
              onChange={(event) => setExceptionForm({ ...exceptionForm, reason: event.target.value })}
              placeholder="Lý do, phạm vi và căn cứ ngoại lệ"
              className="w-full rounded-lg border border-gray-300 p-3 text-sm"
            />
            <input
              value={exceptionForm.evidenceReference}
              onChange={(event) =>
                setExceptionForm({ ...exceptionForm, evidenceReference: event.target.value })
              }
              placeholder="Số văn bản / đường dẫn bằng chứng (nếu có)"
              className="h-10 w-full rounded-lg border border-gray-300 px-3 text-sm"
            />
            <Button type="submit" disabled={busy} className="w-full">
              <Clock3 className="h-4 w-4" />
              Tạo hồ sơ ngoại lệ
            </Button>
          </div>
        </form>

        <section className="rounded-xl border border-gray-200 bg-white shadow-sm">
          <div className="border-b border-gray-100 p-5">
            <h2 className="font-semibold text-gray-900">Danh sách ngoại lệ tháng {month}</h2>
          </div>
          <div className="divide-y divide-gray-100">
            {exceptions.map((item) => (
              <article key={item.id} className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">
                      {item.employeeCode} · {typeText[item.exceptionType] || item.exceptionType}
                    </p>
                    <p className="mt-1 text-sm text-gray-600">{item.reason}</p>
                    {item.evidenceReference && (
                      <p className="mt-1 text-xs text-gray-500">Căn cứ: {item.evidenceReference}</p>
                    )}
                    <p className="mt-1 text-xs text-gray-400">Tạo lúc {formatHrDateTime(item.createdAt)}</p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <HrStatusBadge status={item.status} />
                    {item.status === 'DRAFT' && canApprove && (
                      <>
                        <Button type="button" size="sm" onClick={() => review(item, 'approve')}>
                          Duyệt
                        </Button>
                        <Button type="button" size="sm" variant="secondary" onClick={() => review(item, 'reject')}>
                          Từ chối
                        </Button>
                      </>
                    )}
                    {item.status === 'DRAFT' && (
                      <Button type="button" size="sm" variant="danger" onClick={() => review(item, 'cancel')}>
                        Hủy
                      </Button>
                    )}
                  </div>
                </div>
              </article>
            ))}
            {!exceptions.length && (
              <div className="p-6">
                <HrEmpty
                  title="Chưa có ngoại lệ"
                  description="Chỉ dùng ngoại lệ do nguyên nhân từ phía Công ty và có căn cứ đối soát."
                />
              </div>
            )}
          </div>
        </section>
      </section>

      </>)}
      <TimelineDrawer value={timeline} onClose={() => setTimeline(null)} />
      <HrNightRewardReconcileModal
        key={month}
        isOpen={showReconcile}
        onClose={() => setShowReconcile(false)}
        month={month}
        systemEmployees={data?.employees || []}
      />
    </HrPageShell>
  );
}
