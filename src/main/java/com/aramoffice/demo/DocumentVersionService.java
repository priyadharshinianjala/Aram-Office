package com.aramoffice.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DocumentVersionService {

    private static final int MAX_CONTENT_BYTES = 60_000;

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public DocumentVersionService(
            JdbcTemplate jdbcTemplate,
            AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    public List<DocumentVersion> versions(String documentId) {
        return jdbcTemplate.query(
                "SELECT version_number, text_content, is_current, created_at "
                        + "FROM document_versions WHERE document_id = ? "
                        + "ORDER BY version_number DESC",
                (result, row) -> new DocumentVersion(
                        result.getInt("version_number"),
                        result.getString("text_content"),
                        result.getBoolean("is_current"),
                        result.getTimestamp("created_at").toLocalDateTime()),
                documentId);
    }

    @Transactional
    public int createRevision(
            String documentId,
            String content,
            String actor) {
        String normalizedContent = content == null ? "" : content.trim();
        int contentBytes = normalizedContent.getBytes(StandardCharsets.UTF_8).length;
        if (normalizedContent.isBlank() || contentBytes > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException(
                    "Document content must be non-empty and at most 60,000 UTF-8 bytes.");
        }

        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE document_id = ?",
                Integer.class,
                documentId);
        if (documentCount == null || documentCount == 0) {
            throw new IllegalArgumentException("Document was not found.");
        }
        String existingContent = jdbcTemplate.query(
                "SELECT current_text FROM documents "
                        + "WHERE document_id = ? FOR UPDATE",
                result -> result.next() ? result.getString(1) : null,
                documentId);

        Integer existingVersionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_versions WHERE document_id = ?",
                Integer.class,
                documentId);
        if (existingVersionCount == null || existingVersionCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO document_versions "
                            + "(document_id, version_number, text_content, is_current) "
                            + "VALUES (?, 1, ?, TRUE)",
                    documentId,
                    existingContent == null ? "" : existingContent);
        }

        Integer latestVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) "
                        + "FROM document_versions WHERE document_id = ?",
                Integer.class,
                documentId);
        int nextVersion = latestVersion == null ? 1 : latestVersion + 1;
        jdbcTemplate.update(
                "UPDATE document_versions SET is_current = FALSE "
                        + "WHERE document_id = ? AND is_current = TRUE",
                documentId);
        jdbcTemplate.update(
                "UPDATE documents SET current_text = ? WHERE document_id = ?",
                normalizedContent,
                documentId);
        jdbcTemplate.update(
                "INSERT INTO document_versions "
                        + "(document_id, version_number, text_content, is_current) "
                        + "VALUES (?, ?, ?, TRUE)",
                documentId,
                nextVersion,
                normalizedContent);
        auditLogService.record(
                actor,
                "DOCUMENT_VERSION_CREATED",
                documentId + " (version " + nextVersion + ")");
        return nextVersion;
    }

    @Transactional
    public void createInitialVersion(Document document) {
        jdbcTemplate.update(
                "INSERT INTO document_versions "
                        + "(document_id, version_number, text_content, is_current) "
                        + "VALUES (?, 1, ?, TRUE)",
                document.getId(),
                document.getText() == null ? "" : document.getText());
    }
}
