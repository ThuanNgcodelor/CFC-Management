// eslint-disable-next-line no-unused-expressions -- MCP executes this isolated UI fixture.
async (page) => {
  const errors = []; const requests = []; let failOnce = true;
  page.on('pageerror', (error) => errors.push(error.message));
  await page.route('**/hr/health/reports/**', async (route) => {
    const request = route.request(); const body = request.postDataJSON(); requests.push({ path: request.url().split('/').at(-1), body });
    if (request.url().endsWith('/preview')) return route.fulfill({ json: { status: 200, data: { people: body.employeeId ? 1 : 2, peopleWithData: 1, visits: 4, results: 9, notes: ['Bản nội bộ giả lập; chưa công bố.', 'Thiếu năm không thay bằng 0.'], counts: [{ label: 'Lượt khám năm 2024', count: 2, denominator: 4, basis: 'Tổng lượt trong phạm vi' }] } } });
    if (failOnce) { failOnce = false; return route.fulfill({ status: 500, json: { status: 500, message: 'Lỗi xuất giả lập — bộ lọc được giữ nguyên.' } }); }
    const pdf = request.url().endsWith('/pdf');
    return route.fulfill({ contentType: pdf ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', path: `/tmp/health-phase5-fixtures/${pdf ? 'personal-fake.pdf' : 'collective-fake.xlsx'}` });
  });
  await page.goto('http://127.0.0.1:5195/tests/health-phase4.html?tab=records');
  await page.getByRole('textbox', { name: 'Tìm hồ sơ sức khỏe' }).fill('RECORDDEV'); await page.waitForTimeout(500);
  await page.getByLabel('Nhóm nhân sự hiện tại', { exact: true }).selectOption('OFFICE');
  await page.getByLabel('Sắp xếp hồ sơ sức khỏe').selectOption('codeDesc');
  await page.getByLabel('Báo cáo từ năm').fill('2023'); await page.getByLabel('Báo cáo đến năm').fill('2026');
  await page.getByRole('button', { name: 'Thống kê và xuất theo bộ lọc' }).click();
  await page.getByRole('button', { name: 'Tải Excel báo cáo' }).click();
  await page.getByText('Lỗi xuất giả lập — bộ lọc được giữ nguyên.', { exact: true }).waitFor();
  const excelPromise = page.waitForEvent('download'); await page.getByRole('button', { name: 'Tải Excel báo cáo' }).click();
  const excel = await excelPromise; if (!excel.suggestedFilename().endsWith('.xlsx') || await excel.failure()) throw new Error('Excel download failed');
  const collective = requests.find((r) => r.path === 'excel').body;
  if (collective.keyword !== 'RECORDDEV' || collective.group !== 'OFFICE' || collective.sort !== 'codeDesc' || collective.fromYear !== 2023 || collective.toYear !== 2026 || collective.employeeId || 'page' in collective) throw new Error('Export lost scope or exported only current page');
  await page.keyboard.press('Escape');
  if (await page.getByLabel('Sắp xếp hồ sơ sức khỏe').inputValue() !== 'codeDesc') throw new Error('Closing report lost filters');
  await page.getByRole('button', { name: 'Mở sổ sức khỏe' }).first().click();
  await page.getByRole('button', { name: 'Xuất báo cáo cá nhân', exact: true }).click();
  const pdfPromise = page.waitForEvent('download'); await page.getByRole('button', { name: 'Tải PDF cá nhân' }).click();
  const pdf = await pdfPromise; if (!pdf.suggestedFilename().endsWith('.pdf') || await pdf.failure()) throw new Error('PDF download failed');
  await page.setViewportSize({ width: 390, height: 844 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Report overflows mobile');
  await page.screenshot({ path: '/tmp/health-phase5-mobile-fixture.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.screenshot({ path: '/tmp/health-phase5-desktop-fixture.png', fullPage: true });
  await page.keyboard.press('Escape');
  await page.getByRole('checkbox', { name: 'Chọn lượt FAKE-0' }).click(); await page.locator('input[aria-label="Chọn lượt FAKE-0"]:checked').waitFor(); await page.getByRole('checkbox', { name: 'Chọn lượt FAKE-3' }).click(); await page.locator('input[aria-label="Chọn lượt FAKE-3"]:checked').waitFor();
  await page.getByRole('button', { name: 'Báo cáo các lượt đã chọn' }).click();
  await page.getByRole('button', { name: 'Tải Excel báo cáo' }).waitFor();
  if (requests.at(-1).body.visitIds.join(',') !== 'visit-0,visit-3' || !requests.at(-1).body.employeeId) throw new Error('Selected visits lost');
  await page.keyboard.press('Escape');
  if (await page.getByRole('checkbox', { checked: true }).count() !== 2) throw new Error('Closing export lost selected visits');
  if (errors.length) throw new Error(errors.join('; '));
  await page.unroute('**/hr/health/reports/**');
  return { passed: ['filtered collective scope', 'all pages exported', 'blob JSON error visible', 'download retry', 'Excel download', 'personal PDF download', '390px responsive', 'selected visits scope', 'ESC preserves context'], errors };
}
