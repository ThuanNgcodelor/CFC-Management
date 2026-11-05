package com.booking.system.infrastructure.api;

import com.booking.system.dto.ApiResponse;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InfrastructureController.class)
public class InfrastructureExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, exception.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, exception.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(503, "Dịch vụ hạ tầng hiện chưa sẵn sàng."));
    }
}
