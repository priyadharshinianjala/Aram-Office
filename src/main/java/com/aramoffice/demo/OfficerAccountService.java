package com.aramoffice.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class OfficerAccountService {

    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9._-]{2,64}");
    private static final Pattern USERNAME =
            Pattern.compile("[A-Za-z0-9._@-]{3,100}");
    private static final int MAX_PASSWORD_BYTES = 72;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public OfficerAccountService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public List<Department> departments() {
        return jdbcTemplate.query(
                "SELECT department_id, department_name FROM departments "
                        + "ORDER BY department_name, department_id",
                (result, row) -> new Department(
                        result.getString("department_id"),
                        result.getString("department_name")));
    }

    public List<OfficerAccount> officers() {
        return jdbcTemplate.query(
                "SELECT o.officer_id, o.full_name, o.username, o.department_id, "
                        + "d.department_name, o.enabled "
                        + "FROM officers o LEFT JOIN departments d "
                        + "ON d.department_id = o.department_id "
                        + "ORDER BY o.full_name, o.officer_id",
                (result, row) -> new OfficerAccount(
                        result.getString("officer_id"),
                        result.getString("full_name"),
                        result.getString("username"),
                        result.getString("department_id"),
                        result.getString("department_name"),
                        result.getBoolean("enabled")));
    }

    @Transactional
    public void createOfficer(
            String officerId,
            String fullName,
            String username,
            String password,
            String actor) {
        String normalizedOfficerId = normalize(officerId);
        String normalizedFullName = normalize(fullName);
        String normalizedUsername = normalize(username);
        validateCredentials(normalizedOfficerId, normalizedFullName,
                normalizedUsername, password);
        if (!IDENTIFIER.matcher(normalizedOfficerId).matches()
                || !USERNAME.matcher(normalizedUsername).matches()) {
            throw new IllegalArgumentException(
                    "Enter a valid officer ID and username.");
        }

        jdbcTemplate.update(
                "INSERT INTO app_users (username, password_hash, user_role) "
                        + "VALUES (?, ?, 'OFFICER')",
                normalizedUsername,
                passwordEncoder.encode(password));
        jdbcTemplate.update(
                "INSERT INTO officers "
                        + "(officer_id, full_name, username, department_id, enabled) "
                        + "VALUES (?, ?, ?, NULL, TRUE)",
                normalizedOfficerId,
                normalizedFullName,
                normalizedUsername);
        auditLogService.record(actor, "OFFICER_CREATED", normalizedUsername);
    }

    @Transactional
    public void setOfficerEnabled(
            String username,
            boolean enabled,
            String actor) {
        int changed = jdbcTemplate.update(
                "UPDATE officers SET enabled = ? WHERE username = ?",
                enabled,
                username);
        if (changed != 1) {
            throw new IllegalArgumentException("Officer account was not found.");
        }
        auditLogService.record(
                actor,
                enabled ? "OFFICER_ENABLED" : "OFFICER_DISABLED",
                username);
    }

    @Transactional
    public void resetPassword(
            String username,
            String newPassword,
            String actor) {
        validatePassword(newPassword);
        int changed = jdbcTemplate.update(
                "UPDATE app_users SET password_hash = ? "
                        + "WHERE username = ? AND user_role = 'OFFICER'",
                passwordEncoder.encode(newPassword),
                username);
        if (changed != 1) {
            throw new IllegalArgumentException("Officer account was not found.");
        }
        auditLogService.record(actor, "OFFICER_PASSWORD_RESET", username);
    }

    private boolean exists(String sql, String value) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, value);
        return count != null && count > 0;
    }

    private void validateCredentials(
            String officerId,
            String fullName,
            String username,
            String password) {
        if (officerId.isBlank()
                || fullName.isBlank()
                || fullName.length() > 200
                || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Enter an officer ID, a full name of at most 200 characters, and a valid unique username.");
        }
        validatePassword(password);
    }

    private void validatePassword(String password) {
        int passwordBytes = password == null ? 0
                : password.getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes < 12 || passwordBytes > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "Password must be between 12 and 72 UTF-8 bytes.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
