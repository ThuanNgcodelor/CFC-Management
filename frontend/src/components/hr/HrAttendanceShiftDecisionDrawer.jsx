import { useEffect, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { hrProductionAttendanceApi as api } from '../../api/hrProductionAttendanceApi';
import { HrDrawer } from './HrUi';
import { Button } from '../ui/Button';
import { apiErrorMessage, formatHrDate, formatHrDateTime } from '../../utils/hr';

const time = (value) => value ? formatHrDateTime(value) : '—';

function datePlusDays(value, days) {
  const [year, month, day] = String(value || '').slice(0, 10).split('-').map(Number);
  if (!year || !month || !day) return '';
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return date.toISOString().slice(0, 10);
}

function punchMinutes(value) {
  const match = String(value || '').match(/T(\d{2}):(\d{2})/);
  return match ? Number(match[1]) * 60 + Number(match[2]) : null;
}

function decisionPunches(punches, shift, policy, direction) {
  if (!shift) return [];
  const crossesMidnight = Boolean(policy?.crossesMidnight);
  const expectedDate = direction === 'out' && crossesMidnight
    ? datePlusDays(shift.workDate, 1)
    : shift.workDate;
  return punches.filter((punch) => {
    if (String(punch.punchedAt || '').slice(0, 10) !== expectedDate) return false;
    if (!crossesMidnight) return true;
    const minutes = punchMinutes(punch.punchedAt);
    if (minutes === null) return false;
    // Ca qua đêm: chiều/tối là lượt vào; sáng hôm sau là lượt ra.
    // Tách hai nhóm này để không lấy lượt ra của ngày trước làm lượt vào hôm nay.
    return direction === 'in' ? minutes >= 12 * 60 : minutes < 12 * 60;
  });
}

export function HrAttendanceShiftDecisionDrawer({ shift, policies, readOnly, onClose, onSaved, draft = false, onDraftDecision }) {
  const [punches, setPunches] = useState([]);
  const [adjustments, setAdjustments] = useState([]);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(null);
  const savingRef = useRef(false);
  const selectedPolicy = useMemo(() => policies.find((item) => item.code === (form?.shiftCode || shift?.shiftCode)),
    [form?.shiftCode, policies, shift?.shiftCode]);
  const checkInPunches = useMemo(() => decisionPunches(punches, shift, selectedPolicy, 'in'),
    [punches, selectedPolicy, shift]);
  const checkOutPunches = useMemo(() => decisionPunches(punches, shift, selectedPolicy, 'out'),
    [punches, selectedPolicy, shift]);

  useEffect(() => {
    if (!shift) return;
    setForm({
      action: 'CONFIRM', shiftCode: shift.shiftCode || '', checkInPunchId: '', checkOutPunchId: '',
      workValue: String(shift.workValue ?? 0), nightAllowanceAmount: String(shift.nightAllowanceAmount ?? 0), reason: '',
    });
    Promise.all(draft ? [Promise.resolve((shift.punches || []).map((punch) => ({ ...punch, sourceRowNumber: shift.sourceRowNumber, sourceColumn: punch.id?.split(':')[1], rawValue: punch.punchedAt?.slice(11, 16) }))), Promise.resolve(shift.adjustments || [])] : [api.shiftPunches(shift.id), api.adjustments(shift.id)])
      .then(([nextPunches, nextAdjustments]) => {
        setPunches(nextPunches || []); setAdjustments(nextAdjustments || []);
        setForm((value) => ({ ...value, checkInPunchId: shift.checkInAt ? (nextPunches || []).find((item) => item.punchedAt === shift.checkInAt)?.id || '' : '', checkOutPunchId: shift.checkOutAt ? (nextPunches || []).find((item) => item.punchedAt === shift.checkOutAt)?.id || '' : '' }));
      })
      .catch((error) => toast.error(apiErrorMessage(error, 'Không tải được chi tiết ca.')));
  }, [shift, draft]);

  const submit = async (event) => {
    event.preventDefault();
    if (savingRef.current) return;
    if (!form.reason.trim()) return toast.error('Phải nhập lý do quyết định.');
    savingRef.current = true;
    setSaving(true);
    try {
      const decision = {
        ...form,
        shiftCode: form.shiftCode || null,
        checkInPunchId: form.checkInPunchId || null,
        checkOutPunchId: form.checkOutPunchId || null,
        workValue: Number(form.workValue),
        nightAllowanceAmount: Number(form.nightAllowanceAmount),
        rowVersion: shift.rowVersion,
      };
      const saved = draft ? await onDraftDecision(shift, decision) : await api.decideShift(shift.id, decision);
      if (!draft) {
        // A live decision needs its persisted audit trail before the drawer closes.
        const persistedAdjustments = await api.adjustments(shift.id);
        if (!persistedAdjustments?.length) {
          throw new Error('ATTENDANCE_DECISION_NOT_PERSISTED');
        }
        setAdjustments(persistedAdjustments);
      }
      await onSaved(saved, form.action);
    } catch (error) {
      toast.error(error?.message === 'ATTENDANCE_DECISION_NOT_PERSISTED'
        ? 'Máy chủ chưa xác nhận lịch sử điều chỉnh. Ca vẫn đang mở; không coi là đã lưu.'
        : apiErrorMessage(error, 'Không thể lưu quyết định ca.'));
    }
    finally { savingRef.current = false; setSaving(false); }
  };

  return <HrDrawer isOpen={Boolean(shift)} onClose={onClose} title={shift ? `${shift.employeeCode} · ${formatHrDate(shift.workDate)}` : ''} description={draft ? "Dấu chấm gốc không bị sửa; quyết định chỉ nằm trong phiên thử này." : "Dấu chấm gốc không bị sửa; mọi quyết định đều lưu lịch sử."} size="wide">
    {shift && form && <form onSubmit={submit} className="space-y-5 p-5 sm:p-7">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="text-sm font-medium text-gray-700">Quyết định<select disabled={readOnly} value={form.action} onChange={(event) => setForm({ ...form, action: event.target.value, ...(event.target.value === 'REJECT' ? { checkInPunchId: '', checkOutPunchId: '' } : {}) })} className="mt-1 h-11 w-full rounded-lg border border-gray-300 bg-white px-3"><option value="CONFIRM">Xác nhận/điều chỉnh</option><option value="REJECT">Từ chối, tính 0 công</option></select></label>
        <label className="text-sm font-medium text-gray-700">Ca<select disabled={readOnly || form.action === 'REJECT'} value={form.shiftCode} onChange={(event) => { const shiftCode = event.target.value; const selected = policies.find((item) => item.code === shiftCode); setForm({ ...form, shiftCode, checkInPunchId: '', checkOutPunchId: '', nightAllowanceAmount: String(selected?.nightAllowanceAmount ?? form.nightAllowanceAmount) }); }} className="mt-1 h-11 w-full rounded-lg border border-gray-300 bg-white px-3"><option value="">Giữ ca đề xuất</option>{policies.filter((item) => item.policyGroup === shift.policyGroup).map((item) => <option key={item.id} value={item.code}>{item.code} · {item.name}</option>)}</select></label>
        <label className="text-sm font-medium text-gray-700">Lượt vào<select disabled={readOnly || form.action === 'REJECT'} value={form.checkInPunchId} onChange={(event) => setForm({ ...form, checkInPunchId: event.target.value })} className="mt-1 h-11 w-full rounded-lg border border-gray-300 bg-white px-3"><option value="">Để trống</option>{checkInPunches.map((item) => <option key={item.id} value={item.id}>{time(item.punchedAt)} · cột {item.sourceColumn}</option>)}</select></label>
        <label className="text-sm font-medium text-gray-700">Lượt ra<select disabled={readOnly || form.action === 'REJECT'} value={form.checkOutPunchId} onChange={(event) => setForm({ ...form, checkOutPunchId: event.target.value })} className="mt-1 h-11 w-full rounded-lg border border-gray-300 bg-white px-3"><option value="">Để trống</option>{checkOutPunches.map((item) => <option key={item.id} value={item.id}>{time(item.punchedAt)} · cột {item.sourceColumn}</option>)}</select></label>
        <label className="text-sm font-medium text-gray-700">Số công<select disabled={readOnly} value={form.workValue} onChange={(event) => setForm({ ...form, workValue: event.target.value })} className="mt-1 h-11 w-full rounded-lg border border-gray-300 bg-white px-3"><option value="0">0</option><option value="1">1</option><option value="1.5">1,5</option><option value="2">2</option></select></label>
        <label className="text-sm font-medium text-gray-700">Phụ cấp đêm<input disabled={readOnly} type="number" min="0" step="1000" value={form.nightAllowanceAmount} onChange={(event) => setForm({ ...form, nightAllowanceAmount: event.target.value })} className="mt-1 h-11 w-full rounded-lg border border-gray-300 px-3" /></label>
      </div>
      {!readOnly && <label className="block text-sm font-medium text-gray-700">Lý do bắt buộc<textarea value={form.reason} onChange={(event) => setForm({ ...form, reason: event.target.value })} rows={3} className="mt-1 w-full rounded-lg border border-gray-300 p-3" /></label>}
      {!readOnly && <div className="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900"><p className="font-semibold">{draft ? 'Quyết định thử không ghi vào hệ thống' : 'Hệ thống sẽ kiểm tra các ngày liên quan'}</p><p className="mt-1">Ca đêm dùng lượt vào chiều/tối của ngày bắt đầu và lượt ra sáng hôm sau. Dấu đang thuộc ngày liền trước/sau sẽ bị chặn, không còn tự chuyển lỗi lùi từng ngày.</p></div>}
      <div className="rounded-xl bg-slate-50 p-4 text-sm text-gray-600"><p className="font-semibold text-gray-800">Giải thích hệ thống</p><p className="mt-1">{shift.explanation || 'Không có.'}</p></div>
      <div><h3 className="text-sm font-semibold text-gray-900">Dấu chấm gốc</h3><div className="mt-2 space-y-2">{punches.map((item) => <div key={item.id} className="flex justify-between rounded-lg border border-gray-200 p-3 text-sm"><span>{time(item.punchedAt)}</span><span className="text-gray-500">Dòng {item.sourceRowNumber} · {item.sourceColumn} · {item.rawValue}</span></div>)}{!punches.length && <p className="text-sm text-gray-500">Không có dấu chấm.</p>}</div></div>
      <details><summary className="cursor-pointer text-sm font-semibold text-gray-800">Lịch sử điều chỉnh ({adjustments.length})</summary><div className="mt-2 space-y-2">{adjustments.map((item) => <div key={item.id} className="rounded-lg border border-gray-200 p-3 text-sm"><p>{item.reason}</p><p className="mt-1 text-xs text-gray-500">{formatHrDateTime(item.createdAt)} · {item.createdByActor}</p></div>)}</div></details>
      <div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={onClose} disabled={saving}>{readOnly ? 'Đóng' : 'Hủy'}</Button>{!readOnly && <Button type="submit" disabled={saving}>{saving ? 'Đang lưu...' : draft ? 'Lưu quyết định thử' : 'Lưu và tính lại chuỗi'}</Button>}</div>
    </form>}
  </HrDrawer>;
}

