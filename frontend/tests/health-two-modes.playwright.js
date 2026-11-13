// eslint-disable-next-line no-unused-expressions -- Run through the local browser MCP.
async (page)=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:5195/tests/health-phase7.html');
  if(await page.getByLabel('Mã tra cứu riêng',{exact:true}).count())throw new Error('Basic mode requires secret');
  await page.getByRole('radio',{name:'Dùng mã tra cứu riêng',exact:true}).check();
  if(await page.getByLabel('Số CCCD',{exact:true}).count() || await page.getByLabel('Mã nhân viên',{exact:true}).count())throw new Error('Private mode still asks identity');
  await page.getByLabel('Mã tra cứu riêng',{exact:true}).fill('X'.repeat(32));
  await page.getByRole('radio',{name:'CCCD + mã nhân viên',exact:true}).check();
  if(await page.getByLabel('Mã tra cứu riêng',{exact:true}).count())throw new Error('Secret not hidden');
  await page.getByRole('radio',{name:'Dùng mã tra cứu riêng',exact:true}).check();if(await page.getByLabel('Mã tra cứu riêng',{exact:true}).inputValue())throw new Error('Secret retained on mode switch');
  await page.getByRole('radio',{name:'CCCD + mã nhân viên',exact:true}).check();
  await page.getByLabel('Số CCCD',{exact:true}).fill('000000000001');await page.getByLabel('Mã nhân viên',{exact:true}).fill('DEMO-SELF');
  await page.evaluate(()=>{window.healthSelfFixture.allowIdentityOnly=false;});await page.getByRole('button',{name:'Tra cứu kết quả của tôi',exact:true}).click();
  await page.getByText('Không thể xác minh thông tin hoặc phiên đã hết hạn.',{exact:true}).waitFor();
  await page.evaluate(()=>{window.healthSelfFixture.allowIdentityOnly=true;});await page.getByRole('button',{name:'Tra cứu kết quả của tôi',exact:true}).click();
  await page.getByRole('heading',{name:'Sức khỏe của tôi',exact:true}).waitFor();await page.getByRole('button',{name:'Thoát tra cứu',exact:true}).click();
  await page.getByRole('heading',{name:'Tra cứu sổ sức khỏe',exact:true}).waitFor();
  await page.setViewportSize({width:390,height:844});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw new Error('Mode picker mobile overflow');
  await page.screenshot({path:'/tmp/health-two-modes-login.png',fullPage:true});
  await page.goto('http://127.0.0.1:5195/tests/health-phase7-access.html');
  await page.getByLabel('Lý do quản lý mã tra cứu',{exact:true}).fill('Cho phép hai cách tra cứu giả lập');await page.getByRole('button',{name:'Cấp mã tra cứu',exact:true}).click();
  await page.getByRole('button',{name:'Cho phép CCCD + mã nhân viên',exact:true}).click();await page.getByText('Đang cho phép cả hai cách tra cứu.',{exact:true}).waitFor();
  const call=await page.evaluate(()=>window.healthAccessFixture.calls.at(-1));if(call.allowIdentityOnly!==true||!call.generation)throw new Error('Policy body missing generation or permission');
  if(await page.getByLabel('Mã tra cứu vừa cấp',{exact:true}).count())throw new Error('Policy mutation did not clear visible code');
  await page.getByRole('button',{name:'Chỉ cho phép dùng mã riêng',exact:true}).click();await page.getByText('Hiện chỉ cho tra cứu có mã riêng.',{exact:true}).waitFor();
  await page.screenshot({path:'/tmp/health-two-modes-admin.png',fullPage:true});
  if(errors.length)throw new Error(errors.join('; '));return {passed:['two visible choices','conditional required code','mode switch clears secret','basic denied without permission','basic accepted when allowed','390px picker','manager enable','generation protected policy','clear displayed secret','manager restrict'],errors};
}
