import { useState } from 'react';
import { HrDrawer, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import { healthInput } from './healthImportUi';
import HealthClassificationCorrection from './HealthClassificationCorrection';
export default function HealthImportRowReview({ row, batch, saved, onSave, onClose, onCorrected }) {
  const [choices, setChoices] = useState(saved?.previewToken === row.previewToken ? saved.choices : {});
  const [reason, setReason] = useState(saved?.reason || '');
  const [page, setPage] = useState(0);
  const ready = row.cells.filter((cell) => cell.action === 'CONFLICT').every((cell) => choices[cell.key])
    && (!Object.values(choices).includes('REPLACE') || reason.trim());
  return <HrDrawer isOpen onClose={onClose} size="wide" title={`Đối chiếu — ${row.name}`} description={`Dòng ${row.sourceRow} · SID ${row.sid || 'Chưa có'} · Ngày ${row.examinedOn || 'Chưa xác nhận'}`}>
    <div className="space-y-4 p-5">
      <p className="text-sm text-slate-600">Ngày/năm sinh trong nguồn: {row.sourceBirth || 'Chưa có dữ liệu'}</p>
      {row.errors.length > 0 && <ul className="list-disc rounded-lg bg-red-50 p-4 pl-8 text-sm text-red-800">{row.errors.map((text, index) => <li key={index}>{text}</li>)}</ul>}
      {row.warnings.length > 0 && <ul className="list-disc rounded-lg bg-amber-50 p-4 pl-8 text-sm text-amber-900">{row.warnings.map((text, index) => <li key={index}>{text}</li>)}</ul>}
      <div className="max-w-full overflow-x-auto"><table className="w-full min-w-[560px] text-left text-sm"><thead><tr><th className="p-2">Chỉ tiêu / nguồn</th><th className="p-2">Đang lưu</th><th className="p-2">File mới</th><th className="p-2">Xử lý</th></tr></thead><tbody>
        {row.cells.slice(page * 20, page * 20 + 20).map((cell) => <tr key={cell.key} className={`border-t border-slate-200 ${cell.action === 'CONFLICT' ? 'bg-amber-50' : ''}`}>
          <td className="p-2">{cell.label}<span className="block text-xs text-slate-500">{cell.unit || 'Chưa có đơn vị'} · {cell.method || 'Chưa có phương pháp'}</span></td>
          <td className="max-w-48 whitespace-pre-wrap break-words p-2">{cell.existing ?? 'Chưa có'}<span className="block text-xs text-slate-500">{cell.existingReference}</span></td>
          <td className="max-w-48 whitespace-pre-wrap break-words p-2">{cell.incoming}<span className="block text-xs text-slate-500">{cell.incomingReference}</span></td>
          <td className="p-2">{cell.action === 'CONFLICT' ? <select className={healthInput} aria-label={`Xử lý ${cell.label}`} value={choices[cell.key] || ''} onChange={(event) => setChoices((current) => ({ ...current, [cell.key]: event.target.value }))}>
            <option value="">Chọn quyết định</option><option value="KEEP">Giữ đang lưu</option><option value="REPLACE">Thay bằng file mới</option>
          </select> : cell.action === 'ADD' ? 'Bổ sung mới' : 'Không thay đổi'}</td>
        </tr>)}
      </tbody></table></div>
      <HrPagination page={page} totalPages={Math.ceil(row.cells.length / 20)} totalElements={row.cells.length} onPageChange={setPage} />
      {Object.values(choices).includes('REPLACE') && <label className="block text-sm">Lý do thay kết quả<textarea className={`${healthInput} mt-1 w-full`} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label>}
      {row.corrections?.length > 0 && <div className="rounded-lg border border-slate-200 p-3 text-sm"><h3 className="mb-2 font-semibold">Lịch sử đính chính nguồn</h3>{row.corrections.map((item, index) => <p key={index} className="mb-2 whitespace-pre-wrap break-words">{item.label}: {item.original} → {item.corrected}. Lý do: {item.reason}</p>)}</div>}
      {batch && <HealthClassificationCorrection batch={batch} row={row} onCorrected={onCorrected} onClose={onClose} />}
      <Button disabled={!ready || row.errors.length > 0} onClick={() => { onSave({ rowId: row.id, previewToken: row.previewToken, choices, reason }); onClose(); }}>Lưu quyết định để xác nhận</Button>
    </div>
  </HrDrawer>;
}
