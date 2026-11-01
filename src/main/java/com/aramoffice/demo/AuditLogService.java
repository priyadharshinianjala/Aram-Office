package com.aramoffice.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String actor, String action, String target) {
        jdbcTemplate.update(
                "INSERT INTO audit_log (actor_username, action_type, target_reference) "
                        + "VALUES (?, ?, ?)",
                limit(actor == null ? "unknown" : actor, 100),
                limit(action, 48),
                target == null ? null : limit(target, 512));
    }

    public List<AuditLogEntry> recent() {
        return jdbcTemplate.query(
                "SELECT actor_username, action_type, target_reference, created_at "
                        + "FROM audit_log ORDER BY audit_id DESC LIMIT 100",
                (result, row) -> new AuditLogEntry(
                        result.getString("actor_username"),
                        result.getString("action_type"),
                        result.getString("target_reference"),
                        result.getTimestamp("created_at").toLocalDateTime()));
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength
                ? value : value.substring(0, maxLength);
    }
}
