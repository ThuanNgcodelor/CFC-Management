# Phân tích dữ liệu sổ sức khỏe nhân viên

Ngày rà soát: 10/11/2026. Đây là kết quả đọc dữ liệu và đề xuất cần chốt, chưa phải kế hoạch đã duyệt hoặc chức năng đã triển khai. Không chứa kết quả sức khỏe cá nhân.

Cập nhật sau trao đổi: đã có [kế hoạch Phase 0–9](EMPLOYEE_HEALTH_PLAN.md). Người dùng xác nhận gián tiếp là văn phòng/hành chính, trực tiếp là công nhân; chỉ ADMIN/MANAGER quản trị; công bố từng người/hàng loạt trước khi cá nhân xem; tra cứu không tài khoản CFCBase/Telegram với CCCD và mã nhân viên. Bộ file là nguồn lịch sử đến 2025, 2026 chưa khám. Các câu hỏi cũ bên dưới là dấu vết khảo sát; trạng thái chốt/đề xuất mới xem trong kế hoạch. Phương án xác minh thêm, nguồn ưu tiên khi xung đột và mẫu xuất cụ thể vẫn cần review.

## 1. Phạm vi đã đọc

Đã nạp cả ba workbook bằng openpyxl, kiểm tra cả sheet ẩn, ô có dữ liệu, tiêu đề, công thức và SID; đọc toàn bộ ba bản Markdown tương ứng để đối chiếu cấu trúc/trường/ghi chú. Markdown là bản biểu diễn workbook, không phải ba nguồn kết quả độc lập để import thêm.

| Workbook | Nội dung thực tế |
|---|---|
| KHAM SUC KHOE 2025.xlsx | Một sheet Gián tiếp, tiêu đề hàng 1, 85 dòng kết quả, 47 trường; 85 SID và 85 mã nhân viên khác nhau |
| So sánh kết quả sức khỏe gián tiếp 2023 - 2024.xlsx | Bảy sheet, chỉ Sheet5 hiển thị; sáu sheet ẩn có cả nguồn tổng hợp và bảng thống kê |
| bo sung thong tin SK 2024.xlsx | Sheet1 có 23 dòng cần bổ sung; KÊT QUẢ SK có 13 dòng kết quả và 136 cột tiêu đề, tiêu đề hàng 3 |

Chi tiết workbook so sánh:

| Sheet | Trạng thái | Số dòng có SID | Năm gợi ý từ SID, chưa thay thế ngày khám được xác nhận |
|---|---|---:|---|
| Tổng hợp | Ẩn | 464 | 208 lượt có tiền tố năm 2023; 256 lượt năm 2024 |
| Sheet5 | Hiển thị | 124 | 61 lượt năm 2023; 63 lượt năm 2024; tiêu đề hàng 4 |
| Sheet4 | Ẩn | 125 | 61 lượt năm 2023; 64 lượt năm 2024; tiêu đề hàng 2 |
| Gián tiếp | Ẩn | 78 | Đều có tiền tố năm 2023 |
| Trực tiếp | Ẩn | 130 | Đều có tiền tố năm 2023 |
| Sheet1, Sheet2 | Ẩn | Không phải danh sách kết quả | Hai bảng thống kê phân loại theo nam/nữ, có công thức Excel |

Số lượt không đồng nghĩa số người. Không cộng các sheet thành tổng lượt khám vì nhiều sheet chứa lại cùng SID.

## 2. Các phát hiện ảnh hưởng trực tiếp đến import

1. Mọi SID trong Sheet5, Sheet4, Gián tiếp, Trực tiếp đều nằm trong Tổng hợp. Sheet4 khớp các trường chung với Tổng hợp khi bỏ STT. Sheet5 có 32 SID khác cách ghi họ tên; lần đối chiếu các cột chung không thấy khác các trường kết quả. Một số dòng Gián tiếp/Trực tiếp còn khác trường chung và cần rà soát riêng. Không coi các sheet là nguồn độc lập.
2. Cả 13 SID của KÊT QUẢ SK đã có trong Tổng hợp. Đây là ứng viên bổ sung/đối chiếu, không được mặc định tạo 13 lượt khám mới. Cột Mã NV của cả 13 dòng này trống; Sheet1 riêng có mã để hỗ trợ đối chiếu nhưng có một dòng ghi không có trong danh sách.
3. Code trong file 2025 và file bổ sung là tên/địa chỉ đơn vị, không phải mã nhân viên. File 2025 phải dùng cột J (Mã nhân viên); SID nhận diện lượt khám theo nguồn, không dùng làm mã người xuyên năm.
4. Năm sinh trộn số năm, chuỗi ngày sinh và ô ngày Excel. Khi chỉ có năm sinh phải giữ độ chính xác là năm, không tự tạo ngày 01/01.
5. SID có khoảng trắng cuối; số điện thoại có ô số làm mất số 0 đầu. Số điện thoại không đủ tin cậy để tự ghép người hoặc cấp quyền xem.
6. Phân loại có I, I., II, II., Ii...; Tổng hợp có 24 dòng thiếu phân loại và một dòng chứa lời hướng dẫn trong ô phân loại. File 2025 có bốn dòng thiếu phân loại. Chuẩn hóa cách viết rõ ràng, giữ giá trị gốc, đưa giá trị không hợp lệ vào rà soát.
7. Có ô văn bản chứa _x000D_, xuống dòng, dấu chấm cuối số/chuỗi. Kết quả có thể là số, văn bản, dấu so sánh hoặc khoảng; không ép tất cả thành số.
8. Tên xét nghiệm thay đổi theo máy/phương pháp: Alinity, Cobas, Tosoh G8; khoảng tham chiếu nằm ở cột riêng hoặc tiêu đề và có thể khác giữa các lần khám. Cần lưu tên gốc, phương pháp, đơn vị/khoảng tham chiếu nếu nguồn có. Chưa rõ đơn vị thì không tự khẳng định hai kết quả so sánh trực tiếp được.
9. File bổ sung có 136 cột gồm huyết học, sinh hóa, nước tiểu, tuyến giáp, hình ảnh, thính lực, hô hấp... nhưng nhiều ô trống. Cần danh mục chỉ tiêu mở rộng, không cố định theo 47 cột năm 2025.
10. Sheet Gián tiếp của file so sánh có chiều rộng Excel khai báo đến 16.382 cột, dù dữ liệu thực tế ít hơn nhiều. Parser phải duyệt vùng có dữ liệu và giới hạn an toàn, tránh quét toàn bộ vùng định dạng gây chậm.
11. Sheet1 bổ sung có 10 ghi chú về việc ký hợp đồng năm 2025 nên không có kết quả 2024. Phải phân biệt không có kết quả lịch sử có lý do với thiếu xét nghiệm cần bổ sung; không tự gắn mọi trường hợp là chưa khám.

## 3. Hướng chức năng đề xuất

### Quản lý nội bộ

Đặt module Sổ sức khỏe trong nhóm Nhân sự, có Tổng quan, Đợt khám, Hồ sơ sức khỏe, So sánh và Rà soát dữ liệu. Trang hồ sơ nhân sự có liên kết đến sổ sức khỏe khi người dùng có quyền. ADMIN/MANAGER quản lý; quyền HR_STAFF cần xác nhận riêng cho dữ liệu sức khỏe.

- Quản lý nhiều đợt khám trong một năm, khám bổ sung/tái khám, cơ sở khám và ngày khám được xác nhận.
- Chọn năm/đợt/phòng ban/nhóm/giới tính/phân loại; tìm theo mã hoặc tên, sắp xếp và phân trang; giữ bộ lọc khi đi vào hồ sơ rồi quay lại.
- Hồ sơ theo người: lịch sử từng lần, kết luận/hướng dẫn của cơ sở khám, xét nghiệm và tài liệu đính kèm nếu có; biểu đồ cho chỉ tiêu số có dữ liệu tương thích.
- Hàng đợi chưa ghép nhân sự, trùng SID, chênh lệch nguồn, giá trị sai định dạng, kết quả chưa đầy đủ.
- Tình trạng theo dõi/tái khám do người có quyền ghi nhận, tách khỏi kết luận bác sĩ. Không tự suy diễn chẩn đoán từ thay đổi số.
- Lưu lịch sử sửa, nguồn file/sheet/dòng và người xác nhận; dữ liệu khám cũ không bị xóa khi nhân viên nghỉ việc.

### Import 2026 và các năm tiếp theo

Luồng đề xuất: tải Excel → chọn sheet/loại dữ liệu → nhận diện tiêu đề và ánh xạ trường → chọn/xác nhận đợt khám → ghép nhân viên → xem trước lỗi và thay đổi → xác nhận lưu → công bố kết quả nếu có cổng cá nhân.

- Mẫu có phiên bản; nhận diện theo tên/alias thay vì vị trí cột cố định. Mẫu bệnh viện mới cần xem trước và lưu ánh xạ được duyệt, không hứa nhận mọi cấu trúc tự động.
- Ưu tiên mã nhân viên chính xác; khi thiếu mã, gợi ý theo họ tên/ngày hoặc năm sinh và thông tin hỗ trợ, yêu cầu xác nhận nếu không đủ chắc chắn. Không tự tạo nhân viên trong HR từ dòng sức khỏe chưa khớp.
- Chặn file trùng, phát hiện SID lặp trong phạm vi nguồn/đợt; nguồn bổ sung chỉ đề xuất điền ô trống. Giá trị khác nhau phải trình bày trước/sau, không tự ghi đè.
- Người không có trong danh sách hiện tại vẫn được giữ ở khu vực chờ liên kết, gồm nhân viên đã nghỉ có lịch sử khám.
- Giữ ô trống là thiếu dữ liệu, không đổi thành 0/bình thường. Nhập bổ sung không được xóa kết quả đang có chỉ vì ô file mới trống.
- File chứa nhiều năm không lấy năm từ tên file cho toàn bộ dòng. Tiền tố SID chỉ là gợi ý cần người dùng/cơ sở khám xác nhận quy ước.

### So sánh và xuất

| Phạm vi | Nội dung đề xuất |
|---|---|
| Một người | Chọn 2 hoặc nhiều đợt/năm, chỉ tiêu theo hàng và thời điểm theo cột; kết quả gốc, chênh lệch số khi phù hợp, phương pháp/đơn vị/khoảng tham chiếu, kết luận và phân loại |
| Tập thể | Xuất một workbook theo bộ lọc hoặc toàn bộ người được phép; mỗi người/chỉ tiêu có các cột kỳ đã chọn, không bắt buộc tạo một sheet cho mỗi người |
| Tổng hợp | Số người và lượt khám riêng, phân bố phân loại, người có dữ liệu ở cả hai kỳ/chỉ một kỳ, tình trạng đầy đủ, theo đơn vị/nhóm/giới tính |
| Rà soát | Danh sách chưa liên kết, lỗi import, thiếu kết quả, xung đột nguồn và trường cần bổ sung |

Không so sánh ô thiếu như 0; không gắn tăng/giảm là tốt/xấu chung cho mọi xét nghiệm. Thống kê so sánh phải thể hiện mẫu người thực sự có dữ liệu ở cả hai kỳ, tránh coi thay đổi thành phần nhân sự là thay đổi sức khỏe.

### Tra cứu cá nhân

Đây là ý tưởng đã trao đổi trước, chưa chốt đưa vào bản đầu. Đề xuất CCCD/số điện thoại chỉ dùng định danh ban đầu; cần thêm OTP qua kênh đã xác thực hoặc mã tra cứu riêng đủ mạnh, có giới hạn thử và phiên hết hạn. Chỉ xem kết quả của chính mình đã được công bố; không cung cấp API tìm danh sách người qua cổng công khai. Chưa chọn nhà cung cấp OTP hoặc cách tích hợp định danh bên ngoài.

## 4. Các điểm cần người dùng chốt

1. Phạm vi cả trực tiếp và gián tiếp hay gián tiếp trước? File 2025 còn nguồn trực tiếp khác không?
2. File 2026 theo mẫu bệnh viện hay có thể thống nhất mẫu? Có thể có nhiều bệnh viện/nhiều đợt mỗi năm không?
3. Sheet nào là nguồn chính 2023–2024? Có đồng ý giữ chênh lệch chờ xác nhận và chỉ bổ sung ô thiếu không?
4. Ưu tiên xuất so sánh hai năm hay nhiều năm? Có cần Excel giữ bố cục hiện tại, PDF cho cá nhân, hoặc ZIP nhiều phiếu không?
5. Báo cáo quan trọng nhất cần gửi cho ai, cần các cột nào và có cần ẩn chi tiết cá nhân không?
6. HR_STAFF được quyền gì với kết quả chi tiết? Có người phụ trách y tế cần vai trò riêng không?
7. Có đưa trang cá nhân vào bản đầu? Kênh xác minh nào sử dụng được?
8. Có cần quy trình xác nhận/công bố/khóa đợt và mở lại có lý do? Ai được sửa kết quả gốc?
9. Theo dõi đủ cả nhân viên đã nghỉ, chưa có mã, thuê ngoài hay chỉ người trong HR? Dữ liệu lịch sử chưa ghép thì ai xác nhận liên kết?
10. Ngày khám có thể xác nhận từ SID không, hay cần nhập theo thông tin bệnh viện? Hiện chưa có cột ngày khám thống nhất trong các mẫu.

Chưa import dữ liệu thật, chưa thay schema, chưa sửa chức năng đang chạy và chưa gửi nội dung sức khỏe sang dịch vụ bên ngoài trong lần rà soát này.


## 5. Kiểm chứng Phase 3 dev — 11/11/2026

Người dùng đã chọn **Tổng hợp làm nguồn ban đầu**, giữ khác tên/kết quả chờ xác nhận, và cho phép đọc API nhân sự cfcbooking để sao chép trường tối thiểu vào dev. Các mục đề xuất/chưa chốt phía trên phản ánh thời điểm khảo sát ban đầu; quyền sức khỏe hiện đã chốt chỉ ADMIN/MANAGER, có cổng cá nhân ở phase sau.

- Tổng kiểm kê tất cả sheet kết quả: **1.019 dòng nguồn / 549 SID khác nhau**, cộng riêng **23 dòng công việc**. Không cộng thành 1.042 lượt khám hoặc 549 người.
- Đối chiếu theo SID phát hiện 33 cách ghi tên khác nguồn ban đầu, gồm 32 biến thể chuẩn hóa tương đương và 1 khác tên thực sự. Riêng Sheet5 có 32 trường hợp; giữ cả nguồn để quản trị duyệt.
- 14 ô có giá trị nằm ở cột chưa có tiêu đề trong Trực tiếp; giữ nguyên vị trí/giá trị và chặn, không tự đoán chỉ tiêu hoặc bỏ ô.
- Trên toàn bộ dòng nguồn có 1 phân loại ngoài I–V, 43 dòng không có phân loại và 2 lỗi nguồn. Đây là số dòng lặp qua sheet, không phải số người duy nhất. Thiếu phân loại chỉ là thiếu dữ liệu, không tự gán I/bình thường.
- Không thấy xung đột giá trị khi so cùng khóa tiêu đề/SID trong khảo sát này; không khẳng định mọi metadata hay các phương pháp khác nhau tương đương.
- Snapshot HR riêng có 398 ID/mã/tên và ngày sinh của 249 hồ sơ liên quan. Khớp chính xác mã+tên+thông tin sinh: 83 dòng/83 người, từ file 2025. 789 dòng chỉ có gợi ý cần xác nhận; 147 dòng chưa khớp đủ điều kiện. Không dùng kết quả gợi ý tên để auto import.
- DB dev riêng đã xác nhận 83/85 dòng năm 2025, tạo 83 lượt/2.216 ô kết quả; 936 dòng nguồn còn chờ, gồm cả dòng lặp ở sheet con. Không tự tạo 936 người hoặc lượt mới. Người dùng đã xác nhận năm 2023/2024 theo SID; chưa suy ngày khám hoặc công bố kết quả.

File thật/identity snapshot/báo cáo chi tiết/backup chỉ ở vùng dev riêng với quyền hạn chế, không đưa hồ sơ người thật vào tài liệu này hoặc fixture public. [Bàn giao và cách review](EMPLOYEE_HEALTH_PHASE3_DEV.md).
