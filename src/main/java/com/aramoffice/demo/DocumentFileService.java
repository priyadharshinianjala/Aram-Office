package com.aramoffice.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DocumentFileService {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Logger LOGGER =
            LoggerFactory.getLogger(DocumentFileService.class);
    private static final Pattern DOCUMENT_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,10}");
    private final JdbcTemplate jdbcTemplate;
    private final DocumentService documentService;
    private final DocumentDepartmentService documentDepartmentService;
    private final AuditLogService auditLogService;
    private final Path storageRoot;

    public DocumentFileService(
            JdbcTemplate jdbcTemplate,
            DocumentService documentService,
            DocumentDepartmentService documentDepartmentService,
            AuditLogService auditLogService,
            @Value("${app.documents.storage-dir}") String storageDirectory) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentService = documentService;
        this.documentDepartmentService = documentDepartmentService;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public List<DocumentFile> filesForDocument(String documentId) {
        return jdbcTemplate.query(
                "SELECT f.file_id, f.document_id, f.stored_filename, "
                        + "f.original_filename, f.file_type, f.file_size, "
                        + "f.uploaded_by, f.uploaded_at, f.department_id, "
                        + "d.department_name "
                        + "FROM document_files f JOIN departments d "
                        + "ON d.department_id = f.department_id "
                        + "WHERE f.document_id = ? ORDER BY f.uploaded_at DESC, f.file_id DESC",
                DocumentFileService::mapFile,
                documentId);
    }

    public DocumentFile fileForDocument(String documentId, long fileId) {
        List<DocumentFile> files = jdbcTemplate.query(
                "SELECT f.file_id, f.document_id, f.stored_filename, "
                        + "f.original_filename, f.file_type, f.file_size, "
                        + "f.uploaded_by, f.uploaded_at, f.department_id, "
                        + "d.department_name "
                        + "FROM document_files f JOIN departments d "
                        + "ON d.department_id = f.department_id "
                        + "WHERE f.document_id = ? AND f.file_id = ?",
                DocumentFileService::mapFile,
                documentId,
                fileId);
        return files.stream().findFirst().orElse(null);
    }

    public Path storedPath(DocumentFile file) {
        Path storedPath = storageRoot.resolve(file.storedFilename())
                .normalize();
        if (!storedPath.startsWith(storageRoot)
                || !file.storedFilename().matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalStateException(
                    "Stored document reference is invalid.");
        }
        return storedPath;
    }

    @Transactional
    public DocumentFile upload(
            String documentId,
            String title,
            String category,
            int importance,
            String description,
            String departmentId,
            MultipartFile upload,
            String actor) {
        String normalizedId = normalize(documentId);
        String normalizedTitle = normalize(title);
        String normalizedCategory = normalize(category);
        String normalizedDescription = description == null
                ? "" : description.trim();
        String normalizedDepartment = normalize(departmentId);

        if (!DOCUMENT_ID.matcher(normalizedId).matches()) {
            throw new IllegalArgumentException(
                    "Document ID must be 1–10 letters, numbers, dots, underscores or hyphens.");
        }
        if (normalizedTitle.isBlank() || normalizedTitle.length() > 200) {
            throw new IllegalArgumentException(
                    "Enter a document title of at most 200 characters.");
        }
        if (normalizedCategory.isBlank() || normalizedCategory.length() > 100) {
            throw new IllegalArgumentException(
                    "Enter a document category of at most 100 characters.");
        }
        if (importance < 1 || importance > 5) {
            throw new IllegalArgumentException(
                    "Importance must be between 1 and 5.");
        }
        if (normalizedDescription.getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length > 60_000) {
            throw new IllegalArgumentException(
                    "Description must be at most 60,000 UTF-8 bytes.");
        }
        if (normalizedDepartment.isBlank()
                || !exists("SELECT COUNT(*) FROM departments WHERE department_id = ?",
                normalizedDepartment)) {
            throw new IllegalArgumentException("Select an existing department.");
        }
        ValidatedUpload validated = validateUpload(upload);
        String originalFilename = safeOriginalFilename(upload.getOriginalFilename());
        if (documentService.getDocument(normalizedId) != null) {
            throw new IllegalArgumentException(
                    "That document ID already exists. Choose a new ID so existing records are preserved.");
        }

        documentService.addDocument(new Document(
                normalizedId,
                normalizedTitle,
                normalizedCategory,
                importance,
                normalizedDescription));
        documentDepartmentService.setDepartment(
                normalizedId, normalizedDepartment, actor);

        String storageKey = UUID.randomUUID().toString();
        Path destination = storageRoot.resolve(storageKey).normalize();
        if (!destination.startsWith(storageRoot)) {
            throw new IllegalStateException(
                    "Generated document storage reference is invalid.");
        }
        try {
            createPrivateStorageDirectory();
            Files.createFile(destination);
        } catch (IOException exception) {
            throw new DocumentFileStorageException(
                    "The document file could not be stored securely.", exception);
        }
        try {
            Files.write(destination, validated.bytes(), StandardOpenOption.WRITE);
            restrictFilePermissions(destination);
        } catch (IOException exception) {
            deleteAfterFailure(destination);
            throw new DocumentFileStorageException(
                    "The document file could not be stored securely.", exception);
        }
        registerRollbackCleanup(destination);

        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO document_files "
                            + "(document_id, stored_filename, original_filename, "
                            + "file_type, file_size, uploaded_by, department_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, normalizedId);
            statement.setString(2, storageKey);
            statement.setString(3, originalFilename);
            statement.setString(4, validated.contentType());
            statement.setLong(5, validated.bytes().length);
            statement.setString(6, actor);
            statement.setString(7, normalizedDepartment);
            return statement;
        }, keyHolder);
        auditLogService.record(
                actor,
                "DOCUMENT_FILE_UPLOADED",
                normalizedId + " (" + originalFilename + ")");

        Object generatedId = keyHolder.getKeys().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("file_id"))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (!(generatedId instanceof Number fileId)) {
            throw new IllegalStateException(
                    "The uploaded document metadata was not saved.");
        }
        return fileForDocument(normalizedId, fileId.longValue());
    }

    private ValidatedUpload validateUpload(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("Select a document file to upload.");
        }
        if (upload.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "The selected file exceeds the 10 MB limit.");
        }
        String originalName = upload.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(
                originalName == null ? "" : originalName);
        if (extension == null) {
            throw new IllegalArgumentException(
                    "Choose a PDF, JPG, JPEG, PNG, or WEBP file.");
        }
        String normalizedExtension = extension.toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException exception) {
            throw new DocumentFileStorageException(
                    "The uploaded file could not be read.", exception);
        }
        if (bytes.length == 0 || bytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "The selected file is empty or exceeds the 10 MB limit.");
        }
        String contentType = signatureContentType(bytes);
        String expectedContentType = switch (normalizedExtension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> null;
        };
        if (expectedContentType == null
                || !expectedContentType.equals(contentType)) {
            throw new IllegalArgumentException(
                    "File extension and file signature must match a supported PDF or image format.");
        }
        return new ValidatedUpload(bytes, contentType);
    }

    private String signatureContentType(byte[] bytes) {
        if (startsWith(bytes, new byte[]{'%', 'P', 'D', 'F', '-'})) {
            return "application/pdf";
        }
        if (startsWith(bytes, new byte[]{
                (byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return "image/jpeg";
        }
        byte[] png = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        if (startsWith(bytes, png)) {
            return "image/png";
        }
        if (bytes.length >= 12
                && startsWith(bytes, new byte[]{'R', 'I', 'F', 'F'})
                && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String safeOriginalFilename(String originalFilename) {
        String candidate = originalFilename == null
                ? "" : originalFilename.replace('\\', '/');
        int separator = candidate.lastIndexOf('/');
        candidate = candidate.substring(separator + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (candidate.isBlank()) {
            candidate = "document";
        }
        return candidate.length() <= 255
                ? candidate : candidate.substring(candidate.length() - 255);
    }

    private boolean exists(String sql, String value) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, value);
        return count != null && count > 0;
    }

    private void registerRollbackCleanup(Path path) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                deleteAfterFailure(path);
                            }
                        }
                    });
        }
    }

    private void deleteAfterFailure(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.error("Unable to remove an uncommitted uploaded file.", exception);
        }
    }

    private void createPrivateStorageDirectory() throws IOException {
        Path parent = storageRoot.getParent();
        if (parent == null) {
            throw new IOException(
                    "The configured private storage directory must not be a filesystem root.");
        }
        Files.createDirectories(parent);
        boolean supportsPosix = Files.getFileStore(
                parent).supportsFileAttributeView("posix");
        if (supportsPosix) {
            Files.createDirectories(
                    storageRoot,
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString(
                                    "rwx------")));
            Files.setPosixFilePermissions(
                    storageRoot,
                    java.nio.file.attribute.PosixFilePermissions.fromString(
                            "rwx------"));
        } else {
            Files.createDirectories(storageRoot);
        }
    }

    private void restrictFilePermissions(Path path) throws IOException {
        if (Files.getFileStore(path.getParent())
                .supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(
                    path,
                    java.nio.file.attribute.PosixFilePermissions.fromString(
                            "rw-------"));
        }
    }

    private static DocumentFile mapFile(
            ResultSet result,
            int row) throws SQLException {
        return new DocumentFile(
                result.getLong("file_id"),
                result.getString("document_id"),
                result.getString("stored_filename"),
                result.getString("original_filename"),
                result.getString("file_type"),
                result.getLong("file_size"),
                result.getString("uploaded_by"),
                result.getTimestamp("uploaded_at").toLocalDateTime(),
                result.getString("department_id"),
                result.getString("department_name"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record ValidatedUpload(byte[] bytes, String contentType) {
    }
}
