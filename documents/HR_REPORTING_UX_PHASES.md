# Các phase triển khai hoàn thiện HR và báo cáo nhân sự

Ngày lập: **04/11/2026 — Asia/Ho_Chi_Minh**.  
Trạng thái: **Phase 0–3 đã triển khai và kiểm chứng local ngày 04/11/2026; chưa deploy**.  
Đặc tả nghiệp vụ: [HR_REPORTING_UX_PLAN.md](HR_REPORTING_UX_PLAN.md).

Tài liệu kế hoạch quy định yêu cầu và những điểm còn cần chốt. Tài liệu này quy định thứ tự thực hiện, đầu ra, checklist và điều kiện hoàn tất từng phase. Chia phase không có nghĩa các phương án đang đề xuất đã được duyệt.

## 1. Bảng theo dõi tổng thể

| Phase | Nội dung | Phụ thuộc | Trạng thái |
|---|---|---|---|
| 0 | Xác minh baseline và chuẩn bị dữ liệu kiểm thử | Không | Hoàn tất trong phạm vi Phase 1–3 |
| 1 | Dashboard, tên trạng thái, lối tìm hồ sơ | Baseline cần thiết của Phase 0 | Hoàn tất local |
| 2 | Giữ bộ lọc và ngữ cảnh quay lại ở các màn HR chính | Phase 0; dùng các đường dẫn Phase 1 nếu liên quan | Hoàn tất local |
| 3 | Tìm/lọc/sort Tăng–Giảm và nhập nhanh số chứng từ | Phase 2 có helper điều hướng | Hoàn tất local |
| 4 | Xử lý hồ sơ dừng tiếp nhận và lỗi chặn xóa | Phase 0, 2, 3; người dùng đã chốt vòng đời | Hoàn tất local |
| 5 | Hai báo cáo quân số/nghỉ việc, Excel và phân quyền | Phase 0, 2, 3; quy tắc đã được người dùng chốt | Đang triển khai local; backend/UI có bản đầu, còn nghiệm thu drill-down và dữ liệu thật |
| 6 | Giữ trạng thái trên các màn HR còn lại | Phase 2; Phase 3 cho các luồng liên quan | Hoàn tất local; Phase 5 báo cáo hoãn |
| 7 | Nghiệm thu tổng thể, tài liệu và demo | Các phase trong phạm vi bàn giao đã hoàn tất | Chưa nghiệm thu tổng thể; chưa demo nghiệp vụ/deploy |

Thứ tự ưu tiên: **0 → 1 → 2 → 3 → 4 → 5 → 6 → 7**. Nếu Phase 4 chưa chốt vòng đời, có thể tiếp tục Phase 5 và 6 ở những phần độc lập. Nếu quy tắc báo cáo chưa chốt, tiếp tục Phase 6. Không biến một câu hỏi nghiệp vụ chưa có đáp án thành lý do dừng toàn bộ công việc.

Không có phase lương, cổng sức khỏe, ONLYOFFICE hoặc Booking. Không đưa thời hạn cứng trước khi kiểm tra dữ liệu lịch sử và thiết kế vòng đời Dừng tiếp nhận.

## 2. Phase 0 — Baseline và dữ liệu kiểm thử

**Mục tiêu:** biết trạng thái hiện tại, phạm vi source, dữ liệu và kiểm thử trước khi thay đổi.

- [x] P0.1 Đọc quy định repository và các quyết định liên quan; dùng CodeGraph cho source hiện hành, Graphify cho quan hệ rộng khi cần.
- [x] P0.2 Ghi nhận git diff hiện có để giữ nguyên thay đổi của người dùng; xác định các chỉ số/source đã thay đổi từ lần khảo sát trước.
- [x] P0.3 Xác minh cách đếm employee/probation, quyền ADMIN/MANAGER/HR_STAFF và quy tắc tạo, xác nhận, hủy, điều chỉnh Tăng/Giảm.
- [x] P0.4 Kiểm tra môi trường frontend/backend, JAVA_HOME và bộ kiểm thử hiện có; ghi kết quả chạy thực tế hoặc blocker, không chép trạng thái cũ thành kết quả mới.
- [x] P0.5 Chuẩn bị fixture giả lập: người đang làm, đang thử việc, chờ tiếp nhận, đã nghỉ; nhiều hơn một trang biến động; bản điều chỉnh; nghỉ/tái tuyển và dữ liệu thiếu lịch sử.
- [x] P0.6 Lập danh sách các câu còn mở của kế hoạch, phân rõ câu nào chặn Phase 4 hoặc 5. Dashboard và sửa điều hướng đã rõ không cần đợi toàn bộ câu trả lời.

**Đầu ra:** baseline, fixture/tình huống kiểm thử tái lập được và checklist nghiệp vụ còn mở. Không sao chép thông tin định danh hay lý do sự việc của người thật vào fixture/tài liệu.

**Cổng hoàn tất:** có đủ baseline cho phần sắp sửa và biết kiểm thử nào có thể chạy; thiếu dữ liệu lịch sử báo cáo được ghi rõ để giải quyết ở Phase 5.1.

## 3. Phase 1 — Dashboard, cách gọi và tìm hồ sơ

**Mục tiêu:** Tổng quan hiển thị đúng người đang thử việc, trạng thái dễ hiểu và tìm được hồ sơ dù đã chuyển trạng thái.

- [x] P1.1 Bổ sung số người `IN_PROBATION` vào dữ liệu Tổng quan, giữ tương thích dữ liệu đếm hiện có nếu vẫn có caller sử dụng.
- [x] P1.2 Thay thẻ Bản nháp bằng Đang thử việc; số lấy từ API, không cố định 7.
- [x] P1.3 Giữ Tổng hồ sơ theo employee, không cộng ứng viên thử việc. Đổi Ngừng hoạt động thành Đã nghỉ việc trong đúng ngữ cảnh nhân sự.
- [x] P1.4 Đổi nút thành Thêm hồ sơ nhân sự; hồ sơ DRAFT thành Chờ tiếp nhận; biến động DRAFT thành Chờ xác nhận. Không đổi hàng loạt mọi trạng thái DRAFT trong các miền khác.
- [x] P1.5 Tạo đường bấm chỉ số tới danh sách có bộ lọc; thêm lối Chờ tiếp nhận dễ tìm, thống nhất cách trình bày với người dùng khi xem giao diện cụ thể.
- [x] P1.6 Thêm Tìm trong toàn bộ nhân sự/Tất cả trạng thái từ danh sách đang giới hạn; cho biết khi màn LĐ phổ thông đang giới hạn theo nhóm.
- [x] P1.7 Từ ứng viên đã chuyển hồ sơ, mở đúng hồ sơ nhân sự được liên kết. Sau xác nhận Tăng, giải thích hồ sơ đã chuyển sang Đang làm việc.
- [x] P1.8 Giữ quyền Thử việc hiện có: HR_STAFF thấy số trên Tổng quan nhưng không có đường bấm dẫn tới phân hệ không được phép; không tự cấp quyền quản lý ứng viên.

**Kiểm thử trọng tâm:** 0/N thử việc; ứng viên đã chuyển hồ sơ không bị đếm là Đang thử việc; DRAFT → ACTIVE vẫn tìm được ở Tất cả trạng thái; Tổng hồ sơ không đổi cách đếm do đổi nhãn; kiểm tra ba vai trò.

**Cổng hoàn tất:** số thẻ khớp truy vấn nguồn tại cùng lần tải; nhãn nhất quán; đường tìm hồ sơ hoạt động và không tạo lỗi 403 cho người dùng thao tác đúng quyền.

## 4. Phase 2 — Giữ bộ lọc và đường quay lại của HR chính

**Mục tiêu:** mở chi tiết/sửa rồi trở lại đúng ngữ cảnh, tạo nền dùng chung cho Tăng/Giảm và báo cáo.

- [x] P2.1 Tách helper/hook chuẩn hóa URL state: từ khóa đã áp dụng, filter, sort, page, size, tab và kỳ.
- [x] P2.2 Lưu đường quay lại nội bộ hợp lệ và vị trí cuộn theo mục lịch sử điều hướng; có fallback phù hợp khi mở chi tiết trực tiếp.
- [x] P2.3 Hoàn thiện Nhân sự: danh sách → chi tiết → sửa → Lưu/Hủy; hỗ trợ sửa trực tiếp từ danh sách.
- [x] P2.4 Áp dụng cho Thử việc, LĐ phổ thông, Danh sách tháng và chi tiết tháng.
- [x] P2.5 Áp dụng nền điều hướng cho Tăng/Giảm; các bộ lọc mới được tích hợp ở Phase 3.
- [x] P2.6 Khôi phục điều kiện đã áp dụng khi Back/Forward/reload; không ghi dữ liệu form nhạy cảm/file upload lên URL.
- [x] P2.7 Sau sửa/xóa, tải dữ liệu mới ở cùng bộ lọc, giải thích dòng không còn phù hợp và xử lý trang cuối bị rỗng.

**Kiểm thử trọng tâm:** ở trang 2 trở lên, chọn filter và sort, mở chi tiết/sửa rồi thử nút ứng dụng, Back/Forward, Lưu/Hủy, reload và mở URL trong tab mới. Kiểm tra quay về LĐ phổ thông/báo cáo thay vì luôn ép về danh sách Nhân sự.

**Cổng hoàn tất:** các hành trình màn chính giữ đúng filter/sort/page/tab/kỳ và cuộn sau khi tải; không phục hồi dữ liệu cũ làm người dùng vô tình lặp thao tác ghi.

## 5. Phase 3 — Tra cứu Tăng/Giảm và nhập nhanh số chứng từ

**Mục tiêu:** lọc/sort toàn bộ dữ liệu và giảm thao tác gõ số chứng từ.

### 3A. Tra cứu trên toàn bộ dữ liệu

- [x] P3.1 Bổ sung query backend cho mã/tên, số quyết định, loại, trạng thái, khoảng ngày hiệu lực, nhóm và đơn vị.
- [x] P3.2 Đặt whitelist sort, khóa phụ ổn định, size có giới hạn; filter/sort trước phân trang. Tổng bản ghi dùng cùng điều kiện.
- [x] P3.3 Thay sort chỉ trên 20 dòng bằng sort từ API; đồng bộ với URL state của Phase 2.
- [x] P3.4 Tách nghĩa Người xử lý và Thời điểm xử lý; phân biệt biến động gốc, điều chỉnh và bản đã bị thay thế.
- [x] P3.5 Tìm kiếm có debounce hoặc Áp dụng; xóa lựa chọn hàng loạt khi thay điều kiện; không thay luồng xác nhận/hủy/audit.

### 3B. Số chứng từ đã được chốt

- [x] P3.6 Tăng điền sẵn phần đuôi mẫu `/HĐLĐ-PBHC/2026`; người dùng nhập số đầu.
- [x] P3.7 Cho sửa cả số đầu, ký hiệu, đơn vị và năm; có xem trước chuỗi đầy đủ, nhập nguyên số và đọc được số lịch sử không theo mẫu.
- [x] P3.8 Giảm mặc định trống và không bắt buộc; có thể nhập nguyên số khi cần. Chuyển loại không rò mẫu Tăng sang Giảm hoặc âm thầm ghi đè nội dung đã sửa.
- [x] P3.9 Không tự tăng/cấp số. Không khóa năm 2026; cách gợi ý năm chỉ là tiện ích có thể chỉnh, không đặt kiểm tra nghiệp vụ mới khi chưa chốt.
- [x] P3.10 Lưu/tìm kiếm/xem chi tiết/xuất Excel thống nhất số đầy đủ. Nếu thêm cấu hình mẫu, cập nhật CONFIG_AND_CACHE trong cùng thay đổi.

**Kiểm thử trọng tâm:** hơn một trang, sort và giá trị trùng; mã/tên/số chứng từ; 50/51/52 với phần đuôi có thể sửa; Giảm để trống; đổi loại; số lịch sử; lọc rồi quay lại đúng trang.

**Cổng hoàn tất:** thứ tự đúng giữa mọi trang và số chứng từ được xem trước/lưu đúng; không có cơ chế cấp số mới ngoài phạm vi.

## 6. Phase 4 — Dừng tiếp nhận và hồ sơ bị chặn xóa

**Mục tiêu:** hồ sơ không tiếp tục tuyển có cách kết thúc tiếp nhận và được tra cứu lại, bảo toàn các liên kết nghiệp vụ.

### 4A. Chẩn đoán và chốt thiết kế

- [x] P4.1 API chỉ đọc trả số lượng tham chiếu theo ứng viên nguồn, biến động, Tăng chờ, danh sách tháng, dòng import và nguồn import; chỉ ADMIN/MANAGER truy cập.
- [x] P4.2 Người dùng chốt `INTAKE_STOPPED` (“Dừng tiếp nhận”), giữ trong tổng hồ sơ, không tính đang làm/đã nghỉ; ADMIN/MANAGER được dừng và khôi phục.
- [x] P4.3 Màn hồ sơ cho biết liên kết và Tăng chờ; nếu có tham chiếu thì vô hiệu hóa xóa, hướng sang Dừng tiếp nhận.
- [x] P4.4 Migration V30 bổ sung trạng thái cho employee và snapshot tháng; endpoint cũ giữ nguyên và Tăng vẫn chỉ áp dụng cho DRAFT.

**Cổng trước 4B:** người dùng chốt thiết kế vòng đời và ảnh hưởng chỉ số; có phương án migration, backward compatibility và kiểm thử. Có thể hoàn thiện 4A trước khi chốt 4B.

### 4B. Thực thi nghiệp vụ sau khi chốt

- [x] P4.5 ADMIN/MANAGER dừng/khôi phục có rowVersion và lý do tùy chọn; xác nhận trước khi dừng nêu số Tăng sẽ hủy; audit ghi lại hành động.
- [x] P4.6 Dừng và hủy Tăng chờ trong cùng transaction; xác nhận Tăng khóa theo thứ tự hồ sơ rồi biến động để tránh race hai tab.
- [x] P4.7 Người dừng được lọc riêng; không nằm trong active/inactive; khôi phục về Chờ tiếp nhận, không hồi sinh Tăng đã hủy.
- [x] P4.8 Hồ sơ chỉ có thể dùng Giảm khi đã ACTIVE; việc dừng hồ sơ DRAFT không tạo biến động nghỉ việc.
- [x] P4.9 Xóa vẫn chỉ dành cho hồ sơ không có tham chiếu; thao tác dừng giữ nguyên toàn bộ liên kết.

**Kiểm thử trọng tâm:** chỉ Tăng chờ, có dữ liệu đã xác nhận, referential integrity, audit, quyền, stale rowVersion, hai tab dừng/xác nhận Tăng đồng thời, khôi phục và đếm lại dashboard.

**Cổng hoàn tất:** có cách kết thúc tiếp nhận hợp lệ, không tăng số Đã nghỉ việc hoặc số lượt Giảm chỉ do lưu trữ người chưa được tiếp nhận.

## 7. Phase 5 — Báo cáo quân số và nghỉ việc

### Quy tắc người dùng đã chốt ngày 04/11/2026

- Mốc cuối kỳ theo trạng thái hiệu lực ngày cuối kỳ; đầu kỳ là trạng thái trước ngày đầu kỳ.
- Nghỉ rồi tái tuyển được tính theo cả lượt nghỉ và số người riêng.
- Tình trạng hợp đồng xét theo hợp đồng có hiệu lực tại ngày báo cáo.
- Lịch sử đổi nhóm chưa được lưu nên tạm dùng nhóm hiện tại; giao diện phải nói rõ đây không phải nhóm lịch sử.
- Không đưa lương vào báo cáo.

**Mục tiêu:** chỉ số có thể truy vết tới danh sách nguồn, lọc theo hai tiêu chí độc lập và xuất Excel đúng phạm vi.

### 5A. Quy tắc và chất lượng dữ liệu lịch sử

- [x] P5.1 Chốt mốc đầu/cuối kỳ, ngày hiệu lực nghỉ, cách đếm người/lượt với nghỉ rồi tái tuyển.
- [x] P5.2 Chốt phân loại nhóm và hợp đồng độc lập; hợp đồng xét theo hiệu lực ngày báo cáo.
- [x] P5.3 Xác định baseline, movement điều chỉnh/thay thế và giới hạn nhóm hiện tại; kỳ trước baseline báo không có dữ liệu.
- [x] P5.4 Ghi rõ giới hạn lịch sử nhóm; không suy ngược nhóm cũ từ thông tin hiện tại.

**Cổng trước 5B:** định nghĩa chỉ số được chốt và có fixture chứng minh đối chiếu đúng; khoảng dữ liệu không đủ phải hiển thị giới hạn rõ ràng.

### 5B. API, đối chiếu và quyền đọc

- [x] P5.5 Dùng phép chiếu roster hiện có cho đầu/cuối kỳ; aggregate tối đa 12 tháng và lọc hợp đồng theo truy vấn hàng loạt.
- [x] P5.6 Chỉ lấy movement đã xác nhận, loại movement bị thay thế kể cả khi bản thay thế sang kỳ khác; nghỉ/tái tuyển tính theo lượt và người.
- [ ] P5.7 Đã có số tăng/giảm, chuyển đến/đi theo đơn vị; còn cần đối chiếu các lát cắt hợp đồng và đơn vị với fixture nghiệp vụ trước khi hoàn tất.
- [ ] P5.8 API danh sách phân trang (size tối đa 100), aggregate một năm; không thêm cache. Còn cần giới hạn truy vấn timeline movement ở DB thay vì nạp toàn timeline để hậu xử lý.
- [x] P5.9 GET `/api/v1/hr/reports/**` được mở cho HR_STAFF cùng ADMIN/MANAGER; các phương thức ghi vẫn theo fallback quản lý. Kiểm thử phân quyền tích hợp vẫn cần chạy.

### 5C. Màn báo cáo và xuất Excel

- [x] P5.10 Có mục Báo cáo nhân sự với hai tab; dùng URL state và hủy request cũ bằng `AbortController` (repository chưa cài TanStack Query).
- [x] P5.11 Có lọc năm/tháng/nhóm/hợp đồng/đơn vị/lý do, biểu đồ quân số cuối kỳ và bảng tách người/lượt.
- [x] P5.12 Bấm tăng/giảm/chuyển đến/chuyển đi mở danh sách nguồn theo đúng tháng và bộ lọc; số lượt và số người nghỉ mở các chế độ danh sách tương ứng; mở hồ sơ giữ URL quay lại.
- [x] P5.13 Excel lấy toàn bộ trang theo bộ lọc UI và ghi kỳ, thời điểm xuất; chưa có test đối chiếu workbook với tập dữ liệu nghiệp vụ.
- [ ] P5.14 Responsive cơ bản, loading/error/empty và hủy request đã có; cần Playwright màn nhỏ và đo truy vấn/render trước khi nghiệm thu.
- [x] P5.15 Chỉ movement Tăng/Giảm/Tái tuyển đã xác nhận vào báo cáo; hồ sơ Dừng tiếp nhận không được xem là nghỉ việc.

**Kiểm thử trọng tâm:** ngày biên tháng/năm; nháp/hủy không vào số chính thức; điều chỉnh không đếm chồng; nghỉ/tái tuyển; chuyển đơn vị; hợp đồng đổi sau kỳ; thiếu baseline; tổng khớp drill-down và Excel; 401/403 và quyền đọc HR_STAFF.

**Trạng thái:** bản đầu đã có trong working tree, chưa nghiệm thu Phase 5. Còn thiếu đối chiếu workbook bằng fixture, kiểm thử giao diện với backend/dữ liệu đại diện và tối ưu truy vấn timeline lớn.

## 8. Phase 6 — Giữ trạng thái trên các màn HR còn lại

**Mục tiêu:** hoàn tất yêu cầu điều hướng trên toàn phạm vi HR, dùng nền Phase 2 và giới hạn thay đổi ở phần điều hướng.

- [x] P6.1 Kiểm kê Chấm công hành chính, Thưởng ca đêm, Nhập dữ liệu, Nâng lương, Danh mục và Nhật ký; Payroll ngoài phạm vi.
- [x] P6.2 Lưu URL cho bộ lọc/kỳ Chấm công hành chính và Thưởng ca đêm; tab Nâng lương; trang Nhật ký; loại/bộ lọc/trang Danh mục; trang/file đã chọn/trang xem trước Nhập dữ liệu.
- [x] P6.3 Giữ sequence guard sẵn có ở Thưởng ca đêm và Chấm công sản xuất; không sửa công thức tính.
- [x] P6.4 File upload, form và kết quả xem trước/đối soát không ghi vào URL hay tự khôi phục sau reload.
- [x] P6.5 Đồng bộ URL state Chấm công sản xuất (tháng, file, tab, nhân viên, trang và tìm kiếm tổng quan) và Telegram (chế độ, trạng thái, từ khóa, trang); giữ file upload/form tại local state. Kiểm tra build/lint và thử giao diện chi tiết nhân sự ở desktop/mobile qua MCP.

**Cổng hoàn tất:** trạng thái URL các màn trong phạm vi giữ lại được qua tải lại và trình duyệt; không coi đã sửa toàn bộ HR khi chỉ hoàn tất các màn trong checklist này. Luồng lương tiếp tục nằm ngoài phạm vi.

## 9. Phase 7 — Nghiệm thu, tài liệu và demo

- [x] P7.1 Đối chiếu yêu cầu đã chốt, ghi trạng thái Phase 5 đang có bản đầu và liệt kê cổng nghiệm thu còn mở.
- [x] P7.2 Frontend build/lint/unit tests và backend test mục tiêu chạy ở bản mã cuối.
- [x] P7.3 Ghi rõ backend suite mục tiêu không thay cho toàn bộ suite; V30 chỉ xác minh qua H2, chưa chạy MySQL thật.
- [x] P7.4 Nghiệm thu MCP với fixture giả lập; có kiểm tra 30 tình huống Phase 0–3 và ảnh hồ sơ Phase 4 ở desktop/mobile, không dùng fixture làm số liệu thật.
- [x] P7.5 Cập nhật Master Context, Decisions, checklist và refresh graph AST. Không thay đổi cấu hình/cache; tầng semantic Graphify chưa được tái trích xuất.
- [ ] P7.6 Bộ fixture Playwright có dữ liệu mô phỏng và hướng dẫn chạy; kịch bản trình diễn nghiệp vụ ba ca đúng/sai lệch/thiếu chưa được chuẩn bị hoàn chỉnh.
- [ ] P7.7 Quay video 1–3 phút và đo thời gian làm cùng báo cáo thủ công/bằng hệ thống; số lợi ích phải dựa trên đo hoặc ghi rõ là ước lượng.
- [ ] P7.8 Ghi kết quả local/staging, giới hạn còn lại và cách rollback thay đổi có migration; không ghi đã deploy nếu chưa thực hiện.

**Cổng hoàn tất:** bản bàn giao có bằng chứng kiểm thử, báo cáo khớp nguồn, quyền đúng, tài liệu hiện hành và các giới hạn được nêu rõ.

## 10. Mẫu cập nhật sau từng phase

```text
Phase / subphase:
Trạng thái: Chưa bắt đầu / Đang làm / Chờ chốt nghiệp vụ / Hoàn tất
Đầu việc đã hoàn thành:
Kiểm thử đã chạy và kết quả:
Đối chiếu số liệu / bằng chứng UI:
Mã hoặc tài liệu đã thay đổi:
Giới hạn hoặc câu cần chốt:
Phase có thể tiếp tục:
```

Không đưa dữ liệu nhận dạng, lý do sự việc của người thật, token hoặc thông tin đăng nhập vào phần bằng chứng. Khi bàn giao một phần, chỉ đánh dấu các task thực sự đã nghiệm thu.

## 11. Bàn giao local Phase 0–4, 6 và nghiệm thu — 04/11/2026

Các thay đổi trong tài liệu này đã được triển khai cục bộ, **chưa deploy**. Migration V30 được kiểm tra qua H2; chưa chạy trên MySQL production. Các báo cáo chỉ đọc dữ liệu và chưa được đối chiếu trên DB sản xuất. Phase 5 có bản đầu; chưa nghiệm thu hết các cổng đã liệt kê.

Playwright MCP kiểm tra 30 kịch bản Phase 0–3 bằng API fixture giả lập; ảnh chụp detail Phase 4 dùng hồ sơ mô phỏng có 1 ứng viên nguồn, 2 biến động, trong đó 1 Tăng chờ: [desktop](../.playwright-mcp/page-2026-11-04T06-23-13-321Z.png), [mobile 390px](../.playwright-mcp/page-2026-11-04T06-23-29-511Z.png). Màn hình mobile không tràn ngang. Ảnh chụp không phản ánh dữ liệu production. Backend mục tiêu: 16 test pass; frontend build/unit tests pass, lint 0 lỗi và 10 cảnh báo có sẵn. Không khẳng định đã nghiệm thu toàn bộ hệ thống HR hoặc triển khai production.

### Kết quả Phase 5 ngày 04/11/2026

- Đã thêm `/manager/hr/reports` với hai tab, biểu đồ quân số cuối kỳ, danh sách nghỉ việc, bộ lọc và URL state; drill-down tăng/giảm/chuyển, phân biệt số lượt và số người nghỉ.
- API GET báo cáo và xuất Excel cho ADMIN/MANAGER/HR_STAFF. Kiểm thử ma trận hiện tại xác nhận HR_STAFF đọc được đường dẫn báo cáo và POST bị từ chối.
- `JAVA_HOME=/home/kali/.local/share/jdk-21 ./mvnw -o -Dtest=HrReportServiceTest,HrSecurityContractTest test`: **13 test pass**. Service test kiểm tra baseline thiếu, movement bị thay thế và drill-down; security contract hiện 11 test pass.
- `npm run build`: pass; chunk mới `HrReports` khoảng 11 kB. Cảnh báo chunk Excel 863 kB đã có từ trước.
- `npm run lint`: 0 lỗi, 10 cảnh báo hiện hữu; không phát sinh cảnh báo mới từ màn báo cáo.
- Toàn bộ backend suite: **246 test, 3 failure, 2 error, 2 skipped**. Ba failure là kỳ vọng cũ số migration (29/28 nhưng hiện là 30/29); hai error do thiếu fixture `../CongXn.xlsx`. Không có failure trong `HrReportServiceTest` hoặc security contract; các mục được ghi tại `TECH_DEBT.md` 17b/17h.
- Chưa chạy Playwright cho màn báo cáo, chưa đối chiếu workbook với dữ liệu nghiệp vụ và chưa kiểm tra MySQL. Query projection vẫn tải timeline confirmed rộng; theo dõi tại `TECH_DEBT.md` mục 17g.

### Kết quả chức năng Phase 0–4, 6

- Tổng quan lấy số `IN_PROBATION` từ repository ứng viên, giữ các trường đếm employee cũ. Có kiểm thử 7/0; số 7 quan sát lúc khảo sát không được viết cố định vào ứng dụng. HR_STAFF thấy số và không có liên kết vào Thử việc ngoài quyền.
- Thẻ chỉ số dẫn đến danh sách đúng trạng thái; Chờ tiếp nhận có liên kết riêng. Danh sách nhân sự có đường tìm ở tất cả trạng thái/đơn vị; LĐ phổ thông ghi rõ giới hạn nhóm. Ứng viên đã chuyển hồ sơ mở được đúng employee liên kết. Thông báo xác nhận Tăng giải thích người đó đã sang Đang làm việc.
- Nhân sự, Thử việc/mẫu công việc, LĐ phổ thông, Danh sách tháng/chi tiết và Tăng/Giảm giữ bộ lọc đã áp dụng, trang, tab/kỳ hiện có và nguồn quay lại. URL trực tiếp có fallback nội bộ; sau tải lại dữ liệu khôi phục vị trí cuộn. Trang vượt phạm vi chuyển về trang cuối còn dữ liệu và thông báo. Dữ liệu form/file không ghi vào URL.
- Tăng/Giảm tìm mã/tên/số chứng từ; lọc loại, trạng thái, ngày hiệu lực, nhóm hiện tại, đơn vị nguồn/đích (fallback đơn vị hiện tại khi biến động không có cả hai), bản gốc/điều chỉnh/đã thay thế. **Đây là tra cứu biến động, không phải báo cáo phân loại nhân sự lịch sử.**
- Sort tại DB trước phân trang, khóa phụ `id`, giới hạn 50 dòng; cột Người xử lý và Thời điểm xử lý riêng, sort người theo tên đã phân giải. Bộ lọc dùng nút Áp dụng, xóa lựa chọn hàng loạt khi đổi điều kiện. Giữ cơ chế xác nhận/hủy/audit/rowVersion hiện hữu.
- Tăng gợi ý đuôi theo năm hiện tại của trình duyệt; số đầu và mọi phần đuôi đều sửa được, có nhập nguyên số và xem trước. Giảm để trống hợp lệ; đổi loại hỏi trước khi bỏ nội dung đã nhập. Không cấp số tự động. Excel Tăng dùng số chứng từ đã nhập, fallback số hợp đồng ở bản ghi cũ chưa có số chứng từ; không ghi ngược vào hợp đồng.

### Bằng chứng kiểm thử

| Kiểm tra | Kết quả |
|---|---|
| `npm run build` | Pass; Tăng/Giảm 35.62 → 41.04 kB, Nhân sự 12.41 → 12.28 kB, Tổng quan 5.11 → 5.79 kB; các chunk được sửa dưới 500 kB |
| `npm run lint` | Pass, 0 lỗi và đúng 10 cảnh báo cũ |
| `node --test src/utils/*.test.js src/api/*.test.js` | 7 file test pass, gồm URL state/returnTo, quyền và các helper hiện hữu |
| Backend kiểm thử mục tiêu | 39 test pass: truy vấn DB, API bind ngày/bộ lọc, overview, quyền, nghiệp vụ Tăng/Giảm hiện hữu, workbook Excel và fallback số lịch sử |
| Playwright MCP local | **30 kiểm tra pass**, 150 request giả lập, 0 lỗi runtime; ADMIN/MANAGER/HR_STAFF, 0/7 thử việc, danh sách nhiều trang, Back/Forward/reload/Lưu/Hủy, cuộn, nguồn quay lại, tạo payload chứng từ đầy đủ, mobile 390 px không tràn ngang |
| SQL trên H2 fixture | Trang 20 biến động: phát hiện 82 statement do inverse one-to-one employee, sửa entity graph còn **2** (list + count); có test chống N+1. Chưa đo latency/EXPLAIN trên MySQL dữ liệu thực tế; không thêm index |
| Toàn bộ backend suite bản cuối | **254 test, 0 assertion failure, 3 error, 3 skipped**; suite chưa pass. Hai error thiếu `../CongXn.xlsx`; một error `UnnecessaryStubbingException` ở `HrNightRewardServiceTest.previewLoadsQualificationAndEntitlementDetailsInBatches`, đã tái hiện khi chạy riêng. Ghi trong TECH_DEBT 17b/17f |
| Graph | Đã chạy `bash scripts/update-knowledge-graph.sh` để refresh AST; semantic docs/images không được tái trích xuất trong lần này |

Baseline frontend đã chạy trước sửa: build/lint pass, 10 cảnh báo; precache 5115.78 KiB. Bản cuối 5127.33 KiB; chunk Excel tải theo nhu cầu 862.98 kB vẫn là debt 17c có sẵn. Lần baseline backend gặp một số report cũ không ghi đè được do quyền file; các lượt xác minh dùng suffix report riêng để lấy kết quả đầy đủ, không sửa quyền của file người dùng.

Fixture trình duyệt và cách chạy: [scripts/verification/README.md](../scripts/verification/README.md). Request API được chặn và trả dữ liệu giả lập; không dùng kết quả đó để khẳng định số liệu production hoặc đã kiểm thử UI với backend thật. Backend dùng JDK 21 tại máy phát triển và Maven offline, H2 cho integration test.

Phase 5 không thêm migration/schema. Khi đưa lên môi trường chạy cần phát hành frontend/backend cùng endpoint báo cáo; chưa có xác nhận build/deploy Docker. Quy tắc báo cáo đã được chốt nhưng cần người dùng kiểm tra trên dữ liệu thật trước khi dùng làm số liệu nộp chính thức.
