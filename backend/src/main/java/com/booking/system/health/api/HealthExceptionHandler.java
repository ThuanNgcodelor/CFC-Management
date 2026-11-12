package com.booking.system.health.api;

import com.booking.system.hr.api.HrApiExceptionHandler;
import com.booking.system.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice(basePackageClasses = HealthController.class)
public class HealthExceptionHandler extends HrApiExceptionHandler {
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> invalidFilter() {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "Bộ lọc sổ sức khỏe không hợp lệ."));
    }
}
