import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import HealthSelfRecords from '../src/pages/health/HealthSelfRecords';
import '../src/index.css';

// Fictional UI-only data: no authentication, employee identifiers or live health endpoints are used.
const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0, refetchOnWindowFocus: false } } });
import { api } from './health-self-fixture';
export default function Fixture() {
  const [exited, setExited] = useState(false);
  return <QueryClientProvider client={client}>{exited ? <p>Đã thoát tra cứu giả lập</p> : <HealthSelfRecords api={api} onExit={() => { client.clear(); setExited(true); }} />}</QueryClientProvider>;
}
createRoot(document.getElementById('root')).render(<BrowserRouter><Fixture /></BrowserRouter>);
