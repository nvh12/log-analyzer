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

---

## 3. Chức Năng 2 — Leo Thang Phản Ứng và Chặn IP (Reaction Service)

Đây là chức năng trọng tâm bảo mật: mỗi lần phát hiện tấn công từ một IP tích lũy vào bộ đếm Redis. Dưới ngưỡng → giới hạn tốc độ (`RATE_LIMIT`); đạt ngưỡng → chặn hoàn toàn (`BLOCK`). Chính sách whitelist ngăn chặn chặn nhầm IP đáng tin cậy.

**Kỹ thuật sử dụng:** Phân tích giá trị biên (ngưỡng leo thang = 3), Kiểm thử chuyển trạng thái (chưa chặn → chặn → TTL hết hạn), Kiểm thử fail-open.

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

## 4. Chức Năng 3 — Pipeline Phát Hiện Tấn Công Đầu Cuối (E2E)

Kiểm thử đầu cuối xác nhận toàn bộ dòng chảy dữ liệu: Simulation → log.raw → Processing → Detection → Reaction → Redis/PostgreSQL. Các bài kiểm thử được viết bằng Python (pytest-asyncio) và chạy trên môi trường Docker Compose đầy đủ (`compose.test.yml`).

**Kỹ thuật sử dụng:** Kiểm thử hệ thống bất đồng bộ với polling có timeout — mỗi bước trong pipeline được xác nhận tuần tự trước khi chuyển sang bước tiếp theo, đảm bảo không có điều kiện chạy đua giả tạo.

| TC# | Kịch bản | Đầu vào | Các bước xác nhận | Kết quả thực tế |
|---|---|---|---|---|
| E2E-01 | **DDoS leo thang đến BLOCK** | 6 bản ghi flow với feature XGBoost đặc trưng DDoS (lưu lượng cực cao, gói tin lớn) gửi vào `log.raw` | (1) ≥6 bản ghi trong `normalized_flow` · (2) ≥3 `detection_results` loại `DDOS` · (3) Key `ratelimit:ip:{ip}` tồn tại · (4) Key `blocklist:ip:{ip}` tồn tại · (5) Cả `RATE_LIMIT` và `BLOCK` trong `reaction_logs` | Đạt |
| E2E-02 | **Brute force SSH leo thang** | 10 bản ghi flow port 22 với feature đặc trưng brute force | Tương tự E2E-01 nhưng detection_type=`BRUTE_FORCE` | Đạt |
| E2E-03 | **Web attack → chặn ngay** | 20 HTTP log có payload SQLi/XSS qua Simulation API (`WEB_ATTACK` scenario) | (1) ≥20 bản ghi `normalized_http` · (2) ≥1 `detection_results` loại `WEB_ATTACK` · (3) Key `blocklist:ip:{ip}` tồn tại · (4) Bản ghi `BLOCK` trong `reaction_logs` | Đạt |
| E2E-04 | **Traffic spike → scale up** | 200 HTTP request tốc độ cao (với lịch sử baseline được seed sẵn) | (1) Detection_type=`TRAFFIC` trong `detection_results` · (2) `reaction_logs` có `SCALE_UP` · (3) `scale:state = "scaled_up"` trong Redis | Đạt |
| E2E-05 | **Payload không hợp lệ → DLQ** | Chuỗi không phải JSON gửi vào `log.raw` | (1) Không có bản ghi trong `normalized_http`/`normalized_flow` · (2) Bản ghi trong `drop_audit` với `reason="DEAD_LETTERED"` | Đạt |
| E2E-06 | **`receivedAt=null` → từ chối trước hàng đợi** | `RawLog` với `receivedAt=null` | Không có bản ghi trong `normalized_*`; không có bản ghi trong `drop_audit` | Đạt |

---

## 5. Tổng Kết Kết Quả Kiểm Thử

| Dịch vụ | Unit Test | Integration Test | Tổng |
|---|---|---|---|
| log-processing | 40 | 32 | **72** |
| log-analysis (Python) | 13 | 3 | **16** |
| reaction | 57 | 26 | **83** |
| dashboard | 31 | 48 | **79** |
| E2E (toàn hệ thống) | — | 8 | **8** |
| **Tổng cộng** | **141** | **117** | **258** |

Tất cả **258 trường hợp kiểm thử đều đạt** (passed). Không có trường hợp nào thất bại trong vòng kiểm thử cuối cùng.

Trong quá trình phát triển, một số lỗi được phát hiện nhờ kiểm thử và đã được khắc phục trước khi hoàn thiện:

- **`Severity.NONE` thiếu trong enum Java** (Dashboard): `DetectionControllerIT` báo lỗi HTTP 500 khi deserialize kết quả phát hiện có mức độ `NONE`. Nguyên nhân: enum `Severity` phía dashboard thiếu giá trị `NONE` so với phía detection service. Khắc phục bằng cách bổ sung giá trị.
- **`FormatMapper` cho Flow log** (Dashboard): `JsonConverterIT` thất bại do Hibernate 7.2.12 yêu cầu Jackson 2 nhưng Spring Boot 4 chỉ cung cấp Jackson 3. Khắc phục bằng cách chuyển sang `AttributeConverter` thay vì dùng `@JdbcTypeCode(SqlTypes.JSON)`.
- **Mô hình ML nhận sai key tên feature** (log-analysis): E2E test `test_ddos_detected_and_ip_blocked` không phát sinh phát hiện. Nguyên nhân: tên cột feature trong bản ghi flow không khớp với tên cột mô hình XGBoost đã được huấn luyện. Khắc phục bằng cách chuẩn hóa lại tên trường trong Processing Service.
- **`sourceIp=null` trong phản ứng traffic** (Reaction): `DetectionResultConsumerIT` xác nhận `SCALE_UP` reaction thất bại vì `sourceIp` null không được xử lý đúng khi lưu vào cơ sở dữ liệu. Khắc phục bằng cách cho phép `source_ip` nullable trong schema và logic phản ứng.
