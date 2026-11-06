# Kế hoạch CI/CD cho server Fedora

## Mục tiêu

Giữ nguyên server Fedora, Docker/MySQL và Cloudflare Tunnel. Thay thao tác lặp `git pull` + chạy build bằng kiểm tra tự động và sau đó là deploy có kiểm soát, có health-check và đường quay lại phiên bản ứng dụng trước.

## CI và CD khác nhau thế nào?

- **CI (Continuous Integration):** GitHub tự kiểm tra code. CI không đăng nhập server và không thay đổi website.
- **CD (Continuous Delivery/Deployment):** sau khi CI đạt, một quy trình chuyển phiên bản mới lên server. Chặng này có quyền tác động production nên cần thiết kế và thử riêng.
- **Self-hosted runner:** chương trình đặt trên Fedora, kết nối outbound tới GitHub để nhận việc. Không cần mở SSH hay cổng dịch vụ của ứng dụng ra Internet.

## Các chặng

### Chặng 1 — CI, đã thêm workflow local

`.github/workflows/ci.yml` chạy khi push và khi mở pull request vào `main`:

1. Frontend: `npm ci`, `npm run lint`, `npm run build`.
2. Backend: Java 21, `./mvnw test`.

Chặng này chỉ chạy trên GitHub-hosted runners. Không có secret production, không gọi server, không deploy. Sau khi push file workflow lên GitHub, mở tab **Actions** để xem kết quả.

### Chặng 2 — Làm deploy hiện tại an toàn

**Trạng thái 05/11/2026:** Đã cập nhật script local để giữ JAR trước, ghi commit/thời điểm release, giữ Cloudflare Tunnel chạy trong lúc restart backend và tự rollback JAR khi backend không health-check được hoặc bước xác minh schema thất bại. Đã kiểm tra cú pháp Bash và `--help`; chưa chạy build/deploy trên Fedora, chưa kiểm chứng rollback với database production.

Trước khi tự động hóa, sửa và thử script Linux để:

1. Build trong thư mục staging, không thay JAR đang chạy khi build chưa đạt.
2. Giữ nguyên một release trước đó và ghi commit/version được kích hoạt.
3. Dùng health endpoint phù hợp của ứng dụng để kiểm tra sau khi khởi động.
4. Khi health-check lỗi, khôi phục JAR cũ và khởi động lại phiên bản cũ; báo rõ nếu rollback lỗi.
5. Không rollback database tự động. Flyway thường tiến schema về phía trước; phải xác nhận migration tương thích ngược trước mỗi lần deploy. Backup DB vẫn cần theo chính sách hiện tại.

`run.sh` giữ Cloudflare Tunnel chạy khi backend khởi động lại; nếu health-check hoặc xác minh thất bại, script thử rollback JAR trước. Đã chạy thành công một lần trên Fedora; vẫn cần theo dõi các lần deploy và thử quy trình rollback có kiểm soát. Database không tự rollback.

### Chặng 3 — Runner và bảo vệ main

**Ngày 06/11/2026:** ảnh người dùng xác nhận runner `cfcbase-fedora-prod` online/Idle với label `cfcbase-production`; ruleset `Protect main` Active, nhắm `main`; hai check `Frontend lint and build` và `Backend tests` đã được chọn (cần bấm Save changes). Các lần CI gần nhất trên main xanh. Đây là xác nhận qua ảnh, không phải kiểm tra Settings bằng API.

Người dùng quyết định giữ repository **Public**. Giữ cùng checkout `~/Works/BookingBase`, `.env`, Cloudflare credential, user `david-nguyen` và backend/tunnel user services hiện có. Repo GitHub hiện mang tên `ThuanNgcodelor/CFC-Management`, URL cũ `CFCBase` được chuyển hướng.

Do runner nằm trên production dưới user thuộc `wheel`/`docker`, phương án vận hành tiếp theo chuyển sang **Fedora chủ động pull**, không tạo workflow deploy trên runner này. Trước lần deploy đầu tiên, chạy `systemctl --user disable --now cfcbase-runner.service`, rồi gỡ runner trong GitHub Settings → Actions → Runners. Người dùng đã gửi log xác nhận user service runner bị disable/stop; thao tác gỡ đăng ký trên GitHub chưa được xác nhận. CI vẫn chạy trên `ubuntu-latest`.

### Chặng 4 — Deploy thủ công từ Fedora sau khi CI đạt

**Đã chuẩn bị local:** `deployserver/linux/ci-deploy.py`, dùng Python 3 standard library, không cần PAT cho repo public. Fedora gọi GitHub REST API ra ngoài, không mở thêm cổng mạng. Script chỉ nhận main, từ chối checkout có thay đổi chưa lưu hoặc lịch sử không fast-forward; ghim SHA sau fetch rồi kiểm tra đúng CI workflow `ci.yml`, event push, branch main và đúng SHA. Run mới nhất phải hoàn tất thành công, đồng thời cả hai job bắt buộc đều success (skipped không được tính đạt). API lỗi/rate-limit hoặc CI chưa chạy xong đều chặn deploy.

- `--check`: fetch và báo CI/commit; không merge, build hoặc restart dịch vụ.
- `--deploy`: người vận hành chủ động cho phép cập nhật; kiểm tra lại checkout, fast-forward tới SHA đã kiểm tra, gọi `build-prod.sh`. Chặn nếu user service runner cũ còn active. Các lần chạy wrapper dùng chung khóa để tránh deploy trùng.
- Health-check/rollback JAR vẫn do script hiện có xử lý; không tự rollback database hoặc reset working tree khi deploy lỗi. Nếu build lỗi, website cũ vẫn chạy nhưng checkout có thể đã tiến tới commit mới. Không chạy build/deploy thủ công khác đồng thời với wrapper.

**Trên Fedora, sau khi code script đã có trên main:**

```bash
cd ~/Works/BookingBase
systemctl --user disable --now cfcbase-runner.service
git pull --ff-only origin main
python3 deployserver/linux/ci-deploy.py --check
```

Lệnh pull đầu tiên chỉ để nhận công cụ mới, không tự build/deploy. Sau đó gỡ runner trên GitHub. Khi `--check` báo đủ CI thành công, muốn cập nhật website thì chạy:

```bash
python3 deployserver/linux/ci-deploy.py --deploy
```

Từ các lần sau, wrapper tự fetch/kiểm tra/cập nhật, không cần git pull riêng. Chỉ chạy dưới `david-nguyen`, không dùng sudo. Người dùng đã gửi log deploy thành công release `833d18d3872beec05e608d41b60877ea6368681b`: build/smoke/schema đạt, backend và tunnel active, checkout sạch sau build. Smoke API có đăng nhập bị bỏ qua vì chưa có token; chưa kiểm chứng rollback lỗi trên production. Marker `-dirty` lúc build có thể do thư mục staging, không đồng nghĩa checkout sau build còn bẩn; Chặng 5 bổ sung ignore riêng cho thư mục sinh tự động này.

### Chặng 5 — Fedora tự kiểm tra và deploy

**Đã cài/bật trên Fedora theo log người dùng ngày 06/11/2026:** systemd user timer gọi `ci-deploy.py --auto`. CI có thêm job `Deployment script checks` để chạy kiểm thử wrapper/state/installer. Các job đều chạy trên GitHub-hosted runners.

Luồng sau khi bật: push nhánh → CI → merge vào main → CI push/main xanh → Fedora fetch main qua HTTPS Public và deploy sau lần kiểm tra tiếp theo (khoảng 5 phút, cộng jitter và thời gian build). Không cần GitHub runner, PAT hoặc SSH agent. Git remote hiện có không bị đổi. Timer không nhận hoặc thực thi code từ PR.

Các điều kiện của auto-deploy:

- Bắt buộc có một lần deploy thủ công thành công bằng script mới để ghi baseline `.git/cfcbase-deploy-state.json`. Không suy đoán baseline từ HEAD hoặc marker cũ có `-dirty`.
- Bỏ qua commit đã chạy thành công, không build/restart hoặc gọi GitHub API lại cho cùng release. Vẫn fetch Git để phát hiện bản mới. Backend/tunnel phải active, marker release phải khớp trạng thái thành công; khi chúng lệch nhau thì chặn, không tự sửa.
- Checkout phải là main sạch, fast-forward được. CI phải thành công đúng SHA và đủ ba job: Frontend lint and build, Backend tests, Deployment script checks. CI chưa xong/đỏ thì chờ, API lỗi thì không deploy và kiểm tra lại ở lần timer tiếp theo.
- Migration SQL mới có version lớn hơn release đã chạy, được review tương thích ngược và khai báo đúng SHA256 trong `auto-migrations.json` được tự deploy sau backup. V33 đã khai báo. Sửa/xóa migration cũ, Java migration, version trùng/cũ hoặc thiếu khai báo đều bị chặn trước merge/build. Quy tắc này thay thế gate “mọi migration phải manual” theo yêu cầu người dùng 06/11/2026.
- Trước khi merge/build phải ghi trạng thái `deploying`; sau khi build, health/schema và trạng thái release/dịch vụ đạt mới ghi `success`. Lỗi ghi `failed`; timeout/mất điện có thể để lại `deploying`. Cả hai trạng thái đều chặn mọi lần auto tiếp theo, kể cả khi main có commit khác.
- Chỉ retry sau khi xử lý nguyên nhân và chủ động chạy `--deploy --retry-failed`. Không reset code, không xóa state để bỏ qua chặn, không tự sửa/rollback DB. Wrapper dùng một lock cho manual/auto; không chạy build-prod riêng đồng thời với timer.

**Cài lần đầu trên Fedora, sau khi PR Chặng 5 đã merge và CI main xanh:**

```bash
cd ~/Works/BookingBase
git pull --ff-only origin main
python3 deployserver/linux/ci-deploy.py --deploy
python3 deployserver/linux/ci-deploy.py --status
```

Deploy thủ công này cần chạy một lần để tạo baseline mới; chỉ tiếp tục nếu thành công. Bật linger để user services tồn tại sau logout/reboot nếu chưa có:

```bash
sudo loginctl enable-linger david-nguyen
python3 deployserver/linux/install-deploy-timer.py
systemctl --user start bookingbase-deploy.service
systemctl --user list-timers bookingbase-deploy.timer --no-pager
journalctl --user -u bookingbase-deploy.service -n 80 --no-pager
```

Installer không chạy bằng sudo; kiểm tra main sạch, baseline/release/dịch vụ và linger trước khi bật. Nó lưu PATH/JAVA_HOME hiện tại cho môi trường systemd, không sao chép secret production. Lần start service đầu tiên sẽ báo không cần build lại nếu main chưa thay đổi. Python 3 và các tool build phải có sẵn như khi deploy thủ công. Thay đường dẫn JDK/Node thì chạy lại installer để cập nhật môi trường.

Timer kiểm tra sau boot 2 phút, rồi 5 phút sau khi lần chạy trước kết thúc, có độ trễ ngẫu nhiên tối đa 30 giây và AccuracySec 30 giây. Một lượt tối đa 30 phút; timeout sẽ để lại state chặn retry. Không thêm Restart tự động. GitHub API public giới hạn 60 request/giờ/IP; chu kỳ này dùng tối đa khoảng 24 request/giờ khi liên tục có bản mới, và không gọi API khi không có bản mới. Nếu chung IP có tải khác/rate-limit, wrapper dừng lượt đó, không deploy. [GitHub rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api), [systemd timer](https://www.freedesktop.org/software/systemd/man/latest/systemd.timer.html).

**Tạm dừng lịch tự deploy:**

```bash
systemctl --user disable --now bookingbase-deploy.timer
```

Lệnh dừng lên lịch; nếu service deploy đang chạy, chờ nó hoàn tất rồi mới thao tác manual. Bật lại bằng `systemctl --user enable --now bookingbase-deploy.timer`. Nếu auto đã bị chặn do failed/deploying, bật timer không xóa chặn. Đọc journal và backend log để xử lý; sau đó retry có chủ đích:

```bash
python3 deployserver/linux/ci-deploy.py --deploy --retry-failed
```

Kiểm thử local: `python3 -m unittest discover -s deployserver/linux/tests -v` hiện gồm 45 test, trong đó có repository Git tạm thật. Build/service/GitHub được mô phỏng để không chạm server. Log người dùng xác nhận deploy thủ công release `cbaebcef7549af664f8f00fecb7b9e0bc5250556` thành công, marker sạch và --status sẵn sàng; installer enable timer, service fetch Public HTTPS và kết thúc với “Đang chạy bản mới nhất; không build/restart lại.” Lịch tiếp theo khoảng 5 phút. Chưa chứng minh tự deploy một commit mới, chạy sau reboot hoặc rollback có chủ đích trên Fedora.

### Chặng 5 bổ sung — Tự build, backup và chuyển release sau merge

**Đã triển khai và kiểm thử local, chưa cài phiên bản bổ sung này trên Fedora.** Không thêm runner production hoặc workflow chạy trên Fedora. Timer hiện có tiếp tục fetch commit main mỗi khoảng 5 phút và chỉ thực thi sau CI xanh đúng SHA. CI đỏ/chưa xong không đổi code/release và không khởi động bản mới.

Luồng khi có migration được duyệt:

1. Kiểm tra baseline release, main sạch, CI cả ba job và khai báo migration từ blob Git của candidate (không đọc nội dung chưa commit).
2. Ghi `deploying`, fast-forward checkout; JAR cũ vẫn phục vụ trong lúc npm/Maven build và smoke-test JAR mới.
3. Build/smoke đạt mới chạy backup database bằng script có sẵn; kiểm tra gzip, schema/table và footer kết thúc mysqldump. Dump lỗi/truncated không được publish. Backup tệp 0600, thư mục 0700, giữ 24 bản theo cấu hình hiện có; tên có nanosecond tránh ghi đè hai lần gần nhau.
4. Backup đạt mới thay JAR và marker rồi chạy launcher. Nếu chưa đổi migration, bỏ qua backup deploy này (timer backup định kỳ vẫn riêng).
5. Nếu start/health/schema lỗi, launcher thử khôi phục JAR và chạy lại bản cũ. Nếu preflight launcher lỗi trước restart, build script khôi phục JAR/marker trên đĩa. Ghi `failed`, giữ last_success_sha và dừng auto toàn bộ tới khi xử lý/retry có chủ đích; không che lỗi rollback thất bại.
6. Chỉ ghi `success` khi marker đúng SHA và backend/tunnel đạt kiểm tra. Database không tự restore hoặc rollback; backup chưa thay thế kiểm thử restore và review tương thích.

**Một lần nâng cấp deploy-agent trên Fedora đã có baseline/timer:** agent cũ vẫn chặn migration trước khi tự cập nhật code, nên cần pull bộ script mới một lần. Làm sau khi PR đã merge và CI main xanh:

```bash
cd ~/Works/BookingBase
systemctl --user stop bookingbase-deploy.timer
flock -n .git/cfcbase-deploy.lock git pull --ff-only origin main
```

Chỉ tiếp tục nếu pull thành công. Nếu flock báo bận, chờ lượt deploy đang chạy kết thúc rồi chạy lại; không dừng service đang build/migrate và không reset/stash code. Khóa này trùng khóa wrapper, ngăn pull trong lúc deploy. Pull chỉ nâng code/script, chưa restart JAR đang phục vụ.

```bash
systemctl --user enable --now bookingbase-deploy.timer
systemctl --user start bookingbase-deploy.service
journalctl --user -u bookingbase-deploy.service -n 80 --no-pager
```

Nếu CI chưa xanh, service chờ lượt timer tiếp theo; không cần chạy tay mỗi release. Nếu state cũ là failed/deploying hoặc marker lệch baseline, phải xử lý nguyên nhân trước và dùng `--deploy --retry-failed`, không xóa state. Máy chưa có baseline/timer vẫn dùng phần cài lần đầu phía trên.

**Từ máy dev cho các bản sau:** push nhánh `feat/fedora-deploy` hoặc nhánh tính năng → PR main → CI xanh → merge → CI push/main xanh → Fedora tự build/deploy. Không push trực tiếp main để né ruleset.

**Khi dev thêm migration:** review rằng JAR trước vẫn chạy được sau khi áp dụng, thêm entry đường dẫn/sha256/backwardCompatible/reason vào `deployserver/linux/auto-migrations.json` trong cùng PR. SHA tính từ đúng bytes SQL đã commit (`sha256sum <path>`). Declaration là cam kết review, không phải máy tự chứng minh SQL an toàn. CI kiểm tra manifest còn khớp nội dung; không khai báo phá cấu trúc như xóa/rename/cột bắt buộc không tương thích. V33 chỉ thêm một bảng độc lập nên đã được khai báo. Migration chưa được phép auto vẫn triển khai manual sau rà soát; manual có thay đổi migration hoặc chưa có baseline cũng bắt buộc backup sau build và trước kích hoạt.

**Giới hạn:** vẫn một backend tại cổng 8080 nên lúc restart có gián đoạn ngắn. Không có blue-green hoặc đảm bảo zero downtime; không tự restore DB đang có dữ liệu mới phát sinh. Kiểm thử local dùng Git repo thật và thực thi các shell script với Docker/build/systemd giả lập: build fail, smoke fail, backup fail/truncated, start fail, health fail, rollback thành công/thất bại. Chưa nghiệm thu backup/rollback trên Fedora thật. Kiểm tra cuối local: 45 deploy tests đạt; backend Maven offline Java 21 chạy 261 test, 0 failure/error, 4 skipped; frontend build đạt, lint 0 lỗi/10 cảnh báo cũ; Bash syntax và git diff --check đạt.

### Chặng 6 — Giảm downtime (tùy chọn)

Hiện backend và frontend được phục vụ chung qua ứng dụng Java tại cổng local 8080. Restart có thể tạo gián đoạn ngắn. Blue-green cần thêm reverse proxy local và hai bản ứng dụng chạy luân phiên; chỉ làm nếu thời gian gián đoạn hiện tại không chấp nhận được.

## Cách dùng sau Chặng 1

1. Commit và push workflow cùng code lên GitHub.
2. Mở repo GitHub → tab **Actions**.
3. Chọn workflow **CI** và xem hai job Frontend/Backend.
4. Nếu đỏ, mở job lỗi để xem dòng kiểm tra thất bại. Website production không bị ảnh hưởng bởi workflow này.

## Điều kiện trước khi bật deploy tự động

- CI xanh ổn định trên GitHub.
- Có backup gần nhất và quy trình restore đã kiểm chứng.
- Script health-check/rollback được thử trên môi trường không production hoặc trong cửa sổ bảo trì.
- Runner production cũ đã dừng/gỡ khỏi repo public; cơ chế pull không nhận lệnh từ workflow PR.
- Chốt rõ việc migration database nào được phép chạy tự động và phương án xử lý migration lỗi.
- Thử ít nhất một lần deploy lỗi có chủ ý trên môi trường thử để chứng minh bản cũ vẫn phục hồi được.
