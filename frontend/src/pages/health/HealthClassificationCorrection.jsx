import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { healthImportApi } from '../../api/healthImportApi';
import { Button } from '../../components/ui/Button';
import { HrError } from '../../components/hr/HrUi';
import { healthInput, healthError } from './healthImportUi';

export default function HealthClassificationCorrection({ batch, row, onCorrected, onClose }) {
  const cells = row.cells.filter((cell) => /^(phan loai|phan loai suc khoe|xep loai suc khoe)$/.test(cell.label.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim()));
  const [key, setKey] = useState(cells[0]?.key || '');
  const [value, setValue] = useState('');
  const [reason, setReason] = useState('');
  const mutation = useMutation({ mutationFn: healthImportApi.correctClassification, onSuccess: async (next) => { await onCorrected(next); onClose(); } });
  if (!cells.length || row.state !== 'PENDING') return null;
  return <form className="space-y-3 rounded-xl border border-amber-200 bg-amber-50 p-4" onSubmit={(event) => {
    event.preventDefault(); mutation.mutate({ id: batch.id, rowId: row.id, key, value, reason, rowVersion: row.rowVersion });
  }}>
    <h3 className="font-semibold">Đính chính phân loại sức khỏe</h3>
    <p className="text-sm text-slate-600">Chỉ sửa dòng chưa nhập. Giá trị gốc, người sửa và lý do được giữ trong lịch sử riêng; kết quả chưa được công bố.</p>
    {cells.length > 1 && <label className="block text-sm">Ô phân loại<select className={`${healthInput} mt-1 w-full`} value={key} onChange={(event) => setKey(event.target.value)}>{cells.map((cell) => <option key={cell.key} value={cell.key}>{cell.label}</option>)}</select></label>}
    <label className="block text-sm">Phân loại đính chính<select className={`${healthInput} mt-1 w-full`} required value={value} onChange={(event) => setValue(event.target.value)}><option value="">Chọn I–V</option>{['I', 'II', 'III', 'IV', 'V'].map((code) => <option key={code} value={code}>{code}</option>)}</select></label>
    <label className="block text-sm">Lý do đính chính<textarea className={`${healthInput} mt-1 w-full`} required maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label>
    {mutation.isError && <HrError message={healthError(mutation.error)} />}
    <Button type="submit" disabled={!key || !value || !reason.trim() || mutation.isPending}>Lưu đính chính phân loại</Button>
  </form>;
}
