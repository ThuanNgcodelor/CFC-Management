import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { healthApi } from '../../api/healthApi';
import { HrDrawer, HrError } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';

const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm';
export default function HealthEditor({ type, item, onClose, onSaved }) {
  const campaigns = type === 'campaigns';
  const [draft, setDraft] = useState(item || (campaigns
    ? { code: '', name: '', reportYear: new Date().getFullYear(), provider: '', startsOn: '', endsOn: '', workforceGroup: 'BOTH' }
    : { code: '', name: '', category: '', valueType: 'NUMBER', status: 'ACTIVE', aliases: [] }));
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: healthApi.save,
    onSuccess: async (saved) => { await client.invalidateQueries({ queryKey: ['health', type] }); onSaved?.(saved); onClose(); },
  });
  const set = (field, value) => setDraft((current) => ({ ...current, [field]: value }));
  const field = (label, name, kind = 'text', required = true, maxLength = 255) => <label className="block text-sm font-medium">{label}
    <input className={inputClass} type={kind} value={draft[name] ?? ''} required={required} maxLength={maxLength}
      disabled={mutation.isPending || (name === 'code' && Boolean(item))}
      min={kind === 'number' ? 1900 : undefined} max={kind === 'number' ? 2200 : undefined}
      onChange={(event) => set(name, kind === 'number' ? Number(event.target.value) : event.target.value)} />
  </label>;
  const select = (label, name, choices, disabled = false) => <label className="block text-sm font-medium">{label}
    <select className={inputClass} value={draft[name]} disabled={disabled || mutation.isPending} onChange={(event) => set(name, event.target.value)}>
      {choices.map(([value, text]) => <option key={value} value={value}>{text}</option>)}
    </select>
  </label>;
  const submit = (event) => {
    event.preventDefault();
    const payload = campaigns ? { ...draft, startsOn: draft.startsOn || null, endsOn: draft.endsOn || null } : { ...draft, aliases: draft.aliases.map((value) => value.trim()).filter(Boolean) };
    mutation.mutate({ type, item: payload });
  };
  return <HrDrawer isOpen onClose={() => { if (!mutation.isPending) onClose(); }} title={`${item ? 'Sửa' : 'Thêm'} ${campaigns ? 'đợt khám' : 'chỉ tiêu'}`}>
    <form className="space-y-4 p-5 sm:p-7" onSubmit={submit}>
      {field('Mã', 'code', 'text', true, 40)}
      {item && <p className="text-xs text-slate-500">Mã được giữ ổn định sau khi tạo.</p>}
      {field('Tên', 'name')}
      {campaigns ? <>
        {field('Năm báo cáo', 'reportYear', 'number')}{field('Cơ sở khám', 'provider')}
        {select('Khối nhân sự', 'workforceGroup', [['BOTH', 'Cả hai khối'], ['INDIRECT', 'Gián tiếp — Văn phòng'], ['DIRECT', 'Trực tiếp — Công nhân']])}
        {field('Ngày bắt đầu (nếu đã biết)', 'startsOn', 'date', false)}
        {field('Ngày kết thúc (nếu đã biết)', 'endsOn', 'date', false)}
      </> : <>
        {field('Nhóm chỉ tiêu', 'category', 'text', true, 100)}
        {select('Kiểu kết quả', 'valueType', [['NUMBER', 'Số'], ['TEXT', 'Văn bản'], ['CODED', 'Định tính'], ['COMPOSITE', 'Nhiều thành phần']], Boolean(item))}
        {select('Trạng thái', 'status', [['ACTIVE', 'Đang sử dụng'], ['INACTIVE', 'Ngừng sử dụng']])}
        <label className="block text-sm font-medium">Tên cột tương đương (mỗi dòng một tên)
          <textarea className={inputClass} rows={5} maxLength={7679} disabled={mutation.isPending} value={draft.aliases.join('\n')}
            onChange={(event) => set('aliases', event.target.value.split('\n'))} />
        </label>
        <p className="text-xs text-slate-500">Tối đa 30 tên. Giữ rõ tên máy/phương pháp khi cần phân biệt xét nghiệm.</p>
      </>}
      {mutation.isError && <HrError message={mutation.error?.response?.data?.message || 'Không thể lưu. Vui lòng kiểm tra và thử lại.'} />}
      <div className="flex gap-2"><Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Đang lưu...' : 'Lưu'}</Button>
        <Button type="button" variant="secondary" disabled={mutation.isPending} onClick={onClose}>Hủy</Button></div>
    </form>
  </HrDrawer>;
}
