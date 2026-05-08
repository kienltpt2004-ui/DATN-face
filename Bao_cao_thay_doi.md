# BÁO CÁO TỔNG HỢP CÁC THAY ĐỔI & TÍNH NĂNG MỚI (HỆ THỐNG ĐIỂM DANH AI)

Tài liệu này tổng hợp các nâng cấp quan trọng về logic, tính năng và giao diện đã được triển khai cho hệ thống điểm danh sinh viên bằng nhận diện khuôn mặt và GPS.

---

## 1. CẬP NHẬT QUY TẮC ĐIỂM DANH (ATTENDANCE RULES)

Hệ thống đã được thiết lập quy tắc thời gian nghiêm ngặt để đảm bảo tính minh bạch:

*   **Thời gian mở lớp**: Lớp học hiển thị trên App sinh viên từ **15 phút trước** giờ bắt đầu.
*   **Điểm danh sinh viên (Tự động/App)**:
    *   **0 - 15 phút đầu**: Ghi nhận trạng thái **"Có mặt" (Present)**.
    *   **15 - 30 phút tiếp theo**: Ghi nhận trạng thái **"Muộn" (Late)**.
    *   **Sau 30 phút**: Chặn tính năng tự điểm danh trên App.
*   **Điểm danh thủ công (Giáo viên)**:
    *   Giáo viên có quyền điểm danh cho sinh viên bất cứ lúc nào.
    *   Nếu GV tích chọn sau 30 phút đầu của tiết học: Hệ thống tự động ghi nhận là **"Nửa buổi" (Half)**.
*   **Trọng số chuyên cần**: Trạng thái "Nửa buổi" được tính bằng **0.5 buổi** có mặt khi tính tỷ lệ chuyên cần trong báo cáo.

---

## 2. TÍNH NĂNG XÁC THỰC VỊ TRÍ (GPS GEOFENCING)

Đây là nâng cấp lớn nhất để ngăn chặn việc điểm danh hộ từ xa:

*   **Quản lý vị trí (Admin)**: 
    *   Tích hợp bản đồ tương tác (**Leaflet & OpenStreetMap**).
    *   Tính năng tìm kiếm địa chỉ tự động và lấy tọa độ hiện tại.
    *   Thiết lập **Bán kính quét (Radius)**: Vẽ vòng tròn trực quan trên bản đồ để giới hạn phạm vi điểm danh.
*   **Liên kết lịch học**: Mỗi lịch dạy hiện nay có thể được gán cho một vị trí GPS cụ thể (ví dụ: CS1, CS2 hoặc một tòa nhà cụ thể).
*   **Xác thực Backend**: 
    *   Sử dụng công thức **Haversine** để tính khoảng cách thực tế giữa sinh viên và lớp học.
    *   Chặn điểm danh nếu sinh viên đứng ngoài bán kính cho phép.

---

## 3. CẢI TIẾN QUẢN LÝ LỊCH HỌC (SCHEDULE MANAGEMENT)

*   **Định dạng thời gian 24h**: Loại bỏ hoàn toàn AM/PM gây nhầm lẫn. Sử dụng định dạng `HH:mm` (VD: 09:00, 21:30).
*   **Bộ chọn giờ thông minh (Time Picker)**: 
    *   Ô nhập văn bản hỗ trợ gõ nhanh.
    *   Nút bấm biểu tượng Đồng hồ để mở bảng chọn giờ/phút trực quan.
*   **Ràng buộc trùng lịch**: 
    *   Tự động cảnh báo nếu Giáo viên bị trùng giờ dạy.
    *   Tự động cảnh báo nếu Phòng học bị trùng lịch.
    *   Tự động cảnh báo nếu Lớp học có 2 môn học cùng lúc.

---

## 4. CẬP NHẬT BÁO CÁO & THỐNG KÊ (REPORTS & DASHBOARD)

*   **Thống kê chi tiết**: Thêm cột hiển thị số buổi "Nửa buổi" trong báo cáo tổng hợp.
*   **Tỷ lệ chuyên cần mới**: 
    *   Công thức: `% Chuyên cần = (Số buổi có mặt + Số buổi muộn + 0.5 * Số buổi nửa buổi) / Tổng số buổi`.
*   **Cảnh báo thông minh**: Hệ thống tự động hiển thị "Cảnh báo Đỏ" hoặc "Cảnh báo Vàng" dựa trên tỷ lệ nghỉ của sinh viên (vượt quá 15-20%).

---

## 5. CẢI TIẾN GIAO DIỆN NGƯỜI DÙNG (UI/UX)

*   **Tính phản hồi (Responsive)**: Các bảng cấu hình (Modal) được thiết lập thanh cuộn thông minh, đảm bảo hoạt động tốt trên cả màn hình nhỏ.
*   **Hiệu ứng & Thẩm mỹ**: 
    *   Sử dụng các bộ Icon hiện đại (Lucide React).
    *   Hiệu ứng chuyển cảnh mượt mà, màu sắc hài hòa (Indigo/Emerald).
    *   Bản đồ tích hợp trực tiếp, không cần chuyển trang.

---
**Ghi chú**: Mọi thay đổi về mã nguồn đã được đồng bộ giữa Frontend (React) và Backend (Spring Boot/MySQL). Để hệ thống hoạt động ổn định nhất, hãy đảm bảo Backend luôn được cập nhật bản mới nhất để tự động đồng bộ cấu trúc dữ liệu GPS.
