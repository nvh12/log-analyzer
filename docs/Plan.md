Mô tả đề tài Đồ án tốt nghiệp cử nhân

Hệ thống giám sát, phân tích log, cảnh báo và tự động ứng phó

# Use case

Hệ thống phát hiện bất thường trên hai tầng dữ liệu mạng: tầng nội dung HTTP (HTTP access log) và tầng luồng mạng (network flow), sử dụng kết hợp các phương pháp có giám sát và không giám sát.

## Phát hiện đột biến lưu lượng

- Mục tiêu: Phát hiện sự gia tăng đột ngột của lưu lượng truy cập, sử dụng dữ liệu tần suất truy cập từ HTTP access log.

- Tầng dữ liệu: HTTP access log (CLF format).

- Các phương pháp:

  - Exponential Moving Average (EMA): Sử dụng trung bình từ dữ liệu cửa sổ trượt để xác định baseline và xác định đột biến khi vượt ngưỡng Độ lệch chuẩn (Standard Deviation).

  - Z-Score: Đo lường số độ lệch chuẩn mà một giá trị sai khác so với trung bình trong cửa sổ trượt.

  - IQR (Interquartile Range): Phương pháp thống kê sử dụng khoảng tứ phân vị để xác định ngoại lệ (outlier), ít bị ảnh hưởng bởi giá trị cực đoan.

  - Seasonal Baseline (Robust Z-Score): Sử dụng dữ liệu lịch sử dài hạn (21 ngày) được chia thành 48 bucket (giờ trong ngày × cuối tuần) để mô phỏng kỳ vọng lưu lượng theo ngữ cảnh thời gian. Sử dụng Median và MAD (Median Absolute Deviation) để đảm bảo tính kháng nhiễu (robustness).

  - Ensemble Rule Engine: Kết hợp kết quả từ cả 4 detector trên theo cơ chế bỏ phiếu (k-of-N voting) và hiệu chuẩn ngưỡng dựa trên tỷ lệ báo động giả (False Positive Rate - FPR) cố định.

- Dữ liệu: NASA HTTP (1995). Đánh giá dựa trên các sự kiện thực tế (NASA mission events như STS launches/landings). Phương pháp không giám sát, không yêu cầu nhãn huấn luyện.

- Cách ứng phó khi phát hiện: Gửi cảnh báo, kích hoạt cơ chế mở rộng (scale).

## Phát hiện tấn công DDoS

- Mục tiêu: Phân biệt giữa luồng mạng hợp lệ và tấn công từ chối dịch vụ phân tán, sử dụng phương pháp phân loại có giám sát trên dữ liệu network flow.

- Tầng dữ liệu: Network flow (CICIDS2017 flow records, trích xuất bởi CICFlowMeter).

- Phương pháp:

- XGBoost: Phân loại nhị phân (Benign / DDoS) trên vector đặc trưng flow. Dữ liệu flow đã được trích xuất sẵn bởi CICFlowMeter với ~80 đặc trưng. Quy trình tiền xử lý bao gồm loại bỏ cột hằng số, xử lý NaN/infinity, phân tích tương quan để giảm chiều xuống xác định bộ 43 đặc trưng tối ưu (đảm bảo feature parity với UC4).

- Dữ liệu: CICIDS2017 — Monday (Benign) + Friday afternoon (DDoS) với nhãn được cung cấp sẵn bởi Canadian Institute for Cybersecurity. Chia tập huấn luyện/kiểm thử theo thứ tự thời gian trong ngày Friday (temporal split).

- Cách ứng phó: Gửi cảnh báo, kích hoạt cơ chế giới hạn truy cập.

## Phát hiện tấn công Web

- Mục tiêu: Ngăn chặn các cuộc tấn công như SQL Injection, XSS, Path Traversal,…

- Tầng dữ liệu: HTTP access log (nội dung HTTP request).

- Phương pháp: Chiến lược 2 lớp:

- Rule Engine: Sử dụng Regex để so khớp các mẫu tấn công đã biết (Signatures).

- Lưu ý (kết quả phủ định): OC-SVM và Isolation Forest đã được đánh giá như các lớp không giám sát bổ sung nhưng bị loại bỏ do không cải thiện F1 của cascade. Pipeline cuối cùng chỉ dùng Regex → XGBoost.

- XGBoost: Phân loại nhị phân (Benign / Attack) dựa trên 12 đặc trưng cấu trúc và từ vựng (độ dài request, số ký tự đặc biệt, entropy, độ sâu đường dẫn, và các đặc trưng thống kê tham số). Model được huấn luyện trên cả dữ liệu benign và malicious để đạt độ chính xác cao.

- Dữ liệu: CSIC 2010 HTTP dataset với nhãn benign/attack được cung cấp sẵn. Chia tập huấn luyện/kiểm thử để đảm bảo không có mẫu tấn công nào xuất hiện ở cả train lẫn test.

- Cách ứng phó: Gửi cảnh báo, kích hoạt giới hạn truy cập, chặn địa chỉ IP.

## Phát hiện tấn công Brute Force

- Mục tiêu: Phân biệt giữa luồng mạng hợp lệ và tấn công brute force (FTP-Patator, SSH-Patator), sử dụng phương pháp phân loại có giám sát trên dữ liệu network flow. Bổ sung cho UC2 bằng cách phát hiện tấn công dựa trên xác thực lặp lại — cơ chế hoàn toàn khác biệt so với tấn công thể tích DDoS.

- Tầng dữ liệu: Network flow (CICIDS2017 flow records, trích xuất bởi CICFlowMeter).

- Phương pháp: XGBoost phân loại nhị phân (Benign / Brute Force) trên vector đặc trưng flow. Sử dụng cùng bộ đặc trưng đã giảm chiều với UC2 (uc2_feature_cols.json) để đảm bảo feature parity trong Detection microservice — một flow vào, hai dự đoán độc lập ra.

- Dữ liệu: CICIDS2017 — Monday (Benign) + Tuesday (FTP-Patator, SSH-Patator) với nhãn được cung cấp sẵn bởi Canadian Institute for Cybersecurity. Tập huấn luyện gồm toàn bộ Monday và 70% đầu của Tuesday (bao gồm cả SSH-Patator và FTP-Patator). Tập kiểm thử gồm 30% cuối của Tuesday (chỉ chứa FTP-Patator). Chia theo ranh giới giây để tránh data leakage.

- Lưu ý về tập kiểm thử: Do SSH-Patator (02:09–03:11) và FTP-Patator (09:17–10:30) là hai chiến dịch tấn công tuần tự không chồng lấp trong dữ liệu CICIDS2017 Tuesday, không tồn tại điểm cắt thời gian nào có thể đưa cả hai loại vào cả train lẫn test. Ranh giới 70/30 được chọn để model học được cả hai loại tấn công; đánh giá được thực hiện trên FTP-Patator — cùng cơ chế xác thực lặp lại với SSH-Patator nhưng nhắm vào giao thức khác nhau.

- So sánh với UC2: UC2 (XGBoost) phát hiện tấn công thể tích DDoS dựa trên tốc độ byte/packet cao và luồng kéo dài. UC4 (XGBoost) phát hiện tấn công brute force dựa trên kết nối ngắn lặp lại, RST flag và cổng đích đặc trưng. Hai model chạy đồng thời trong Detection microservice trên cùng một flow record.

- Cách ứng phó: Gửi cảnh báo, kích hoạt cơ chế giới hạn truy cập.

# Kiến trúc hệ thống

Hệ thống được thiết kế theo kiến trúc microservice, gồm: Processing, Detection, Reaction, Dashboard và Simulation.

Hệ thống xử lý hai tầng dữ liệu song song: tầng HTTP access log (cho UC1 và UC3) và tầng network flow (cho UC2 và UC4). Giao tiếp qua RabbitMQ với các queue chính:

- **log.raw**: Tiếp nhận dữ liệu thô từ module Simulation gửi tới Processing service.
- **log.normalized.http** (Processing → Detection): dữ liệu HTTP access log đã chuẩn hóa và tổng hợp theo cửa sổ 60 giây.
- **log.normalized.flow** (Processing → Detection): dữ liệu network flow đã chuẩn hóa, gửi theo từng flow record (43 đặc trưng).
- **detection.results** (Detection → Reaction & Dashboard): kết quả phát hiện từ tất cả use case, sử dụng schema alert thống nhất.
- **reaction.results** (Reaction → Dashboard): kết quả và hành động phản ứng tự động (như chặn IP, giới hạn tốc độ), dùng để hiển thị thời gian thực trên Dashboard.

## Processing

- Tiếp nhận và chuẩn hóa dữ liệu từ hai nguồn:

- HTTP track: Parse log định dạng CLF, tổng hợp theo cửa sổ trượt 60 giây, gửi tới queue log.normalized.http.

- Flow track: Chuẩn hóa flow record từ CICIDS2017 (xử lý NaN/infinity, chọn lọc đặc trưng), gửi từng flow record tới queue log.normalized.flow.

- Xây dựng bằng Spring Boot, sử dụng Redis để cache và tạo queue xử lý log bên trong module.

## Detection

- Phân tích dữ liệu trên hai tầng:

- Tầng HTTP: EMA, Z-score, IQR, Seasonal Baseline, Ensemble (UC1); Rule Engine, XGBoost (UC3).

- Tầng Flow: XGBoost (UC2); XGBoost (UC4).

- Tất cả model chạy trong cùng một FastAPI server. Tiếp nhận dữ liệu từ cả hai queue (log.normalized.http và log.normalized.flow), xử lý và gửi kết quả thống nhất tới detection.results.

- Xây dựng bằng FastAPI (Python).

## Reaction

- Tổng hợp và xử lý kết quả phân tích từ Detection.

- Dựa trên kết quả tổng hợp, cập nhật trạng thái trong Redis để kích hoạt các cơ chế phản ứng phù hợp như gửi cảnh báo, giới hạn truy cập hay mở rộng (scale).

- Xây dựng bằng Spring Boot.

## Dashboard

- Cung cấp UI để giám sát hệ thống, bao gồm lịch sử log (HTTP và flow), lịch sử cảnh báo và xử lý, trạng thái hiện tại của hệ thống.

- Đọc dữ liệu lịch sử trực tiếp từ PostgreSQL, đồng thời đăng ký (subscribe) các kênh nhận kết quả phát hiện (`detection.results`) và phản ứng (`reaction.results`) qua RabbitMQ để phục vụ truyền dữ liệu thời gian thực (SSE) cho Live UI. PostgreSQL vẫn đóng vai trò là nguồn dữ liệu chính xác (source of truth) để đồng bộ lại dữ liệu khi người dùng chuyển trang hoặc kết nối lại.

- Xây dựng API bằng Spring Boot, UI bằng Next.js / React.

## Simulation

- Hoạt động trên hai chế độ:

- HTTP generator: Sinh HTTP access log cho Processing (UC1, UC3) trong các trường hợp bình thường, tăng tải và tấn công web. Sử dụng Faker, SlowApi.

- Flow replay: Phát lại (replay) flow record từ file CSV CICIDS2017 theo lịch trình thời gian, cung cấp dữ liệu flow cho Processing (UC2, UC4).

- Đóng vai trò Mock App, tiếp nhận và thực thi các cơ chế phản ứng (hạn chế truy cập, chặn IP, mở rộng) bằng cách đọc trạng thái từ Redis.

- Xây dựng bằng FastAPI, sử dụng Faker, SlowApi, Uvicorn.

# Hạ tầng chia sẻ (Shared Infrastructure)

- PostgreSQL: Lưu trữ log đã chuẩn hóa (hai bảng: HTTP log và flow record), lịch sử cảnh báo và xử lý, cung cấp dữ liệu cho Dashboard.

- Redis: Cache được dùng chung cho Processing (cửa sổ trượt HTTP), Reaction (cập nhật danh sách chặn IP, trạng thái hạn chế truy cập) và Simulation (đọc trạng thái để thực thi các cơ chế phản ứng).

# Thuật toán và mô hình

## Phát hiện đột biến lưu lượng

- Input: chuỗi thời gian tần suất request trong cửa sổ trượt 60 giây (HTTP access log).

- EMA / Z-Score / IQR: xác định baseline và ngưỡng bất thường trên dữ liệu cửa sổ trượt ngắn hạn.

- Seasonal Baseline: xác định baseline dựa trên chu kỳ thời gian (diurnal/weekly) sử dụng thống kê robust (Median, MAD).

- Ensemble Rule Engine: hiệu chuẩn ngưỡng (calibration) và tổng hợp kết quả (voting) để giảm báo động giả và phân loại mức độ nghiêm trọng (Severity).

- Output: anomaly score, vote count, severity level và binary flag (đột biến / bình thường).

## Phát hiện tấn công DDoS

- Input: vector đặc trưng flow từ CICFlowMeter (~80 đặc trưng gốc, giảm xuống chính xác 43 đặc trưng sau tiền xử lý). Các đặc trưng bao gồm: Total Length of Fwd Packets, Total Bwd Packets, Flow Bytes/s, Flow Packets/s, Flow IAT Mean/Std/Min, Fwd/Bwd Packet Length Mean/Max/Min, và các đặc trưng flag/header. (Flow Duration được loại bỏ do tính tương quan thấp sau xử lý).

- XGBoost: Phân loại nhị phân có giám sát (Benign / DDoS). Huấn luyện trên Monday benign + Friday DDoS với nhãn được cung cấp sẵn.

- Output: predicted class, probability score.

## Phát hiện tấn công Web

- Input: raw HTTP request (URL, method, headers, body).

- Rule Engine (Regex): so khớp chữ ký tấn công đã biết, trả về binary match.

- XGBoost: sử dụng vector đặc trưng 12 chiều bao gồm các nhóm: cấu trúc (độ dài, ký tự đặc biệt), độ phức tạp (entropy, độ sâu path) và từ vựng (thống kê tên tham số lạ) để phân loại. Model cung cấp khả năng nhận diện các biến thể tấn công phức tạp dựa trên cấu trúc request.

- Output: layer kích hoạt và mức độ tin cậy (confidence).

## Phát hiện tấn công Brute Force

- Input: vector đặc trưng flow (sử dụng chung bộ đặc trưng đã giảm chiều với UC2 - uc2_feature_cols.json).

- XGBoost: Phân loại nhị phân có giám sát (Benign / Brute Force). Huấn luyện trên Monday (Benign) và Tuesday (Brute Force).

- Output: predicted class, probability score.

- Đặc trưng nhận diện: Tấn công brute force được model nhận diện dựa trên các đặc tính kết nối ngắn lặp lại, sự xuất hiện của RST flag và các cổng đích đặc trưng (21 cho FTP, 22 cho SSH).

- So sánh với UC2: UC2 tập trung vào tấn công thể tích (DDoS), trong khi UC4 tập trung vào tấn công dựa trên xác thực (Brute Force). Cả hai đều sử dụng XGBoost nhưng được huấn luyện trên các tập dữ liệu tấn công khác nhau, hoạt động song song trong Detection microservice để bao phủ phổ tấn công mạng rộng hơn.

# Dữ liệu

## Dữ liệu thực

- NASA HTTP (1995): Web access logs định dạng CLF, sử dụng cho phát hiện đột biến lưu lượng (UC1). Đánh giá dựa trên các sự kiện nhiệm vụ NASA thực tế (STS-71 landing, STS-70 launch/landing).

- CSIC 2010: HTTP request bình thường và tấn công web (SQLi, XSS, Path Traversal) với nhãn benign/attack, sử dụng cho phát hiện tấn công web (UC3).

- CICIDS2017: Network flow dataset từ Canadian Institute for Cybersecurity. Gồm ~2.83 triệu flow record với ~80 đặc trưng được trích xuất bởi CICFlowMeter. Dữ liệu thu thập trong 5 ngày (Monday-Friday, July 3-7, 2017) với nhãn benign và các loại tấn công (Brute Force, DoS, DDoS, Web Attack, Botnet, Port Scan, Infiltration, Heartbleed). Sử dụng cho phát hiện tấn công DDoS (UC2) và phát hiện tấn công Brute Force (UC4).

## Dữ liệu mô phỏng (từ Simulation)

- HTTP generator:

- Normal traffic: sinh theo phân phối Poisson.

- Traffic spike: tăng đột ngột N lần baseline.

- Web attack: payload SQLi, XSS, Path Traversal.

- Flow replay:

- Phát lại flow record từ CICIDS2017 CSV theo thứ tự thời gian, mô phỏng luồng dữ liệu flow thời gian thực cho UC2 và UC4.

# Kiểm thử

## Kiểm thử phát hiện (Detection)

- Metrics: Precision, Recall, F1-Score, False Positive Rate.

- Phương pháp: chạy từng use case với dữ liệu thực, so sánh kết quả phát hiện với nhãn do dataset cung cấp.

- Mỗi use case được đánh giá độc lập bằng confusion matrix và chỉ số F1.

- So sánh cross-UC: đánh giá hiệu quả phát hiện của UC2 (DDoS) vs. UC4 (Brute Force) trên cùng luồng dữ liệu flow, phân tích khả năng nhận diện đa dạng các loại tấn công mạng.

## Kiểm thử tích hợp (Integration)

- Kiểm tra giao tiếp giữa các service qua RabbitMQ (`log.raw`, `log.normalized.http`, `log.normalized.flow`, và `detection.results`).

- Kiểm tra Dashboard hiển thị đúng dữ liệu từ PostgreSQL (cả HTTP log và flow record).

## Kiểm thử hệ thống (End-to-End)

- Kịch bản HTTP: Simulation sinh tấn công web → Processing (CLF parser) → Detection (UC1/UC3) → Reaction → thực thi phản ứng.

- Kịch bản Flow: Simulation replay flow DDoS → Processing (Flow normalizer) → Detection (UC2/UC4) → Reaction → thực thi phản ứng.

- Đo lường: thời gian phản ứng đầu cuối (từ khi log/flow được sinh ra đến khi phản ứng được thực thi).

- Kiểm tra: trạng thái Redis được cập nhật đúng, log/flow và alert được lưu vào PostgreSQL.

## Kiểm thử tải (Load Testing)

- Công cụ: Locust.

- Mục tiêu: đảm bảo pipeline xử lý được lưu lượng cao trên cả hai track (HTTP và flow) mà không mất dữ liệu.

# Nguồn dữ liệu

- NASA HTTP (1995):
https://www.kaggle.com/datasets/souhagaa/nasa-access-log-dataset-1995

- CSIC 2010:
https://www.kaggle.com/datasets/ispangler/csic-2010-web-application-attacks
https://gitlab.fing.edu.uy/gsi/web-application-attacks-datasets

- CICIDS2017:
https://www.unb.ca/cic/datasets/ids-2017.html
https://www.kaggle.com/datasets/chethuhn/network-intrusion-dataset