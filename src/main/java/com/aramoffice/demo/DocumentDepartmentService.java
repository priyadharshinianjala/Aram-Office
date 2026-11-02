package com.aramoffice.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentDepartmentService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public DocumentDepartmentService(
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void setDepartment(
            String documentId,
            String departmentName,
            String actor) {
        String departmentId = resolveDepartmentId(departmentName);
        jdbcTemplate.update(
                "INSERT INTO document_departments "
                        + "(document_id, department_id) VALUES (?, ?)",
                documentId,
                departmentId);
        auditLogService.record(
                actor,
                "DOCUMENT_DEPARTMENT_SET",
                documentId + " -> " + departmentId);
    }

    @Transactional
    public String resolveDepartmentId(String departmentName) {
        String normalizedName = departmentName == null
                ? "" : departmentName.trim();
        if (normalizedName.isBlank() || normalizedName.length() > 150
                || normalizedName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Enter a department name of at most 150 characters.");
        }

        List<String> existing = jdbcTemplate.query(
                "SELECT department_id FROM departments "
                        + "WHERE LOWER(department_name) = LOWER(?) "
                        + "ORDER BY department_id",
                (result, row) -> result.getString("department_id"),
                normalizedName);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        String departmentId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO departments (department_id, department_name) "
                        + "VALUES (?, ?)",
                departmentId,
                normalizedName);
        return departmentId;
    }

    public String departmentIdForDocument(String documentId) {
        List<String> departmentIds = jdbcTemplate.query(
                "SELECT department_id FROM document_departments "
                        + "WHERE document_id = ?",
                (result, row) -> result.getString("department_id"),
                documentId);
        return departmentIds.stream().findFirst().orElseThrow(() ->
                new IllegalStateException(
                        "The document department was not saved."));
    }

    public String departmentName(String documentId) {
        List<String> assignedDepartments = jdbcTemplate.query(
                "SELECT d.department_name FROM document_departments dd "
                        + "JOIN departments d ON d.department_id = dd.department_id "
                        + "WHERE dd.document_id = ?",
                (result, row) -> result.getString("department_name"),
                documentId);
        if (!assignedDepartments.isEmpty()) {
            return assignedDepartments.get(0);
        }
        List<String> fileDepartments = jdbcTemplate.query(
                "SELECT d.department_name FROM document_files f "
                        + "JOIN departments d ON d.department_id = f.department_id "
                        + "WHERE f.document_id = ? "
                        + "ORDER BY f.uploaded_at DESC, f.file_id DESC",
                (result, row) -> result.getString("department_name"),
                documentId);
        return fileDepartments.stream().findFirst().orElse(null);
    }

}
