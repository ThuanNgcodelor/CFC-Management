# Thử chấm công ca sản xuất và đối soát ca đêm

Mở **Chấm công → tab Thử nghiệm**. Tab này dùng bảng tổng quan, chi tiết ca và form quyết định của màn Chấm công ca sản xuất. Mục **Thử chấm công** cũ ở thanh bên đã bỏ.

1. Chọn file Time Attendance, chẳng hạn `File công thử nghiệm.xlsx`. Backend đọc file trong request, ghép theo parser/matcher, cấu hình ca/công, chính sách cá nhân và miễn chấm đang dùng, đồng thời tra mã nhân viên trong hồ sơ. Bảng tổng quan có các cột công tạm tính, ca đêm, tăng ca, đêm + tăng ca và cần xử lý giống màn chính. Nhân viên chưa có hồ sơ như A001 vẫn hiện trong file; các ngày tự ghép của họ chuyển thành **Cần kiểm tra**. Ngày không có dấu chấm giữ trạng thái **Không chấm**, không cộng vào cột Cần xử lý.
2. Chọn nhân viên, lọc **Cần xử lý**, mở **Kiểm tra** để xác nhận/điều chỉnh hoặc từ chối. Form dùng cùng lựa chọn ca, dấu vào/ra, công, phụ cấp và lý do như màn chính. Mỗi lần lưu, trình duyệt gửi lại file gốc cùng các quyết định thử; backend ghép lại chuỗi ngày liên quan trong bộ nhớ. **Đặt lại kết quả thử** xóa các quyết định của phiên.
3. Bấm **Đối soát thưởng ca đêm**, chọn `Gửi thuận.xlsx`. Bản thử đọc các cột `MS`, `Họ và tên`, `Công ca đêm` rồi mở modal đối soát như màn thưởng ca đêm. Các mã chỉ có ở file công, ví dụ A001 nếu file xí nghiệp chỉ gửi B124 và C743, xuất hiện ở nhóm **Chỉ có trong hệ thống** của bản đối soát. Số ca ở đây là số dự kiến sau quyết định thử, chưa phải thưởng đã chốt.

Giới hạn: file tối đa 15 MB, tối đa 200 ngày công. Bản thử chưa áp dụng sự cố máy hoặc các điều kiện tháng/chu kỳ của thưởng thật. Không tạo import, ca, audit event, qualification hay entitlement trong DB. Tải lại hoặc đóng trang sẽ xóa kết quả thử. Quy trình thật vẫn yêu cầu chốt file và chốt thưởng riêng.
