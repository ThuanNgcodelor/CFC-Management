package com.booking.system.infrastructure.api;

import com.booking.system.dto.ApiResponse;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {InfrastructureController.class, RouterConnectionController.class})
public class InfrastructureExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, exception.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, exception.getMessage()));
    }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, org.springframework.dao.DuplicateKeyException.class})
    public ResponseEntity<ApiResponse<Void>> conflict() {
        return ResponseEntity.status(409).body(ApiResponse.error(409, "Cấu hình hoặc danh mục đã thay đổi; tải lại trước khi lưu."));
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "Kiểm tra địa chỉ, tài khoản và độ dài mật khẩu; dữ liệu nhập chưa hợp lệ."));
    }
    @ExceptionHandler(com.booking.system.infrastructure.service.RouterCredentialCipher.CredentialException.class)
    public ResponseEntity<ApiResponse<Void>> credential(com.booking.system.infrastructure.service.RouterCredentialCipher.CredentialException exception) {
        return ResponseEntity.status(503).body(ApiResponse.error(503, exception.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(503, "Dịch vụ hạ tầng hiện chưa sẵn sàng."));
    }
}
