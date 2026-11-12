package com.booking.system.health.service;

import org.apache.poi.xssf.streaming.*;
import org.apache.poi.ss.util.CellRangeAddress;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Per-export private spool, without changing POI's process-wide temporary-file strategy. */
final class HealthPrivateWorkbook extends SXSSFWorkbook {
    private final Path directory;
    private final Map<String,Integer> columns=new HashMap<>();
    HealthPrivateWorkbook() throws IOException {
        super(50);
        // Fail closed on filesystems that cannot enforce private POSIX permissions.
        directory=Files.createTempDirectory("cfc-health-export-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    void columns(String sheet,int count){columns.put(sheet,count);}
    void finishSheets(){for(var sheet:this){sheet.createFreezePane(0,1);sheet.setAutoFilter(new CellRangeAddress(0,Math.max(0,sheet.getLastRowNum()),0,columns.get(sheet.getSheetName())-1));}}
    @Override protected SheetDataWriter createSheetDataWriter() throws IOException {
        return new GZIPSheetDataWriter(){@Override public File createTempFile() throws IOException {
            return Files.createTempFile(directory,"sheet-",".xml.gz",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))).toFile();
        }};
    }
    @Override public void close() throws IOException {
        try{super.close();}finally{
            try(var files=Files.list(directory)){for(var file:files.toList())Files.deleteIfExists(file);}
            Files.deleteIfExists(directory);
        }
    }
}
