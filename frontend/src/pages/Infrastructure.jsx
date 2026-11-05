import { useCallback, useEffect, useState } from 'react';
import { Activity, Plus, RefreshCw, Router, Search, Server, ShieldAlert } from 'lucide-react';
import toast from 'react-hot-toast';
import { infrastructureApi } from '../api/infrastructureApi';

const EMPTY_FORM = { name: '', deviceType: 'ROUTER', vendor: '', model: '', managementIp: '', firmwareVersion: '', site: '', location: '', notes: '' };
const typeNames = { ROUTER: 'Router', SWITCH: 'Switch', ACCESS_POINT: 'Wi-Fi / AP', SERVER: 'Máy chủ', WAN: 'Đường truyền', OTHER: 'Khác' };
const formatLogTime = (value) => {
  const nanoseconds = Number(value);
  return Number.isFinite(nanoseconds) ? new Date(nanoseconds / 1_000_000).toLocaleString('vi-VN') : value;
};

export default function Infrastructure() {
  const [tab, setTab] = useState('devices');
  const [devices, setDevices] = useState([]);
  const [logs, setLogs] = useState([]);
  const [status, setStatus] = useState(null);
  const [query, setQuery] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [logQuery, setLogQuery] = useState('');
  const [deviceIp, setDeviceIp] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState('');
  const [form, setForm] = useState(EMPTY_FORM);
  const [busy, setBusy] = useState(false);

  const loadDevices = useCallback(async (signal) => {
    try {
      const [page, routerStatus] = await Promise.all([
        infrastructureApi.devices({ page: 0, size: 100, q: query }, signal),
        infrastructureApi.routerStatus(signal),
      ]);
      setDevices(page?.content || []);
      setStatus(routerStatus || null);
    } catch (error) { if (!signal?.aborted) toast.error(error?.response?.data?.message || 'Không tải được dữ liệu hạ tầng.'); }
  }, [query]);

  useEffect(() => { const controller = new AbortController(); loadDevices(controller.signal); return () => controller.abort(); }, [loadDevices]);
  useEffect(() => { const timer = window.setTimeout(() => setQuery(searchInput.trim()), 250); return () => window.clearTimeout(timer); }, [searchInput]);
  const loadLogs = async () => {
    setBusy(true);
    try {
      const result = await infrastructureApi.logs({ q: logQuery, deviceIp, size: 200 }, undefined);
      setLogs(result?.content || []);
    } catch (error) { toast.error(error?.response?.data?.message || 'Không tải được nhật ký.'); }
    finally { setBusy(false); }
  };

  const addDevice = async (event) => {
    event.preventDefault(); setBusy(true);
    try { if (editingId) await infrastructureApi.updateDevice(editingId, form); else await infrastructureApi.addDevice(form); setShowForm(false); setEditingId(''); setForm(EMPTY_FORM); toast.success(editingId ? 'Đã cập nhật thiết bị.' : 'Đã thêm thiết bị.'); await loadDevices(new AbortController().signal); }
    catch (error) { toast.error(error?.response?.data?.message || 'Không thể thêm thiết bị.'); }
    finally { setBusy(false); }
  };

  return <section className="space-y-6">
    <header className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-widest text-emerald-700">Quản trị hệ thống</p><h1 className="mt-2 text-2xl font-bold">Hạ tầng mạng</h1><p className="mt-1 text-sm text-slate-500">Danh mục thiết bị, trạng thái kết nối và nhật ký mạng nội bộ.</p></div><button className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white" onClick={() => loadDevices(new AbortController().signal)}><RefreshCw className="mr-2 inline h-4 w-4"/>Tải lại</button></header>
    <nav className="flex gap-2 border-b"><button className={`px-4 py-2 ${tab === 'devices' ? 'border-b-2 border-emerald-700 font-semibold text-emerald-800' : 'text-slate-500'}`} onClick={() => setTab('devices')}>Thiết bị</button><button className={`px-4 py-2 ${tab === 'logs' ? 'border-b-2 border-emerald-700 font-semibold text-emerald-800' : 'text-slate-500'}`} onClick={() => setTab('logs')}>Nhật ký</button></nav>
    {tab === 'devices' ? <>
      <div className="grid gap-4 md:grid-cols-3"><article className="rounded-xl border bg-white p-5"><div className="text-sm text-slate-500">Thiết bị đang theo dõi</div><div className="mt-2 text-3xl font-bold">{devices.length}</div></article><article className="rounded-xl border bg-white p-5"><div className="flex items-center gap-2 text-sm text-slate-500"><Router className="h-4 w-4"/>MikroTik RouterOS</div><div className="mt-2 text-xl font-semibold">{status?.status === 'ONLINE' ? 'Đang kết nối' : status?.status === 'OFFLINE' ? 'Mất kết nối' : 'Chưa cấu hình'}</div><div className="mt-1 text-xs text-slate-500">{status?.softwareVersion ? `RouterOS ${status.softwareVersion} · uptime ${status.uptime}` : status?.lastError}</div></article><article className="rounded-xl border bg-white p-5"><div className="flex items-center gap-2 text-sm text-slate-500"><Activity className="h-4 w-4"/>Tài nguyên Router</div><div className="mt-2 text-xl font-semibold">{status?.cpuLoad != null ? `${status.cpuLoad}% CPU` : 'Chưa có số liệu'}</div><div className="mt-1 text-xs text-slate-500">{status?.interfacesUp ?? '—'} interface hoạt động · kiểm tra {status?.checkedAt ? new Date(status.checkedAt).toLocaleString('vi-VN') : 'chưa có'}</div></article></div>
      <div className="flex flex-wrap items-center justify-between gap-3"><label className="flex min-w-60 items-center gap-2 rounded-lg border bg-white px-3 py-2"><Search className="h-4 w-4 text-slate-400"/><input className="w-full outline-none" placeholder="Tìm thiết bị hoặc IP" value={searchInput} onChange={(e) => setSearchInput(e.target.value)}/></label><button onClick={() => setShowForm((v) => !v)} className="rounded-lg border bg-white px-4 py-2"><Plus className="mr-2 inline h-4 w-4"/>Thêm thiết bị</button></div>
      {showForm && <form onSubmit={addDevice} className="grid gap-3 rounded-xl border bg-white p-5 md:grid-cols-3">{[['name','Tên thiết bị'],['managementIp','IP quản trị'],['vendor','Hãng'],['model','Model'],['firmwareVersion','Phiên bản'],['site','Khu vực'],['location','Vị trí']].map(([key,label])=><label key={key} className="text-sm">{label}<input required={['name','managementIp'].includes(key)} className="mt-1 w-full rounded-lg border px-3 py-2" value={form[key]} onChange={(e)=>setForm({...form,[key]:e.target.value})}/></label>)}<label className="text-sm">Loại thiết bị<select className="mt-1 w-full rounded-lg border px-3 py-2" value={form.deviceType} onChange={(e)=>setForm({...form,deviceType:e.target.value})}>{Object.entries(typeNames).map(([key,value])=><option key={key} value={key}>{value}</option>)}</select></label><label className="text-sm md:col-span-2">Ghi chú<input className="mt-1 w-full rounded-lg border px-3 py-2" value={form.notes} onChange={(e)=>setForm({...form,notes:e.target.value})}/></label><div className="flex items-end gap-2"><button disabled={busy} className="rounded-lg bg-emerald-700 px-4 py-2 text-white">{editingId ? 'Cập nhật' : 'Lưu thiết bị'}</button><button type="button" onClick={()=>{setShowForm(false);setEditingId('');setForm(EMPTY_FORM);}} className="rounded-lg border px-4 py-2">Hủy</button></div></form>}
      <div className="overflow-x-auto rounded-xl border bg-white"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="p-3">Thiết bị</th><th className="p-3">Loại</th><th className="p-3">IP quản trị</th><th className="p-3">Hãng / model</th><th className="p-3">Firmware</th><th className="p-3">Vị trí</th><th className="p-3">Thao tác</th></tr></thead><tbody>{devices.map((device)=><tr className="border-t" key={device.id}><td className="p-3 font-semibold">{device.name}</td><td className="p-3">{typeNames[device.deviceType] || device.deviceType}</td><td className="p-3 font-mono">{device.managementIp}</td><td className="p-3">{[device.vendor,device.model].filter(Boolean).join(' · ') || '—'}</td><td className="p-3">{device.status?.softwareVersion || device.firmwareVersion || '—'}</td><td className="p-3">{device.site || device.location || '—'}</td><td className="whitespace-nowrap p-3"><button className="mr-3 text-emerald-700" onClick={()=>{setEditingId(device.id);setForm(Object.fromEntries(Object.keys(EMPTY_FORM).map((key)=>[key,device[key]||''])));setShowForm(true);}}>Sửa</button><button className="text-rose-700" onClick={async()=>{if(!window.confirm(`Ẩn thiết bị ${device.name}?`))return;try{await infrastructureApi.archiveDevice(device.id);await loadDevices(new AbortController().signal);}catch{toast.error('Không thể ẩn thiết bị.');}}}>Ẩn</button></td></tr>)}</tbody></table>{devices.length===0&&<p className="p-8 text-center text-sm text-slate-500"><Server className="mx-auto mb-2 h-6 w-6"/>Chưa có thiết bị trong danh mục.</p>}</div>
      <p className="flex items-start gap-2 text-xs text-slate-500"><ShieldAlert className="h-4 w-4 shrink-0"/>Switch Ruijie và AP hiện mới là danh mục thủ công; giám sát tự động MikroTik chỉ bật khi máy chủ được cấu hình HTTPS, tài khoản RouterOS chỉ đọc và truy cập được management IP.</p>
    </> : <section className="space-y-4 rounded-xl border bg-white p-5"><div><h2 className="font-semibold">Nhật ký mạng (tối đa 7 ngày)</h2><p className="mt-1 text-sm text-slate-500">Nguồn nhật ký cần được chuyển tới Alloy và lưu trong Loki.</p></div><div className="flex flex-wrap gap-2"><input className="min-w-52 flex-1 rounded-lg border px-3 py-2" placeholder="Tìm nội dung" value={logQuery} onChange={(e)=>setLogQuery(e.target.value)}/><input className="rounded-lg border px-3 py-2" placeholder="IP thiết bị" value={deviceIp} onChange={(e)=>setDeviceIp(e.target.value)}/><button disabled={busy} onClick={loadLogs} className="rounded-lg bg-emerald-700 px-4 py-2 text-white">Tìm nhật ký</button></div><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-xs uppercase"><tr><th className="p-3">Thời gian</th><th className="p-3">Thiết bị</th><th className="p-3">Mức</th><th className="p-3">Nội dung</th></tr></thead><tbody>{logs.map((log,index)=><tr key={`${log.timestamp}-${index}`} className="border-t"><td className="whitespace-nowrap p-3">{formatLogTime(log.timestamp)}</td><td className="p-3">{log.deviceIp || '—'}</td><td className="p-3">{log.severity}</td><td className="p-3">{log.message}</td></tr>)}</tbody></table></div>{logs.length===0&&<p className="py-6 text-center text-sm text-slate-500">Chưa có nhật ký phù hợp hoặc Loki chưa được cấu hình.</p>}</section>}
  </section>;
}
