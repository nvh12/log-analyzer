# Đặc Tả Thiết Kế Giao Diện — Dashboard Frontend

> Tài liệu này mô tả **thiết kế giao diện** (UI design specification) của ứng dụng Dashboard Frontend thuộc hệ thống Log Analyzer. Các wireframe trong tài liệu là bản phác thảo thiết kế — không phải ảnh chụp màn hình sản phẩm cuối.

---

## 1. Đặc Tả Màn Hình Mục Tiêu

### 1.1 Độ Phân Giải & Kích Thước

| Loại màn hình | Độ phân giải | Breakpoint Tailwind | Ghi chú |
|---|---|---|---|
| Desktop (primary) | 1920 × 1080 px | `xl:` (≥ 1280 px) | Target chính, layout đầy đủ |
| Laptop | 1440 × 900 px | `lg:` (≥ 1024 px) | Sidebar hiển thị, layout 2–3 cột |
| Tablet landscape | 1024 × 768 px | `lg:` (≥ 1024 px) | Layout co lại, sidebar vẫn hiển thị |
| Tablet portrait | 768 × 1024 px | `md:` (≥ 768 px) | Sidebar ẩn, hamburger menu hiện |
| Mobile | 375 × 812 px | `sm:` (≥ 640 px) | Tất cả layout đơn cột |

**Màn hình mục tiêu chính**: Desktop 1920×1080, tỉ lệ 16:9. Ứng dụng là công cụ giám sát vận hành nên thường được mở trên màn hình rộng của kỹ sư/quản trị viên.

### 1.2 Số Lượng Màu Sắc

- **Không gian màu**: sRGB 24-bit (16.7 triệu màu), không yêu cầu màn hình HDR.
- **Chế độ màu**: Dark mode mặc định và duy nhất — phù hợp môi trường server/NOC với ánh sáng thấp.
- **Palette thực tế**: ~12 tone màu chức năng (xem mục 2.3), tất cả là các giá trị Tailwind CSS có sẵn.

### 1.3 Font & Typography

- **Font chính**: JetBrains Mono (monospace) — dùng cho toàn bộ ứng dụng, phù hợp với dữ liệu kỹ thuật (IP, timestamp, log).
- **Fallback**: `ui-monospace`, `Consolas`, `monospace`.
- **Bộ ký tự**: Latin + ASCII; không yêu cầu hỗ trợ Unicode mở rộng.

---

## 2. Chuẩn Hóa Thiết Kế Giao Diện

### 2.1 Bố Cục Tổng Thể

```
┌─────────────────────────────────────────────────────────────────┐
│  SIDEBAR (w-44 = 176px, fixed, dark bg-gray-900)                │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  [≡]  Log Analyzer              ← Logo / title (lg:)    │    │
│  │  ─────────────────────────────                          │    │
│  │  ● Live                         ← NavLink active        │    │
│  │    Logs                                                  │    │
│  │    Detections                                            │    │
│  │    Reactions                                             │    │
│  │    System                                                │    │
│  │    Simulation                                            │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
│  MAIN CONTENT (flex-1, bg-gray-950, overflow-y-auto, p-4/p-6)   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  <Page Content>                                          │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

- **Sidebar**: Cố định bên trái, chiều rộng 176 px (w-44), luôn hiển thị từ breakpoint `lg:` trở lên.
- **Mobile**: Sidebar ẩn; nút hamburger `☰` góc trên trái hiện ra. Khi nhấn, sidebar trượt ra từ trái với overlay tối phía sau.
- **Main content**: Cuộn dọc độc lập, padding 16 px (mobile) / 24 px (desktop).

### 2.2 Lưới (Grid) & Khoảng Cách

| Thành phần | Quy tắc |
|---|---|
| Khoảng cách giữa các card | `gap-4` (16 px) |
| Padding trong card | `p-4` (16 px) |
| Padding card header | `px-4 py-2` |
| Khoảng cách giữa các phần tử trong form | `space-y-3` hoặc `gap-3` |
| Lề trang (mobile) | `p-4` |
| Lề trang (desktop) | `p-6` |

**Cột responsive** dùng pattern chuẩn:
- 1 cột → `sm:` 2 cột → `lg:` 3–4 cột

### 2.3 Bảng Màu Chức Năng

| Tên màu | Giá trị Tailwind | Mục đích sử dụng |
|---|---|---|
| Background trang | `bg-gray-950` (#030712) | Nền toàn trang |
| Background card | `bg-gray-900` (#111827) | Nền card, drawer |
| Background input | `bg-gray-800` (#1f2937) | Ô nhập liệu, bảng |
| Đường viền | `border-gray-800` / `border-gray-700` | Card, input, bảng |
| Text chính | `text-gray-300` (#d1d5db) | Nội dung văn bản thông thường |
| Text phụ / nhãn | `text-gray-400` / `text-gray-500` | Header card, placeholder, metadata |
| Xanh lá (OK / Active) | `text-green-400`, `bg-green-900/30` | Trạng thái hoạt động, thành công |
| Đỏ (Critical / Error) | `text-red-400`, `bg-red-950` | Cảnh báo nghiêm trọng, lỗi, block |
| Vàng (Warning / Medium) | `text-yellow-400` | Mức độ MEDIUM, cảnh báo |
| Cam (High severity) | `text-orange-400` | Mức độ HIGH |
| Xanh dương (Info) | `text-blue-300`, `bg-blue-900` | Thông tin, DDOS type |
| Tím (Web Attack) | `text-purple-300`, `bg-purple-900` | WEB_ATTACK type |
| Trắng (LOW severity) | `text-gray-300` | Mức độ thấp |

### 2.4 Thiết Kế Nút (Buttons)

Tất cả nút dùng font monospace, không có border-radius lớn (rounded = 6 px).

| Loại nút | Màu | Khi nào dùng | Ví dụ |
|---|---|---|---|
| **Primary** | `bg-green-700 hover:bg-green-600 text-white` | Hành động chính, xác nhận | "Start", "Add to Whitelist" |
| **Danger** | `bg-red-700 hover:bg-red-600 text-white` | Hành động hủy/xóa/ngừng | "Stop", "Lift Block", "Remove" |
| **Info / Secondary** | `bg-blue-700 hover:bg-blue-600 text-white` | Hành động trung tính | "Replay" |
| **Ghost** | `text-gray-400 hover:text-gray-200` | Đóng, ẩn drawer | "✕ Close" |
| **Disabled** | `opacity-50 cursor-not-allowed` | Input chưa hợp lệ | Nút Start khi form trống |

**Kích thước nút**: `px-3 py-1` (nhỏ, inline trong bảng) hoặc `px-4 py-2` (tiêu chuẩn trong form).

### 2.5 Thiết Kế Ô Nhập Liệu & Điều Khiển

Tất cả input/select/datetime dùng class `.field`:

```
bg-gray-800  border border-gray-700  rounded  px-2 py-1
font-mono  text-gray-300  text-sm
focus:border-gray-500  focus:outline-none
```

- **Placeholder**: `text-gray-500`, mô tả ngắn gọn kiểu dữ liệu (ví dụ: `192.168.1.1`, `YYYY-MM-DD`).
- **Select**: Dùng SVG arrow tùy chỉnh (màu `gray-400`), ẩn dropdown mặc định của trình duyệt.
- **Date-time**: Dùng `datetime-local` input, hiển thị lên trước field tương ứng.
- **Disabled state**: `opacity-50 cursor-not-allowed` — dùng khi field không áp dụng (vd: Source IP disabled cho scenario ngẫu nhiên).

### 2.6 Thiết Kế Badge

Badge là thẻ màu nhỏ hiển thị loại/trạng thái, dùng `text-xs`, `px-1.5 py-0.5`, `rounded`.

| Badge | Màu nền | Màu chữ | Hiển thị |
|---|---|---|---|
| TRAFFIC | `bg-gray-700` | `text-gray-200` | TRAFFIC |
| DDOS | `bg-blue-900` | `text-blue-300` | DDOS |
| WEB_ATTACK | `bg-purple-900` | `text-purple-300` | WEB_ATTACK |
| BRUTE_FORCE | `bg-orange-900` | `text-orange-300` | BRUTE_FORCE |
| CRITICAL | `bg-red-900` | `text-red-300` | CRITICAL |
| HIGH | `bg-orange-900` | `text-orange-300` | HIGH |
| MEDIUM | `bg-yellow-900` | `text-yellow-300` | MEDIUM |
| LOW | `bg-gray-800` | `text-gray-300` | LOW |
| BLOCK | `bg-red-900` | `text-red-300` | BLOCK |
| RATE_LIMIT | `bg-yellow-900` | `text-yellow-300` | RATE_LIMIT |
| SCALE_UP | `bg-green-900` | `text-green-300` | SCALE_UP |

### 2.7 Chỉ Số Trạng Thái (Status Dot)

Hình tròn nhỏ 8×8 px, đặt inline trước text nhãn.

| Màu | Trạng thái |
|---|---|
| `bg-green-500` + `animate-pulse` | Đang hoạt động, kết nối ổn định |
| `bg-red-500` | Lỗi, ngắt kết nối, bị block |
| `bg-yellow-400` | Cảnh báo, đang kết nối lại |
| `bg-orange-500` | Mức HIGH |
| `bg-gray-500` | Không hoạt động / idle |

### 2.8 Vị Trí Hiển Thị Thông Điệp Phản Hồi

| Loại thông báo | Vị trí | Thời gian | Màu |
|---|---|---|---|
| **Toast thành công** | Fixed, góc dưới-phải (`bottom-6 right-6`) | 2.5 giây tự động đóng | `bg-gray-800 border-gray-700 text-gray-200` |
| **Toast lỗi** | Fixed, góc dưới-phải | 2.5 giây tự động đóng | `bg-red-950 border-red-700 text-red-300` |
| **Error banner** | Inline, đầu section lỗi | Cho đến khi Retry | `bg-red-950 border-red-800 text-red-300` với nút "Retry" bên phải |
| **Empty state** | Trung tâm section | Thường xuyên | `text-gray-500 italic` |
| **Loading** | Trung tâm hoặc đầu section | Trong khi tải | Animated "Loading…" |

**Toast** có cấu trúc:
```
┌─────────────────────────────────┐
│ ✓  IP 10.0.0.1 added to         │  ← Toast thành công
│    whitelist                     │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ ✕  Failed to lift block:         │  ← Toast lỗi (đỏ)
│    Connection refused            │
└─────────────────────────────────┘
```

### 2.9 Thiết Kế Card

```
┌─────────────────────────────────────────────────────┐
│ CARD HEADER (bg-gray-900, border-b border-gray-800)  │
│ text-xs text-gray-400 uppercase tracking-wide        │
├─────────────────────────────────────────────────────┤
│                                                       │
│  <nội dung>                                           │
│                                                       │
└─────────────────────────────────────────────────────┘
```

- Border radius: `rounded-lg` (8 px)
- Border: `border border-gray-800`

### 2.10 Thiết Kế Bảng Dữ Liệu

```
┌──────────────────────────────────────────────────────────────┐
│ [Bộ lọc]  IP: [________]  Severity: [▼ All]  From: [_______] │
├────┬──────────────┬──────────┬────────┬───────────────────────┤
│ ID │ Type         │ Severity │ Src IP │ Detected At           │
├────┼──────────────┼──────────┼────────┼───────────────────────┤
│ 42 │ [DDOS]       │ [HIGH]   │ 10.0.1 │ 2026-06-09 14:32:00   │  ← Row clickable
├────┼──────────────┼──────────┼────────┼───────────────────────┤
│ 41 │ [BRUTE_FORCE]│ [MEDIUM] │ 10.0.2 │ 2026-06-09 14:31:55   │
└────┴──────────────┴──────────┴────────┴───────────────────────┘
                                          [← Prev]  Page 1  [Next →]
```

- Header row (`th`): `text-xs text-gray-400 uppercase`, `bg-gray-900 sticky top-0`
- Data row (`td`): `text-sm`, hover `bg-gray-800/30`
- Selected row: `bg-gray-800/50 ring-1 ring-gray-600`
- Pagination: nút `Prev` / `Next` với `text-gray-400`, disabled khi ở trang đầu/cuối

### 2.11 Drawer Chi Tiết

Khi click vào một dòng trong bảng, drawer trượt ra từ phải:

```
                                    ┌──────────────────────────┐
                                    │ [✕]  Detection #42        │
                                    │ ──────────────────────── │
                                    │ Type:     [DDOS]          │
                                    │ Severity: [HIGH]          │
                                    │ Src IP:   10.0.0.15       │
                                    │ Dst IP:   192.168.1.1     │
                                    │ Dst Port: 80              │
                                    │ Confidence: 0.94          │
                                    │ Detected:  2026-06-09...  │
                                    │ ──────────────────────── │
                                    │ ▶ Raw Payload             │
                                    └──────────────────────────┘
```

- Chiều rộng tối đa: `max-w-lg` (512 px)
- Overlay phía sau: `bg-black/50`, click để đóng
- Nút đóng: `✕` góc trên phải

---

## 3. Wireframe Các Màn Hình Chức Năng Chính

### 3.1 Màn Hình Live Dashboard (`/`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ● Live                                                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ ● TRAFFIC    │ │ ○ DDOS       │ │ ○ WEB_ATTACK │ │ ○ BRUTE_FORCE│       │
│  │  Conf: 0.87  │ │  Conf: -     │ │  Conf: -     │ │  Conf: -     │       │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘       │
│   [4 status tiles — một hàng, mỗi tile chiếm 1/4 chiều rộng]                │
│                                                                               │
│  ┌────────────────────────────┐  ┌──────────────────────────────────────┐   │
│  │ EVENT STREAM               │  │ THROUGHPUT (1m / 5m / 30m)           │   │
│  │ Type:[▼] Sev:[▼] IP:[___] │  │  ^                                    │   │
│  │ ─────────────────────────  │  │  │  ~~~HTTP~~~                        │   │
│  │ 14:32 [DDOS][HIGH] 10.0.1 │  │  │      ~~~Flow~~~                    │   │
│  │ 14:32 REACTION BLOCK       │  │  └──────────────────────────────────→ │   │
│  │ 14:31 [TRAFFIC][MED] ...   │  │  ← Line chart, 2 đường               │   │
│  │ 14:31 [BRUTE][LOW]  ...   │  └──────────────────────────────────────┘   │
│  │  (scrollable, max 100)     │                                              │
│  │  [cuộn dừng khi hover]     │  ┌──────────────────────────────────────┐   │
│  └────────────────────────────┘  │ ACTIVE REACTIONS                     │   │
│                                   │ Blocklist       │ Rate Limits        │   │
│                                   │ 10.0.0.15 [45s] │ 10.0.1.3 100r/m   │   │
│                                   │ 10.0.0.22 [12s] │                    │   │
│                                   └──────────────────────────────────────┘   │
│                                                                               │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐          │
│  │ RECENT DETECTIONS│  │ RECENT REACTIONS │  │ RECENT LOGS      │          │
│  │ (compact table)  │  │ (compact table)  │  │ (compact table)  │          │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘          │
│   [3 preview cards — 1/3 mỗi card, bottom row]                               │
│                                                                               │
│                                           ● Connected  [góc dưới phải]       │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Điểm thiết kế chính**:
- 4 UC tile trên cùng: status dot màu xanh (đang firing) / xám (idle), confidence score.
- Cột trái (Event Stream): ~40% chiều rộng; bộ lọc inline; tự cuộn, dừng khi hover.
- Cột phải (Throughput + Active Reactions): ~55% chiều rộng.
- 3 preview card ở cuối trang: hiển thị 5 bản ghi gần nhất mỗi loại.

---

### 3.2 Màn Hình Logs (`/logs`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Logs                                                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  [HTTP Logs]  [Flow Logs]   ← Tabs, tab active có border-bottom trắng       │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ IP:[__________]  Status:[▼ All]  From:[___________]  To:[__________]│    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌──────────┬──────────────┬────────┬─────────────────────┬────────┬──────┐ │
│  │Timestamp │ Src IP       │ Method │ Path                 │ Status │ Bytes│ │
│  ├──────────┼──────────────┼────────┼─────────────────────┼────────┼──────┤ │
│  │14:32:01  │ 10.0.0.15    │ POST   │ /api/login           │[200]   │ 1.2k │ │
│  │14:32:00  │ 10.0.0.22    │ GET    │ /admin               │[404]   │  512 │ │
│  │14:31:59  │ 192.168.1.5  │ GET    │ /index.html          │[200]   │ 8.4k │ │
│  │  ...     │              │        │                      │        │      │ │
│  └──────────┴──────────────┴────────┴─────────────────────┴────────┴──────┘ │
│                                    [← Prev]  Page 3 / 14  [Next →]          │
│                                                                               │
│  [Khi click một dòng → drawer chi tiết trượt từ phải]                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Status code màu**: 2xx → `text-green-400`, 3xx → `text-blue-400`, 4xx → `text-yellow-400`, 5xx → `text-red-400`.

---

### 3.3 Màn Hình Detections (`/detections`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Detections                                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  Type:[▼ All]  Severity:[▼ All]  From:[___________]  To:[___________]       │
│                                                                               │
│  ┌────┬──────────────┬──────────┬─────────┬────────┬──────────────┬───────┐ │
│  │ ID │ Type         │ Severity │ Anomaly │  Conf  │ Src IP       │  At   │ │
│  ├────┼──────────────┼──────────┼─────────┼────────┼──────────────┼───────┤ │
│  │ 42 │[DDOS]        │[HIGH]    │   ✓     │  0.94  │ 10.0.0.15   │14:32  │ │
│  │ 41 │[BRUTE_FORCE] │[MEDIUM]  │   ✓     │  0.78  │ 10.0.0.22   │14:31  │ │
│  │ 40 │[TRAFFIC]     │[LOW]     │   ✗     │  0.51  │ —           │14:30  │ │
│  └────┴──────────────┴──────────┴─────────┴────────┴──────────────┴───────┘ │
│                                        [← Prev]  Page 1 / 5  [Next →]       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 3.4 Màn Hình Reactions (`/reactions`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Reactions                                                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌────────────────────────────┐  ┌─────────────────────┐  ┌───────────────┐ │
│  │ IP BLOCKLIST (2)           │  │ RATE LIMITS         │  │ WHITELIST     │ │
│  │ ───────────────────────    │  │ ─────────────────── │  │ ─────────────  │ │
│  │ [✓Lift][□WL] 10.0.0.15 45s│  │ 10.0.1.3  100r/m   │  │ IP:[________] │ │
│  │ [□Lift][✓WL] 10.0.0.22 12s│  │ 10.0.1.7   50r/m   │  │ [Add]         │ │
│  │                            │  │                     │  │ ─────────────  │ │
│  │                            │  │                     │  │ 192.168.1.100 │ │
│  │                            │  │                     │  │ [Remove]      │ │
│  │                            │  │                     │  │ 10.0.0.22 *   │ │
│  │                            │  │                     │  │ [Remove]      │ │
│  └────────────────────────────┘  └─────────────────────┘  └───────────────┘ │
│                                                          [Apply changes] →   │
│  (* italic = pending, not yet saved)                                          │
│                                                                               │
│  Action:[▼ All]  From:[___________]  To:[___________]                        │
│                                                                               │
│  ┌────┬──────────────┬──────────────┬──────────────┬──────────┬──────────┐  │
│  │ ID │ Action       │ Target IP    │ Cause        │ Src IP   │ At       │  │
│  ├────┼──────────────┼──────────────┼──────────────┼──────────┼──────────┤  │
│  │ 15 │[BLOCK]       │ 10.0.0.15    │[DDOS]        │10.0.0.15 │14:32:01  │ [Lift block] │
│  │ 14 │[RATE_LIMIT]  │ 10.0.1.3     │[BRUTE_FORCE] │10.0.1.3  │14:31:55  │  │
│  │ 13 │[SCALE_UP]    │ —            │[TRAFFIC]     │ —        │14:30:10  │  │
│  └────┴──────────────┴──────────────┴──────────────┴──────────┴──────────┘  │
│                                        [← Prev]  Page 1 / 3  [Next →]       │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Điểm thiết kế chính**:
- Blocklist card: mỗi IP có 2 checkbox — **Lift** (đỏ) và **WL** (xanh lá). Tick checkbox thêm IP vào trạng thái pending.
- Whitelist card: hiển thị effective whitelist (bao gồm thay đổi pending chưa lưu). IP pending (chưa lưu) hiện bằng italic. Form "Add" thêm IP thủ công vào pending.
- **Apply changes** button: chỉ hiện khi có thay đổi pending. Khi nhấn, gọi đúng 2 API song song, cả hai đều gọi tới Dashboard service (không gọi trực tiếp Simulation): `PUT /api/reactions/whitelist` (Dashboard proxy sang `PUT /admin/whitelist` của Simulation) + `POST /api/reactions/blocks/lift` (thao tác trực tiếp lên Redis trong Dashboard, không đi qua Simulation — Reaction service chỉ đọc whitelist/blocklist, không expose endpoint nào).
- Timeline table: nút "Lift block" riêng lẻ vẫn giữ nguyên để override từng dòng lịch sử.

---

### 3.5 Màn Hình Simulation (`/simulation`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Simulation                                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ BASELINE TRAFFIC                                                      │    │
│  │ ● Running  │  Sent: 1,240  │  Type: HTTP  │         [■ Stop Baseline]│    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌────────────────────────────────────────┐  ┌───────────────────────────┐  │
│  │ MAIN SIMULATION                         │  │ REPLAY FROM FILE          │  │
│  │ ─────────────────────────────────────   │  │ ─────────────────────────  │  │
│  │ Status: ○ Idle                          │  │ Source Key:               │  │
│  │                                         │  │ [logs/attack_2026.csv___] │  │
│  │ Scenario:  [▼ DDOS                  ]   │  │                           │  │
│  │ Count:     [0 (unlimited)___________]   │  │ Count:  [0___]            │  │
│  │ Rate/s:    [100_____________________]   │  │ Rate/s: [50__]            │  │
│  │ Source IP: [disabled — random_______]   │  │ Src IP: [_____] optional  │  │
│  │ Attack %:  [0.8_____________________]   │  │ Dst IP: [_____] optional  │  │
│  │                                         │  │                           │  │
│  │ [▶ Start Simulation]  [■ Stop]          │  │ [▶ Replay]   [■ Stop]     │  │
│  └────────────────────────────────────────┘  └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 3.6 Màn Hình System (`/system`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ System                                                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌──────────────────────────────────┐  ┌───────────────────────────────┐    │
│  │ RABBITMQ QUEUES                   │  │ DETECTION SERVICE CONFIG       │    │
│  │ ─────────────────────────────     │  │ ─────────────────────────────  │    │
│  │ http_log_queue:       1,204       │  │ {                              │    │
│  │ flow_log_queue:         892       │  │   "thresholds": {              │    │
│  │ detection_result_queue:   3       │  │     "traffic": {...},          │    │
│  │ ─────────────────────────────     │  │     "ddos": {...}              │    │
│  │ REDIS                             │  │   }                            │    │
│  │ ─────────────────────────────     │  │ }                              │    │
│  │ Hits:      48,302                 │  │ ← Monospace, scrollable        │    │
│  │ Misses:     1,204                 │  │                                │    │
│  │ Hit Rate:   97.6%                 │  │                                │    │
│  └──────────────────────────────────┘  └───────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Phong Cách Thiết Kế Tổng Thể

| Thuộc tính | Quyết định thiết kế | Lý do |
|---|---|---|
| **Theme** | Dark mode duy nhất | Màn hình NOC/server, giảm mỏi mắt |
| **Font** | Monospace (JetBrains Mono) | Dữ liệu kỹ thuật: IP, timestamp, log path |
| **Màu sắc** | Xám làm nền, màu chức năng rõ ràng | Dễ phân biệt mức độ nghiêm trọng |
| **Animation** | Chỉ `animate-pulse` cho status đang hoạt động | Không gây mất tập trung |
| **Border radius** | `rounded` (6px) hoặc `rounded-lg` (8px) | Hiện đại nhưng không quá mềm mại |
| **Mật độ thông tin** | Cao — nhiều dữ liệu trên một màn hình | Người dùng là kỹ sư/admin, không cần UX đơn giản hóa |
| **Responsive** | Mobile-first nhưng tối ưu cho desktop | Công cụ vận hành, chủ yếu dùng trên màn hình lớn |
