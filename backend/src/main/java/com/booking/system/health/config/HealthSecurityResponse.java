package com.booking.system.health.config;

import com.booking.system.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Write API errors directly: servlet error dispatch can otherwise turn a health 403 into 401. */
public final class HealthSecurityResponse {
    private static final String ROOT="/api/v1/hr/health";
    private static final ObjectMapper JSON=new ObjectMapper();
    private HealthSecurityResponse() { }
    public static boolean writeIfHealth(HttpServletRequest request,HttpServletResponse response,int status) throws IOException {
        String path=request.getRequestURI();
        if(!path.equals(ROOT)&&!path.startsWith(ROOT+"/"))return false;
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store, private");
        response.setHeader("X-Content-Type-Options","nosniff");
        String message=status==HttpServletResponse.SC_FORBIDDEN?"Bạn không có quyền truy cập sổ sức khỏe.":"Vui lòng đăng nhập để truy cập sổ sức khỏe.";
        JSON.writeValue(response.getWriter(),ApiResponse.error(status,message));
        return true;
    }
}
