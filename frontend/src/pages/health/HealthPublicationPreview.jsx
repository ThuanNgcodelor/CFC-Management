import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { healthPublicationApi } from '../../api/healthPublicationApi';
import { HrDrawer, HrError, HrLoading } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';

export default function HealthPublicationPreview({ scope, onClose, onChanged }) {
  const [fixedIds, setFixedIds] = useState(null); const [chosen, setChosen] = useState(null);
  const [reason, setReason] = useState(''); const [acknowledge, setAcknowledge] = useState(false);
  const [working, setWorking] = useState(''); const [error, setError] = useState(''); const [outcomes, setOutcomes] = useState([]);
  const body = { ...scope, employeeIds: fixedIds || scope.employeeIds };
  const query = useQuery({ queryKey: ['health', 'publication-preview', body], queryFn: ({ signal }) => healthPublicationApi.preview(body, signal) });
  useEffect(() => {
    if (query.data && fixedIds === null) { setFixedIds(query.data.rows.map((p) => p.employeeId)); setChosen(query.data.rows.flatMap((p) => p.visits.map((v) => v.visitId))); }
  }, [query.data, fixedIds]);
  const selected = chosen || []; const busy = Boolean(working) || query.isFetching;
  const run = async (action) => {
    const people = query.data.rows.map((p) => ({ employeeId: p.employeeId, visits: p.visits.filter((v) => selected.includes(v.visitId)).map((v) => ({ visitId: v.visitId, token: v.token })) })).filter((p) => p.visits.length);
    if (people.some((p) => p.visits.length > 20)) { setError('Mỗi người chọn tối đa 20 lượt trong một lần thao tác.'); return; }
    setWorking(action); setError(''); const result = [];
    try {
      for (let i = 0; i < people.length; i += 20) {
        const value = await healthPublicationApi.apply(action, { campaignId: scope.campaignId, people: people.slice(i, i + 20), reason: reason.trim(), acknowledgeIncomplete: acknowledge });
        result.push(...value.outcomes); setOutcomes([...result]);
      }
      await onChanged(); await query.refetch();
    } catch (failure) { setError(`${healthError(failure)} Có thể thử lại cùng phạm vi; server kiểm tra phiên bản và không tạo trùng.`); }
    finally { setWorking(''); }
  };
  const changeCampaign = async () => {
    setWorking('state'); setError('');
    try { await healthPublicationApi.campaignState(scope.campaignId, { rowVersion: query.data.campaign.rowVersion, closed: query.data.campaign.status !== 'CLOSED', reason: reason.trim() }); await onChanged(); await query.refetch(); }
    catch (failure) { setError(healthError(failure)); } finally { setWorking(''); }
  };
  return <HrDrawer isOpen size="full" onClose={() => { if (!working) onClose(); }} title="Xem trước rà soát và công bố">
    <div className="space-y-4 p-4 sm:p-6">
      <p className="text-sm text-slate-600">Phạm vi được cố định theo danh sách xem trước, không tự thêm người mới khi bộ lọc đổi. Mỗi người được xử lý trong một transaction: nếu một lượt bị chặn, toàn bộ lượt đã chọn của người đó giữ nguyên. Người khác vẫn được xử lý.</p>
      {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} /> : <>
        <p className="rounded-xl bg-slate-50 p-4 text-sm"><strong>{query.data.campaign.name} · {query.data.campaign.year}</strong> · {query.data.campaign.status === 'CLOSED' ? 'Đã khóa' : 'Đang mở'}<br />{query.data.people} người · {query.data.visits} lượt · Có thể công bố: {query.data.publishable} · Cần xử lý: {query.data.blocked}</p>
        <label className="block text-sm font-medium">Lý do rà soát / công bố / thu hồi / khóa đợt<textarea className={`${healthInput} mt-1 w-full`} aria-label="Lý do thao tác công bố" maxLength={500} rows={2} disabled={busy} value={reason} onChange={(e) => setReason(e.target.value)} /></label>
        <label className="block text-sm"><input type="checkbox" checked={acknowledge} disabled={busy} onChange={(e) => setAcknowledge(e.target.checked)} /> Tôi đã đối chiếu người, năm/đợt, nguồn và chấp nhận gắn nhãn dữ liệu chưa đầy đủ; thiếu dữ liệu không được hiểu là bình thường.</label>
        <div className="flex flex-wrap gap-3">
          <Button variant="secondary" disabled={busy || !fixedIds || !selected.length || !reason.trim() || !acknowledge || query.data.campaign.status === 'CLOSED'} onClick={() => run('review')}>Xác nhận đã rà soát</Button>
          <Button disabled={busy || !fixedIds || !selected.length || !reason.trim()} onClick={() => run('publish')}>Công bố các lượt đã chọn</Button>
          <Button variant="secondary" disabled={busy || !fixedIds || !selected.length || !reason.trim()} onClick={() => run('revoke-batch')}>Thu hồi các lượt đã chọn</Button>
          <Button variant="secondary" disabled={busy || !reason.trim()} onClick={changeCampaign}>{query.data.campaign.status === 'CLOSED' ? 'Mở khóa đợt' : 'Khóa đợt'}</Button>
          <Button variant="secondary" disabled={busy} onClick={() => query.refetch()}>Xem trước lại</Button>
        </div>
        {working && <p role="status" className="text-sm">Đang xử lý, vui lòng giữ màn hình này…</p>}
        {outcomes.length > 0 && <div className="rounded-lg border border-slate-200 p-3 text-sm"><strong>Kết quả lần thao tác: {outcomes.filter((r) => r.status === 'SUCCESS').length} người thành công · {outcomes.filter((r) => r.status === 'SKIPPED').length} bỏ qua · {outcomes.filter((r) => ['BLOCKED', 'CONFLICT'].includes(r.status)).length} bị chặn/xung đột</strong>{outcomes.map((r) => <p key={r.employeeId}>{query.data.rows.find((p) => p.employeeId === r.employeeId)?.code || 'Nhân viên trong phạm vi'}: {r.affected} lượt · {r.message}</p>)}</div>}
        {error && <HrError message={error} />}
        <div className="max-h-[55vh] space-y-3 overflow-y-auto">{query.data.rows.map((p) => <article key={p.employeeId} className="rounded-xl border border-slate-200 p-4"><h3 className="font-semibold">{p.code} · {p.name}</h3>{p.visits.map((v) => <div key={v.visitId} className="mt-3 space-y-1 border-t border-slate-100 pt-3 text-sm"><label><input type="checkbox" aria-label={`Chọn lượt công bố ${v.sid}`} disabled={busy} checked={selected.includes(v.visitId)} onChange={() => setChosen((old) => old.includes(v.visitId) ? old.filter((id) => id !== v.visitId) : [...old, v.visitId])} /> SID {v.sid} · {v.results} kết quả · {v.reviewed ? 'Đã rà soát bản hiện tại' : 'Chờ rà soát'} · {v.latest ? `Phiên bản mới nhất v${v.latest.revision} (${v.latest.status === 'REVOKED' ? 'Đã thu hồi' : 'Đã phát hành'})` : 'Chưa công bố'}</label>{v.blockers.map((text) => <p key={text} className="text-red-700">Chặn: {text}</p>)}{v.warnings.map((text) => <p key={text} className="text-amber-800">Lưu ý: {text}</p>)}</div>)}</article>)}</div>
        <p className="text-xs text-slate-500">Khóa đợt chỉ chặn biên tập/rà soát/import; không tự công bố hoặc thu hồi. Có thể phát hành bản đã rà soát hoặc thu hồi khi đợt đã khóa. Chưa có trang cá nhân công khai.</p>
      </>}
    </div>
  </HrDrawer>;
}
