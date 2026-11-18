// eslint-disable-next-line no-unused-expressions -- MCP executes against the test-only localhost backend.
async (page)=>{
  const root='http://127.0.0.1:18086/api/v1';
  // ADMIN is accepted only by the fake JwtUtils in the test-source launcher, never by the real app.
  const adminHeaders={Authorization:'Bearer ADMIN'};
  const campaigns=await (await page.request.get(`${root}/hr/health/campaigns`,{headers:adminHeaders})).json();
  const campaign=campaigns.data.content[0];
  const candidates=await (await page.request.get(`${root}/hr/health/publications/candidates?campaignId=${campaign.id}`,{headers:adminHeaders})).json();
  const own=candidates.data.content.find(p=>p.code==='PUBLICDEV01');const foreignPerson=candidates.data.content.find(p=>p.code==='PUBLICDEV02');
  const status=await (await page.request.get(`${root}/hr/health/personal-access/${own.employeeId}`,{headers:adminHeaders})).json();
  const issued=await (await page.request.post(`${root}/hr/health/personal-access/${own.employeeId}/issue`,{headers:adminHeaders,data:{generation:status.data.generation,reason:'Cấp mã pilot HTTP giả lập'}})).json();
  const release=own.visits[0].latest;
  const fixture={accessCode:issued.data.code,adminToken:'ADMIN',employeeId:own.employeeId,releaseId:release.id,releaseVersion:release.rowVersion,foreignReleaseId:foreignPerson.visits[0].latest.id};
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:5196/health-lookup');
  await page.getByRole('radio',{name:'Dùng mã tra cứu riêng',exact:true}).check();
  if(await page.getByLabel('Số CCCD',{exact:true}).count())throw new Error('Private mode has identity input');
  await page.getByLabel('Mã tra cứu riêng',{exact:true}).fill(fixture.accessCode);
  await page.getByRole('button',{name:'Tra cứu kết quả của tôi',exact:true}).click();
  await page.getByRole('heading',{name:'Sức khỏe của tôi',exact:true}).waitFor();
  await page.getByRole('checkbox').first().waitFor();
  if(await page.getByRole('checkbox').count()!==2)throw new Error('HTTP profile not owner-scoped');
  await page.getByRole('button',{name:'Xem kết quả 2025',exact:true}).first().click();
  await page.getByRole('heading',{name:'Chỉ tiêu giả lập',exact:true}).waitFor();
  const download=page.waitForEvent('download');await page.getByRole('button',{name:'Tải phiếu PDF của tôi',exact:true}).click();
  const file=await download;if(await file.failure()||!file.suggestedFilename().endsWith('.pdf'))throw new Error('HTTP PDF failed');
  await page.keyboard.press('Escape');
  await page.getByRole('checkbox').nth(0).check();await page.getByRole('checkbox').nth(1).check();
  await page.getByRole('button',{name:'So sánh 2 lượt đã chọn',exact:true}).click();
  await page.getByRole('cell',{name:/Kết luận giả lập <0.5/}).first().waitFor();
  await page.setViewportSize({width:390,height:844});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw new Error('HTTP mobile overflow');
  await page.screenshot({path:'/tmp/health-phase8-real-http-mobile.png',fullPage:true});
  await page.keyboard.press('Escape');await page.getByRole('button',{name:'Thoát tra cứu',exact:true}).click();
  await page.getByRole('heading',{name:'Tra cứu sổ sức khỏe',exact:true}).waitFor();
  const sessionResponse=await page.request.post(`${root}/health-self/session`,{data:{mode:'ACCESS_CODE',accessCode:fixture.accessCode}});
  if(sessionResponse.status()!==200)throw new Error('HTTP private session failed');
  const token=(await sessionResponse.json()).data.token;const headers={'X-Health-Session':token};
  const foreign=await page.request.get(`${root}/health-self/releases/${fixture.foreignReleaseId}`,{headers});if(foreign.status()!==404)throw new Error('HTTP IDOR not blocked');
  const releaseResponse=await page.request.get(`${root}/health-self/releases/${fixture.releaseId}`,{headers});if(releaseResponse.status()!==200||!releaseResponse.headers()['cache-control'].includes('no-store'))throw new Error('HTTP privacy header missing');
  const cors=await page.request.get(`${root}/health-self/capabilities`,{headers:{Origin:'https://untrusted.example.test'}});if(cors.status()!==403)throw new Error('Untrusted origin allowed');
  const revoked=await page.request.post(`${root}/hr/health/publications/${fixture.releaseId}/revoke`,{headers:{Authorization:`Bearer ${fixture.adminToken}`},data:{employeeId:fixture.employeeId,rowVersion:fixture.releaseVersion,reason:'Thu hồi pilot giả lập HTTP'}});
  if(revoked.status()!==200)throw new Error('HTTP admin revoke failed');
  if((await page.request.get(`${root}/health-self/releases/${fixture.releaseId}/pdf`,{headers})).status()!==404)throw new Error('HTTP revoked PDF remains accessible');
  await page.request.post(`${root}/health-self/logout`,{headers});
  if((await page.request.get(`${root}/health-self/profile?from=2025&to=2026`,{headers})).status()!==401)throw new Error('HTTP session survived logout');
  let limited=false;
  for(let i=0;i<24;i++){
    const response=await page.request.post(`${root}/health-self/session`,{headers:{'X-Forwarded-For':`203.0.113.${i+1}`},data:{mode:'ACCESS_CODE',accessCode:'Z'.repeat(32)}});
    if(response.status()===429){limited=true;break;}
  }
  if(!limited)throw new Error('HTTP limiter bypassed by forwarded header');
  const caches=await page.evaluate(async()=>{const names=await window.caches.keys();for(const name of names){const cache=await window.caches.open(name);for(const request of await cache.keys())if(request.url.includes('/api/v1/health-self/'))return false;}return true;});
  if(!caches)throw new Error('Private API cached');
  if(errors.length)throw new Error(errors.join('; '));
  return {passed:['actual UI + HTTP API','code-only request','owner-only history','published detail','actual PDF download','comparison','390px','IDOR','private response headers','untrusted CORS origin rejected','real admin revoke blocks PDF','logout invalidates token','forwarded-header spoof cannot bypass limiter','no private API in CacheStorage'],errors};
}
