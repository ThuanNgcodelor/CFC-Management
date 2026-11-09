// Development-only harness: not imported by the application or its production build.
import React, { useEffect, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import { useHrListState } from '../src/hooks/useHrListState';
import { useDebouncedHrSearchValue } from '../src/hooks/useDebouncedHrSearchValue';
import HrProductionAttendance from '../src/pages/hr/HrProductionAttendance';
import '../src/index.css';

const defaults = { employeeKeyword: '', employeeCode: '', view: 'NEEDS_REVIEW', page: 0 };

export function Probe() {
  const { values, setValues, getValues } = useHrListState(defaults);
  const [draft, setDraft] = useDebouncedHrSearchValue(values.employeeKeyword,
    (value) => window.setTimeout(() => setValues({ employeeKeyword: value }, { replace: true }), 200));
  const firstWriter = useRef(setValues);
  useEffect(() => {
    window.__listProbe = { write: setValues, firstWriter: firstWriter.current, getValues, stable: firstWriter.current === setValues };
  });
  return <><input aria-label="Probe search" value={draft} onChange={(event) => setDraft(event.target.value)} /><output data-testid="probe-values">{JSON.stringify(values)}</output></>;
}

export function LocationProbe() {
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => { window.__attendanceNavigate = navigate; });
  return <output data-testid="route">{location.pathname}{location.search}</output>;
}

const initial = new URLSearchParams(window.location.search).get('scenario') === 'probe'
  ? '/probe' : '/manager/hr/attendance/production?month=2026-10&importId=import-oct&view=ALL';

createRoot(document.getElementById('test-root')).render(
  <HelmetProvider><MemoryRouter initialEntries={[initial]}><LocationProbe /><Routes>
    <Route path="/probe" element={<Probe />} />
    <Route path="/manager/hr/attendance/production" element={<HrProductionAttendance />} />
  </Routes></MemoryRouter></HelmetProvider>,
);
