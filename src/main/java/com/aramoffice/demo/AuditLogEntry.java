package com.aramoffice.demo;

import java.time.LocalDateTime;

public record AuditLogEntry(
        String actor,
        String action,
        String target,
        LocalDateTime createdAt) {
}
