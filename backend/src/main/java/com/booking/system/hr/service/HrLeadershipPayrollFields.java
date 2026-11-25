package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.entity.HrPayrollImportRow;
import java.util.*;

/** Source amounts are preserved; the import never calculates the allowance or payroll. */
public final class HrLeadershipPayrollFields {
    private HrLeadershipPayrollFields() { }
    public static final Map<String,String> INCOME = ordered("luongCoBan","Lương cơ bản","buDauNam","Bù đầu năm (cột 30%)");
    public static final Map<String,String> DEDUCTIONS = ordered("thueTncn","Thuế TNCN","bhxh","BHXH 10,5%","baoGiat","Bgiặt","htkk","HTKK","thuDangPhi","Đảng","asxh","Quỹ ASXH","xhhc","Quỹ HC","doanPhi","Thu ĐP");
    private static Map<String,String> ordered(String... pairs) {
        var result = new LinkedHashMap<String,String>();
        for (int n=0;n<pairs.length;n+=2) result.put(pairs[n],pairs[n+1]);
        return Collections.unmodifiableMap(result);
    }
    public static boolean isLeadership(Map<String,Object> values) {return "LEADERSHIP".equals(values.get("payrollType"));}
    public static void requireValid(Map<String,Object> values) {
        if (values.containsKey("validationErrors")) throw HrApiException.badRequest("PAYROLL_ROW_INVALID","Dòng lương còn ô lỗi hoặc thiếu số liệu; sửa Excel và nhập lại trước khi tạo hoặc gửi phiếu.");
    }
    public static String caption(HrPayrollImportRow row,String month,Map<String,Object> values) {
        var message = new StringBuilder("PHIẾU LƯƠNG THÁNG ").append(HrPayrollMessageRenderer.displayMonth(month))
            .append("\nBAN ĐIỀU HÀNH\n\nHọ và tên: ").append(row.getEmployeeName()).append("\nMã nhân viên: ").append(row.getEmployeeCode()).append("\n\nCHI TIẾT LƯƠNG");
        INCOME.forEach((key,label)->money(message,label,key,values));
        money(message,"Tổng tiền lương","tienLuong",values);
        message.append("\n\nKHOẢN THU TRONG LƯƠNG");
        DEDUCTIONS.forEach((key,label)->money(message,label,key,values));
        money(message,"Tổng khoản thu","tongThu",values);
        money(message,"THỰC NHẬN (CHUYỂN KHOẢN)","nganHangChuyen",values);
        return message.append("\n\nNếu có thắc mắc về phiếu lương, vui lòng liên hệ phòng Kế toán.").toString();
    }
    private static void money(StringBuilder out,String label,String key,Map<String,Object> values) {
        out.append('\n').append(label).append(": ").append(HrPayrollMessageRenderer.formattedMoney(values,key)).append(" đ");
    }
}
