package com.aramoffice.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("demo")
@Transactional
class OfficerAccessIntegrationTest {

    private static final Path TEST_STORAGE_DIRECTORY =
            createTemporaryStorageDirectory();

    @DynamicPropertySource
    static void configureDocumentStorage(DynamicPropertyRegistry registry) {
        registry.add(
                "app.documents.storage-dir",
                () -> TEST_STORAGE_DIRECTORY.toString());
    }

    @AfterAll
    static void removeTemporaryDocumentStorage() throws IOException {
        if (Files.exists(TEST_STORAGE_DIRECTORY)) {
            try (var paths = Files.walk(TEST_STORAGE_DIRECTORY)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Path createTemporaryStorageDirectory() {
        try {
            return Files.createTempDirectory("aram-office-upload-tests-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired
    private OfficerAccountService officerAccountService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private OfficerDocumentService officerDocumentService;

    @Autowired
    private DatabaseUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DocumentVersionService documentVersionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentFileService documentFileService;

    @Autowired
    private DocumentDepartmentService documentDepartmentService;

    @Autowired
    private DocumentCreationService documentCreationService;

    @Autowired
    private CitizenSearchService citizenSearchService;

    @Test
    void everyEnabledOfficerCanSeeTheCompleteDocumentRepository() {
        officerAccountService.createOfficer(
                "REV-OFFICER-TEST", "Ravi Revenue", "revenue-test",
                "RevenuePass2026!", "admin");
        officerAccountService.createOfficer(
                "HEA-OFFICER-TEST", "Hema Health", "health-test",
                "HealthPass2026!", "admin");
        officerAccountService.createOfficer(
                "EDU-OFFICER-TEST", "Esha Education", "education-test",
                "EducationPass2026!", "admin");

        List<String> allDocuments =
                documentIds(documentService.getAllDocuments());
        assertThat(documentIds(
                officerDocumentService.allDocumentsForOfficer("revenue-test")))
                .containsExactlyInAnyOrderElementsOf(allDocuments);
        assertThat(documentIds(
                officerDocumentService.allDocumentsForOfficer("health-test")))
                .containsExactlyInAnyOrderElementsOf(allDocuments);
        assertThat(documentIds(
                officerDocumentService.allDocumentsForOfficer("education-test")))
                .containsExactlyInAnyOrderElementsOf(allDocuments);
        assertThat(officerDocumentService.isEnabledOfficer("revenue-test"))
                .isTrue();
    }

    @Test
    void newOfficerCanSeeAllExistingDocumentsImmediately() {
        officerAccountService.createOfficer(
                "EDU-OFFICER-TEST", "Priya Sharma", "education-test",
                "EducationPass2026!", "admin");

        List<String> existingDocuments =
                documentIds(documentService.getAllDocuments());
        assertThat(documentIds(
                officerDocumentService.allDocumentsForOfficer("education-test")))
                .containsExactlyInAnyOrderElementsOf(existingDocuments);
        assertThat(officerDocumentService.isEnabledOfficer("education-test"))
                .isTrue();
    }

    @Test
    void disabledOfficerIsReturnedAsDisabledByAuthenticationLookup() {
        officerAccountService.createOfficer(
                "DIS-OFFICER-TEST", "Disabled Officer", "disabled-test",
                "DisabledPass2026!", "admin");
        assertThat(userDetailsService.loadUserByUsername("disabled-test")
                .isEnabled()).isTrue();

        officerAccountService.setOfficerEnabled(
                "disabled-test", false, "admin");

        UserDetails officer = userDetailsService.loadUserByUsername("disabled-test");
        assertThat(officer.isEnabled()).isFalse();
        assertThat(officerDocumentService.isEnabledOfficer("disabled-test"))
                .isFalse();
        assertThat(officerDocumentService.allDocumentsForOfficer("disabled-test"))
                .isEmpty();
        assertThat(passwordEncoder.matches(
                "DisabledPass2026!", officer.getPassword())).isTrue();
    }

    @Test
    void newlyCreatedOfficerHasNoDepartmentAndCanLoadTheirProfile() {
        officerAccountService.createOfficer(
                "MOVE-OFFICER-TEST", "Moving Officer", "moving-officer-test",
                "MovingOfficerPass2026!", "admin");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT department_id FROM officers WHERE username = ?",
                String.class,
                "moving-officer-test")).isNull();
        assertThat(officerDocumentService.officerProfile(
                "moving-officer-test").departmentName()).isNull();
    }

    @Test
    void revisionsPreserveEarlierContentAndOnlyMarkLatestAsCurrent() {
        int nextVersion = documentVersionService.createRevision(
                "D101",
                "Revised property tax instructions.",
                "admin");

        assertThat(nextVersion).isEqualTo(2);
        assertThat(documentVersionService.versions("D101"))
                .extracting(DocumentVersion::versionNumber)
                .containsExactly(2, 1);
        assertThat(documentVersionService.versions("D101"))
                .extracting(DocumentVersion::current)
                .containsExactly(true, false);
        assertThat(documentVersionService.versions("D101"))
                .extracting(DocumentVersion::text)
                .containsExactly(
                        "Revised property tax instructions.",
                        "Updated property tax instructions for assessment, online payment, and receipt retrieval.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_text FROM documents WHERE document_id = 'D101'",
                String.class))
                .isEqualTo("Revised property tax instructions.");
    }

    @Test
    void creatingDocumentAlsoCreatesItsInitialVersion() {
        Document created = documentService.addDocument(new Document(
                "D900", "Versioned test document", "Test", 1, "Initial body"));

        assertThat(documentVersionService.versions(created.getId()))
                .singleElement()
                .satisfies(version -> {
                    assertThat(version.versionNumber()).isEqualTo(1);
                    assertThat(version.text()).isEqualTo("Initial body");
                    assertThat(version.current()).isTrue();
                });
    }

    @Test
    void textDocumentStoresItsSelectedDepartment() {
        Document created = documentCreationService.create(
                new Document(
                        "D908", "Department metadata test", "Test", 2,
                        "Document content"),
                "Health",
                "",
                "admin");

        assertThat(documentDepartmentService.departmentName(created.getId()))
                .isEqualTo("Health");
    }

    @Test
    void typedDepartmentIsCreatedAndExistingDepartmentNameIsReused() {
        Document first = documentCreationService.create(
                new Document(
                        "D910", "Typed department first", "Test", 2, "Text"),
                "Legal Affairs",
                "",
                "admin");
        Document second = documentCreationService.create(
                new Document(
                        "D911", "Typed department second", "Test", 2, "Text"),
                " legal affairs ",
                "",
                "admin");

        assertThat(documentDepartmentService.departmentName(first.getId()))
                .isEqualTo("Legal Affairs");
        assertThat(documentDepartmentService.departmentName(second.getId()))
                .isEqualTo("Legal Affairs");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM departments "
                        + "WHERE LOWER(department_name) = LOWER(?)",
                Integer.class,
                "legal affairs"))
                .isEqualTo(1);
    }

    @Test
    void textDocumentCanBeLinkedToAnExistingCitizenForPersonSearch() {
        jdbcTemplate.update(
                "INSERT INTO citizens (citizen_id, full_name) VALUES (?, ?)",
                "CIT-LINK-TEST",
                "Person Link Test");

        Document created = documentCreationService.create(
                new Document(
                        "D912", "Person-linked record", "Test", 2, "Text"),
                "Health",
                "CIT-LINK-TEST",
                "admin");

        assertThat(documentService.getDocument(created.getId()).getCitizen()
                .getCitizenId()).isEqualTo("CIT-LINK-TEST");
    }

    @Test
    void uploadedDocumentCanBeLinkedToAPersonAndTypedDepartment()
            throws IOException {
        jdbcTemplate.update(
                "INSERT INTO citizens (citizen_id, full_name) VALUES (?, ?)",
                "CIT-UPLOAD-TEST",
                "Upload Person Test");
        byte[] pdfBytes = "%PDF-1.7\nPerson-linked upload"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        documentFileService.upload(
                "D913", "Person-linked PDF", "Certificate", 2,
                "Upload description", "Community Services",
                "CIT-UPLOAD-TEST",
                new MockMultipartFile(
                        "file", "person-record.pdf", "application/pdf",
                        pdfBytes),
                "admin");

        Document created = documentService.getDocument("D913");
        assertThat(created.getCitizen().getCitizenId())
                .isEqualTo("CIT-UPLOAD-TEST");
        assertThat(documentDepartmentService.departmentName("D913"))
                .isEqualTo("Community Services");
    }

    @Test
    void docxUploadIsLinkedToSelectedPersonAndCanBeDownloadedAsOriginal()
            throws IOException {
        jdbcTemplate.update(
                "INSERT INTO citizens (citizen_id, full_name) VALUES (?, ?)",
                "CIT-DOCX-TEST",
                "Word Upload Person");
        byte[] docxBytes = wordDocumentBytes();

        DocumentFile saved = documentFileService.upload(
                "D914", "Person Word Record", "Certificate", 3,
                "Word record description", "Community Services",
                "CIT-DOCX-TEST",
                new MockMultipartFile(
                        "file", "person-record.docx",
                        "application/octet-stream", docxBytes),
                "admin");

        assertThat(saved.fileType()).isEqualTo(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(Files.readAllBytes(documentFileService.storedPath(saved)))
                .containsExactly(docxBytes);
        assertThat(documentService.getDocument("D914").getCitizen()
                .getCitizenId()).isEqualTo("CIT-DOCX-TEST");
        assertThat(citizenSearchService.documentsFor(
                        new Citizen("CIT-DOCX-TEST", "Word Upload Person"), ""))
                .extracting(Document::getId)
                .contains("D914");
        assertThat(documentDepartmentService.departmentName("D914"))
                .isEqualTo("Community Services");
    }

    @Test
    void textDocumentCanStoreContentLongerThan255Characters() {
        String content = "A".repeat(600);
        Document created = documentCreationService.create(
                new Document(
                        "D909", "Long content test", "Test", 2, content),
                "Health",
                "",
                "admin");

        assertThat(documentService.getDocument(created.getId()).getText())
                .isEqualTo(content);
        assertThat(documentVersionService.versions(created.getId()))
                .singleElement()
                .extracting(DocumentVersion::text)
                .isEqualTo(content);
    }

    @Test
    void pdfUploadStoresOriginalBytesWithoutCreatingAssignments()
            throws IOException {
        officerAccountService.createOfficer(
                "REV-UPLOAD-TEST", "Revenue Upload Officer",
                "revenue-upload-test",
                "RevenueUploadPass2026!", "admin");
        byte[] pdfBytes = "%PDF-1.7\nSample original PDF bytes"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "..\\secret-income.pdf", "image/png", pdfBytes);

        DocumentFile saved = documentFileService.upload(
                "D901", "Income Certificate", "Certificate", 4,
                "Income certificate record", "Education", "",
                upload, "admin");

        assertThat(documentDepartmentService.departmentName("D901"))
                .isEqualTo("Education");
        assertThat(saved.fileType()).isEqualTo("application/pdf");
        assertThat(saved.originalFilename()).isEqualTo("secret-income.pdf");
        assertThat(Files.readAllBytes(documentFileService.storedPath(saved)))
                .containsExactly(pdfBytes);
        assertThat(documentIds(
                officerDocumentService.allDocumentsForOfficer("revenue-upload-test")))
                .contains("D901");
    }

    @Test
    void pngUploadIsStoredWithoutSelectingAnOfficer() throws IOException {
        officerAccountService.createOfficer(
                "HLT-UPLOAD-TEST", "Health Upload Officer",
                "health-upload-test",
                "HealthUploadPass2026!", "admin");
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0};
        MockMultipartFile upload = new MockMultipartFile(
                "file", "scan.png", "application/octet-stream", pngBytes);

        DocumentFile saved = documentFileService.upload(
                "D902", "Medical Record", "Health", 3,
                "Scanned health document", "Health", "",
                upload, "admin");

        assertThat(documentDepartmentService.departmentName("D902"))
                .isEqualTo("Health");
        assertThat(saved.fileType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(documentFileService.storedPath(saved)))
                .containsExactly(pngBytes);
        assertThat(documentIds(
                officerDocumentService.allDocumentsForOfficer("health-upload-test")))
                .contains("D902");
    }

    @Test
    void jpegAndWebpUploadsAreAcceptedFromTheirFileSignatures() {
        byte[] jpegBytes = new byte[]{
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
        byte[] webpBytes = new byte[]{
                'R', 'I', 'F', 'F', 4, 0, 0, 0,
                'W', 'E', 'B', 'P'};

        DocumentFile jpeg = documentFileService.upload(
                "D906", "JPEG scan", "Health", 2, "",
                "Health", "", new MockMultipartFile(
                        "file", "scan.jpeg", "application/octet-stream",
                        jpegBytes), "admin");
        DocumentFile webp = documentFileService.upload(
                "D907", "WEBP scan", "Health", 2, "",
                "Health", "", new MockMultipartFile(
                        "file", "scan.webp", "application/octet-stream",
                        webpBytes), "admin");

        assertThat(jpeg.fileType()).isEqualTo("image/jpeg");
        assertThat(webp.fileType()).isEqualTo("image/webp");
    }

    @Test
    void uploadRejectsUnsupportedExtensionsSignatureMismatchAndOversizeFiles() {
        MockMultipartFile executable = new MockMultipartFile(
                "file", "payload.exe", "application/pdf",
                "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        MockMultipartFile mismatched = new MockMultipartFile(
                "file", "payload.jpg", "image/jpeg",
                "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.pdf", "application/pdf",
                new byte[(int) DocumentFileService.MAX_FILE_SIZE + 1]);
        MockMultipartFile malformedWord = new MockMultipartFile(
                "file", "not-a-word-file.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0});
        MockMultipartFile legacyWord = new MockMultipartFile(
                "file", "legacy.doc", "application/msword",
                new byte[]{
                        (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                        (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});

        assertThatThrownBy(() -> documentFileService.upload(
                "D903", "Bad file", "Test", 1, "", "Education", "",
                executable, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> documentFileService.upload(
                "D904", "Mismatched file", "Test", 1, "", "Education", "",
                mismatched, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> documentFileService.upload(
                "D905", "Oversized file", "Test", 1, "", "Education", "",
                oversized, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB");
        assertThatThrownBy(() -> documentFileService.upload(
                "D915", "Malformed Word file", "Test", 1, "",
                "Education", "", malformedWord, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
        assertThatThrownBy(() -> documentFileService.upload(
                "D916", "Legacy Word file", "Test", 1, "",
                "Education", "", legacyWord, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
    }

    private byte[] wordDocumentBytes() throws IOException {
        java.io.ByteArrayOutputStream bytes =
                new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types><Override PartName=\"/word/document.xml\" "
                    + "ContentType=\"application/vnd.openxmlformats-officedocument."
                    + "wordprocessingml.document.main+xml\"/></Types>")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private List<String> documentIds(List<Document> documents) {
        return documents.stream().map(Document::getId).toList();
    }
}
