package com.aramoffice.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OfficerDocumentService {

    private final JdbcTemplate jdbcTemplate;

    public OfficerDocumentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OfficerAccount officerProfile(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT o.officer_id, o.full_name, o.username, "
                        + "o.department_id, d.department_name, o.enabled "
                        + "FROM officers o LEFT JOIN departments d "
                        + "ON d.department_id = o.department_id "
                        + "WHERE o.username = ?",
                (result, row) -> new OfficerAccount(
                        result.getString("officer_id"),
                        result.getString("full_name"),
                        result.getString("username"),
                        result.getString("department_id"),
                        result.getString("department_name"),
                        result.getBoolean("enabled")),
                username);
    }

    public List<Document> allDocumentsForOfficer(String username) {
        return jdbcTemplate.query(
                "SELECT d.document_id, d.title, d.category, "
                        + "d.importance, d.current_text "
                        + "FROM documents d "
                        + "WHERE EXISTS (SELECT 1 FROM officers o "
                        + "WHERE o.username = ? AND o.enabled = TRUE) "
                        + "ORDER BY d.document_id",
                (result, row) -> new Document(
                        result.getString("document_id"),
                        result.getString("title"),
                        result.getString("category"),
                        result.getInt("importance"),
                        result.getString("current_text")),
                username);
    }

    public boolean isEnabledOfficer(String username) {
        Boolean allowed = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM officers "
                        + "WHERE username = ? AND enabled = TRUE)",
                Boolean.class,
                username);
        return Boolean.TRUE.equals(allowed);
    }
}
