# Tra cứu dự án bằng Graphify MCP

Graphify đã được cài tại `~/.local/share/graphify/venv` và đăng ký MCP `graphify` trong cấu hình Codex của máy này. Server chạy qua stdio và đọc `graphify-out/graph.json` của CFCBase. Cấu hình này gắn với đường dẫn local; khi di chuyển repository cần sửa lại đối số MCP. Không cần API key để truy vấn đồ thị.

Sau khi cài hoặc sửa cấu hình MCP, tải lại Codex/IDE và mở phiên mới. Dùng `codex mcp get graphify` để xem cấu hình. Phiên chat đang mở có thể chưa nhận tool mới.

Có thể yêu cầu trợ lý: “Dùng Graphify MCP, giải thích các quan hệ quanh HrNightRewardService, depth 1, budget 1500 token”. Với câu hỏi khác, nêu tên module hoặc symbol cụ thể để tìm đúng nhánh. Khi kết quả bị cắt, thu hẹp câu hỏi hoặc lọc quan hệ trước khi tăng budget. CodeGraph tiếp tục dùng để đọc source hiện tại và xác minh trước khi sửa code.

MCP trả về phần đồ thị phù hợp thay vì đọc toàn bộ repository. Điều này có thể giảm token ngữ cảnh cho câu hỏi kiến trúc và quan hệ; không bảo đảm một tỷ lệ tiết kiệm cố định. Không đưa toàn bộ `graph.json`, cache hoặc báo cáo vào chat. Các quy tắc tra cứu đã được ghi trong `AGENTS.md` cho phiên sau.

Sau khi sửa mã nguồn, chạy từ repository:

```bash
bash scripts/update-knowledge-graph.sh
```

Script cập nhật AST local, giữ lớp semantic và tạo lại các cụm/báo cáo/HTML mà không gọi LLM đặt tên cụm. Nếu sửa tài liệu hoặc hình ảnh, cần chạy lại quy trình skill Graphify để trích xuất semantic. Truy vấn MCP tự nạp lại đồ thị khi tệp thay đổi; truy vấn không tự quét lại source. Không có watcher hoặc git hook tự chạy.

Khi MCP chưa có trong phiên, vẫn có thể dùng CLI:

```bash
~/.local/share/graphify/venv/bin/graphify query "HrNightRewardService" --budget 1500
```

Bản đồ chỉ mô tả các tệp đã được phân tích, không đọc trạng thái DB production hoặc toàn bộ hệ điều hành. Một số tham chiếu chưa phân giải và cạnh bị gộp được ghi trong `graphify-out/DIAGNOSTICS.md`; luôn đối chiếu code cho kết luận chính xác. Artifact và cache được `.gitignore` bỏ qua.

Đã kiểm tra bắt tay stdio, danh sách tool, `graph_stats` và `query_graph` bằng MCP client thực tế. Kiểm tra trong sandbox bị timeout; cùng kiểm tra chạy thành công bên ngoài sandbox. Không chạy build/test ứng dụng vì chỉ thay đổi cấu hình công cụ và tài liệu.
