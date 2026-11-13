package com.booking.system.health.importer.baseline;

import java.util.Locale;
import java.util.Set;
import static com.booking.system.health.importer.HealthImportSupport.*;

public final class HealthClassification {
    private static final Set<String> CODES=Set.of("I","II","III","IV","V");
    private HealthClassification() { }
    public static boolean isClassification(String label) {
        return Set.of("phan loai","phan loai suc khoe","xep loai suc khoe").contains(normalized(label));
    }
    public static String normalize(String raw) {
        String value=clean(raw).toUpperCase(Locale.ROOT).replaceFirst("\\.$", "");
        return CODES.contains(value) ? value : null;
    }
}
