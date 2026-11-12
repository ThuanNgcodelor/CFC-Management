import { useCallback, useEffect, useRef, useState } from 'react';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { healthSelfApi } from '../../api/healthSelfApi';
import { HrEmpty, HrError, HrLoading } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthError, healthInput } from './healthImportUi';
import HealthSelfRecords from './HealthSelfRecords';

function Portal({ client }) {
  const [session, setSession] = useState(null);
  const [form, setForm] = useState({ citizenNumber: '', employeeCode: '', accessCode: '', mode: 'IDENTITY' });
  const [state, setState] = useState({ working: false, error: '' });
  const controller = useRef(null);
  const privateRoot = useRef(null);
  const capabilities = useQuery({ queryKey: ['health-self-capabilities'], queryFn: ({ signal }) => healthSelfApi.capabilities(signal) });
  const exit = useCallback(() => {
    privateRoot.current?.setAttribute('hidden', '');
    if (session) healthSelfApi.logout(session.token).catch(() => { console.warn('Không thể đóng phiên tra cứu trên máy chủ; phiên sẽ tự hết hạn.'); });
    setSession(null);
    setForm({ citizenNumber: '', employeeCode: '', accessCode: '', mode: 'IDENTITY' }); setState({ working: false, error: '' });
    client.cancelQueries({ queryKey: ['health-self'] }); client.removeQueries({ queryKey: ['health-self'] });
  }, [client, session]);
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    if (!session) return;
    const timeout = setTimeout(exit, Math.max(0, Date.parse(`${session.expiresAt}Z`) - Date.now()));
    const hide = () => exit(); const restore = (event) => { if (event.persisted) exit(); };
    window.addEventListener('pagehide', hide); window.addEventListener('pageshow', restore);
    return () => { clearTimeout(timeout); window.removeEventListener('pagehide', hide); window.removeEventListener('pageshow', restore); };
  }, [session, exit]);
  const lookup = async (event) => {
    event.preventDefault(); setState({ working: true, error: '' });
    try {
      controller.current?.abort(); controller.current = new AbortController();
      const result = await healthSelfApi.lookup(form.mode === 'IDENTITY' ? { mode: form.mode, citizenNumber: form.citizenNumber, employeeCode: form.employeeCode } : { mode: form.mode, accessCode: form.accessCode }, controller.current.signal);
      if (!controller.current.signal.aborted) { setForm({ citizenNumber: '', employeeCode: '', accessCode: '', mode: 'IDENTITY' }); setSession({ ...result, api: healthSelfApi.forSession(result.token) }); }
    } catch (error) { if (!controller.current.signal.aborted) setState({ working: false, error: healthError(error) }); }
    finally { if (!controller.current.signal.aborted) setState((value) => ({ ...value, working: false })); }
  };
  if (session) return <div ref={privateRoot}><p className="bg-emerald-50 px-4 py-2 text-center text-xs text-emerald-900">Phiên tra cứu kết thúc lúc {new Date(`${session.expiresAt}Z`).toLocaleTimeString('vi-VN')} trên thiết bị này.</p><HealthSelfRecords api={session.api} onExit={exit} /></div>;
  return <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-8"><section className="w-full max-w-lg space-y-5 rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm sm:p-8">
    <header><p className="text-xs font-semibold tracking-widest text-emerald-700">CFC · CHĂM SÓC SỨC KHỎE NHÂN VIÊN</p><h1 className="mt-3 text-2xl font-semibold">Tra cứu sổ sức khỏe</h1><p className="mt-2 text-sm text-slate-600">Không cần tài khoản CFCBase. Chọn cách tra cứu phù hợp với quyền mà phòng nhân sự đã cấp cho bạn.</p></header>
    {capabilities.isPending ? <HrLoading /> : capabilities.isError ? (capabilities.error.status === 404 ? <HrEmpty title="Tra cứu cá nhân chưa được mở" description="Liên hệ phòng Tổ chức – Hành chính – Nhân sự để được hỗ trợ." /> : <HrError message={healthError(capabilities.error)} onRetry={capabilities.refetch} />) : <form className="space-y-4" autoComplete="off" onSubmit={lookup}>
      <fieldset className="space-y-2"><legend className="mb-2 text-sm font-medium">Cách tra cứu</legend>
        {[['IDENTITY', 'CCCD + mã nhân viên'], ['ACCESS_CODE', 'Dùng mã tra cứu riêng']].map(([mode, label]) => <label key={mode} className="flex items-center gap-2 rounded-lg border border-slate-200 p-3 text-sm"><input type="radio" name="lookupMode" value={mode} checked={form.mode === mode} disabled={state.working} onChange={() => { setForm({ citizenNumber: '', employeeCode: '', accessCode: '', mode }); setState({ working: false, error: '' }); }} />{label}</label>)}
      </fieldset>
      {form.mode === 'IDENTITY' && <p className="text-xs text-slate-600">Cách này chỉ dùng được khi nhân sự đã cho phép tra cứu bằng hai thông tin định danh của bạn.</p>}
      {[['citizenNumber', 'Số CCCD', 'password', '[0-9]{12}', 12], ['employeeCode', 'Mã nhân viên', 'text', undefined, 32], ['accessCode', 'Mã tra cứu riêng', 'password', '[A-Za-z0-9_\\-]{32}', 32]].filter(([key]) => form.mode === 'ACCESS_CODE' ? key === 'accessCode' : key !== 'accessCode').map(([key, label, type, pattern, maxLength]) => <label key={key} className="block text-sm font-medium">{label}<input className={`${healthInput} mt-1 block w-full`} aria-label={label} name={key} type={type} inputMode={key === 'citizenNumber' ? 'numeric' : 'text'} required pattern={pattern} maxLength={maxLength} autoComplete="off" spellCheck={false} value={form[key]} disabled={state.working} onChange={(event) => setForm({ ...form, [key]: event.target.value })} /></label>)}
      {state.error && <HrError message={state.error} />}
      <Button type="submit" className="w-full" disabled={state.working}>{state.working ? 'Đang xác minh…' : 'Tra cứu kết quả của tôi'}</Button>
    </form>}
    <p className="text-xs leading-relaxed text-slate-500">Mất mã, thiếu CCCD hoặc chưa thấy kết quả? Liên hệ nhân sự để xác minh và cấp lại. Không chia sẻ mã tra cứu. Phiên tự kết thúc sau thời hạn hiển thị trong hệ thống; thoát sau khi dùng trên máy chung.</p>
  </section></main>;
}
export default function HealthSelfPortal() {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0, gcTime: 0, refetchOnWindowFocus: false, refetchOnReconnect: false }, mutations: { retry: false } } }));
  useEffect(() => () => client.clear(), [client]);
  return <QueryClientProvider client={client}><Portal client={client} /></QueryClientProvider>;
}
