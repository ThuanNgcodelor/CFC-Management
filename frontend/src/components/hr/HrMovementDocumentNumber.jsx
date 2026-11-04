import { useState } from 'react';
import { defaultMovementSuffix, movementNumberParts } from '../../utils/hrMovementSearch';

const INPUT = 'mt-2 min-h-11 w-full rounded-lg border border-gray-300 px-3 font-normal outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100';

export function HrMovementDocumentNumber({ type, value, onChange }) {
  const parts = movementNumberParts(value);
  const [suffix, setSuffix] = useState(parts.suffix || defaultMovementSuffix());
  const [fullEntry, setFullEntry] = useState(!parts.split);
  const split = type === 'INCREASE' && !fullEntry && parts.split;
  const currentSuffix = parts.suffix || suffix;
  return <div className="text-sm font-semibold text-gray-900">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <span>Số quyết định / chứng từ</span>
      {type === 'INCREASE' && (parts.split || split) && <button type="button" onClick={() => setFullEntry((current) => !current)} className="text-xs font-medium text-blue-700 underline underline-offset-4">
        {split ? 'Nhập số đầy đủ' : 'Nhập theo phần'}
      </button>}
    </div>
    {split ? <div className="grid gap-3 sm:grid-cols-[110px_minmax(0,1fr)]">
      <label className="text-xs text-gray-600">Số đầu<input aria-label="Số đầu chứng từ" value={parts.number} inputMode="numeric" pattern="[0-9]+" maxLength="20" placeholder="50" className={INPUT} onChange={(event) => onChange(event.target.value ? `${event.target.value}${currentSuffix}` : '')} /></label>
      <label className="text-xs text-gray-600">Phần đuôi (sửa được)<input aria-label="Phần đuôi chứng từ" value={currentSuffix} maxLength="80" className={INPUT} onChange={(event) => {
        setSuffix(event.target.value);
        onChange(parts.number ? `${parts.number}${event.target.value}` : '');
      }} /></label>
    </div> : <input aria-label="Số chứng từ đầy đủ" value={value} onChange={(event) => onChange(event.target.value)} maxLength="100" placeholder={type === 'DECREASE' ? 'Không bắt buộc, tự nhập nếu cần' : 'Nhập nguyên số chứng từ'} className={INPUT} />}
    <p className="mt-2 break-words text-xs font-normal text-gray-500">{value ? `Xem trước: ${value}` : 'Chưa nhập số chứng từ. Không tự cấp hoặc tăng số.'}</p>
  </div>;
}
