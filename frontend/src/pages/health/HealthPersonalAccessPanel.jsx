import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { baseApi } from '../../api/baseApi';
import { unwrapApiData } from '../../api/hrApiUtils';
import { HrError, HrLoading } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';

const root = '/hr/health/personal-access';
export default function HealthPersonalAccessPanel({ employeeId }) {
  const client = useQueryClient();
  const [reason, setReason] = useState('');
  const [state, setState] = useState({ working: false, error: '', code: null });
  const status = useQuery({ queryKey: ['health', 'personal-access', employeeId], queryFn: async ({ signal }) => unwrapApiData(await baseApi.get(`${root}/${encodeURIComponent(employeeId)}`, { signal })) });
  useEffect(() => {
    const clear = () => setState((value) => ({ ...value, code: null }));
    const hide = () => { if (document.visibilityState === 'hidden') clear(); };
    window.addEventListener('pagehide', clear); document.addEventListener('visibilitychange', hide);
    return () => { window.removeEventListener('pagehide', clear); document.removeEventListener('visibilitychange', hide); };
  }, []);
  const apply = async (action, policy = undefined) => {
    setState({ working: true, error: '', code: null });
    try {
      const result = unwrapApiData(await baseApi.post(`${root}/${encodeURIComponent(employeeId)}/${action}`, { generation: status.data.generation, reason: reason.trim(), ...(policy === undefined ? {} : { allowIdentityOnly: policy }) }));
      setState({ working: false, error: '', code: result?.code || null });
      await client.invalidateQueries({ queryKey: ['health', 'personal-access', employeeId] });
    } catch (error) { setState({ working: false, error: healthError(error), code: null }); }
  };
  return <section className="space-y-3 rounded-xl border border-emerald-200 bg-white p-4">
    <h2 className="font-semibold">Quyền và mã tra cứu sức khỏe cá nhân</h2>
    <p className="text-sm text-slate-600">Nhân viên vào <a className="text-emerald-700 underline" href="/health-lookup" target="_blank" rel="noreferrer">trang tra cứu cá nhân</a>, chọn CCCD + mã nhân viên khi được phép, hoặc chỉ nhập mã tra cứu riêng. Cấp lại/thu hồi sẽ kết thúc các phiên cũ. Thu hồi chặn cả hai cách tra cứu. Chỉ giao mã sau khi xác minh đúng người.</p>
    {status.isPending ? <HrLoading /> : status.isError ? <HrError message={healthError(status.error)} onRetry={status.refetch} /> : <>
      <p className="text-sm">{!status.data.eligible ? 'Chưa có CCCD 12 chữ số hợp lệ; kiểm tra tại hồ sơ nhân sự trước khi cấp mã.' : !status.data.issued ? 'Chưa cấp mã.' : status.data.revoked ? 'Mã đã bị thu hồi.' : `Đã cấp mã · Hạn dùng (UTC): ${status.data.expiresAt}`}</p>
      {status.data.issued && !status.data.revoked && <div className="space-y-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm">
        <p>{status.data.allowIdentityOnly ? 'Đang cho phép cả hai cách tra cứu.' : 'Hiện chỉ cho tra cứu có mã riêng.'}</p>
        <p>Cho phép hai trường nghĩa là người biết đúng CCCD và mã nhân viên có thể xem kết quả đã công bố mà không cần mã riêng. Đổi lựa chọn sẽ kết thúc phiên cũ; cấp lại mã mặc định quay về chỉ dùng mã riêng.</p>
        <Button variant="secondary" disabled={!reason.trim() || state.working || status.isFetching} onClick={() => apply('policy', !status.data.allowIdentityOnly)}>{status.data.allowIdentityOnly ? 'Chỉ cho phép dùng mã riêng' : 'Cho phép CCCD + mã nhân viên'}</Button>
      </div>}
      <label className="block text-sm">Lý do cấp/thu hồi<input aria-label="Lý do quản lý mã tra cứu" className={`${healthInput} mt-1 block w-full`} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      <div className="flex flex-wrap gap-3"><Button disabled={!status.data.eligible || !reason.trim() || state.working || status.isFetching} onClick={() => apply('issue')}>{status.data.issued ? 'Cấp lại mã tra cứu' : 'Cấp mã tra cứu'}</Button><Button variant="secondary" disabled={!status.data.issued || status.data.revoked || !reason.trim() || state.working || status.isFetching} onClick={() => apply('revoke')}>Thu hồi mã tra cứu</Button></div>
      {state.code && <div className="space-y-2 rounded-lg bg-amber-50 p-3"><p className="text-sm">Mã chỉ hiện ở lần cấp này. Giao riêng cho đúng người; hệ thống không lưu bản rõ để xem lại.</p><code className="block select-all break-all text-lg" aria-label="Mã tra cứu vừa cấp">{state.code}</code><Button variant="secondary" onClick={() => setState((value) => ({ ...value, code: null }))}>Ẩn mã</Button></div>}
    </>}
    {state.error && <HrError message={state.error} />}
  </section>;
}
