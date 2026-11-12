import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { healthApi } from '../../api/healthApi';
import { useDebouncedHrSearchValue } from '../../hooks/useDebouncedHrSearchValue';
import { HrDrawer, HrLoading, HrError, HrEmpty, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import HealthEditor from './HealthEditor';
import { healthInput, healthError } from './healthImportUi';

export default function HealthCatalogPicker({ type, onSelect, onClose, allowClosed = false }) {
  const [keyword, setKeyword] = useState('');
  const [draft, setDraft] = useDebouncedHrSearchValue(keyword, (value) => { setKeyword(value); setPage(0); });
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const query = useQuery({ queryKey: ['health', type, 'picker', keyword, page],
    queryFn: ({ signal }) => healthApi.list(type, { keyword, page, size: 20 }, signal) });
  if (creating) return <HealthEditor type={type} item={null} onClose={() => setCreating(false)} onSaved={(item) => { onSelect(item); onClose(); }} />;
  return <HrDrawer isOpen onClose={onClose} title={type === 'campaigns' ? 'Chọn đợt khám' : 'Chọn chỉ tiêu'}>
    <div className="space-y-3 p-5">
      <input className={`${healthInput} w-full`} aria-label="Tìm trong danh mục" placeholder="Tìm mã hoặc tên" value={draft} maxLength={100} onChange={(event) => setDraft(event.target.value)} />
      <Button variant="secondary" onClick={() => setCreating(true)}>Tạo {type === 'campaigns' ? 'đợt khám' : 'chỉ tiêu'} mới</Button>
      {query.isPending ? <HrLoading /> : query.isError ? <HrError message={healthError(query.error)} onRetry={query.refetch} />
        : <>{query.data.content.length === 0 && <HrEmpty title="Chưa có mục phù hợp" />}
          {query.data.content.map((item) => <button key={item.id} type="button" className="block w-full rounded-lg border border-slate-200 p-3 text-left hover:bg-emerald-50"
            disabled={item.status === 'INACTIVE' || (item.status === 'CLOSED' && !allowClosed)} onClick={() => { onSelect(item); onClose(); }}>
            <strong>{item.code} — {item.name}</strong><span className="block text-xs text-slate-500">{type === 'campaigns' ? `${item.reportYear} · ${item.provider}` : `${item.category} · ${item.valueType}`}</span>
          </button>)}
          <HrPagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} loading={query.isFetching} onPageChange={setPage} />
        </>}
    </div>
  </HrDrawer>;
}
