package com.attendance.backend.service;

import com.attendance.backend.entity.Student;
import com.attendance.backend.repository.StudentRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class FaceRecognitionService {

    @Value("${ai.service.url:https://face-api-production-fccc.up.railway.app}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private StudentRepository studentRepository;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FaceRecognitionService.class);

    // --- Các hàm hỗ trợ cũ để sửa lỗi build ---
    public String getFaceEmbedding(org.springframework.web.multipart.MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return getFaceEmbeddingFromBase64(base64);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xử lý file: " + e.getMessage());
        }
    }

    public String getFaceEmbedding(String base64Image) {
        return getFaceEmbeddingFromBase64(base64Image);
    }

    private String getFaceEmbeddingFromBase64(String base64Image) {
        String url = aiServiceUrl + "/face/register";
        Map<String, String> body = new HashMap<>();
        body.put("image", base64Image);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            if (response.getBody() != null && response.getBody().containsKey("embedding")) {
                return response.getBody().get("embedding").toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi AI: " + e.getMessage());
        }
        return null;
    }

    public Map<String, Object> verifyFace(org.springframework.web.multipart.MultipartFile file, String storedEmbedding) {
        try {
            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return verifyFace(base64, storedEmbedding);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xử lý file: " + e.getMessage());
        }
    }
    // --- Kết thúc các hàm hỗ trợ cũ ---

    @Transactional
    public void registerFace(String studentId, String base64Image) {
        logger.info("[DEBUG] Đang đăng ký khuôn mặt cho SV: {}", studentId);
        
        String url = aiServiceUrl + "/face/register";
        
        Map<String, String> body = new HashMap<>();
        body.put("image", base64Image);

        try {
            logger.info("[DEBUG] Gọi API Postman URL: {}", url);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            
            if (response.getBody() != null && response.getBody().containsKey("embedding")) {
                List<Double> embeddingList = (List<Double>) response.getBody().get("embedding");
                String embeddingStr = embeddingList.toString();
                
                Student student = studentRepository.findById(studentId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy sinh viên: " + studentId));
                
                student.setFaceEmbedding(embeddingStr);
                studentRepository.save(student);
                logger.info("[DEBUG] Đăng ký THÀNH CÔNG cho SV: {}. Embedding: {}", studentId, embeddingStr);
            } else {
                throw new RuntimeException("AI response không chứa embedding");
            }
        } catch (Exception e) {
            logger.error("[DEBUG] Lỗi đăng ký: {}", e.getMessage());
            throw new RuntimeException("Lỗi kết nối AI Service: " + e.getMessage());
        }
    }

    public Map<String, Object> verifyFace(String base64Image, String storedEmbedding) {
        String url = aiServiceUrl + "/face/verify";
        logger.info("[DEBUG] Gọi API Xác thực Postman URL: {}", url);

        Map<String, Object> body = new HashMap<>();
        body.put("image", base64Image);
        
        // Convert "[0.1, 0.2, ...]" -> List<Double>
        String clean = storedEmbedding.replace("[", "").replace("]", "");
        String[] parts = clean.split(",");
        List<Double> embeddingList = new ArrayList<>();
        for (String p : parts) {
            embeddingList.add(Double.parseDouble(p.trim()));
        }
        body.put("embedding", embeddingList);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            return response.getBody();
        } catch (Exception e) {
            logger.error("[DEBUG] Lỗi xác thực: {}", e.getMessage());
            throw new RuntimeException("Lỗi xác thực khuôn mặt: " + e.getMessage());
        }
    }

    @Transactional
    public void updateFace(String studentId, String base64Image) {
        registerFace(studentId, base64Image);
    }
}
