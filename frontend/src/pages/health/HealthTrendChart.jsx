export default function HealthTrendChart({ row, visits }) {
  const values = row.cells.map((cell) => Number(cell.numeric));
  if (!row.numericComparable || values.some((value) => !Number.isFinite(value))) return null;
  const min = Math.min(...values); const max = Math.max(...values); const span = max - min || 1;
  const points = values.map((value, index) => ({ x: 55 + index * 490 / (values.length - 1), y: 150 - (value - min) / span * 115, value,
    label: visits.some((visit, other) => other !== index && visit.year === visits[index].year) ? visits[index].examinedOn : visits[index].year }));
  return <figure className="rounded-xl border border-slate-200 bg-white p-3"><figcaption className="font-semibold">{row.indicator.label} · {row.indicator.unit}</figcaption>
    <p className="text-xs text-slate-500">Biểu đồ số học theo lượt, không phân loại bình thường/bệnh lý. Khoảng tham chiếu xem riêng trong bảng.</p>
    <svg className="mt-3 w-full" viewBox="0 0 600 205" role="img" aria-label={`Xu hướng ${row.indicator.label}`}>
      <line x1="40" y1="165" x2="565" y2="165" stroke="#cbd5e1" /><polyline fill="none" stroke="#2563eb" strokeWidth="2" points={points.map((p) => `${p.x},${p.y}`).join(' ')} />
      {points.map((p, index) => <g key={visits[index].id}><circle cx={p.x} cy={p.y} r="4" fill="#2563eb" /><text x={p.x} y={p.y - 12} textAnchor="middle" fontSize="12">{p.value}</text><text x={p.x} y="185" textAnchor="middle" fontSize="12">{p.label}</text></g>)}
    </svg>
  </figure>;
}
