import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { healthImportApi } from '../../api/healthImportApi';
import { HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import HealthCatalogPicker from './HealthCatalogPicker';
import { healthInput, healthError } from './healthImportUi';

const FIELDS = [['', 'Chọn cách xử lý'], ['IGNORE', 'Bỏ qua'], ['NAME', 'Họ tên'], ['EMPLOYEE_CODE', 'Mã nhân viên'], ['BIRTH', 'Ngày/năm sinh'], ['SID', 'SID lượt khám'], ['EXAM_DATE', 'Ngày khám'], ['RESULT', 'Kết quả'], ['REFERENCE', 'Khoảng tham chiếu']];
const normalize = (value) => value.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase().trim();
function initialMapping(sheet) {
  const result = {};
  for (const column of sheet?.columns || []) {
    const knownIgnored = /^(stt|code|phone|gioi tinh|khoi|chuc vu|phong ban|don vi|don vi cong tac|ghi chu)$/.test(normalize(column.name));
    result[column.index] = { field: column.suggested !== 'IGNORE' ? column.suggested : knownIgnored ? 'IGNORE' : '', indicatorId: column.indicatorId || null, unit: '', method: '' };
  }
  return result;
}
export default function HealthImportMapping({ campaign, onStaged }) {
  const [picked, setPicked] = useState(null);
  const [settings, setSettings] = useState({ sheet: '', headerRow: '', headerRows: 1, sourceNamespace: '', defaultDate: '', dateFromSidConfirmed: false, noSidDateKeyConfirmed: false, profileName: '' });
  const [custom, setCustom] = useState({});
  const [acknowledged, setAcknowledged] = useState(false);
  const [columnPage, setColumnPage] = useState(0);
  const [profilePage, setProfilePage] = useState(0);
  const [picker, setPicker] = useState(null);
  const [labels, setLabels] = useState({});
  const inspect = useQuery({ queryKey: ['health', 'inspect', picked?.key, settings.sheet, settings.headerRow, settings.headerRows], enabled: Boolean(picked),
    queryFn: ({ signal }) => healthImportApi.inspect(picked.file, { sheet: settings.sheet || undefined, headerRow: settings.headerRow || undefined, headerRows: settings.headerRows }, signal) });
  const sheet = inspect.data?.sheets.find((item) => item.name === settings.sheet) || inspect.data?.sheets.find((item) => item.resultSheet) || inspect.data?.sheets[0];
  const profiles = useQuery({ queryKey: ['health', 'mapping-profiles', sheet?.fingerprint, profilePage], enabled: Boolean(sheet),
    queryFn: ({ signal }) => healthImportApi.profiles(sheet.fingerprint, signal, profilePage) });
  const mapping = { ...initialMapping(sheet), ...custom };
  const columns = sheet?.columns || [];
  const unmapped = columns.filter((column) => !mapping[column.index]?.field).length;
  const stage = useMutation({ mutationFn: healthImportApi.stage, onSuccess: onStaged });
  const update = (field, value) => {
    setSettings((current) => ({ ...current, [field]: value }));
    if (['sheet', 'headerRow', 'headerRows'].includes(field)) { setCustom({}); setAcknowledged(false); setColumnPage(0); setProfilePage(0); }
  };
  const map = (index, patch) => { setCustom((current) => ({ ...current, [index]: { ...mapping[index], ...patch } })); setAcknowledged(false); };
  const submit = (event) => {
    event.preventDefault();
    stage.mutate({ file: picked.file, request: { ...settings, campaignId: campaign.id, sheet: sheet.name,
      headerRow: Number(settings.headerRow || sheet.headerRow), headerRows: Number(settings.headerRows), defaultDate: settings.defaultDate || null,
      profileName: settings.profileName || null, columns: mapping } });
  };
  return <section className="rounded-xl border border-slate-200 bg-white p-4 sm:p-6">
    <h2 className="text-lg font-semibold">1. Chọn file và ánh xạ cột</h2>
    <p className="my-2 text-sm text-slate-500">Chỉ nhận Excel .xlsx tối đa 10 MB. Chọn rõ sheet ẩn nếu cần; dữ liệu chưa được lưu thành kết quả ở bước này.</p>
    <label className="block text-sm font-medium">File kết quả bệnh viện
      <input className="mt-2 block w-full text-sm" type="file" accept=".xlsx" disabled={stage.isPending} onChange={(event) => {
        const file = event.target.files?.[0]; setPicked(file ? { file, key: crypto.randomUUID() } : null);
        setSettings((current) => ({ ...current, sheet: '', headerRow: '', headerRows: 1, dateFromSidConfirmed: false, noSidDateKeyConfirmed: false })); setCustom({}); setAcknowledged(false); setColumnPage(0); setProfilePage(0);
      }} />
    </label>
    {inspect.isPending && picked && <HrLoading label="Đang kiểm tra cấu trúc Excel..." />}
    {inspect.isError && <HrError message={healthError(inspect.error)} onRetry={inspect.refetch} />}
    {sheet && !inspect.isError && <form className="mt-4 space-y-4" onSubmit={submit}>
      <fieldset disabled={stage.isPending || inspect.isFetching} className="grid gap-3 sm:grid-cols-2">
        <label className="text-sm">Sheet<select className={`${healthInput} mt-1 w-full`} value={sheet.name} onChange={(event) => { update('sheet', event.target.value); update('headerRow', ''); }}>
          {inspect.data.sheets.map((item) => <option key={item.name} value={item.name}>{item.name}{item.hidden ? ' (ẩn)' : ''} — {item.dataRows} dòng</option>)}
        </select></label>
        <label className="text-sm">Mã nguồn bệnh viện (dùng thống nhất cho các năm)<input className={`${healthInput} mt-1 w-full`} required maxLength={100} value={settings.sourceNamespace} onChange={(event) => update('sourceNamespace', event.target.value)} /></label>
        <label className="text-sm">Hàng bắt đầu tiêu đề<input className={`${healthInput} mt-1 w-full`} type="number" min={1} max={100} required value={settings.headerRow || sheet.headerRow} onChange={(event) => update('headerRow', Number(event.target.value))} /></label>
        <label className="text-sm">Số hàng tiêu đề<select className={`${healthInput} mt-1 w-full`} value={settings.headerRows} onChange={(event) => update('headerRows', Number(event.target.value))}>{[1, 2, 3].map((n) => <option key={n} value={n}>{n}</option>)}</select></label>
        <label className="text-sm">Ngày khám chung (chỉ điền khi đã biết)<input className={`${healthInput} mt-1 w-full`} type="date" value={settings.defaultDate} onChange={(event) => update('defaultDate', event.target.value)} /></label>
        <label className="text-sm">Tên mẫu ánh xạ mới (không bắt buộc)<input className={`${healthInput} mt-1 w-full`} maxLength={100} value={settings.profileName} onChange={(event) => update('profileName', event.target.value)} /></label>
        <label className="flex gap-2 text-sm sm:col-span-2"><input type="checkbox" checked={settings.dateFromSidConfirmed} onChange={(event) => update('dateFromSidConfirmed', event.target.checked)} />Tôi đã xác minh tiền tố SID là ngày khám và đồng ý dùng ngày đó khi cột ngày khám trống.</label>
        <label className="flex gap-2 text-sm sm:col-span-2"><input type="checkbox" checked={settings.noSidDateKeyConfirmed} onChange={(event) => update('noSidDateKeyConfirmed', event.target.checked)} />Với dòng thiếu SID, tôi xác nhận mỗi người chỉ có một lượt khám/ngày tại nguồn này và đồng ý dùng khóa người/ngày. Nếu có nhiều lượt cùng ngày, phải bổ sung mã lượt riêng vào file.</label>
        {profiles.data?.content.length > 0 && <label className="text-sm">Dùng mẫu cùng cấu trúc<select className={`${healthInput} mt-1 w-full`} defaultValue="" onChange={(event) => {
          const profile = profiles.data.content.find((item) => item.id === event.target.value); if (!profile) return;
          const saved = JSON.parse(profile.mappingJson); setCustom(saved.columns); update('sourceNamespace', saved.sourceNamespace); setAcknowledged(false);
        }}><option value="">Chọn mẫu đã lưu</option>{profiles.data.content.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>}
      </fieldset>
      {profiles.isError && <HrError message={healthError(profiles.error)} onRetry={profiles.refetch} />}
      {profiles.data?.totalPages > 1 && <HrPagination page={profilePage} totalPages={profiles.data.totalPages} totalElements={profiles.data.totalElements} loading={profiles.isFetching} onPageChange={setProfilePage} />}
      {!sheet.resultSheet && <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Sheet này có thể là bảng thống kê hoặc danh sách công việc. Hãy kiểm tra trước khi chọn.</p>}
      <p className="text-sm font-medium">{columns.length} cột · {unmapped} cột chưa chọn cách xử lý</p>
      <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[720px] text-left text-sm"><thead><tr><th className="p-2">Cột / Tên gốc</th><th className="p-2">Trường đích</th><th className="p-2">Chỉ tiêu / Đơn vị / Phương pháp</th></tr></thead><tbody>
        {columns.slice(columnPage * 20, columnPage * 20 + 20).map((column) => { const value = mapping[column.index]; return <tr key={column.index} className="border-t border-slate-100">
          <td className="p-2">{column.letter} · {column.name || '(Không có tiêu đề)'}</td>
          <td className="p-2"><select aria-label={`Trường đích cột ${column.letter}`} className={healthInput} disabled={stage.isPending} value={value.field} onChange={(event) => map(column.index, { field: event.target.value })}>
            {FIELDS.map(([id, text]) => <option key={id} value={id}>{text}</option>)}
          </select></td>
          <td className="space-y-2 p-2">{['RESULT', 'REFERENCE'].includes(value.field) && <>
            <Button type="button" size="sm" variant="secondary" disabled={stage.isPending} onClick={() => setPicker(column.index)}>{labels[value.indicatorId] || (value.indicatorId ? 'Đã chọn chỉ tiêu — Đổi' : 'Chọn / Tạo chỉ tiêu')}</Button>
            <input className={`${healthInput} block w-full`} aria-label={`Đơn vị cột ${column.letter}`} placeholder="Đơn vị (nếu nguồn có)" maxLength={100} value={value.unit || ''} disabled={stage.isPending} onChange={(event) => map(column.index, { unit: event.target.value })} />
            <input className={`${healthInput} block w-full`} aria-label={`Phương pháp cột ${column.letter}`} placeholder="Máy / phương pháp" maxLength={255} value={value.method || ''} disabled={stage.isPending} onChange={(event) => map(column.index, { method: event.target.value })} />
          </>}</td>
        </tr>; })}
      </tbody></table></div>
      <HrPagination page={columnPage} totalPages={Math.ceil(columns.length / 20)} totalElements={columns.length} loading={stage.isPending} onPageChange={setColumnPage} />
      <p className="text-xs text-slate-500">Khoảng tham chiếu phải gắn cùng chỉ tiêu, đơn vị và phương pháp với cột kết quả. Cột Code của các mẫu hiện tại là đơn vị, không phải mã nhân viên.</p>
      <label className="flex gap-2 text-sm"><input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />Tôi đã kiểm tra sheet, tiêu đề và các cột bỏ qua.</label>
      {stage.isError && <HrError message={healthError(stage.error)} />}
      <Button type="submit" disabled={!acknowledged || unmapped > 0 || stage.isPending || inspect.isFetching}>{stage.isPending ? 'Đang tạo lô...' : 'Tạo lô và xem trước'}</Button>
    </form>}
    {picker !== null && <HealthCatalogPicker type="indicators" onClose={() => setPicker(null)} onSelect={(item) => { map(picker, { indicatorId: item.id }); setLabels((current) => ({ ...current, [item.id]: item.name })); }} />}
  </section>;
}
