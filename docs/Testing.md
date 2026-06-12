# Kiểm Thử

## 1. Tổng Quan và Kỹ Thuật Kiểm Thử

Hệ thống được kiểm thử theo ba tầng độc lập nhau: **kiểm thử đơn vị (unit test)** cho từng lớp nghiệp vụ, **kiểm thử tích hợp (integration test)** với hạ tầng thực thông qua Testcontainers, và **kiểm thử đầu cuối (end-to-end test)** trên toàn bộ ngăn xếp microservice.

Các kỹ thuật kiểm thử áp dụng:

| Kỹ thuật | Nơi áp dụng |
|---|---|
| **Phân hoạch tương đương** (Equivalence Partitioning) | Kiểm thử chuẩn hóa log: phân lớp CLF hợp lệ / Combined Format / Flow JSON / đầu vào lỗi |
| **Phân tích giá trị biên** (Boundary Value Analysis) | Kiểm thử ngưỡng leo thang: lần 1, lần 2, đúng ngưỡng 3, vượt ngưỡng |
| **Kiểm thử chuyển trạng thái** (State Transition Testing) | Vòng đời IP: chưa chặn → bị chặn → hết TTL → bị loại khỏi tập hợp |
| **Kiểm thử hộp trắng** (White-box / Mock Verification) | Xác minh đúng phương thức Redis được gọi với đúng tham số trong unit test |
| **Kiểm thử fail-open** | Xác minh hệ thống không chặn hợp lệ khi Redis ném ngoại lệ timeout |
| **Kiểm thử đường lỗi** (Error Path Testing) | Payload không hợp lệ → DLQ; `receivedAt = null` → từ chối trước khi vào hàng đợi |
| **Kiểm thử hệ thống tích hợp container** | Testcontainers khởi động PostgreSQL 17.5, RabbitMQ 4.2.4, Redis 7.4 thực |
| **Kiểm thử đầu cuối bất đồng bộ** | pytest-asyncio + polling có timeout trên toàn bộ pipeline 5 service |

---

## 2. Chức Năng 1 — Chuẩn Hóa Bản Ghi Log (Log Processing Service)

`LogProcessingService` là cửa vào duy nhất của dữ liệu thô vào hệ thống. Hàm `process()` phân tích cú pháp định dạng CLF/Combined (HTTP) và JSON (Flow), tách trường, và trả về `ProcessingResult` tương ứng.

**Kỹ thuật sử dụng:** Phân hoạch tương đương (7 lớp tương đương), Phân tích giá trị biên (chuỗi `-` cho kích thước phản hồi).

**Phương thức được kiểm thử:** `LogProcessingService.process(RawLog)` — duy nhất, qua `LogProcessingServiceTest` (JUnit/AssertJ).

| TC# | Mô tả | Đầu vào đại diện | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| LP-01 | CLF tối giản hợp lệ | `192.168.1.1 - - [...] "GET /index.html HTTP/1.0" 200 1234` | `ip=192.168.1.1`, `method=GET`, `status=200`, `size=1234`, `queryString=""` | Đạt |
| LP-02 | Combined Format (có Referer + UA) | `10.0.0.2 - - [...] "POST /api/login HTTP/1.1" 401 512 "https://..." "Mozilla/5.0"` | `referer="https://..."`, `userAgent="Mozilla/5.0"` | Đạt |
| LP-03 | URL có query string | `GET /search?q=test&page=2 HTTP/1.0` | `url="/search"`, `queryString="q=test&page=2"` | Đạt |
| LP-04 | Kích thước phản hồi là `-` (giá trị biên) | `"HEAD / HTTP/1.0" 304 -` | `responseSize=0` | Đạt |
| LP-05 | Dòng log không hợp lệ | `"not a valid log line"` | Ném `IllegalArgumentException("Unparseable CLF entry")` | Đạt |
| LP-06 | Flow JSON hợp lệ | JSON với `source_ip`, `dest_ip`, `features` | `sourceIp`, `destIp`, `features` map đầy đủ | Đạt |
| LP-07 | Flow JSON thiếu trường IP tuỳ chọn | JSON không có `source_ip`, `dest_ip` | `sourceIp=""`, `destIp=""` (mặc định rỗng) | Đạt |
| LP-08 | Flow JSON không hợp lệ | `"definitely not json"` | Ném `RuntimeException("Failed to parse flow record")` | Đạt |
| LP-09 | Feature với giá trị zero | `"zeroed_feature": 0.0` trong map | Giá trị zero được giữ nguyên trong `features` | Đạt |

**Thống kê:** 9 trường hợp kiểm thử đơn vị (`LogProcessingServiceTest`) bao phủ 7 lớp tương đương định dạng log (CLF tối giản, Combined, query string, Flow JSON hợp lệ/thiếu trường/không hợp lệ, dòng log lỗi) và phân tích giá trị biên (kích thước phản hồi `-`, feature zero) — **9/9 đạt**.

---

## 3. Chức Năng 2 — Leo Thang Phản Ứng và Chặn IP (Reaction Service)

Đây là chức năng trọng tâm bảo mật: mỗi lần phát hiện tấn công từ một IP tích lũy vào bộ đếm Redis. Dưới ngưỡng → giới hạn tốc độ (`RATE_LIMIT`); đạt ngưỡng → chặn hoàn toàn (`BLOCK`). Chính sách whitelist ngăn chặn chặn nhầm IP đáng tin cậy.

**Kỹ thuật sử dụng:** Phân tích giá trị biên (ngưỡng leo thang = 3), Kiểm thử chuyển trạng thái (chưa chặn → chặn → TTL hết hạn), Kiểm thử fail-open.

**Phương thức được kiểm thử:** `EscalatingIpReactionService.doHandle(ReactionInput)` (logic leo thang dùng chung cho `BruteForceReactionService`/`DDoSReactionService`); `RedisRateLimitService.limit(String, Severity)` / `isLimited(String)`; `RedisIpBlockService.block(String, Severity)` / `isBlocked(String)`.

### 3.1 Kiểm Thử Đơn Vị (Mockito)

| TC# | Mô tả | Điều kiện tiên quyết | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| RX-01 | Lần phát hiện đầu tiên → `RATE_LIMIT` | Script Redis trả về `1L` | `rateLimitService.limit()` được gọi; `ipBlockService.block()` không được gọi; ghi log `RATE_LIMIT` | Đạt |
| RX-02 | Lần phát hiện thứ hai → vẫn `RATE_LIMIT` | Script Redis trả về `2L` | Vẫn chỉ gọi `rateLimitService.limit()` | Đạt |
| RX-03 | Đúng ngưỡng leo thang (lần 3) → `BLOCK` | Script Redis trả về `3L` | `ipBlockService.block()` được gọi; `rateLimitService.limit()` không được gọi; ghi log `BLOCK` | Đạt |
| RX-04 | Vượt ngưỡng (lần 10) → `BLOCK` | Script Redis trả về `10L` | `ipBlockService.block()` được gọi | Đạt |
| RX-05 | Script Redis trả về `null` → xử lý như lần đầu | Script trả về `null` | `rateLimitService.limit()` được gọi (không bị NullPointerException) | Đạt |
| RX-06 | IP trong whitelist → bỏ qua chặn | Redis xác nhận IP có trong `whitelist:ips` | Không ghi vào `blocklist:ips`; không tạo metadata key | Đạt |
| RX-07 | TTL chặn theo mức độ nghiêm trọng | `LOW` / `MEDIUM` / `CRITICAL` | TTL lần lượt là 5 phút / 30 phút / 24 giờ | Đạt |
| RX-08 | Redis ném `QueryTimeoutException` khi kiểm tra trạng thái chặn | `hasKey()` ném timeout | `isBlocked()` trả về `false` (fail-open) | Đạt |
| RX-09 | Metadata key hết hạn TTL → tự dọn | `blocklist:ips` có IP nhưng `blocklist:ip:{ip}` không tồn tại | `isBlocked()` trả về `false` và xoá IP khỏi `blocklist:ips` | Đạt |

### 3.2 Kiểm Thử Tích Hợp (Testcontainers + Redis thực)

| TC# | Mô tả | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|
| RX-IT-01 | `block()` → IP tồn tại trong cả set và metadata key | `isMember("blocklist:ips")=true`; `hasKey("blocklist:ip:{ip}")=true` | Đạt |
| RX-IT-02 | `block()` với IP được whitelist → không ghi gì vào Redis | Không có key nào được tạo | Đạt |
| RX-IT-03 | Mô phỏng TTL hết hạn thủ công → `isBlocked()` dọn dẹp set | Trả về `false`; `isMember` sau đó cũng `false` | Đạt |
| RX-IT-04 | `DetectionResultConsumerIT`: DDoS lần 3 → BLOCK trong Redis | Key `blocklist:ip:{ip}` tồn tại sau đủ 3 lần phát hiện | Đạt |
| RX-IT-05 | IP whitelist không bị chặn dù phát hiện CRITICAL | Không có key blocklist nào được tạo | Đạt |

---

## 3.3 Quản Lý Whitelist và Gỡ Chặn IP (Dashboard)

Dashboard cung cấp API cho quản trị viên xem/thay thế danh sách IP whitelist (`whitelist:ips` trong Redis) và gỡ chặn (lift) hàng loạt các IP đang nằm trong `blocklist:ips`.

**Kỹ thuật sử dụng:** Kiểm thử hộp trắng (Mockito cho `WhitelistStore`), Kiểm thử tích hợp container (`ReactionControllerIT` với Redis thực).

**Phương thức được kiểm thử:** `WhitelistStore.listWhitelistedIps()` / `replaceWhitelist(List<String>)`; `ReactionController` — `GET/PUT /api/reactions/whitelist`, `POST /api/reactions/blocks/lift`.

### 3.3.1 Kiểm Thử Đơn Vị (Mockito)

| TC# | Mô tả | Điều kiện tiên quyết | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| WL-01 | `listWhitelistedIps()` khi set rỗng (`null`) | `members()` trả về `null` | Trả về danh sách rỗng | Đạt |
| WL-02 | `listWhitelistedIps()` trả về đúng các IP | `members()` trả về `{1.2.3.4, 5.6.7.8}` | Danh sách chứa đúng 2 IP | Đạt |
| WL-03 | `replaceWhitelist()` xóa và ghi lại toàn bộ | Danh sách mới `{1.2.3.4, 5.6.7.8}` | Gọi `delete()` rồi `add()` với cả 2 IP | Đạt |
| WL-04 | `replaceWhitelist()` với danh sách rỗng | Danh sách mới rỗng | Chỉ gọi `delete()`, không gọi `add()` | Đạt |
| WL-05 | `listWhitelistedIps()` trả về danh sách bất biến | `members()` trả về `{1.2.3.4}` | Gọi `add()` trên kết quả ném `UnsupportedOperationException` | Đạt |

### 3.3.2 Kiểm Thử Tích Hợp (Testcontainers + Redis thực)

| TC# | Mô tả | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|
| WL-IT-01 | `GET /api/reactions/whitelist` khi chưa có entry | Trả về mảng rỗng | Đạt |
| WL-IT-02 | `GET /api/reactions/whitelist` khi có IP trong `whitelist:ips` | Trả về mảng chứa đúng IP | Đạt |
| WL-IT-03 | `PUT /api/reactions/whitelist` với danh sách IP mới | Whitelist cũ bị thay thế hoàn toàn bởi danh sách mới | Đạt |
| WL-IT-04 | `PUT /api/reactions/whitelist` với body rỗng | `whitelist:ips` bị xóa sạch | Đạt |
| WL-IT-05 | `POST /api/reactions/blocks/lift` với danh sách IP | Khóa `blocklist:ip:{ip}` và thành viên trong `blocklist:ips` bị xóa cho từng IP | Đạt |

**Thống kê:** 14 trường hợp kiểm thử đơn vị (9 cho leo thang phản ứng RX-01–RX-09 + 5 cho quản lý whitelist WL-01–WL-05) và 10 trường hợp kiểm thử tích hợp (5 RX-IT-01–RX-IT-05 + 5 WL-IT-01–WL-IT-05) = **24 trường hợp**, tất cả đạt.

---

## 4. Chức Năng 3 — Pipeline Phát Hiện Tấn Công Đầu Cuối (E2E)

Kiểm thử đầu cuối xác nhận toàn bộ dòng chảy dữ liệu: Simulation → log.raw → Processing → Detection → Reaction → Redis/PostgreSQL. Các bài kiểm thử được viết bằng Python (pytest-asyncio) và chạy trên môi trường Docker Compose đầy đủ (`compose.test.yml`).

**Kỹ thuật sử dụng:** Kiểm thử hệ thống bất đồng bộ với polling có timeout — mỗi bước trong pipeline được xác nhận tuần tự trước khi chuyển sang bước tiếp theo, đảm bảo không có điều kiện chạy đua giả tạo.

**Điểm vào được kiểm thử:** Publish trực tiếp vào queue `log.raw` (E2E-01, E2E-02, E2E-05, E2E-06) và Simulation API (`/simulate/start` với scenario `WEB_ATTACK`/`TRAFFIC_SPIKE`, E2E-03/E2E-04); xác nhận trạng thái cuối qua bảng PostgreSQL (`normalized_http`, `normalized_flow`, `detection_results`, `reaction_logs`, `drop_audit`) và key Redis (`ratelimit:ip:*`, `blocklist:ip:*`, `scale:state`).

| TC# | Kịch bản | Đầu vào | Các bước xác nhận | Kết quả thực tế |
|---|---|---|---|---|
| E2E-01 | **DDoS leo thang đến BLOCK** | 6 bản ghi flow với feature XGBoost đặc trưng DDoS (lưu lượng cực cao, gói tin lớn) gửi vào `log.raw` | (1) ≥6 bản ghi trong `normalized_flow` · (2) ≥3 `detection_results` loại `DDOS` · (3) Key `ratelimit:ip:{ip}` tồn tại · (4) Key `blocklist:ip:{ip}` tồn tại · (5) Cả `RATE_LIMIT` và `BLOCK` trong `reaction_logs` | Đạt |
| E2E-02 | **Brute force SSH leo thang** | 10 bản ghi flow port 22 với feature đặc trưng brute force | Tương tự E2E-01 nhưng detection_type=`BRUTE_FORCE` | Đạt |
| E2E-03 | **Web attack → chặn ngay** | 20 HTTP log có payload SQLi/XSS qua Simulation API (`WEB_ATTACK` scenario) | (1) ≥20 bản ghi `normalized_http` · (2) ≥1 `detection_results` loại `WEB_ATTACK` · (3) Key `blocklist:ip:{ip}` tồn tại · (4) Bản ghi `BLOCK` trong `reaction_logs` | Đạt |
| E2E-04 | **Traffic spike → scale up** | 200 HTTP request tốc độ cao (với lịch sử baseline được seed sẵn) | (1) Detection_type=`TRAFFIC` trong `detection_results` · (2) `reaction_logs` có `SCALE_UP` · (3) `scale:state = "scaled_up"` trong Redis | Đạt |
| E2E-05 | **Payload không hợp lệ → DLQ** | Chuỗi không phải JSON gửi vào `log.raw` | (1) Không có bản ghi trong `normalized_http`/`normalized_flow` · (2) Bản ghi trong `drop_audit` với `reason="DEAD_LETTERED"` | Đạt |
| E2E-06 | **`receivedAt=null` → từ chối trước hàng đợi** | `RawLog` với `receivedAt=null` | Không có bản ghi trong `normalized_*`; không có bản ghi trong `drop_audit` | Đạt |

*Lưu ý triển khai:* E2E-01 và E2E-02 trước đây là hai file riêng (`test_ddos.py`, `test_brute_force.py`) với hai hàm test gần như trùng lặp; nay đã gộp thành một hàm tham số hóa `test_flow_attack_detected_and_ip_blocked` (tham số `source_ip/dest_ip/dest_port/features/n_records/detection_type`, các case `[ddos]`/`[brute_force]`) trong `tests/e2e/test_flow_attacks.py` — cùng các bước xác nhận, không thay đổi phạm vi kiểm thử.

**Thống kê:** 6 kịch bản kiểm thử đầu cuối (E2E-01–E2E-06) bao phủ toàn bộ pipeline cho DDoS, brute force, web attack, traffic spike (leo thang đến scale-up), và 2 đường lỗi xử lý log (payload không hợp lệ, `receivedAt=null`) — **6/6 đạt**.

---

## 5. Chức Năng 4 — Xử Lý Lỗi và Hàng Đợi Chết (Log Processing)

Khi một bản ghi log không thể xử lý (payload hỏng, hết số lần thử lại), hệ thống không được phép làm mất dữ liệu một cách âm thầm: bản ghi phải được chuyển vào Dead Letter Queue (DLQ), lý do lỗi phải được ghi vào `drop_audit`, và lỗi ghi audit không được làm sập consumer. Đồng thời, `LogProcessingPoller` phải tự áp dụng backpressure khi hàng đợi xử lý nội bộ quá tải, tránh tràn bộ nhớ khi Redis dồn ứ bản ghi.

**Kỹ thuật sử dụng:** Phân hoạch tương đương (có/không có header `x-death`, JSON hợp lệ/hỏng/thiếu trường), Kiểm thử đường lỗi (lỗi tại tầng audit/Redis không lan truyền), Kiểm thử đồng thời (lấp đầy `ThreadPoolTaskExecutor` bằng `CountDownLatch` để xác minh ngưỡng backpressure một cách tất định, không flaky).

### 5.1 Kiểm Thử Đơn Vị (Mockito)

| TC# | Mô tả | Đầu vào | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| RES-01 | `DeadLetterConsumer`: nhiều entry `x-death` | Header `x-death` chứa `[{"reason":"expired"}, {"reason":"rejected"}]` | `recordDeadLetter()` được gọi với lý do của entry **đầu tiên** (`"expired"`) | Đạt |
| RES-02 | `DeadLetterConsumer`: thiếu header `x-death` | Message không có header `x-death` | Lý do ghi nhận là `"unknown"` | Đạt |
| RES-03 | `DeadLetterConsumer`: entry `x-death` thiếu khóa `reason` | `x-death = [{"queue":"raw-log-dlq"}]` | Lý do ghi nhận là `"unknown"` | Đạt |
| RES-04 | `DeadLetterConsumer`: body không phải JSON | Body = `"not json"` | `logId=null`, lý do `"unknown"`, không ném ngoại lệ | Đạt |
| RES-05 | `DeadLetterConsumer`: JSON hợp lệ nhưng thiếu trường `id` | Body = `{"message":"no id field"}` | `logId=null` | Đạt |
| RES-06 | `DeadLetterConsumer`: `dropAuditRepository.recordDeadLetter()` ném lỗi | Mock ném `RuntimeException("audit down")` | Không lan truyền ngoại lệ ra ngoài `onDeadLetter()` | Đạt |
| RES-07 | `DlqRetryScheduler`: ghi audit `RETRY_EXHAUSTED` ném lỗi khi đã hết số lần thử | `dropAuditRepository.record(..., RETRY_EXHAUSTED)` ném `RuntimeException` | `retryFailedLogs()` không ném lỗi; `logProcessingService.process()` không được gọi cho bản ghi đó | Đạt |
| RES-08 | `RawLogConsumer`: `queueService.enqueue()` ném lỗi | Mock ném `RuntimeException("redis down")` | Ném `AmqpRejectAndDontRequeueException` với `cause` là `RuntimeException` gốc → message vào DLQ | Đạt |
| RES-09 | `RawLogConsumer`: `receivedAt = null` | `RawLog` không có `receivedAt` | `enqueue()` **không** được gọi; message bị bỏ qua âm thầm (không vào DLQ) | Đạt |
| RES-10 | `RedisQueueService`: script Redis trả `null` | `redisTemplate.execute(...)` → `null` | `enqueue()` trả về `false` | Đạt |
| RES-11 | `RedisQueueService`: Redis ném lỗi khi enqueue | `redisTemplate.execute(...)` ném `RuntimeException("redis down")` | Ném `RuntimeException` mới với thông điệp chứa `"Failed to enqueue log id=..."` | Đạt |
| RES-12 | `LogProcessingPoller`: hàng đợi executor vượt ngưỡng backpressure | `corePoolSize=2`, `backpressureThreshold=5`; lấp đầy 8 tác vụ chặn (2 luồng + 6 hàng đợi > 5) | `queueService.dequeueBatch()` **không** được gọi trong 200ms tiếp theo | Đạt |

### 5.2 Kiểm Thử Tích Hợp (Testcontainers + RabbitMQ/PostgreSQL thực)

| TC# | Mô tả | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|
| RES-IT-01 | `DeadLetterConsumerIT`: message bị reject vào DLQ thực qua RabbitMQ | Bản ghi `drop_audit` được tạo với lý do trích từ header `x-death` thực | Đạt |
| RES-IT-02 | `DlqRetrySchedulerIT`: bản ghi lỗi được retry và xử lý lại thành công | Sau retry, bản ghi xuất hiện trong `processed_logs`; số lần thử được cập nhật trong DB | Đạt |

**Thống kê:** 12 trường hợp đơn vị mới (trong đó `DeadLetterConsumerTest` là file hoàn toàn mới với 6/6 trường hợp, trước đây `DeadLetterConsumer` không có kiểm thử đơn vị) + 2 trường hợp tích hợp đã có từ trước (`DeadLetterConsumerIT`, `DlqRetrySchedulerIT`) = **14 trường hợp**, tất cả đạt.

---

## 6. Chức Năng 5 — API Điều Khiển Mô Phỏng Tấn Công (Simulation Service)

`simulation_router` là cổng điều khiển duy nhất để bắt đầu/dừng kịch bản mô phỏng (`NORMAL`, `TRAFFIC_SPIKE`, `DDOS`, `BRUTE_FORCE`, `WEB_ATTACK`), phát lại dữ liệu CSV từ MinIO (`/replay`), và quản lý luồng traffic nền (`/baseline`). Trước vòng kiểm thử này, router (5 endpoint công khai + dependency `_get_replay_loader`) **chưa có kiểm thử nào**.

**Kỹ thuật sử dụng:** Kiểm thử hộp đen qua FastAPI `TestClient` với `dependency_overrides` (mock `SimulationUseCase`) và `unittest.mock.patch.object` (mock `ReplayLoader`); Phân hoạch tương đương cho các nhánh lỗi HTTP (202/409/503/404/422).

### 6.1 Kiểm Thử Router (FastAPI TestClient + Mock)

| TC# | Mô tả | Điều kiện tiên quyết | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| SIM-01 | `POST /start` với scenario hợp lệ | `scenario="TRAFFIC_SPIKE"` | HTTP 202; `log_type` được suy ra tự động từ `SCENARIO_LOG_TYPE` (`"HTTP"`); `use_case.start()` được gọi với `log_type=HTTP` | Đạt |
| SIM-02 | `POST /start` khi đã có mô phỏng chạy | `use_case.start()` ném `RuntimeError("Simulation already running")` | HTTP 409, `detail="Simulation already running"` | Đạt |
| SIM-03 | `POST /stop` | — | HTTP 200, `{"message": "Simulation stopped"}`; `use_case.stop()` được gọi | Đạt |
| SIM-04 | `GET /status` | `use_case.status()` trả về `{"state": "running", "sent": 5}` | HTTP 200 trả nguyên trạng thái từ use case | Đạt |
| SIM-05 | `POST /replay` với CSV hợp lệ | `loader.load()` trả `[{"feature_a": 1.0}]` | HTTP 202, `rows_loaded=1`; `use_case.replay()` nhận đúng `source_key` và `rows` | Đạt |
| SIM-06 | `POST /replay` khi MinIO chưa cấu hình | `_get_replay_loader()` trả `None` (`MINIO_ACCESS_KEY` rỗng) | HTTP 503 | Đạt |
| SIM-07 | `POST /replay` với `source_key` không tồn tại | `loader.load()` ném `FileNotFoundError` | HTTP 404 | Đạt |
| SIM-08 | `POST /replay` với CSV rỗng | `loader.load()` trả `[]` | HTTP 422 | Đạt |
| SIM-09 | `POST /replay` khi đang chạy mô phỏng khác | `use_case.replay()` ném `RuntimeError("Simulation already running")` | HTTP 409 | Đạt |
| SIM-10 | `GET /baseline` | `baseline_use_case.status()` trả `{"state": "running"}` | HTTP 200 trả nguyên trạng thái baseline | Đạt |
| SIM-11 | `POST /baseline/stop` | — | HTTP 200, `{"message": "Baseline stop signal sent"}`; `baseline_use_case.stop()` được gọi | Đạt |

**Thống kê:** 11 trường hợp mới (`test_simulation_router.py`, file hoàn toàn mới) bao phủ 100% nhánh điều khiển luồng của 6 endpoint (`/start`, `/stop`, `/status`, `/replay`, `/baseline`, `/baseline/stop`), kết hợp với 11 trường hợp đã có của `test_simulation_use_case.py` (logic khóa Redis, trạng thái, phát lại) → **22 trường hợp** cho toàn bộ chức năng điều khiển mô phỏng, tất cả đạt.

---

## 7. Chức Năng 6 — Lập Lịch Phát Hiện Tự Động và Ánh Xạ Kết Quả Hiển Thị

Hai thành phần phối hợp để đưa kết quả phát hiện đến người dùng theo thời gian thực: (1) `DetectionJobRunner` (log-analysis) chạy định kỳ theo lịch cron, lấy cửa sổ log gần nhất, gọi use case phát hiện traffic/web-attack, và duy trì lịch sử/baseline theo mùa trong Redis; (2) `DetectionMapper` (dashboard) ánh xạ entity `detection_results` từ PostgreSQL sang DTO hiển thị (`DetectionSummaryView`/`DetectionDetailView`), bao gồm việc làm giàu payload với `method_flags` cho phát hiện loại `TRAFFIC`. Trước vòng kiểm thử này, **cả hai thành phần đều không có kiểm thử**.

**Kỹ thuật sử dụng:** Kiểm thử hộp trắng với `AsyncMock`/`MagicMock` cho các cổng (`window_adapter`, `history_adapter`, use case); Phân hoạch tương đương cho `_get_trigger()` (cron 5 trường / 6 trường / số trường không hợp lệ); Kiểm thử bất biến đối tượng trả về (`job_status()` trả bản sao độc lập).

### 7.1 `DetectionJobRunner` (log-analysis, pytest + AsyncMock)

| TC# | Mô tả | Điều kiện tiên quyết | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| DJ-01 | Cửa sổ traffic rỗng | `window_adapter.get_window()` trả `[]` | Không gọi `traffic_use_case.execute()`; không gọi `update_history()` | Đạt |
| DJ-02 | Chạy traffic bình thường | Cửa sổ có 1 log; lịch sử `[100.0, 101.0]`; bucket mùa `[(95.0, 1.0)]` | `execute()` nhận `seasonal_summaries=[(95.0, 1.0)]`; `update_history("traffic:history", [100.0, 101.0, 1.0], limit=360)` | Đạt |
| DJ-03 | Chuyển giờ, đủ mẫu lịch sử (≥3) | `_last_traffic_hour=10`, giờ hiện tại=11, lịch sử có 3 mẫu | `update_timed_history("traffic:seasonal", ...)` được gọi đúng 1 lần; `_last_traffic_hour` → 11 | Đạt |
| DJ-04 | Chuyển giờ, không đủ mẫu (1 mẫu) | `_last_traffic_hour=10`, giờ hiện tại=11, lịch sử có 1 mẫu | `update_timed_history()` **không** được gọi; `_last_traffic_hour` vẫn → 11 | Đạt |
| DJ-05 | Web-attack: không có log mới | `_last_web_attack_run=1000.0`; log có `ts=500.0` | `web_attack_use_case.execute()` không được gọi; watermark giữ nguyên | Đạt |
| DJ-06 | Web-attack: có 2 log mới | `_last_web_attack_run=0.0`; log `ts=100.0` và `ts=200.0` | `execute()` được gọi 2 lần; `_last_web_attack_run` → 200.0 | Đạt |
| DJ-07 | Web-attack: 1 request lỗi | `execute()` ném `RuntimeError` ở lần gọi đầu | Lần gọi thứ 2 vẫn được thực thi (lỗi không chặn các request còn lại) | Đạt |
| DJ-08 | Cron 5 trường | `"*/10 * * * *"` | Trả về `CronTrigger` hợp lệ (không `None`) | Đạt |
| DJ-09 | Cron 6 trường | `"0 */10 * * * *"` | Trả về `CronTrigger` hợp lệ | Đạt |
| DJ-10 | Cron sai số trường | `"* * *"` | Ném `ValueError("Invalid cron expression...")` | Đạt |
| DJ-11 | `job_status()` trả bản sao độc lập | — | Sửa giá trị trả về không ảnh hưởng `_consecutive_failures` nội bộ | Đạt |

### 7.2 `DetectionMapper` (dashboard, JUnit/AssertJ)

| TC# | Mô tả | Điều kiện tiên quyết | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| DM-01 | `toSummary()` ánh xạ đầy đủ trường | Entity với đầy đủ các trường cơ bản | `id`, `detectionType`, `severity`, `anomaly`, `confidence`, `sourceIp`, `detectedAt` khớp đúng entity | Đạt |
| DM-02 | `toDetail()` với `TRAFFIC` + `methodFlags` | `detectionType=TRAFFIC`, `methodFlags={"GET":true,"POST":false}` | `payload` chứa khóa `"method_flags"` với giá trị tương ứng | Đạt |
| DM-03 | `toDetail()` với `TRAFFIC` + `methodFlags=null` | `detectionType=TRAFFIC`, `methodFlags=null` | `payload` rỗng (không có khóa `method_flags`) | Đạt |
| DM-04 | `toDetail()` với loại khác `TRAFFIC` (vd `DDOS`) dù có `methodFlags` | `detectionType=DDOS`, `networkLayer=FLOW`, `methodFlags={"GET":true}` | `payload` rỗng — `method_flags` chỉ áp dụng cho `TRAFFIC` | Đạt |

**Thống kê:** 11 trường hợp cho `DetectionJobRunner` (`test_detection_job_runner.py`, file mới — chạy `pytest -q` cho kết quả **11 passed**) + 4 trường hợp cho `DetectionMapper` (`DetectionMapperTest.java`, file mới) = **15 trường hợp**, nâng độ phủ của hai thành phần lập lịch/ánh xạ kết quả từ 0% lên kiểm thử đầy đủ các nhánh logic chính.

---

## 8. Tổng Kết Kết Quả Kiểm Thử

| Dịch vụ | Unit Test | Integration Test | Tổng |
|---|---|---|---|
| log-processing | 63 | 30 | **93** |
| log-analysis (Python) | 84 | 2 | **86** |
| reaction | 92 | 26 | **118** |
| dashboard | 39 | 52 | **91** |
| simulation (Python) | 140 | 4 | **144** |
| E2E (toàn hệ thống) | — | 8 | **8** |
| **Tổng cộng** | **418** | **122** | **540** |

Tất cả **540/540 trường hợp kiểm thử đều đạt** (passed).

> **Đã sửa**: 6 trường hợp trong `simulation/tests/test_scaler.py` trước đó thất bại do bộ test chưa được cập nhật theo 2 thay đổi trước đây của `infrastructure/scaler.py`: (1) `os.kill()` được gọi trong vòng lặp với `asyncio.sleep(0.1)` xen giữa mỗi tín hiệu (chống tràn hàng đợi tín hiệu gunicorn) khiến `asyncio.sleep` bị mock raise `CancelledError` quá sớm, trước khi đủ tín hiệu được gửi; (2) `init()` có guard `redis.exists(_LOCK_KEY)` nhưng test dùng `AsyncMock` mặc định (giá trị truthy) nên `redis.set()` không bao giờ được gọi. Đã sửa số lần `asyncio.sleep` mong đợi cho từng kịch bản scale (= 1 lần poll + số tín hiệu gửi), mock `redis.exists` trả `False`/`True` tương ứng, và thêm 1 trường hợp mới `test_init_skips_reset_when_lock_held` cho nhánh guard.

> **Lưu ý điều chỉnh số liệu**: Số liệu của `reaction` trong bản cập nhật trước (53/26=79) chưa phản ánh bộ kiểm thử mở rộng được thêm ở vòng rà soát trước đó (`AlertHtmlTemplateTest`, `CompositeAlertServiceTest`, `JpaReactionLogServiceTest`, `RetryTest`, `DetectionResultConsumerTest`, `ReactionResultPublisherTest`, `EscalatingIpReactionServiceTestBase` dùng chung cho `BruteForceReactionServiceTest`/`DDoSReactionServiceTest`). Số liệu 92/26=118 ở trên được đếm trực tiếp từ số phương thức `@Test` hiện có trong mã nguồn.

Các bổ sung kiểm thử trong vòng rà soát gần nhất, theo từng dịch vụ:

- **log-processing**: thêm 12 trường hợp biên cho `LogProcessingService` (mã trạng thái HTTP 100/99/599/600, độ dài URL/IP/User-Agent tối đa và vượt ngưỡng, phương thức HTTP không xác định, referer rỗng vs `null`, sanitize giá trị Flow feature tràn số `Infinity`/`-Infinity` về `0.0`); 1 trường hợp cho `DlqRetryScheduler` (lỗi audit khi retry đã hết hạn không lan truyền); 2 trường hợp cho `RedisQueueService` (script Redis trả `null`, Redis ném lỗi khi enqueue); 2 trường hợp cho `RawLogConsumer` (enqueue ném lỗi → DLQ, `receivedAt=null` → bỏ qua âm thầm); 1 trường hợp cho `LogProcessingPoller` (bỏ qua dequeue khi hàng đợi executor vượt ngưỡng backpressure); và file mới `DeadLetterConsumerTest` (6 trường hợp cho việc trích xuất lý do/log id từ message DLQ). Đồng thời gỡ bỏ `EventServiceImplTest` (trùng lặp hoàn toàn với `EventServiceImplIT` chạy trên RabbitMQ thực).
- **dashboard**: thêm file mới `DetectionMapperTest` (4 trường hợp) bao phủ ánh xạ `DetectionResultEntity` → `DetectionSummaryView`/`DetectionDetailView`, bao gồm logic `method_flags` chỉ áp dụng cho `DetectionType.TRAFFIC`.
- **log-analysis**: thêm file mới `test_detection_job_runner.py` (11 trường hợp) bao phủ `DetectionJobRunner` — chạy job traffic (rỗng, bình thường, chuyển giờ với/không đủ mẫu lịch sử theo mùa), chạy job web-attack (không có log mới, có log mới, một request lỗi không chặn các request khác), phân tích biểu thức cron (5/6 trường, không hợp lệ), và bản sao độc lập của `job_status()`.
- **simulation**: thêm file mới `test_simulation_router.py` (11 trường hợp) bao phủ toàn bộ endpoint của `simulation_router` — `/start` (202, log_type tự suy ra từ scenario, 409 khi đang chạy), `/stop`, `/status`, `/replay` (202, 503 khi MinIO chưa cấu hình, 404 khi không tìm thấy nguồn, 422 khi CSV rỗng, 409 khi đang chạy), và `/baseline`, `/baseline/stop`.

Trong quá trình phát triển, một số lỗi được phát hiện nhờ kiểm thử và đã được khắc phục trước khi hoàn thiện:

- **`Severity.NONE` thiếu trong enum Java** (Dashboard): `DetectionControllerIT` báo lỗi HTTP 500 khi deserialize kết quả phát hiện có mức độ `NONE`. Nguyên nhân: enum `Severity` phía dashboard thiếu giá trị `NONE` so với phía detection service. Khắc phục bằng cách bổ sung giá trị.
- **`FormatMapper` cho Flow log** (Dashboard): `JsonConverterIT` thất bại do Hibernate 7.2.12 yêu cầu Jackson 2 nhưng Spring Boot 4 chỉ cung cấp Jackson 3. Khắc phục bằng cách chuyển sang `AttributeConverter` thay vì dùng `@JdbcTypeCode(SqlTypes.JSON)`.
- **Mô hình ML nhận sai key tên feature** (log-analysis): E2E test `test_flow_attack_detected_and_ip_blocked[ddos]` (trước đây `test_ddos_detected_and_ip_blocked`) không phát sinh phát hiện. Nguyên nhân: tên cột feature trong bản ghi flow không khớp với tên cột mô hình XGBoost đã được huấn luyện. Khắc phục bằng cách chuẩn hóa lại tên trường trong Processing Service.
- **`sourceIp=null` trong phản ứng traffic** (Reaction): `DetectionResultConsumerIT` xác nhận `SCALE_UP` reaction thất bại vì `sourceIp` null không được xử lý đúng khi lưu vào cơ sở dữ liệu. Khắc phục bằng cách cho phép `source_ip` nullable trong schema và logic phản ứng.
