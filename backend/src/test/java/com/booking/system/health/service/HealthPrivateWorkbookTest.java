package com.booking.system.health.service;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class HealthPrivateWorkbookTest {
    @Test void sheetSpoolIsPrivateAndCleanedEvenIfOutputFails() throws Exception {
        var before=directories();Path created;
        try(var book=new HealthPrivateWorkbook()) {
            var sheet=book.createSheet("Fake");book.columns("Fake",1);
            for(int n=0;n<200;n++)sheet.createRow(n).createCell(0).setCellValue("Chỉ tiêu giả lập");
            var added=directories();added.removeAll(before);assertThat(added).hasSize(1);created=added.iterator().next();
            assertThat(Files.getPosixFilePermissions(created)).isEqualTo(PosixFilePermissions.fromString("rwx------"));
            try(var files=Files.list(created)){for(var file:files.toList())assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-------"));}
            book.finishSheets();
            assertThatThrownBy(()->book.writeAvoidingTempFiles(new OutputStream(){@Override public void write(int b) throws IOException {throw new IOException("Fixture output failed");}})).isInstanceOf(IOException.class);
        }
        assertThat(Files.exists(created)).isFalse();
    }
    private static Set<Path> directories() throws IOException {try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))){return files.filter(p->p.getFileName().toString().startsWith("cfc-health-export-")).collect(java.util.stream.Collectors.toSet());}}
}
