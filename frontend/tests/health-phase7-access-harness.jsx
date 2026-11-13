import React from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { baseApi } from '../src/api/baseApi';
import HealthPersonalAccessPanel from '../src/pages/health/HealthPersonalAccessPanel';
import '../src/index.css';

const state={allowIdentityOnly:false,issued:false,revoked:false,generation:null,calls:[]};window.healthAccessFixture=state;
baseApi.get=async ()=>({data:{data:{eligible:true,allowIdentityOnly:state.allowIdentityOnly,issued:state.issued,revoked:state.revoked,generation:state.generation,expiresAt:'2027-01-01T00:00:00'}}});
baseApi.post=async (path,body)=>{
  state.calls.push({path,generation:body.generation,reason:body.reason,allowIdentityOnly:body.allowIdentityOnly});
  if(path.endsWith('/issue')){state.issued=true;state.revoked=false;state.generation='fixture-generation';return {data:{data:{code:'X'.repeat(32),expiresAt:'2027-01-01T00:00:00'}}};}
  if(path.endsWith('/policy')){state.allowIdentityOnly=body.allowIdentityOnly;state.generation='fixture-policy';return {data:{data:null}};}
  state.revoked=true;state.generation='fixture-revoked';return {data:{data:null}};
};
const client=new QueryClient({defaultOptions:{queries:{retry:false,gcTime:0,staleTime:0,refetchOnWindowFocus:false}}});
createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><main className="mx-auto max-w-4xl p-4"><HealthPersonalAccessPanel employeeId="fictional-person" /></main></QueryClientProvider>);
