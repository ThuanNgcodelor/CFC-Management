# Kiểm chứng HR UX Phase 0–3

`hr-phase03-browser.js` là hàm chạy bằng Playwright MCP, dùng dữ liệu giả lập trong browser context riêng. Toàn bộ API `/api/v1/` được intercept; các request ngoài local/API giả lập bị chặn. Không cần tài khoản thật hoặc kết nối database.

Khởi động frontend từ thư mục `frontend`:

```bash
npm run dev -- --host 127.0.0.1 --port 5175 --strictPort
```

Gọi tool `browser_run_code_unsafe` của Playwright MCP với `filename` là đường dẫn tuyệt đối tới `scripts/verification/hr-phase03-browser.js`. Đây là biểu thức hàm async dành cho tool, không phải script để chạy trực tiếp bằng Node. Kết quả thành công có `passed: 30`, `fixtureOnly: true`; nếu có `failure` thì ca chạy chưa đạt. Context tự đóng sau khi chạy.

Fixture gồm 65 nhân sự/biến động, 45 ứng viên với 7 đang thử việc, 25 kỳ danh sách; đổi số thử việc về 0 và kiểm tra ba vai trò trong cùng kịch bản. Các ca kiểm tra bao gồm trang vượt phạm vi, URL mở trực tiếp, back/forward/reload/cuộn, Lưu/Hủy, nguồn quay lại, số chứng từ đủ chuỗi và viewport mobile.

Chạy unit test frontend từ `frontend`:

```bash
node --test src/utils/*.test.js src/api/*.test.js
```

Chạy backend test liên quan từ `backend` sau khi đặt `JAVA_HOME` tới JDK 21:

```bash
./mvnw -o -Dtest=HrMovementSearchRepositoryTest,HrManagementServiceTest,HrActivityControllerTest,HrActivityQueryServiceTest,HrSecurityContractTest,HrPhase5WorkforceServiceTest,HrExcelExportServiceTest -Dsurefire.reportNameSuffix=hr-ux-phase03-local test
```

Suite mục tiêu có 39 test pass tại thời điểm bàn giao. Toàn bộ suite còn blocker ngoài phạm vi được ghi trong `documents/HR_REPORTING_UX_PHASES.md`; không dùng kết quả suite mục tiêu để khẳng định toàn bộ backend pass.
