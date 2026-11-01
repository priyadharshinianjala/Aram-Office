package com.aramoffice.demo;

import java.time.LocalDateTime;

public record DocumentVersion(
        int versionNumber,
        String text,
        boolean current,
        LocalDateTime createdAt) {
}
