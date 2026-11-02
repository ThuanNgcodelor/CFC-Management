# Thử chấm công và đối soát ca đêm

Mục **Thử chấm công** nằm trong menu của Nhân viên nhân sự (`HR_STAFF`). Màn hình này chỉ xem và sửa dữ liệu tạm trong tab trình duyệt. Không dùng các nút import, chốt hoặc mở khóa chấm công thật.

1. Chọn `File công thử nghiệm.xlsx`. Hệ thống đọc file mà không lưu, ghép cặp lượt vào/ra theo parser, ca làm và quy tắc tính công đang dùng. Bảng tổng quan cho thấy 3 nhân viên, số công tạm tính và các ngày cần kiểm tra.
   Bản thử nhận tối đa 200 ngày công và file tối đa 15 MB; không dành cho import chính thức.
2. Chọn một nhân viên để xem các ngày cần kiểm tra. `Sửa thử` cho phép chọn ca, chỉnh giờ vào/ra và mức công; `Xác nhận thử ca đã ghép` xác nhận các ca tự ghép trong bản thử. Tất cả thao tác chỉ thay đổi màn hình hiện tại. `Đặt lại kết quả thử` khôi phục kết quả ban đầu.
3. Chọn `Gửi thuận.xlsx` để đối soát. File này đọc danh sách xí nghiệp ở các cột `MS`, `Họ và tên`, `Công ca đêm`; hai mã C743 và B124 được so với ca đêm dự kiến sau chỉnh sửa thử. Mã A001 (Nguyễn Trung Thuận) xuất hiện trong nhóm **Chỉ có trong file chấm công**.

Ca đêm dự kiến tính ngày có ca đủ điều kiện thưởng và công dương; ngày còn “Cần kiểm tra” được báo riêng. Cột xác nhận thử trong bảng tổng quan cho biết bao nhiêu ca đã được xử lý trên màn hình. Bản thử áp dụng ca chung của công nhân, chưa áp dụng miễn chấm hoặc chính sách riêng từng nhân viên. Kết quả này không thay thế quy trình thưởng thật: quy trình thật còn yêu cầu import và từng ca đã chốt, hồ sơ nhân viên hợp lệ và các điều kiện chương trình. Đóng hoặc tải lại trang sẽ xóa mọi chỉnh sửa thử.
