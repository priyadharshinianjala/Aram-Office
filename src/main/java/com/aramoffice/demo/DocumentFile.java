package com.aramoffice.demo;

import java.time.LocalDateTime;

public record DocumentFile(
        long fileId,
        String documentId,
        String storedFilename,
        String originalFilename,
        String fileType,
        long fileSize,
        String uploadedBy,
        LocalDateTime uploadedAt,
        String departmentId,
        String departmentName) {

    public boolean isImage() {
        return fileType.startsWith("image/");
    }

    public boolean isPdf() {
        return fileType.equals("application/pdf");
    }

    public String formattedSize() {
        return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
    }
}
