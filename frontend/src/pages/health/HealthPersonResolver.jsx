import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { healthImportApi } from '../../api/healthImportApi';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { HrDrawer, HrLoading, HrError } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';

export default function HealthPersonResolver({ batch, row, onClose, onChanged }) {
  const [keyword, setKeyword] = useState('');
  const [draft, setDraft] = useDebouncedHrSearchValue(keyword, setKeyword);
  const [person, setPerson] = useState(null);
  const [reason, setReason] = useState('');
  const query = useQuery({ queryKey: ['health', 'import-employees', keyword], enabled: keyword.length >= 2,
    queryFn: ({ signal }) => healthImportApi.employees(keyword, signal) });
  const mutation = useMutation({ mutationFn: healthImportApi.resolve, onSuccess: async (value) => {
    await onChanged(value); onClose();
  } });
  const candidates = keyword.length >= 2 ? query.data || [] : row.candidates;
  return <HrDrawer isOpen onClose={() => { if (!mutation.isPending) onClose(); }} title={`Ghép nhân sự — Dòng ${row.sourceRow}`}>
    <form className="space-y-4 p-5" onSubmit={(event) => { event.preventDefault(); mutation.mutate({ id: batch.id, rowId: row.id, employeeId: person.id, reason, rowVersion: row.rowVersion }); }}>
      <p className="text-sm">Nguồn: <strong>{row.name}</strong> · {row.code || 'Chưa có mã'}</p>
      <p className="text-sm text-slate-600">Ngày/năm sinh trong nguồn: {row.sourceBirth || 'Chưa có dữ liệu'}. Đối chiếu cùng mã, tên và thông tin sinh trước khi ghép; gợi ý không tự xác nhận người.</p>
      <label className="block text-sm">Tìm mã hoặc tên trong nhân sự<input className={`${healthInput} mt-1 w-full`} value={draft} maxLength={100} onChange={(event) => setDraft(event.target.value)} /></label>
      {query.isFetching && <HrLoading label="Đang tìm nhân sự..." />}
      {query.isError && <HrError message={healthError(query.error)} onRetry={query.refetch} />}
      {candidates.map((item) => <label key={item.id} className="flex gap-3 rounded-lg border border-slate-200 p-3 text-sm">
        <input type="radio" name="health-person" checked={person?.id === item.id} onChange={() => setPerson(item)} />
        <span>{item.code} · {item.name}<span className="block text-xs text-slate-500">Ngày sinh: {item.birthDate || 'Chưa có dữ liệu'}</span></span>
      </label>)}
      <label className="block text-sm">Lý do/xác nhận đối chiếu<textarea className={`${healthInput} mt-1 w-full`} required maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      {mutation.isError && <HrError message={healthError(mutation.error)} />}
      <Button type="submit" disabled={!person || !reason.trim() || mutation.isPending}>Xác nhận ghép người</Button>
    </form>
  </HrDrawer>;
}
