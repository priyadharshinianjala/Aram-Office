package com.aramoffice.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
            String departmentId,
            String actor) {
        String normalizedDepartment = departmentId == null
                ? "" : departmentId.trim();
        if (normalizedDepartment.isBlank()
                || !exists(normalizedDepartment)) {
            throw new IllegalArgumentException("Select an existing department.");
        }
        jdbcTemplate.update(
                "INSERT INTO document_departments "
                        + "(document_id, department_id) VALUES (?, ?)",
                documentId,
                normalizedDepartment);
        auditLogService.record(
                actor,
                "DOCUMENT_DEPARTMENT_SET",
                documentId + " -> " + normalizedDepartment);
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

    private boolean exists(String departmentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM departments WHERE department_id = ?",
                Integer.class,
                departmentId);
        return count != null && count > 0;
    }
}
