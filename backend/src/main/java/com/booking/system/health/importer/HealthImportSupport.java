package com.booking.system.health.importer;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportJsonCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

public final class HealthImportSupport {
    public static final int MAX_ROWS = 2500, MAX_COLUMNS = 256, MAX_CELLS = 200000;
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024, MAX_ZIP_BYTES = 64 * 1024 * 1024;
    public static final int MAX_CELL_CHARS = 16000, MAX_SELECTION = 100;
    private static final HrImportJsonCodec CODEC = new HrImportJsonCodec();
    private HealthImportSupport() { }
    public static String normalized(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
    public static String clean(String text) {
        return text == null ? "" : text.replace("_x000D_", "\n").strip();
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("Thiếu SHA-256.", ex); }
    }
    public static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    public static String json(Object value) {
        try { return CODEC.write(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Không thể mã hóa dữ liệu sức khỏe."); }
    }
    public static <T> T read(String text, Class<T> type) {
        try { return CODEC.read(text, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Dữ liệu sức khỏe lưu trữ không hợp lệ."); }
    }
    public static HrApiException invalid(String message) { return HrApiException.badRequest("HEALTH_IMPORT_INVALID", message); }
    public static String valueKey(String indicatorId, String unit, String method) {
        return hash(indicatorId + "|" + clean(unit) + "|" + clean(method));
    }
}
