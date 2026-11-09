# Rà soát tìm kiếm và bộ lọc Chấm công — 06/11/2026

Phạm vi: luồng tìm/lọc ở Ca sản xuất, Hành chính, Thử nghiệm và hai cách xem Thưởng ca đêm. Không thay đổi công thức công/thưởng hoặc dữ liệu. Rà soát source hiện tại, implementation React Router đang cài và kiểm tra MCP trên production với phiên ADMIN có sẵn; chỉ GET và thao tác tìm/lọc. Phần 06/11 bên dưới ghi nhận trạng thái trước sửa. Bản sửa local ngày 09/11 được ghi ở cuối tài liệu.

## Kết luận đã tái hiện

Ca sản xuất có race condition giữa URL, callback tải import và draft debounce. Phản hồi request cũ có thể thay từ khóa hoặc trạng thái lọc mới. Không phải lỗi đặc biệt của mã C628 hoặc số 8; phản hồi chậm làm lỗi dễ xuất hiện hơn.

| Thao tác | Điều kiện | Quan sát |
| --- | --- | --- |
| Điền C628 một lần | Mạng bình thường | Giá trị cuối C628, 7 request production attendance hoàn tất; có 2 lần replaceState dù import không đổi |
| Gõ C628, cách ký tự khoảng 420 ms | Mạng bình thường | Giá trị cuối C628, 28 request; mỗi từ khóa đã debounce lại kéo imports, summary, policies, rules, incidents, shifts và employee-summaries |
| Gõ C628 như trên | Giữ lại phản hồi GET imports đầu tiên thêm 1.200 ms tại trình duyệt rồi trả nguyên dữ liệu | URL đã tới employeeKeyword=C628, sau đó quay về employeeKeyword=C; React ghi lại ô nhập thành C, tổng 35 request hoàn tất trong cửa sổ quan sát |
| Chọn Sẵn sàng, sau khoảng 100 ms chọn Tất cả | Giữ lại một phản hồi GET imports thêm 1.000 ms tại trình duyệt | URL AUTO_MATCHED → ALL → AUTO_MATCHED, 21 request hoàn tất; lựa chọn sau bị phản hồi trước ghi đè |

Các số request là kết quả trong những lần đo cụ thể, không phải định mức cho mọi môi trường. Delay chỉ được thêm khi nhận phản hồi tại browser, không làm chậm hay sửa backend/database. Interceptor thử nghiệm đã được gỡ và trang thử đã đóng sau đo. Chưa tự tái hiện chuỗi chỉ còn riêng số 8; đã chứng minh cơ chế ghi lùi giá trị đủ để giải thích chữ/số mất hoặc nhảy.

## Chuỗi nguyên nhân trong source

1. `useHrListState` phụ thuộc `setSearchParams`. Implementation React Router hiện cài tạo callback setter theo `[navigate, searchParams]`; callback functional nhận snapshot searchParams của render chứa callback, không phải hàng đợi state React luôn lấy URL mới nhất.
2. Đổi keyword/view/page làm setter đổi identity; `setValues` → `setImportId` → `loadImports` → `loadBase` cũng đổi. Ba effect tải base/shifts/employee-summaries chạy lại. Vì thế tìm trên bảng nhân viên cũng tải thống kê tháng và cấu hình không liên quan.
3. `loadImports` luôn gọi `setImportId(...)` sau GET, kể cả import hợp lệ không đổi. Với closure cũ, thao tác tưởng chỉ giữ nguyên import lại xây toàn bộ query string từ URL cũ. Request trả sai thứ tự có thể bỏ keyword/view mới.
4. `useDebouncedHrSearchValue` có effect `setDraft(value)` mỗi khi value URL đổi. Khi URL bị ghi lùi, effect này ghi lùi ô đang nhập. Tăng debounce chỉ thay xác suất, không giải quyết lỗi trạng thái cũ.
5. `loadShifts`/`loadEmployeeSummaries` đã có sequence guard, nhưng nó chỉ bảo vệ kết quả hai danh sách. `loadImports` và `loadBase` chưa có guard tương đương, trong khi `loadImports` còn ghi URL. Guard hiện tại không chặn được chuỗi trên.

Điểm đọc source: `frontend/src/hooks/useHrListState.js:8`, `frontend/src/hooks/useDebouncedHrSearchValue.js:3`, `frontend/src/pages/hr/HrProductionAttendance.jsx:216`, `:260`, `:268`, `:286`, `:321`, `:355`.

Trang đã dùng useMemo/useCallback; lỗi là dependency và quyền cập nhật state, không thể kết luận thiếu memoization. Không còn timer refresh nền 30 giây trong source Ca sản xuất; vẫn có refresh khi tab trở lại visible, tạo thêm đường tải imports cần bảo vệ.

## Các màn khác — kết quả đọc source

| Màn | Kết quả |
| --- | --- |
| Ca sản xuất: hai ô tìm, filter, phân trang | Dùng chung URL setter/dependency chain nên cùng vùng ảnh hưởng; search và filter đã tái hiện như trên |
| Hành chính | Ô mã dùng URL trực tiếp; tổng hợp chạy bằng nút tải. Không cùng vòng auto loadImports. Tuy nhiên reload/preview/loadSummary chưa chống phản hồi cũ; kết quả có thể không còn khớp bộ lọc khi đổi nhanh. Đây là rủi ro đọc source, chưa tái hiện bằng delay ở production |
| Thử nghiệm | Từ khóa local state, lọc useMemo và phân trang; không dùng hook URL/debounce gây ghi lùi ở Ca sản xuất. Lọc nhân viên chạy mỗi lần gõ, chưa đo độ trễ với file lớn |
| Thưởng ca đêm theo tháng | Từ khóa local state, debounce 250 ms, lọc useMemo và phân trang; không dùng vòng URL/import ở Ca sản xuất |
| Thưởng ca đêm theo người | Tìm kiếm có debounce và AbortController, lịch sử đọc theo người/kỳ. Chưa thấy cùng đường ghi URL từ loadImports; chưa nghiệm thu toàn bộ tương tác/thay người với phản hồi chậm |

Không kết luận các màn ngoài Ca sản xuất hoàn toàn không có lỗi. Chưa audit lại toàn bộ import, matcher, duyệt/chốt hoặc công thức thưởng trong phạm vi chẩn đoán tìm kiếm này.

## Hướng sửa đề xuất

1. Tách nhóm dữ liệu: base chỉ theo tháng hoặc thao tác thay dữ liệu; shifts chỉ theo import + bộ lọc chi tiết + trang; employee-summaries chỉ theo import + từ khóa tổng quan + trang. Không để identity setter URL vô tình kích hoạt tải base.
2. Setter URL phải áp dụng lên trạng thái hiện tại, không snapshot cũ; no-op khi query/import không đổi. Khi nhiều cập nhật cùng tick, phải hợp nhất, không để hai setter làm mất thay đổi của nhau.
3. Không cho response import cũ đổi URL/selection; bảo vệ context tháng/import bằng guard phù hợp. Giữ sequence guard hiện hữu cho shifts/summaries tới khi có migration screen rõ ràng, không trộn cơ chế mới tùy tiện.
4. Draft đang nhập không bị acknowledgement cũ ghi đè; vẫn cần hỗ trợ Back/Forward và điều hướng ngoài. Chống callback/timer cũ, không chỉ tăng thời gian debounce hoặc thêm useMemo.
5. Kiểm thử hồi quy với response đảo thứ tự: C → C6 → C62 → C628, xóa/nhập lại, hai ô tìm, thay tháng/file, chọn người, filter/page và Back/Forward. Assert cả nội dung input, URL, kết quả và số request. Chỉ đổi keyword tổng quan không được gọi lại imports/summary/policies/rules/incidents hoặc shifts không liên quan.

Tại thời điểm audit 06/11 chưa sửa. Cập nhật triển khai 09/11 ở phần sau.

## Bản sửa local — 09/11/2026

- URL writer giữ identity ổn định, đọc search hiện tại, hợp nhất các patch trước render và bỏ qua no-op. Callback từ màn đã unmount không được navigate. Thêm getValues/functional updates để phần async đọc đúng tháng/file hiện tại, không phụ thuộc render cũ.
- Debounce vẫn 350 ms: dùng callback mới nhất, acknowledgement của giá trị đã commit không ghi đè draft mới hơn; Back/Forward hoặc giá trị ngoài thay đổi sẽ đồng bộ lại và hủy timer. Không thêm polling, cache hay thư viện.
- Ca sản xuất: thêm sequence guard cho imports/base theo tháng và cleanup; giữ guard shifts/summaries, vô hiệu hóa cả khi selection rỗng/unmount. Import hợp lệ không làm thay URL; fallback file mới đặt workflow rõ ràng. Đổi tháng bỏ chọn file cũ; không hiện card/import thuộc tháng trước.
- Khởi tạo/status workflow phân biệt đổi trạng thái của cùng file với điều hướng vào file/deep link, để không xóa search/page/view đã lưu. Đổi/xóa file qua thao tác chọn vẫn reset các bộ lọc chi tiết phù hợp.
- Refresh visibility lỗi chỉ báo lỗi, không giả lập danh sách rỗng rồi tự bỏ selection. Mutate/drawer không tải lại dữ liệu vào context đã thay đổi.

Regression harness dùng production component/hooks và MemoryRouter, API giả lập có response đảo thứ tự, không dùng dữ liệu/credential thật. 13 nhóm kiểm tra MCP đạt: C628, acknowledgement chậm, URL writer cũ, hai patch cùng tick, Back/Forward, callback sau unmount, hai ô tìm cùng chờ debounce, phân trang, filter nhanh, đổi file, deep link và đổi tháng khi request cũ chưa về. Hướng dẫn/tệp chạy ở `frontend/tests/README.md`.

Trong fixture tương ứng: điền C628 một lần chỉ gọi 1 employee-summaries request thay vì 7; gõ mỗi ký tự cách 420 ms gọi 4 employee-summaries request thay vì 28 request nhiều loại. Filter chi tiết chỉ gọi shifts; không kéo lại imports/summary/policies/rules/incidents. Chunk HrProductionAttendance từ khoảng 41,93 kB lên 43,49 kB do thêm guard, không thêm dependency hoặc chunk lớn. Đây là đo local/fixture, không phải benchmark production sau deploy.

Chưa deploy bản sửa này hoặc nghiệm thu lại với backend production sau deploy. Không thay schema/backend/công thức công/thưởng. Rủi ro tìm/lọc riêng của Hành chính nêu ở audit vẫn ngoài phạm vi sửa này; không coi các màn khác đã được nghiệm thu toàn bộ.

Kiểm tra cuối 09/11: 13 nhóm browser regression đạt; 7 tệp utility/API Node tests đạt; frontend build đạt, lint 0 lỗi và 10 cảnh báo cũ; backend Maven offline Java 21 chạy 261 tests, 0 failure/error, 4 skipped; git diff --check đạt. Đã refresh AST graph, không coi đây là tái trích xuất semantic tài liệu.
