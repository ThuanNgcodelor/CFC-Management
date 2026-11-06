import { useEffect, useRef, useState } from 'react';
import { infrastructureApi } from '../../api/infrastructureApi';
import { HrError, HrLoading } from '../../components/hr/HrUi';

const EMPTY = { baseUrl: 'https://192.168.1.1', username: '', password: '', enabled: true, rowVersion: 0 };
const inputClass = 'mt-1 w-full rounded-lg border px-3 py-2 disabled:bg-slate-100';

export default function RouterConnectionPanel({ onSaved }) {
  const [form, setForm] = useState(EMPTY);
  const [metadata, setMetadata] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const requests = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    requests.current = controller;
    infrastructureApi.routerConnection(controller.signal).then((data) => {
      if (controller.signal.aborted) return;
      setMetadata(data);
      setForm({ ...EMPTY, ...data, password: '', enabled: data.source === 'NONE' ? true : data.enabled });
    }).catch((failure) => {
      if (!controller.signal.aborted) setError(failure?.response?.data?.message || 'Không tải được cấu hình MikroTik.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, []);

  const change = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }));
    setResult(null);
    setError('');
  };
  const act = async (action) => {
    const signal = requests.current.signal;
    setBusy(action); setError(''); setResult(null);
    const payload = Object.fromEntries(Object.keys(EMPTY).map((key) => [key, form[key]]));
    try {
      if (action === 'test') {
        const response = await infrastructureApi.testRouterConnection(payload, signal);
        if (!signal.aborted) setResult(response);
      } else {
        const data = await infrastructureApi.saveRouterConnection(payload, signal);
        if (signal.aborted) return;
        setMetadata(data);
        setForm({ ...EMPTY, ...data, password: '' });
        // Saving and connecting are independent: a failed probe must not imply the save failed.
        setResult({ connected: null, message: 'Đã lưu cấu hình và mã hóa mật khẩu trên server.' });
        if (data.enabled) {
          try {
            const status = await infrastructureApi.refreshRouter(signal);
            if (!signal.aborted) setResult({ connected: status.status === 'ONLINE', status,
              message: status.status === 'ONLINE' ? 'Đã lưu và kết nối thành công.' : `Đã lưu. ${status.lastError || 'Chưa có số liệu.'}` });
          } catch (failure) {
            if (!signal.aborted) setResult({ connected: false, message: `Đã lưu cấu hình. ${failure?.response?.data?.message || 'Chưa kiểm tra được kết nối.'}` });
          }
        }
        if (!signal.aborted) await onSaved(signal);
      }
    } catch (failure) {
      if (!signal.aborted) setError(failure?.response?.data?.message || 'Không thực hiện được; kiểm tra kết nối tới server.');
    } finally { if (!signal.aborted) setBusy(''); }
  };

  if (loading) return <HrLoading label="Đang tải cấu hình MikroTik..." />;
  if (!metadata) return <HrError message={error} />;
  const changedIdentity = form.baseUrl.replace(/\/$/, '') !== metadata.baseUrl?.replace(/\/$/, '') || form.username.trim() !== metadata.username;
  const requiresPassword = !metadata.passwordConfigured || changedIdentity;

  return <div className="space-y-4 rounded-xl border bg-white p-5">
    <div><h2 className="text-lg font-semibold">Kết nối MikroTik</h2>
      <p className="mt-1 text-sm text-slate-600">Nhập tài khoản MikroTik có quyền đọc REST API. Máy chủ Fedora sẽ kết nối tới router qua mạng LAN.</p>
      <p className="mt-1 text-xs text-slate-500">{metadata.source === 'WEB' ? 'Đang dùng cấu hình đã lưu trên web.' : metadata.source === 'ENV' ? 'Đang dùng cấu hình server; lưu tại đây để chuyển sang quản lý trên web.' : 'Chưa lưu tài khoản kết nối.'}</p>
    </div>
    <form onSubmit={(event) => { event.preventDefault(); act('save'); }} className="space-y-4">
      <fieldset disabled={Boolean(busy)} className="grid min-w-0 gap-4 md:grid-cols-3">
        <label className="min-w-0 text-sm">Địa chỉ HTTPS MikroTik
          <input type="url" required maxLength={200} placeholder="https://192.168.1.1" value={form.baseUrl} className={inputClass} onChange={(event) => change('baseUrl', event.target.value)} />
        </label>
        <label className="min-w-0 text-sm">Tài khoản MikroTik
          <input required maxLength={80} autoComplete="off" value={form.username} className={inputClass} onChange={(event) => change('username', event.target.value)} />
        </label>
        <label className="min-w-0 text-sm">Mật khẩu MikroTik
          <input type="password" required={requiresPassword} maxLength={256} autoComplete="new-password" value={form.password} placeholder={requiresPassword ? 'Nhập mật khẩu MikroTik' : 'Để trống để giữ mật khẩu đã lưu'} className={inputClass} onChange={(event) => change('password', event.target.value)} />
        </label>
        <label className="flex items-center gap-2 text-sm md:col-span-3"><input type="checkbox" checked={form.enabled} onChange={(event) => change('enabled', event.target.checked)} />Bật giám sát tài nguyên và đồng bộ DHCP/ARP</label>
      </fieldset>
      <div className="flex flex-wrap gap-3">
        <button disabled={Boolean(busy)} type="button" onClick={(event) => { if (event.currentTarget.form.reportValidity()) act('test'); }} className="rounded-lg border px-4 py-2 disabled:opacity-50">{busy === 'test' ? 'Đang kiểm tra...' : 'Kiểm tra kết nối'}</button>
        <button disabled={Boolean(busy)} className="rounded-lg bg-emerald-700 px-4 py-2 text-white disabled:opacity-50">{busy === 'save' ? 'Đang lưu và kiểm tra...' : 'Lưu cấu hình'}</button>
      </div>
    </form>
    {error && <HrError message={error} />}
    {result && <div role="status" className={`rounded-lg border p-4 text-sm ${result.connected === false ? 'border-amber-300 bg-amber-50' : 'border-emerald-200 bg-emerald-50'}`}>
      <p>{result.message}</p>
      {result.status?.status === 'ONLINE' && <p className="mt-2">RouterOS {result.status.softwareVersion} · Uptime {result.status.uptime} · CPU {result.status.cpuLoad ?? '—'}% · RAM trống {result.status.freeMemory == null ? '—' : `${Math.round(result.status.freeMemory / 1024 / 1024)} MiB`} · {result.status.interfacesUp} interface hoạt động</p>}
    </div>}
    <p className="text-xs leading-relaxed text-slate-500">RouterOS cần bật dịch vụ HTTPS (www-ssl), cho phép IP máy chủ và có chứng thư được Java trên Fedora tin cậy. Kiểm tra kết nối chỉ đọc dữ liệu, chưa lưu cấu hình. Mật khẩu đã lưu không được trả lại trình duyệt.</p>
  </div>;
}
