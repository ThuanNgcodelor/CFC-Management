const root = `${import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1'}/health-self`;

// Deliberately independent of the CFCBase login/refresh interceptors and cookies.
export async function healthSelfRequest(path, { method = 'GET', body, token, signal, file = false } = {}) {
  const response = await fetch(`${root}${path}`, { method, signal, cache: 'no-store', credentials: 'omit', referrerPolicy: 'no-referrer',
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { 'X-Health-Session': token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  if (!response.ok) {
    let data;
    try { data = await response.json(); } catch { data = { message: 'Không thể tra cứu lúc này. Vui lòng thử lại.' }; }
    const error = new Error(data.message); error.status = response.status; error.response = { data }; throw error;
  }
  return file ? response.blob() : (await response.json()).data;
}
export const healthSelfApi = {
  capabilities: (signal) => healthSelfRequest('/capabilities', { signal }),
  lookup: (body, signal) => healthSelfRequest('/session', { method: 'POST', body, signal }),
  logout: (token) => healthSelfRequest('/logout', { method: 'POST', token }),
  forSession: (token) => ({
    profile: (params, signal) => healthSelfRequest(`/profile?${new URLSearchParams(params)}`, { token, signal }),
    detail: (id, params, signal) => healthSelfRequest(`/releases/${encodeURIComponent(id)}?${new URLSearchParams(params)}`, { token, signal }),
    compare: (body, signal) => healthSelfRequest('/compare', { method: 'POST', body, token, signal }),
    pdf: async (id, signal) => {
      const blob = await healthSelfRequest(`/releases/${encodeURIComponent(id)}/pdf`, { token, signal, file: true });
      if (signal.aborted) return;
      const url = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = url; link.download = 'So_suc_khoe_ca_nhan.pdf'; document.body.appendChild(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
  }),
};
