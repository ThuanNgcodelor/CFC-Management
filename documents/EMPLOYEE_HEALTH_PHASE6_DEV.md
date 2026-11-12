# Sổ sức khỏe — bàn giao Phase 6 ở dev

Hoàn thiện **12/11/2026**. Đã có rà soát, công bố/thu hồi hàng loạt, snapshot phiên bản và khóa/mở đợt. Chỉ triển khai/test dev; không deploy, không ghi production, chưa mở trang cá nhân không đăng nhập. Chưa rà soát hoặc công bố dữ liệu thật trong DB dev.

## Mở và dùng

**http://127.0.0.1:5195/manager/hr/health?tab=publications** — tab **Công bố và phiên bản** trong khung hiện hữu.

1. **Chọn đợt công bố**. Chọn được cả đợt đang mở và đã khóa để xem lịch sử hoặc mở khóa; màn import vẫn giữ mặc định không chọn đợt đã khóa.
2. Tìm mã/tên, lọc nhóm HR hiện tại. Hàng đợi chỉ gồm người có lượt đã nhập của đợt, phân trang 20 người. Dòng staging chưa ghép/chưa xác nhận không tự trở thành lượt đủ điều kiện.
3. **Rà soát / công bố người này**, chọn người trên trang, hoặc **Xem trước tất cả theo bộ lọc**. “Chọn trang này” chỉ chọn tối đa 20 người của trang đó; “tất cả theo bộ lọc” tối đa 200 người, vượt phải thu hẹp. Mỗi lượt có token/phiên bản, trạng thái biên tập/công bố, lỗi chặn và dữ liệu còn thiếu.
4. Nhập lý do, đối chiếu định danh/năm/nguồn và tích chấp nhận nhãn **dữ liệu chưa đầy đủ** → **Xác nhận đã rà soát**. Rà soát không công bố. Dữ liệu nguồn thiếu ngày khám/đơn vị/phương pháp/tham chiếu không bị suy thành bình thường hoặc tự điền; chưa có danh mục bắt buộc để khẳng định đủ gói khám.
5. Sau khi xem trước được tải lại, bấm **Công bố các lượt đã chọn**. Có thể bỏ chọn riêng lượt. Một request tối đa 20 người, mỗi người tối đa 20 lượt; UI chia phạm vi lớn thành các request 20 người tuần tự.
6. Một người được ghi toàn bộ lượt đã chọn trong một transaction. Nếu một lượt của họ lỗi/xung đột, các lượt của người đó được giữ nguyên; người khác vẫn có thể thành công. Màn hình báo số người thành công/bỏ qua/bị chặn và số lượt. Retry không tạo thêm phiên bản khi nội dung hiện tại đã được phát hành đúng.
7. **Phiên bản SID…** hoặc **Hồ sơ sức khỏe → Lịch sử công bố**: đọc lịch sử lý do/người/thời điểm. Chỉ phiên bản mới nhất còn công bố có nút xem kết quả phân trang/tìm chỉ tiêu và tải PDF. Bản cũ giữ lịch sử, không tạo đường tải vòng qua thu hồi.
8. Thu hồi một phiên bản ở lịch sử hoặc **Thu hồi các lượt đã chọn** trong xem trước, có lý do. Thu hồi không xóa dữ liệu biên tập. File đã tải trước đó không thể thu hồi khỏi thiết bị.
9. **Khóa đợt / Mở khóa đợt** cần lý do và rowVersion. Khóa chặn import/rà soát/chỉnh metadata đợt; vẫn có thể công bố bản đã rà soát hoặc thu hồi. Khóa/mở không tự công bố hay thu hồi. Có thể mở picker chọn lại đợt đã khóa sau khi đóng/mở trang.

Phạm vi xem trước được cố định bằng ID, không tự thêm người mới khi tìm kiếm/bộ lọc phía sau thay đổi. Sau lỗi mạng có thể thử lại; server đọc lại token/ownership và tình trạng hiện tại. Trong khi đang ghi, drawer giữ mở để không mất kết quả tiến độ. Đóng màn xem/tải chỉ đọc hủy request; ESC không xóa dữ liệu.

## Bản biên tập và phiên bản

- `HealthVisit.status=CONFIRMED` ở Phase 6 nghĩa là đã rà soát **đúng hash nội dung hiện tại**, không phải đã công bố. Hash bao gồm nhận diện HR hiện có, năm/cơ sở/nguồn của lượt, giá trị raw và metadata chỉ tiêu/nguồn. Token xem trước còn gắn rowVersion lượt/đợt và phiên bản công bố mới nhất.
- Thiếu liên kết/mã/tên, không có kết quả, kết quả trống, ngày khác năm đợt, phân loại ngoài I–V/mâu thuẫn, hoặc vượt giới hạn là lỗi chặn. Dấu chấm cuối phân loại dùng cùng quy tắc chuẩn hóa Phase 3; raw không đổi. Một lần duyệt phải có lý do và chấp nhận nhãn chưa đầy đủ.
- Import bổ sung thật sự thay giá trị giữ luồng cũ đưa lượt về REVIEW. Đổi metadata chỉ tiêu/HR/đợt cũng làm hash khác nên phải rà soát lại. Không cập nhật snapshot đã công bố ngầm.
- Mỗi lần phát hành tạo hàng revision mới với snapshot JSON bất biến, hash, lý do công bố và thông tin rà soát. Snapshot chứa từng ô raw/tham chiếu/provenance, không chứa CCCD/liên hệ/lương hoặc workbook gốc nhiều người.
- **Bản hiện hành = revision cao nhất của lượt và status PUBLISHED**. Bản PUBLISHED cũ có thể còn trong DB nhưng không là bản hiện hành. Nếu revision cao nhất REVOKED thì không có bản đang được hiển thị; không tìm lùi một bản PUBLISHED cũ. Công bố lại sau thu hồi phải xem trước mới và tăng revision.
- Thu hồi chỉ thay trạng thái/lý do/version, không sửa snapshot. Audit riêng health giữ lý do/người/thao tác, không đẩy nội dung ra feed HR chung.
- API nội dung/tệp kiểm tra lại quyền, đúng người và revision hiện hành ở mỗi request; tải PDF kiểm tra lại sau render. Request bắt đầu sau thu hồi không lấy được nội dung đó. Quản trị vẫn được xem/xuất bản biên tập qua chức năng nội bộ theo quyền sẵn có; đó không phải tệp công bố.

## Kỹ thuật và giới hạn

- V36 thêm dấu rà soát trên lượt, lý do/hash phiên bản và lý do audit; mở snapshot TEXT thành LONGTEXT để giữ nguồn thay vì cắt dữ liệu. Các cột bổ sung tương thích hàng cũ; bản thiếu hash không được dùng làm nội dung công bố mới. Không sửa V34/V35, không thêm V36 vào manifest auto-deploy.
- API mới dưới `/api/v1/hr/health/publications/**`, ADMIN/MANAGER/no-store. POST có `@Valid`, read nội dung và PDF kiểm tra ownership. Chưa có API anonymous của Phase 7.
- Preview: tối đa 200 người, 500 lượt, 10.000 kết quả. Mỗi snapshot tối đa 1.000 kết quả / 2 MiB UTF-8; lý do tối đa 500 ký tự. Quá giới hạn trả lỗi thu hẹp, không cắt nguồn. Candidate list max20 người/page; lịch sử/kết quả max100/page, UI20.
- Ghi qua bean transaction riêng, READ_COMMITTED, một người/request unit. Thứ tự khóa: đợt → employee → lượt → chỉ tiêu; khóa chỉ tiêu theo ID bằng PESSIMISTIC_WRITE ngắn để không copy metadata dở dang, dùng được với MySQL và fixture H2 hiện có. Unique (visit, revision) hiện hữu bảo vệ revision; không thêm index.
- Các truy vấn tập lấy người/lượt/kết quả/phiên bản, không gọi repository theo mỗi ô/người của danh sách đọc. SaveAll cho lượt/phiên bản/audit. File/PDF render ngoài transaction; PDF công bố có slot render riêng 1/instance và dùng font/cap PDF Phase 5. Không thêm queue/cache/dependency/polling/env.
- Query keys private theo scope/person/visit/publication; signal hủy read, gc0, không persist. Mutation invalidate nhóm health; preview cố định ID trước khi cập nhật token. Bộ lọc hàng đợi trên URL, lựa chọn/preview/raw không lưu trên URL. Campaign picker hiện lưu lựa chọn trong phiên của tab; đổi sang tab khác rồi quay lại cần chọn đợt lại.

## Kiểm chứng dev

- Full backend offline H2 **352 test, 0 failure/error, 5 skip cũ**; 347 test thực chạy. **13 integration Phase 6** gồm preview không ghi, duyệt rõ, snapshot bất biến, import bổ sung giữ bản cũ, retry/audit không trùng, atomic theo người, lỗi stale/catalog/classification, khóa/mở, quyền/ownership, hai ADMIN/MANAGER đồng thời, thu hồi revision mới nhất không lùi về bản cũ, nội dung/PDF sau thu hồi trả 404.
- **11 nhóm MCP UI Phase 6** đạt: picker, phạm vi cố định, xác nhận rà soát, thành công một phần, tạo revision, khóa/mở và chọn lại đợt khóa, snapshot khác biên tập, tải PDF, responsive390px, thu hồi bỏ nút tải, tìm kiếm debounce. Regression Phase 4 **12 nhóm**, Phase 5 **9 nhóm** đạt.
- Frontend lint **0 lỗi, 10 warning cũ**, build đạt, chunk HrHealth **115,07 kB** so với Phase 5 **96,47 kB**; 2 Node tests quyền/URL đạt. Không thêm dependency.
- API/UI dev thật xem trước đợt 2025: **83 người/83 lượt, 0 được công bố ngay, 0 publication**; phải rà soát trước. Sau nâng V36: vẫn **83 lượt, 2.216 kết quả, 936 dòng nguồn pending, 0 lượt CONFIRMED, 0 publication**. Backup SQL riêng trước V36 và proof số lượng ở thư mục private dev. Không phát hành kết quả thật để chạy demo.
- PDF giả lập đọc bằng PDFBox và render bằng Poppler; ảnh/file demo riêng không chứa dữ liệu thật. Chưa nghiệm thu V36 trên MySQL thật/rollout nhiều instance hoặc cổng cá nhân.

## Phần còn lại

Baseline thật 2023–2024 còn chờ người quản trị xác minh liên kết ở Phase 3. Phase 5 còn cần người dùng mở Excel/LibreOffice duyệt mẫu. Phase 7 cần chốt cơ chế xác minh ngoài CCCD/mã nhân viên trước khi mở Internet; Phase 6 không coi mã nhân viên/CCCD là bí mật và chưa cấp phiên tra cứu cá nhân.
