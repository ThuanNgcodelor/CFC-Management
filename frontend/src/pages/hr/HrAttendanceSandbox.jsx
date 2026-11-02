import { useMemo, useState } from 'react';
import { FlaskConical } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { HrDrawer, HrEmpty, HrError, HrLoading, HrPageHeader, HrPageShell, HrPagination } from '../../components/hr/HrUi';
import SEOHead from '../../components/SEOHead';
import { previewAttendanceFile } from '../../api/hrAttendanceSandboxApi';
import { apiErrorMessage, formatHrDate } from '../../utils/hr';
import { parseFactoryNightReward, reconcileSandbox, summarizeSandbox, validWorkValue } from '../../utils/attendanceSandbox';

const employeePageSize = 50;
const dayKey = (day) => `${day.employeeCode}:${day.workDate}`;
const number = (value) => Number(value || 0).toLocaleString('vi-VN', { maximumFractionDigits: 2 });
const time = (value) => value ? value.slice(11, 16) : '—';
const dateTimeInput = (value) => value ? value.slice(0, 16) : '';

function statusLabel(status) {
  return {
    AUTO_MATCHED: 'Đã ghép thử', NEEDS_REVIEW: 'Cần kiểm tra', NO_PUNCH: 'Không chấm',
    CONFIRMED: 'Đã xác nhận thử', REJECTED: 'Bỏ qua thử',
  }[status] || status;
}

function ReviewDrawer({ day, policies, onClose, onSave, onReject }) {
  const [shiftCode, setShiftCode] = useState(day.shiftCode || '');
  const [checkInAt, setCheckInAt] = useState(dateTimeInput(day.checkInAt));
  const [checkOutAt, setCheckOutAt] = useState(dateTimeInput(day.checkOutAt));
  const [workValue, setWorkValue] = useState(String(day.workValue ?? 0));
  const [error, setError] = useState('');

  const save = (event) => {
    event.preventDefault();
    if (!shiftCode || !validWorkValue(workValue) || (Number(workValue) > 0 &&
      (!checkInAt || !checkOutAt || checkOutAt <= checkInAt))) {
      setError('Chọn ca, mức công hợp lệ và hai giờ chấm đúng thứ tự khi tính công.');
      return;
    }
    const policy = policies.find((item) => item.code === shiftCode);
    onSave({ ...day, shiftCode, checkInAt: checkInAt || null, checkOutAt: checkOutAt || null,
      workValue: Number(workValue), countsTowardNightReward: Boolean(policy?.countsTowardNightReward),
      status: 'CONFIRMED', explanation: 'Đã chỉnh sửa và xác nhận trong bản thử nghiệm.' });
  };

  return <HrDrawer isOpen onClose={onClose} title={`Kiểm tra ${day.employeeCode} · ${formatHrDate(day.workDate)}`}
    description="Thay đổi chỉ nằm trên trang thử nghiệm, không ghi vào chấm công thật.">
    <form onSubmit={save} className="space-y-4 p-5">
      <p className="text-sm text-gray-600">{day.employeeName} · Dòng Excel {day.sourceRowNumber}</p>
      <p className="text-sm text-gray-600">Dấu chấm trong file: {day.punches.length
        ? day.punches.map((punch) => `${formatHrDate(punch.punchedAt.slice(0, 10))} ${time(punch.punchedAt)}`).join(' · ')
        : 'Không có'}</p>
      <label className="block text-sm font-medium">Ca làm
        <select value={shiftCode} onChange={(event) => setShiftCode(event.target.value)} className="mt-1 w-full rounded-lg border p-2">
          <option value="">Chọn ca</option>
          {policies.map((policy) => <option key={policy.code} value={policy.code}>{policy.code} · {policy.name}</option>)}
        </select>
      </label>
      <label className="block text-sm font-medium">Lượt vào
        <input type="datetime-local" value={checkInAt} onChange={(event) => setCheckInAt(event.target.value)}
          className="mt-1 w-full rounded-lg border p-2" />
      </label>
      <label className="block text-sm font-medium">Lượt ra
        <input type="datetime-local" value={checkOutAt} onChange={(event) => setCheckOutAt(event.target.value)}
          className="mt-1 w-full rounded-lg border p-2" />
      </label>
      <label className="block text-sm font-medium">Công tính thử
        <select value={workValue} onChange={(event) => setWorkValue(event.target.value)} className="mt-1 w-full rounded-lg border p-2">
          {[0, 0.5, 1, 1.5, 2].map((value) => <option key={value} value={value}>{number(value)}</option>)}
        </select>
      </label>
      {error && <p className="text-sm text-red-700">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit">Xác nhận thử</Button>
        <Button type="button" variant="secondary" onClick={() => onReject(day)}>Bỏ qua ngày này</Button>
        <Button type="button" variant="secondary" onClick={onClose}>Đóng</Button>
      </div>
    </form>
  </HrDrawer>;
}

function EmployeeTable({ summaries, selectedCode, onSelect, page, onPageChange }) {
  const pageRows = summaries.slice(page * employeePageSize, (page + 1) * employeePageSize);
  return <section className="rounded-xl border border-gray-200 bg-white p-5">
    <h2 className="mb-1 text-lg font-semibold">Tổng quan theo nhân viên</h2>
    <p className="mb-4 text-sm text-gray-500">Công và ca đêm là số tạm tính. Ca còn cần kiểm tra vẫn được tính dự kiến để phát hiện chênh lệch sớm.</p>
    <div className="overflow-x-auto"><table className="min-w-[900px] w-full text-left text-sm">
      <thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr>
        <th className="p-3">Nhân viên</th><th className="p-3">Ngày</th><th className="p-3">Công tạm tính</th>
        <th className="p-3">Ca đêm dự kiến</th><th className="p-3">Ca đêm xác nhận thử</th><th className="p-3">Cần kiểm tra</th>
      </tr></thead>
      <tbody>{pageRows.map((item) => <tr key={item.employeeCode}
        className={`cursor-pointer border-t hover:bg-emerald-50 ${selectedCode === item.employeeCode ? 'bg-emerald-50' : ''}`}
        onClick={() => onSelect(item.employeeCode)}>
        <td className="p-3"><strong>{item.employeeCode}</strong><span className="block text-xs text-gray-500">{item.employeeName}</span></td>
        <td className="p-3">{item.days}</td><td className="p-3">{number(item.workValue)}</td>
        <td className="p-3">{item.proposedNightShifts}</td><td className="p-3">{item.confirmedNightShifts}</td>
        <td className="p-3 font-semibold text-amber-700">{item.reviewDays || '—'}</td>
      </tr>)}</tbody>
    </table></div>
    <HrPagination page={page} totalPages={Math.ceil(summaries.length / employeePageSize)}
      totalElements={summaries.length} onPageChange={onPageChange} />
  </section>;
}

function Reconciliation({ result }) {
  return <section className="rounded-xl border border-gray-200 bg-white p-5">
    <h2 className="text-lg font-semibold">Đối soát thưởng ca đêm với xí nghiệp</h2>
    <p className="mb-4 text-sm text-gray-500">So sánh theo mã nhân viên với số ca đêm dự kiến sau khi sửa thử. Ngày chưa kiểm tra được báo riêng; đây chưa phải kết quả thưởng đã chốt.</p>
    <div className="overflow-x-auto"><table className="min-w-[700px] w-full text-left text-sm">
      <thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr>
        <th className="p-3">Nhân viên</th><th className="p-3">Xí nghiệp</th><th className="p-3">Dự kiến</th>
        <th className="p-3">Chênh lệch XN − thử nghiệm</th><th className="p-3">Kết quả</th>
      </tr></thead><tbody>{result.matched.map((row) => <tr key={row.employeeCode} className="border-t">
        <td className="p-3"><strong>{row.employeeCode}</strong><span className="block text-xs text-gray-500">{row.employeeName}</span></td>
        <td className="p-3">{row.nightShifts}</td><td className="p-3">{row.systemNightShifts}</td>
        <td className="p-3">{row.difference}</td><td className={`p-3 font-medium ${row.difference ? 'text-red-700' : 'text-emerald-700'}`}>
          {row.unresolvedDays ? `Còn ${row.unresolvedDays} ngày cần kiểm tra` : row.difference ? 'Lệch số ca' : 'Khớp'}
        </td>
      </tr>)}</tbody></table></div>
    {result.onlyInFactory.length > 0 && <div className="mt-4 rounded-lg bg-amber-50 p-4 text-sm text-amber-900">
      <strong>Chỉ có trong file xí nghiệp:</strong> {result.onlyInFactory.map((row) => `${row.employeeCode} · ${row.employeeName}`).join(', ')}
    </div>}
    {result.onlyInAttendance.length > 0 && <div className="mt-3 rounded-lg bg-blue-50 p-4 text-sm text-blue-900">
      <strong>Chỉ có trong file chấm công:</strong> {result.onlyInAttendance.map((row) => `${row.employeeCode} · ${row.employeeName}`).join(', ')}
    </div>}
  </section>;
}

export default function HrAttendanceSandbox() {
  const [preview, setPreview] = useState(null);
  const [days, setDays] = useState([]);
  const [factoryRows, setFactoryRows] = useState(null);
  const [selectedCode, setSelectedCode] = useState('');
  const [editing, setEditing] = useState(null);
  const [reviewOnly, setReviewOnly] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);

  const summaries = useMemo(() => summarizeSandbox(days), [days]);
  const result = useMemo(() => factoryRows ? reconcileSandbox(factoryRows, summaries) : null, [factoryRows, summaries]);
  const selectedDays = useMemo(() => days.filter((day) => day.employeeCode === selectedCode
    && (!reviewOnly || day.status === 'NEEDS_REVIEW' || day.status === 'NO_PUNCH')),
  [days, selectedCode, reviewOnly]);

  const loadAttendance = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setBusy(true); setError('');
    try {
      const next = await previewAttendanceFile(file);
      setPreview(next); setDays(next.days); setFactoryRows(null); setSelectedCode(''); setPage(0);
    } catch (requestError) { setError(apiErrorMessage(requestError, 'Không đọc được file công thử nghiệm.')); }
    finally { setBusy(false); event.target.value = ''; }
  };
  const loadFactory = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setBusy(true); setError('');
    try { setFactoryRows(await parseFactoryNightReward(file)); }
    catch (requestError) { setError(requestError.message || 'Không đọc được file xí nghiệp.'); }
    finally { setBusy(false); event.target.value = ''; }
  };
  const replaceDay = (next) => {
    setDays((current) => current.map((day) => dayKey(day) === dayKey(next) ? next : day));
    setEditing(null);
  };

  return <HrPageShell>
    <SEOHead title="CFC Base | Thử nghiệm chấm công" url="https://cfcbooking.io.vn/manager/hr/attendance/sandbox" />
    <HrPageHeader eyebrow="Khu thử nghiệm · không lưu dữ liệu" title="Thử chấm công và thưởng ca đêm"
      description="Tải file công để ghép hai lượt vào/ra theo bộ ghép ca hiện tại. Sửa và xác nhận thử các ngày cần kiểm tra, rồi tải file xí nghiệp để đối soát." />
    <div className="mb-5 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
      <FlaskConical className="mr-2 inline h-4 w-4" />Dữ liệu chỉ ở trang này. Bản thử áp dụng ca chung của công nhân, chưa áp dụng miễn chấm hay chính sách riêng từng người. Tải lại hoặc đóng trang sẽ xóa kết quả thử; không tạo import, không chốt công hay ghi thưởng vào hệ thống.
    </div>
    <section className="mb-5 grid gap-4 rounded-xl border border-gray-200 bg-white p-5 md:grid-cols-2">
      <label className="block text-sm font-medium">1. File công thử nghiệm (.xlsx)
        <input type="file" accept=".xlsx" disabled={busy} onChange={loadAttendance} className="mt-2 block w-full rounded-lg border p-2 text-sm" />
      </label>
      <label className="block text-sm font-medium">2. File xí nghiệp gửi (.xlsx)
        <input type="file" accept=".xlsx" disabled={busy || !preview} onChange={loadFactory} className="mt-2 block w-full rounded-lg border p-2 text-sm" />
      </label>
      {preview && <p className="text-sm text-gray-600 md:col-span-2">Tháng {preview.attendanceMonth} · {preview.totalRows} ngày công · {preview.totalPunches} dấu chấm</p>}
    </section>
    {busy && <HrLoading label="Đang xử lý file thử nghiệm..." />}
    {error && <div className="mb-5"><HrError message={error} /></div>}
    {!preview && !busy && <HrEmpty title="Chưa có file công" description="Chọn File công thử nghiệm.xlsx để bắt đầu. File sẽ không được lưu vào hệ thống." />}
    {preview && <div className="space-y-5">
      <div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" onClick={() => setDays((current) => current.map((day) =>
        day.status === 'AUTO_MATCHED' ? { ...day, status: 'CONFIRMED' } : day))}>Xác nhận thử ca đã ghép</Button>
        <Button type="button" variant="secondary" onClick={() => { setDays(preview.days); setSelectedCode(''); }}>Đặt lại kết quả thử</Button>
      </div>
      <EmployeeTable summaries={summaries} selectedCode={selectedCode} onSelect={setSelectedCode} page={page} onPageChange={setPage} />
      {selectedCode && <section className="rounded-xl border border-gray-200 bg-white p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">Ngày công · {selectedCode}</h2>
          <label className="text-sm"><input type="checkbox" checked={reviewOnly} onChange={(event) => setReviewOnly(event.target.checked)} /> Chỉ xem cần kiểm tra</label>
        </div>
        {selectedDays.length === 0 ? <HrEmpty title="Không có ngày cần kiểm tra" /> : <div className="max-h-[460px] overflow-auto">
          <table className="min-w-[780px] w-full text-left text-sm"><thead className="sticky top-0 bg-gray-50 text-xs uppercase text-gray-500"><tr>
            <th className="p-3">Ngày</th><th className="p-3">Lượt vào</th><th className="p-3">Lượt ra</th><th className="p-3">Ca</th>
            <th className="p-3">Công</th><th className="p-3">Trạng thái</th><th className="p-3">Thao tác</th>
          </tr></thead><tbody>{selectedDays.map((day) => <tr key={dayKey(day)} className="border-t">
            <td className="p-3">{formatHrDate(day.workDate)}</td><td className="p-3">{time(day.checkInAt)}</td><td className="p-3">{time(day.checkOutAt)}</td>
            <td className="p-3">{day.shiftCode || '—'}</td><td className="p-3">{number(day.workValue)}</td><td className="p-3">{statusLabel(day.status)}</td>
            <td className="p-3"><Button type="button" size="sm" variant="secondary" onClick={() => setEditing(day)}>Sửa thử</Button></td>
          </tr>)}</tbody></table></div>}
      </section>}
      {result && <Reconciliation result={result} />}
    </div>}
    {editing && <ReviewDrawer key={dayKey(editing)} day={editing} policies={preview.policies} onClose={() => setEditing(null)}
      onSave={replaceDay} onReject={(day) => replaceDay({ ...day, status: 'REJECTED', workValue: 0, countsTowardNightReward: false })} />}
  </HrPageShell>;
}
