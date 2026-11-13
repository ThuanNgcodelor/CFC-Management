// eslint-disable-next-line no-unused-expressions -- MCP executes this isolated fixture.
async (page)=>{
  const errors=[];page.on('pageerror',(error)=>errors.push(error.message));await page.goto('http://127.0.0.1:5195/tests/health-phase7-access.html');
  await page.getByText('Chưa cấp mã.',{exact:true}).waitFor();
  if(await page.getByRole('button',{name:'Cấp mã tra cứu',exact:true}).isEnabled())throw new Error('Missing reason allowed');
  await page.getByLabel('Lý do quản lý mã tra cứu',{exact:true}).fill('Xác minh giả lập');await page.getByRole('button',{name:'Cấp mã tra cứu',exact:true}).click();
  await page.getByLabel('Mã tra cứu vừa cấp',{exact:true}).waitFor();
  if(await page.getByLabel('Mã tra cứu vừa cấp',{exact:true}).innerText()!=='X'.repeat(32))throw new Error('Issued code missing');
  await page.getByRole('button',{name:'Ẩn mã',exact:true}).click();
  if(await page.getByLabel('Mã tra cứu vừa cấp',{exact:true}).count())throw new Error('Code not cleared');
  await page.getByRole('button',{name:'Cấp lại mã tra cứu',exact:true}).click();await page.getByLabel('Mã tra cứu vừa cấp',{exact:true}).waitFor();
  const call=await page.evaluate(()=>window.healthAccessFixture.calls.at(-1));if(call.generation!=='fixture-generation')throw new Error('Optimistic version missing');
  await page.evaluate(()=>window.dispatchEvent(new Event('pagehide')));
  await page.getByLabel('Mã tra cứu vừa cấp',{exact:true}).waitFor({state:'detached'});
  await page.getByRole('button',{name:'Thu hồi mã tra cứu',exact:true}).click();await page.getByText('Mã đã bị thu hồi.',{exact:true}).waitFor();
  await page.setViewportSize({width:390,height:844});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw new Error('Admin access mobile overflow');
  await page.screenshot({path:'/tmp/health-phase7-admin-access.png',fullPage:true});
  if(errors.length)throw new Error(errors.join('; '));return {passed:['reason required','issue once-visible code','hide clears secret','rotate carries generation','pagehide clears secret','revoke changes state','390px responsive'],errors};
}
