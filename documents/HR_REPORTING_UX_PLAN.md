# Kế hoạch hoàn thiện Tổng quan, tra cứu và báo cáo nhân sự

Ngày lập: **04/11/2026 — Asia/Ho_Chi_Minh**.  
Trạng thái: **Đã triển khai Phase 0–3 tại local ngày 04/11/2026; Phase 4–7 còn lại theo checklist**.  
Đối tượng sử dụng: phòng Tổ chức – Hành chính – Nhân sự.

Bổ sung ngày 04/11/2026: tra cứu hồ sơ qua mọi trạng thái; xử lý hồ sơ ngừng tiếp nhận có tham chiếu; nhập nhanh số chứng từ trong Tăng/Giảm.

Checklist thực hiện và cổng nghiệm thu từng phase: [HR_REPORTING_UX_PHASES.md](HR_REPORTING_UX_PHASES.md). Phần hiện trạng khảo sát bên dưới mô tả trước thay đổi; trạng thái triển khai và bằng chứng kiểm thử hiện hành nằm trong tài liệu phase.

## 1. Mục tiêu và phạm vi

Giúp người sử dụng nhìn đúng tình trạng nhân sự, tìm được người đứng sau mỗi chỉ số, giữ nguyên điều kiện tra cứu khi xem chi tiết, và lập hai báo cáo:

1. **Biến động quân số tháng/năm:** đầu kỳ, tăng, giảm, cuối kỳ; bấm vào số để xem danh sách và biến động nguồn.
2. **Danh sách nghỉ việc:** lọc theo thời gian, nhóm nhân sự, tình trạng hợp đồng, phòng/xí nghiệp và lý do; xuất Excel theo bộ lọc.

Phạm vi sửa trải nghiệm bao gồm các màn HR có danh sách, chi tiết hoặc form sửa. Ưu tiên Nhân sự, Tăng/Giảm, Thử việc, LĐ phổ thông và Danh sách tháng; sau đó áp dụng cùng tiêu chí điều hướng cho các màn HR còn lại.

Bổ sung thao tác xử lý hồ sơ chờ tiếp nhận nhưng không tiếp tục tuyển và bộ nhập số chứng từ có phần đuôi điền sẵn. Phương án vòng đời ngừng tiếp nhận còn là đề xuất. Người dùng đã chốt: chỉ Tăng điền sẵn phần đuôi, số đầu nhập tay; Giảm để trống và tự điền nếu cần; mọi phần của số chứng từ đều sửa được.

**Loại khỏi kế hoạch:** tính lương, đối soát lương, thay đổi chức năng Gửi phiếu lương, cổng sức khỏe, mở lại ONLYOFFICE và mở rộng Booking. Việc bỏ lương khỏi đề tài không có nghĩa xóa chức năng lương hiện có.

## 2. Những yêu cầu người dùng đã chốt

| Nội dung | Yêu cầu đã chốt |
|---|---|
| Thẻ Tổng quan | Thay **Bản nháp** bằng **Đang thử việc**, đếm trạng thái đang thử việc từ phân hệ Thử việc |
| Số thử việc | MCP trình duyệt đã xác nhận **7 người** ngày 04/11/2026; đây là số quan sát, không phải hằng số để ghi vào mã |
| Tổng hồ sơ | Không cộng người đang thử việc vào tổng hồ sơ nhân sự |
| Người nghỉ | Đổi nhãn **Ngừng hoạt động** thành **Đã nghỉ việc** trong ngữ cảnh trạng thái nhân sự |
| Nút thêm | **Tạo/Thêm hồ sơ nháp → Thêm hồ sơ nhân sự** |
| Trạng thái hồ sơ | Hồ sơ nhân sự `DRAFT` hiển thị **Chờ tiếp nhận** |
| Trạng thái biến động | Biến động `DRAFT` hiển thị **Chờ xác nhận** |
| Phân loại báo cáo | Tách **nhóm nhân sự** và **tình trạng hợp đồng** thành hai tiêu chí độc lập |
| Quyền báo cáo | `HR_STAFF` được xem và xuất hai báo cáo; `ADMIN`/`MANAGER` tiếp tục quản lý nghiệp vụ |
| Điều hướng | Xem chi tiết/sửa rồi quay lại phải khôi phục bộ lọc, sắp xếp và trang đang xem |
| Khả năng tìm hồ sơ | Cần tìm được hồ sơ dù đã chuyển từ Chờ tiếp nhận sang Đang làm việc; không để người dùng tưởng hồ sơ biến mất |
| Hồ sơ không tiếp tục tuyển | Cần xử lý được trường hợp hồ sơ chờ tiếp nhận có tham chiếu nên không xóa được; phương án đề xuất là Dừng tiếp nhận và lưu trữ lịch sử |
| Nhập số chứng từ | Chỉ Tăng điền sẵn phần đuôi mẫu `/HĐLĐ-PBHC/2026`; người dùng nhập số đầu, sửa được cả phần đuôi/năm. Giảm để trống, không bắt buộc, tự điền nếu cần. Không tự tăng/cấp số |

Không đổi tên enum hoặc chuyển trạng thái dữ liệu chỉ để thay nhãn. `DRAFT` của hồ sơ, biến động, ứng viên và tài liệu là các ngữ cảnh khác nhau; không thay chuỗi toàn hệ thống một cách máy móc.

## 3. Hiện trạng đã kiểm tra

### 3.1 Tổng quan và hồ sơ chờ tiếp nhận

- `HrManagementService.overview()` đếm hồ sơ nhân sự theo `ACTIVE`, `DRAFT`, `INACTIVE`; chưa lấy số người `IN_PROBATION`.
- MCP trình duyệt đã kiểm tra danh sách Thử việc: **7 người đang thử việc**; danh sách Nhân sự lọc `status=DRAFT`: **3 hồ sơ**.
- Ba hồ sơ chờ tiếp nhận đã được lưu trong bảng nhân sự nhưng chưa hoàn tất xác nhận Tăng. Chúng không đồng nghĩa với ứng viên đang thử việc.
- Hồ sơ chuyển từ người đạt thử việc có thể ở trạng thái Chờ tiếp nhận trước khi xác nhận Tăng. LĐ phổ thông cũng có thể đi qua bước này.
- Theo ảnh người dùng cung cấp: **397 = 349 đang làm việc + 3 chờ tiếp nhận + 45 đã nghỉ việc**. Bộ số này dùng để giải thích phạm vi đếm, không phải dữ liệu seed hay giá trị cố định.
- Lần kiểm tra MCP tiếp theo trong cùng ngày chỉ còn **2 hồ sơ DRAFT**. Hồ sơ thứ ba trước đó đã hiển thị **Đang làm việc** khi tìm trên tất cả trạng thái. Đây là dữ liệu thay đổi theo thời điểm; thông tin lần trước không được dùng làm khẳng định trạng thái hiện tại.
- Kiểm tra tab Lịch sử của hai hồ sơ đang được hỏi: mỗi người có **1 biến động Tăng ở trạng thái nháp**, đủ để kích hoạt điều kiện chặn xóa. Chưa kiểm kê toàn bộ loại tham chiếu khác của từng hồ sơ.
- `HrWorkforceService.deleteDraftEmployee()` chặn nếu tồn tại bất kỳ biến động, snapshot, dòng import hoặc ứng viên nguồn. Thông báo hiện tại liệt kê các khả năng chung, không cho biết liên kết nào thực sự đang chặn.
- Đổi biến động sang Đã hủy vẫn không làm điều kiện `countByEmployee_Id > 0` biến mất. Vì vậy không hướng dẫn người dùng rằng chỉ hủy biến động là có thể xóa hồ sơ.

### 3.2 Tăng/Giảm

- Danh sách hiện tải từng trang 20 dòng.
- Có sắp xếp ở trình duyệt nhưng chỉ trên nội dung trang vừa tải, nên thứ tự giữa các trang không bảo đảm.
- Chưa có bộ tìm kiếm/lọc đầy đủ cho lịch sử biến động.
- Trang, sắp xếp và trạng thái form phần lớn được giữ bằng state của component, không đủ để khôi phục sau điều hướng.

### 3.3 Mất trạng thái khi quay lại

- Nhân sự đã có bộ lọc/trang trên URL; nút Danh sách trong chi tiết lại điều hướng về đường dẫn gốc và làm rơi bộ lọc.
- Thử việc chỉ đưa tab lên URL; các bộ lọc và trang còn nằm trong state.
- Danh sách tháng và LĐ phổ thông dùng nhiều state cục bộ cho điều kiện tra cứu.
- Chấm công/Thưởng ca đêm đã nhớ một số lựa chọn, nhưng chưa có quy ước chung cho toàn bộ hành trình danh sách → chi tiết → sửa → quay lại.

### 3.4 Báo cáo sẵn có

- Đã có xuất Excel nhân sự theo tháng/năm với Tăng, Giảm và danh sách tháng trong `HrExcelExportService`.
- Quân số được dựng từ dữ liệu nền và biến động đã xác nhận theo ngày hiệu lực qua `HrRosterProjectionService`.
- Phần mới cần bổ sung khả năng tổng hợp, lọc, truy vết, đối chiếu chỉ số với danh sách và xuất đúng phạm vi đang xem.

## 4. Thiết kế Tổng quan và cách gọi

Giữ bốn thẻ chính:

| Thẻ | Nguồn dữ liệu | Hành vi bấm |
|---|---|---|
| Tổng hồ sơ | Tổng hồ sơ nhân sự; gồm đang làm, chờ tiếp nhận, đã nghỉ; không cộng ứng viên thử việc | Danh sách nhân sự, không đặt bộ lọc trạng thái |
| Đang làm việc | Hồ sơ nhân sự `ACTIVE` | Danh sách nhân sự lọc đang làm |
| Đang thử việc | Ứng viên `IN_PROBATION` | Danh sách Thử việc lọc đúng trạng thái nếu người xem có quyền |
| Đã nghỉ việc | Hồ sơ nhân sự `INACTIVE` | Danh sách nhân sự lọc đã nghỉ |

**Đề xuất trình bày:** dưới Tổng hồ sơ có liên kết phụ **Chờ tiếp nhận: N** để truy cập các hồ sơ trước đây nằm trong thẻ Bản nháp. Đây không phải thẻ chính thứ năm; cần xác nhận cách đặt liên kết trước khi nghiệm thu giao diện.

- Thẻ Đang thử việc đếm theo trạng thái nghiệp vụ, không tự đổi người quá ngày kết thúc thành Không đạt hoặc Đã nghỉ.
- Vì Tổng hồ sơ không gồm ứng viên thử việc, không thể hiện bốn thẻ như các phần cộng lại thành một tổng; có chú thích rõ phạm vi.
- Các đường dẫn từ chỉ số phải truyền được bộ lọc vào màn đích.
- Giữ quyền Thử việc hiện có: quyền xem Tổng quan không tự cấp quyền quản lý hoặc đọc toàn bộ ứng viên. Nếu `HR_STAFF` chưa có quyền vào Thử việc, thẻ vẫn hiển thị tổng số nhưng không dẫn tới màn bị 403. Nếu cần cho vai trò này xem danh sách thử việc, phải chốt riêng phạm vi chỉ đọc.
- Rà lại nút, badge, tooltip, mô tả, toast và nhãn liên quan để dùng nhất quán các tên đã chốt trong đúng ngữ cảnh.

### 4.1 Tìm hồ sơ và nhận biết vì sao không còn trong bộ lọc

- Danh sách nhân sự là điểm tìm kiếm chung, có các lựa chọn trạng thái rõ: Tất cả, Chờ tiếp nhận, Đang làm việc, Đã nghỉ việc và nhóm lưu trữ nếu bổ sung ở mục 4.2.
- Thêm lối vào Chờ tiếp nhận dễ thấy từ Danh sách nhân sự và Tổng quan; không phụ thuộc người dùng tự biết nhãn cũ Hồ sơ nháp.
- Mục LĐ phổ thông ghi rõ đang giới hạn theo nhóm. Có liên kết **Tìm trong toàn bộ nhân sự** mang theo từ khóa và bỏ giới hạn nhóm/trạng thái.
- Khi tìm không ra trong bộ lọc hiện tại, có thao tác **Tìm ở tất cả trạng thái**; không tự bỏ bộ lọc mà không thông báo.
- Từ Thử việc có trạng thái Đã chuyển hồ sơ, cung cấp nút **Xem hồ sơ nhân sự** dẫn tới hồ sơ đã liên kết và hiển thị tình trạng tiếp nhận hiện tại.
- Sau xác nhận Tăng, thông báo rõ hồ sơ đã chuyển sang Đang làm việc; khi quay về danh sách Chờ tiếp nhận, giải thích vì sao dòng vừa xử lý không còn ở đây.
- Chỉ số Tổng quan và danh sách đích dùng cùng định nghĩa, tải lại sau thao tác liên quan; số minh họa trong tài liệu không được cố định trong UI.

### 4.2 Hồ sơ ngừng tiếp nhận có tham chiếu — phương án đề xuất

**Vấn đề:** một hồ sơ vẫn Chờ tiếp nhận nhưng đã có biến động Tăng chờ xác nhận; xóa bị chặn để bảo toàn liên kết. Người dùng cần một cách kết thúc việc tiếp nhận thay vì để hồ sơ nằm chờ vô thời hạn.

**Thao tác đề xuất:** **Dừng tiếp nhận**, đưa hồ sơ vào nhóm **Đã dừng tiếp nhận** có thể tra cứu lại. Đây là thiết kế mới cần người dùng duyệt, không phải tính năng hiện có.

1. ADMIN/MANAGER mở hộp thoại xem trạng thái và các liên kết đang chặn: loại tham chiếu, số lượng và đường xem chi tiết được phép.
2. Phân biệt hồ sơ chưa từng được ghi nhận vào quân số với hồ sơ có lịch sử đã xác nhận. Với hồ sơ có lịch sử đang làm/tháng đã chốt, hướng người dùng sang nghiệp vụ Giảm hoặc điều chỉnh phù hợp; không dùng Dừng tiếp nhận để giấu lịch sử đã có hiệu lực.
3. Với hồ sơ đủ điều kiện ngừng tiếp nhận: nhập ngày và lý do nghiệp vụ. Các biến động Tăng còn chờ phải được xử lý rõ ràng, xem trước số lượng, rồi hủy cùng việc dừng tiếp nhận trong một giao dịch ngắn có audit và `rowVersion`.
4. Không xóa dây chuyền ứng viên nguồn, biến động đã xác nhận, snapshot, import hoặc tài liệu có tham chiếu. Không tạo Tăng rồi Giảm giả chỉ để làm hồ sơ biến mất.
5. Bổ sung vòng đời lưu trữ tiếp nhận, kiểm tra schema và migration cần thiết. Đây là thay đổi nghiệp vụ mới, khác với đổi nhãn DRAFT. Không tái dùng INACTIVE để gọi người chưa được tiếp nhận là Đã nghỉ việc.
6. Hồ sơ đã dừng không nằm trong số Chờ tiếp nhận, danh sách chọn tạo Tăng hoặc quân số đang làm; backend cũng phải chặn xác nhận một biến động Tăng cũ còn mở, kể cả yêu cầu đồng thời từ tab khác.
7. Tổng hồ sơ vẫn là số hồ sơ nhân sự đang được lưu; nếu tính cả hồ sơ đã dừng, phải có nhãn/đường xem riêng và số phân rã rõ ràng. Người dùng cần duyệt cách tính và trình bày trước khi triển khai.
8. Báo cáo nghỉ việc và biến động quân số không tự tính hồ sơ chỉ ngừng tiếp nhận là một lượt Giảm. Nếu có nhu cầu, bổ sung danh sách Dừng tiếp nhận riêng để thống kê tuyển dụng không hoàn tất.
9. Giữ nút Xóa vĩnh viễn chỉ cho hồ sơ thực sự đủ điều kiện xóa; kiểm tra đầy đủ liên kết. Với hồ sơ có tham chiếu, hiển thị giải thích cụ thể và thao tác phù hợp thay vì chỉ báo lỗi sau khi bấm.
10. Khôi phục tiếp nhận, nếu người dùng cần, là thao tác riêng có audit; không tự kích hoạt lại hợp đồng hay xác nhận Tăng cũ.

HR_STAFF vẫn chỉ đọc phần thông tin hồ sơ được phép; không có quyền dừng/khôi phục/xóa hoặc xem thêm lý do nội bộ chỉ vì được xem báo cáo. Tài liệu kế hoạch không lưu tên hoặc cáo buộc cá nhân của hồ sơ thật; lý do xử lý được quản lý nhập trong nghiệp vụ có phân quyền.

## 5. Tìm kiếm, lọc và sắp xếp Tăng/Giảm

### Bộ lọc đề xuất

- Từ khóa: mã nhân sự, họ tên, số quyết định.
- Loại biến động: Tăng, Giảm, Tái tuyển và loại khác thực sự được API hỗ trợ.
- Trạng thái: Chờ xác nhận, Đã xác nhận, Đã hủy.
- Khoảng ngày hiệu lực; thao tác chọn nhanh tháng hoặc năm.
- Nhóm nhân sự, phòng/xí nghiệp; bản điều chỉnh hoặc bản đã được thay thế có dấu hiệu nhận biết riêng.

### Quy tắc vận hành

- Backend lọc và sắp xếp **trước khi phân trang**. Tổng bản ghi, danh sách và phân trang dùng cùng điều kiện.
- Cho sắp xếp theo mã, tên, loại, ngày hiệu lực, trạng thái, người xử lý và thời điểm xử lý khi có dữ liệu tương ứng.
- Cột Người xử lý và Thời điểm xử lý cần tách nghĩa; không dùng một tiêu đề tên người để ngầm sắp theo ngày.
- Chỉ nhận các trường sort nằm trong danh sách cho phép; có khóa phụ ổn định để dữ liệu cùng giá trị không nhảy trang.
- Đổi điều kiện lọc hoặc sort đưa về trang đầu; mở chi tiết rồi quay lại giữ trang cũ.
- Tìm kiếm có debounce hoặc nút Áp dụng; không gửi yêu cầu sau mỗi phím gõ mà không kiểm soát.
- Chọn hàng loạt chỉ áp dụng cho những dòng được chọn rõ ràng; không tự giữ lựa chọn ẩn qua bộ lọc mới.
- Giữ nguyên cơ chế xác nhận, hủy, điều chỉnh, audit và kiểm tra `rowVersion` hiện có.

### Nhập nhanh số chứng từ trong form Biến động nhân sự mới

Yêu cầu: người dùng nhập/sửa phần số đầu, không phải gõ lại toàn bộ phần đuôi. Ví dụ giao diện:

```text
Số thứ tự: [50]    Mẫu: /HĐLĐ-PBHC/2026
Xem trước: 50/HĐLĐ-PBHC/2026
```

- Nhập `51` hoặc `52` tạo phần xem trước tương ứng. Số `62` trong ví dụ người dùng là một số chứng từ, không phải giá trị cố định cho mọi hồ sơ mới.
- Lưu và xuất chuỗi số đầy đủ, bảo đảm tìm kiếm theo số đầu hoặc chuỗi đầy đủ đều có ý nghĩa rõ ràng.
- Cho chỉnh loại/đơn vị/năm của phần đuôi hoặc nhập nguyên số theo mẫu khác; không ép mọi quyết định lịch sử vào HĐLĐ-PBHC.
- **Đã chốt:** chỉ biến động Tăng điền sẵn phần đuôi `/HĐLĐ-PBHC/2026`. Cả phần số đầu, ký hiệu, đơn vị và năm đều cho phép sửa; mẫu điền sẵn chỉ để nhập nhanh, không khóa cứng.
- **Đã chốt:** biến động Giảm mặc định để trống trường số quyết định/chứng từ, người dùng tự nhập nguyên số nếu cần. Không bắt buộc trường này, không tự ghép đuôi HĐLĐ và không dùng giá trị còn sót từ form Tăng.
- **Đã chốt:** người dùng nhập số thứ tự đầu; không gợi ý số tiếp theo, không tự tăng/cấp số, không đặt ràng buộc duy nhất mới.
- Chuyển loại Tăng/Giảm không tự mang mẫu sang loại khác. Nếu đang có số do người dùng sửa, không âm thầm ghi đè hoặc xóa; cho giữ/chỉnh lại hoặc xác nhận đặt về mặc định của loại mới.
- Đề xuất chọn năm theo ngày chứng từ, có thể sửa khi nhập bù; không cố định năm 2026 và không tự đổi chứng từ cũ khi sang năm mới. Quan hệ ngày ký/năm chứng từ cần được duyệt nếu bổ sung kiểm tra bắt buộc.
- Bộ cấp số tự động nằm ngoài phạm vi đã chốt. Chỉ xem xét ở yêu cầu riêng sau này, với quy tắc phạm vi số và xử lý đồng thời được duyệt.
- Khi sửa/điều chỉnh, đọc nguyên số cũ; chỉ tách phần số/đuôi khi nhận diện chắc chắn, có chế độ nhập đầy đủ cho mẫu không nhận diện được. Không ghi lại hàng loạt dữ liệu cũ.
- Các mẫu/cấu hình mới cần được ghi trong CONFIG_AND_CACHE khi triển khai. Không đổi cơ chế sinh số hợp đồng ở phân hệ Hợp đồng chỉ vì tiện ích nhập số tại Tăng/Giảm.

## 6. Quy ước giữ trạng thái cho các màn HR

### Trạng thái cần giữ

URL giữ bộ lọc đã áp dụng, sort, page, size, tab và kỳ báo cáo. Bản nháp nội dung form và dữ liệu cá nhân nhạy cảm không được đưa lên URL. Vị trí cuộn được lưu theo mục lịch sử điều hướng để khôi phục sau khi danh sách tải xong.

### Hành trình cần hỗ trợ

1. Danh sách đã lọc → Chi tiết → nút Quay lại/Danh sách.
2. Danh sách → Chi tiết → Sửa → Lưu/Hủy → quay về đúng ngữ cảnh.
3. Danh sách → Sửa trực tiếp → Lưu/Hủy.
4. Back/Forward trình duyệt, tải lại trang và mở URL đã lọc trong tab mới.
5. Chi tiết được mở trực tiếp không có nguồn quay lại: dùng danh sách mặc định phù hợp.
6. Hồ sơ mở từ LĐ phổ thông hoặc báo cáo: trở lại đúng màn nguồn, không luôn ép về Danh sách nhân sự.

### Cách làm đề xuất

- Có helper/hook dùng chung để đọc, chuẩn hóa và cập nhật tham số danh sách, giới hạn thay đổi ở phần điều hướng của từng màn.
- Truyền nguồn quay lại là đường dẫn nội bộ hợp lệ; không tạo chuyển hướng tới URL bên ngoài từ tham số người dùng.
- Phân biệt bộ lọc đang nhập với bộ lọc đã Áp dụng. Back/Forward khôi phục bộ lọc đã áp dụng.
- Sau khi sửa dữ liệu, tải lại danh sách ở cùng điều kiện; nếu dòng không còn phù hợp bộ lọc thì nó có thể biến mất đúng nghiệp vụ.
- Nếu trang cuối không còn bản ghi sau thay đổi, lùi tới trang hợp lệ và giải thích ngắn cho người dùng.
- Không phục hồi các hàng được chọn, file upload hoặc form đang nhập theo kiểu gây thực thi thao tác cũ ngoài ý muốn.
- Giữ sequence guard hiện hữu ở Chấm công/Thưởng ca đêm cho tới khi chính màn đó được chuyển sang Query.

### Thứ tự rà soát

| Đợt | Màn hình |
|---|---|
| 1 | Nhân sự: danh sách, chi tiết, sửa; Tăng/Giảm |
| 2 | Thử việc, LĐ phổ thông, Danh sách tháng và chi tiết tháng |
| 3 | Các màn HR còn lại có danh sách/chi tiết: Chấm công, Thưởng ca đêm, Nhập dữ liệu, Nâng lương, Danh mục, Nhật ký và Telegram nếu có hành trình tương ứng |

Đợt 3 sửa việc giữ trạng thái điều hướng; không mở rộng nghiệp vụ của các module đó. Luồng lương nằm ngoài kế hoạch này.

## 7. Hai báo cáo mới

### 7.1 Vị trí giao diện đề xuất

Mục **Báo cáo nhân sự** dưới khu Nhân sự; đường dẫn đề xuất `/manager/hr/reports` với hai tab:

- Biến động quân số.
- Danh sách nghỉ việc.

Có liên kết từ Tổng quan và Danh sách tháng. Bộ lọc dùng chung giữa chỉ số, biểu đồ, bảng chi tiết và file Excel.

### 7.2 Biến động quân số tháng/năm

- Chọn tháng, năm, nhóm nhân sự, tình trạng hợp đồng và đơn vị.
- Bảng năm có từng tháng và các chỉ số đầu kỳ, tăng, giảm, cuối kỳ; xem rõ phạm vi dữ liệu lịch sử sẵn có.
- Bấm một chỉ số mở danh sách nguồn đúng kỳ, đúng bộ lọc; có liên kết tới hồ sơ hoặc biến động khi vai trò có quyền.
- Mỗi chỉ số phải công bố đơn vị đếm: người tại thời điểm hoặc lượt biến động trong kỳ.
- Báo cáo toàn công ty có thể đối chiếu đầu kỳ + tăng/tái tuyển − giảm = cuối kỳ sau khi chốt quy tắc. Báo cáo theo đơn vị còn phải tính chuyển đến/chuyển đi nếu có; theo tình trạng hợp đồng còn phải tính các lần đổi nhóm phân loại. Không dùng công thức giản lược cho mọi lát cắt.
- Người thay đổi phòng hoặc hợp đồng sau kỳ báo cáo không được làm lịch sử phân loại thay đổi chỉ vì lấy giá trị hiện tại.
- Không cộng quân số cuối 12 tháng thành quân số cuối năm. Tổng lượt tăng/giảm của năm và quân số tại mốc cuối năm là các đại lượng khác nhau.

### 7.3 Danh sách nghỉ việc

Các cột đề xuất: mã nhân sự, họ tên, nhóm nhân sự tại thời điểm nghỉ, đơn vị tại thời điểm nghỉ, tình trạng/loại hợp đồng, ngày vào làm, ngày hiệu lực nghỉ, lý do, số/ngày quyết định, trạng thái biến động và liên kết nguồn.

- Lọc theo khoảng ngày hiệu lực hoặc tháng/năm, nhóm, hợp đồng, đơn vị và lý do.
- Chỉ lấy biến động Giảm có hiệu lực nghiệp vụ sau khi giải quyết các bản điều chỉnh/thay thế.
- Phân biệt **nghỉ trong kỳ** với **hiện đang nghỉ**: người nghỉ rồi tái tuyển vẫn có lịch sử nghỉ trong kỳ.
- Hiển thị riêng số người và số lượt khi một người có nhiều lần nghỉ/tái tuyển.
- Lý do hiện có thể là văn bản tự do. Dùng tìm kiếm văn bản trước; chỉ thống kê theo nhóm lý do khi có quy tắc phân nhóm được duyệt.
- Excel xuất toàn bộ kết quả của bộ lọc, không chỉ trang đang hiển thị; có thông tin kỳ, phạm vi, thời điểm xuất và sheet tổng hợp/chi tiết phù hợp.

### 7.4 Ràng buộc dữ liệu chung

- Báo cáo chỉ đọc; không tự xác nhận Tăng/Giảm hoặc sửa hợp đồng.
- Không đưa Chờ tiếp nhận, ứng viên đang thử việc hoặc biến động Chờ xác nhận vào quân số chính thức chỉ vì có hồ sơ.
- Tái sử dụng lịch sử và phép chiếu quân số hiện hành; không tạo bộ công thức song song thiếu đối chiếu.
- Loại ảnh hưởng của bản đã bị thay thế, giữ liên kết audit để truy vết.
- Kỳ không có dữ liệu nền/lịch sử phải báo **Chưa có dữ liệu**, không coi là 0.
- Hồ sơ chưa xác định nhóm/hợp đồng phải được trình bày thành **Chưa xác định**, không ngầm gán Văn phòng hay Chính thức.
- Nếu schema chưa lưu được phân loại tại quá khứ, nêu giới hạn và phương án bổ sung trước khi cam kết báo cáo lịch sử chính xác; không suy ngược bằng thông tin hiện tại.

### 7.5 Quy tắc đã chốt và trạng thái triển khai

- Người dùng chốt ngày 04/11/2026: quân số theo trạng thái hiệu lực cuối kỳ; nghỉ/tái tuyển đếm cả lượt và người riêng; hợp đồng xét theo hiệu lực ngày báo cáo; nhóm dùng nhóm hiện tại và phải ghi rõ giới hạn lịch sử. Không đưa lương vào phạm vi.
- Bản đầu đã có ở `/manager/hr/reports`: bảng/biểu đồ quân số, danh sách nghỉ việc, bộ lọc nhóm và hợp đồng độc lập, đơn vị/lý do, phân trang, xuất Excel; HR_STAFF được đọc/xuất qua API GET.
- Đang dùng nhóm hiện tại vì hệ thống chưa snapshot lịch sử đổi nhóm. “Chưa ghi nhận HĐLĐ hiệu lực” cũng không khẳng định người đó chưa từng ký; dữ liệu hợp đồng lịch sử có thể chưa đầy đủ.
- Chưa nghiệm thu: đối chiếu Excel và API với fixture nghiệp vụ, kiểm thử ma trận quyền và giao diện qua Playwright/backend thật. Truy vấn projection hiện vẫn nạp timeline movement lớn, cần tối ưu theo khoảng ngày trước khi ký nghiệm thu dữ liệu lớn.

## 8. Phân quyền và kỹ thuật

| Vai trò | Tổng quan/Nhân sự | Báo cáo mới | Tăng/Giảm và Thử việc |
|---|---|---|---|
| ADMIN, MANAGER | Theo quyền hiện tại | Xem, lọc, truy vết, xuất | Giữ quyền quản lý hiện tại |
| HR_STAFF | Chỉ đọc theo quyền hiện tại | Xem, lọc, xuất; truy vết qua nội dung chỉ đọc được phép | Không tự mở rộng quyền sửa hoặc quyền vào phân hệ |
| EMPLOYEE/không đăng nhập | Không cấp thêm | Không được truy cập | Không cấp thêm |

- Mở quyền đọc báo cáo ở API và UI cùng lúc. Nếu chi tiết biến động nguồn chưa cho HR_STAFF đọc, báo cáo cần cung cấp phần bằng chứng chỉ đọc tối thiểu hoặc ẩn đường dẫn không được phép; không mở cả controller quản lý biến động.
- API mới dưới `/api/v1/hr/...`, DTO theo quy ước dự án và được `HrApiExceptionHandler` xử lý.
- Danh sách có phân trang và giới hạn size; aggregate theo kỳ hữu hạn. Xuất Excel có giới hạn phạm vi rõ ràng, không dùng API danh sách vô hạn.
- Backend tổng hợp theo lô, tránh N+1; giao dịch đọc ngắn. Chưa thêm cache nghiệp vụ.
- Dùng TanStack Query cho màn báo cáo mới theo quyết định hiện có; kiểm tra dependency thực tế trước khi triển khai. Đo bundle/API/render trước và sau phần tối ưu liên quan.
- Tách hook/component/service mới khi cần; không tiếp tục dồn state và logic vào `HrMovements.jsx` đã dài.
- Giữ cách tải thư viện Excel theo nhu cầu; không thêm static import nặng cho màn báo cáo.
- Chỉ thêm index khi có truy vấn thực tế và kết quả EXPLAIN; không dự kiến thay timezone hoặc làm migration đổi tên enum.

## 9. Các câu nghiệp vụ còn cần chốt

Các câu này chỉ chặn phần báo cáo phụ thuộc vào chúng; không chặn sửa nhãn dashboard và điều hướng đã rõ.

| Câu hỏi | Đề xuất để người dùng duyệt |
|---|---|
| Chốt đầu/cuối kỳ theo mốc nào? Nghỉ có hiệu lực đúng ngày cuối tháng tính ra sao? | Đầu kỳ là trước các sự kiện có hiệu lực trong kỳ; cuối kỳ sau khi áp dụng toàn bộ sự kiện đến hết ngày cuối kỳ. Đối chiếu với quy ước quân số đang dùng trước khi triển khai |
| “Hợp đồng chính thức” được xác định bằng điều kiện nào? | Từ HĐLĐ được ghi nhận, có hiệu lực tại ngày chốt báo cáo/ngày nghỉ; cần xác nhận có yêu cầu đã ký hay không và nguồn trạng thái ký hiện có |
| Nghỉ rồi tái tuyển trong kỳ có tính cả hai lượt không? | Ghi đủ từng lượt và hiển thị thêm số người riêng; cần xác nhận cách trình bày với mẫu báo cáo phòng đang nộp |
| Các phòng/nhóm thay đổi trong kỳ báo cáo xếp theo lúc nào? | Quân số theo mốc đầu/cuối kỳ; biến động theo phân loại tại ngày hiệu lực; kiểm tra khả năng lịch sử của schema |
| Đặt liên kết Chờ tiếp nhận ở đâu? | Liên kết phụ dưới Tổng hồ sơ và bộ lọc rõ trong Danh sách nhân sự |
| Dùng thao tác Dừng tiếp nhận/lưu trữ cho hồ sơ chưa xác nhận Tăng nhưng đã có liên kết? | Duyệt quy trình mục 4.2, quyền quản lý, khả năng khôi phục và cách tính hồ sơ lưu trữ trong Tổng hồ sơ |

Người dùng đã chốt tách nhóm và hợp đồng, nhưng chưa chốt các điều kiện chi tiết trên. Không tự coi Văn phòng là Chính thức hoặc LĐ phổ thông là chưa chính thức.

## 10. Các phase triển khai

Checklist chi tiết, đầu ra, phụ thuộc và tiêu chí hoàn tất nằm tại [HR_REPORTING_UX_PHASES.md](HR_REPORTING_UX_PHASES.md). Bảng này thay thế cách đặt tên A/A2/B/C/D/E trước đây.

| Phase | Nội dung | Điểm nghiệm thu chính |
|---|---|---|
| 0 | Baseline, môi trường và fixture | Có bằng chứng hiện trạng và bộ kiểm thử tái lập được |
| 1 | Dashboard, cách gọi, đường tìm hồ sơ | Số thử việc đúng; tổng hồ sơ đúng phạm vi; hồ sơ chuyển trạng thái vẫn tìm được |
| 2 | Giữ filter/sort/page và đường quay lại ở HR chính | Back/Forward, Danh sách, Lưu/Hủy, reload giữ đúng điều kiện và cuộn |
| 3 | Tra cứu Tăng/Giảm và nhập số chứng từ | Filter/sort toàn bộ dữ liệu; chỉ Tăng điền sẵn đuôi, mọi phần sửa được; Giảm để trống hợp lệ |
| 4 | Dừng tiếp nhận và chẩn đoán chặn xóa | Chốt lifecycle trước khi làm phần ghi; bảo toàn lịch sử và quân số |
| 5 | Hai báo cáo, drill-down, Excel và quyền | Chốt quy tắc trước khi tính; aggregate, nguồn và Excel khớp; HR_STAFF xem/xuất đúng quyền |
| 6 | Điều hướng các màn HR còn lại | Hoàn tất các hành trình thuộc đợt 3 mục 6, không mở rộng nghiệp vụ khác |
| 7 | Nghiệm thu tổng thể, tài liệu và demo | Có bằng chứng kiểm thử ở phiên bản bàn giao và giới hạn được ghi rõ |

Phase 4 có cổng xác nhận vòng đời; Phase 5 có cổng chốt chỉ số và dữ liệu lịch sử. Nếu một cổng chưa được chốt, tiếp tục các phase độc lập thay vì dừng toàn bộ công việc.

Lịch 7 ngày trước đây chỉ là ước lượng cho phạm vi ban đầu. Nếu vẫn phải thi trong 7 ngày, chọn phần demo sau Phase 0 và ghi rõ những phase chưa hoàn thành. Không coi bản dự thi là hoàn tất yêu cầu điều hướng toàn bộ HR khi chưa nghiệm thu Phase 6.

## 11. Kiểm thử cần có khi triển khai

1. Dashboard đếm thử việc 0/N; đổi trạng thái ứng viên phải làm số thay đổi ở lần tải lại; ứng viên đã chuyển hồ sơ không đếm lại là Đang thử việc.
2. Tổng hồ sơ vẫn bao gồm Chờ tiếp nhận và không cộng ứng viên thử việc. Đổi nhãn không thay enum, dữ liệu hoặc luồng Tăng.
3. Tăng/Giảm có hơn một trang: lọc/sort toàn bộ, giá trị trùng được sắp ổn định, page vượt phạm vi được xử lý.
4. Mở chi tiết/sửa từ từng màn nguồn, thử cả nút ứng dụng và Back/Forward; kiểm tra từ khóa, tab, kỳ, sort, page và cuộn.
5. Mở URL trực tiếp, URL sai tham số, dữ liệu bị xóa hoặc không còn phù hợp bộ lọc sau sửa.
6. Báo cáo với Tăng/Giảm đúng biên ngày, bản nháp/đã hủy, điều chỉnh thay thế, nghỉ rồi tái tuyển và nhiều lượt trên một người.
7. Chuyển đơn vị hoặc thay hợp đồng sau ngày báo cáo; dữ liệu thiếu lịch sử phải báo rõ thay vì cho số sai.
8. Tổng năm, từng tháng và danh sách nguồn khớp cùng định nghĩa; không cộng nhầm quân số tồn thành tổng lượt.
9. Excel đủ toàn bộ kết quả đã lọc, đúng số và thứ tự, định dạng ngày/mã phù hợp; dữ liệu văn bản được ghi như văn bản, không thực thi công thức từ nội dung người dùng.
10. API không đăng nhập trả 401; vai trò không được phép trả 403; HR_STAFF xem/xuất được nhưng không gọi được các API ghi chỉ dành quản lý.
11. Giao diện điện thoại và desktop: bộ lọc sử dụng được, chi tiết và đường quay lại rõ ràng, số liệu không bị cắt mất.
12. Hồ sơ đổi DRAFT → ACTIVE không còn trong Chờ tiếp nhận nhưng vẫn tìm được qua Tất cả trạng thái và liên kết từ ứng viên nguồn; kết quả phải tải lại thay vì dùng số chụp ở lần kiểm tra trước.
13. Xóa bị chặn hiển thị đúng từng loại liên kết/số lượng. Hủy biến động không được bị hiểu nhầm là tự giải phóng mọi điều kiện xóa.
14. Dừng tiếp nhận hồ sơ chỉ có Tăng chờ: kiểm tra ngày/lý do/quyền/rowVersion, hủy các Tăng chờ hợp lệ, giữ audit và nguồn import; không phát sinh lượt Giảm. Thử hai tab xác nhận Tăng và dừng tiếp nhận cùng lúc, bảo đảm chỉ một kết quả hợp lệ.
15. Hồ sơ có biến động đã xác nhận hoặc ảnh hưởng quân số không được kết thúc bằng đường ngừng tiếp nhận để né xử lý lịch sử. Khôi phục không tự kích hoạt lại dữ liệu cũ.
16. Mẫu số chứng từ Tăng: nhập 50/51/52, sửa phần đuôi/đơn vị/năm, nhập số đầy đủ và sửa số lịch sử không nhận diện được; bản xem trước, payload lưu, chi tiết và Excel phải khớp. Giảm để trống vẫn lưu được, tự nhập số đầy đủ khi cần; chuyển loại không làm rò mẫu Tăng sang Giảm hoặc ghi đè dữ liệu đã sửa. Không có hành vi tự tăng số.

Kiểm tra ứng dụng khi triển khai: `npm run build`, `npm run lint`, backend `./mvnw test` với Java được cấu hình và các kiểm thử mục tiêu. Baseline kiểm thử cần xác minh lại; không coi trạng thái ghi trong tài liệu cũ là kết quả chạy mới. Nếu suite bị chặn bởi fixture hoặc môi trường, ghi đúng phần đã chạy và phần bị chặn.

## 12. Tài liệu, nghiệm thu và trình diễn

- Cập nhật Master Context và Decisions khi chức năng được triển khai; tài liệu này giữ trạng thái từng giai đoạn.
- Chỉ đóng mục Technical Debt đã thực sự xử lý, không xóa nợ dựa trên kế hoạch.
- Cập nhật bản đồ AST sau thay đổi mã theo `scripts/update-knowledge-graph.sh`; thay đổi semantic tài liệu cần quy trình Graphify tương ứng, không coi AST refresh là tái trích xuất semantic.
- Demo dùng dữ liệu giả lập/được phép, có ba ca rõ ràng: đúng, sai lệch, dữ liệu thiếu/lỗi.
- Video 1–3 phút: mở chỉ số → lọc nghỉ việc theo tháng/năm và hai tiêu chí độc lập → xem nguồn → quay lại giữ bộ lọc → xuất Excel khớp danh sách.
- Đo thời gian làm cùng một báo cáo thủ công và bằng hệ thống; ghi số lần thực hiện và người dùng thực tế, không tự đặt số phút tiết kiệm.

**Trạng thái hiện tại:** đã chốt phạm vi và lập kế hoạch; chưa sửa API, giao diện, schema hoặc dữ liệu nhân sự.
