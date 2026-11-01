package com.aramoffice.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        LoginController.class, DocumentController.class,
        CitizenController.class, OfficerAdminController.class})
@Import(SecurityConfig.class)
class SecurityFlowWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private CitizenAccessService citizenAccessService;

    @MockBean
    private OfficerDocumentService officerDocumentService;

    @MockBean
    private CitizenSearchService citizenSearchService;

    @MockBean
    private OfficerAccountService officerAccountService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private DocumentVersionService documentVersionService;

    @MockBean
    private DocumentFileService documentFileService;

    @MockBean
    private DocumentDepartmentService documentDepartmentService;

    @MockBean
    private DocumentCreationService documentCreationService;

    @TempDir
    private Path temporaryStorage;

    @Test
    void loginPageDoesNotOfferPublicRegistration() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Sign in to your account")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"password\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Sign up"))));

        mockMvc.perform(get("/signup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void unauthenticatedDashboardRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void officerSeesRepositoryEmptyStateWhenThereAreNoDocuments() throws Exception {
        OfficerAccount officer = new OfficerAccount(
                "EDU-104", "Priya Sharma", "officer104",
                "EDU", "Education", true);
        when(officerDocumentService.officerProfile("officer104"))
                .thenReturn(officer);
        when(officerDocumentService.allDocumentsForOfficer("officer104"))
                .thenReturn(List.of());
        when(documentService.getCategories(List.of())).thenReturn(List.of());
        when(documentService.search("", "", List.of())).thenReturn(List.of());
        when(documentService.suggestions("", List.of())).thenReturn(List.of());

        mockMvc.perform(get("/").with(user("officer104").roles("OFFICER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "The document repository is empty.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Priya Sharma")));

        verify(officerDocumentService).allDocumentsForOfficer("officer104");
    }

    @Test
    void officerSearchesTheSharedDocumentRepository() throws Exception {
        Document shared = new Document(
                "D101", "Revenue Circular", "Circular", 4,
                "Revenue process");
        OfficerAccount officer = new OfficerAccount(
                "REV-101", "Ravi Officer", "revenue-officer",
                "REV", "Revenue", true);
        when(officerDocumentService.officerProfile("revenue-officer"))
                .thenReturn(officer);
        when(officerDocumentService.allDocumentsForOfficer("revenue-officer"))
                .thenReturn(List.of(shared));
        when(documentService.getCategories(List.of(shared)))
                .thenReturn(List.of("Circular"));
        when(documentService.search("D101", "", List.of(shared)))
                .thenReturn(List.of(new SearchResult(shared, 1000)));
        when(documentService.suggestions("D101", List.of(shared)))
                .thenReturn(List.of());

        mockMvc.perform(get("/search")
                        .param("query", "D101")
                        .with(user("revenue-officer").roles("OFFICER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Revenue Circular")));

        verify(officerDocumentService).allDocumentsForOfficer(
                "revenue-officer");
    }

    @Test
    void disabledOfficerCannotOpenDocumentsInAnExistingSession() throws Exception {
        Document privateDocument = new Document(
                "D202", "Health Record", "Health", 4, "Private record");
        when(documentService.getDocument("D202")).thenReturn(privateDocument);
        when(officerDocumentService.isEnabledOfficer("revenue-officer"))
                .thenReturn(false);

        mockMvc.perform(get("/documents/D202")
                        .with(user("revenue-officer").roles("OFFICER")))
                .andExpect(status().isNotFound());

        verify(auditLogService, never()).record(
                any(), eq("DOCUMENT_VIEWED"), any());
        verify(auditLogService).record(
                "revenue-officer", "DOCUMENT_ACCESS_DENIED", "D202");
    }

    @Test
    void anyEnabledOfficerCanOpenARepositoryDocumentAndItIsAudited()
            throws Exception {
        Document shared = new Document(
                "D101", "Revenue Circular", "Circular", 4, "Shared text");
        when(documentService.getDocument("D101")).thenReturn(shared);
        when(officerDocumentService.isEnabledOfficer("revenue-officer"))
                .thenReturn(true);
        when(documentVersionService.versions("D101")).thenReturn(List.of(
                new DocumentVersion(
                        2, "Shared text", true,
                        LocalDateTime.of(2026, 10, 30, 12, 0)),
                new DocumentVersion(
                        1, "Earlier shared text", false,
                        LocalDateTime.of(2026, 10, 29, 12, 0))));

        mockMvc.perform(get("/documents/D101")
                        .with(user("revenue-officer").roles("OFFICER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Revenue Circular")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Earlier shared text")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Version 2")));

        verify(auditLogService).record(
                "revenue-officer", "DOCUMENT_VIEWED", "D101");
    }

    @Test
    void onlyAdministratorsCanUseOfficerAdministrationAndDocumentEntry()
            throws Exception {
        mockMvc.perform(get("/admin")
                        .with(user("officer").roles("OFFICER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/officers")
                        .with(csrf())
                        .with(user("officer").roles("OFFICER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/documents/add")
                        .with(user("officer").roles("OFFICER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/people/search")
                        .with(user("officer").roles("OFFICER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(officerAccountService);
    }

    @Test
    void administratorCanCreateOfficerWithoutDepartmentDetails()
            throws Exception {
        mockMvc.perform(post("/admin/officers")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("officerId", "OFF-104")
                        .param("fullName", "Priya Sharma")
                        .param("username", "officer104")
                        .param("password", "OfficerPass2026!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        verify(officerAccountService).createOfficer(
                "OFF-104", "Priya Sharma", "officer104",
                "OfficerPass2026!", "admin");
    }

    @Test
    void administratorSeesFileUploadInTheAddDocumentForm() throws Exception {
        when(officerAccountService.departments())
                .thenReturn(List.of(new Department("REV", "Revenue")));

        mockMvc.perform(get("/documents/add")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Original PDF or image (optional)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Document department")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"departmentId\" required")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "10 MB")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("```html"))));
    }

    @Test
    void onlyAdministratorsCanCreateDocumentVersions() throws Exception {
        mockMvc.perform(post("/documents/D101/versions")
                        .with(user("officer").roles("OFFICER"))
                        .with(csrf())
                        .param("textContent", "Updated content"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentVersionService);
    }

    @Test
    void onlyAdministratorsCanOpenTheAddDocumentPage() throws Exception {
        mockMvc.perform(get("/documents/add")
                        .with(user("officer").roles("OFFICER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/documents/upload")
                        .with(user("officer").roles("OFFICER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentFileService);
    }

    @Test
    void administratorCanUploadAFileFromTheAddDocumentForm() throws Exception {
        DocumentFile saved = new DocumentFile(
                1, "D201", "7df8b935-9c2a-42d2-8d46-56350b6eb290",
                "income.pdf", "application/pdf", 8, "admin",
                LocalDateTime.of(2026, 10, 30, 12, 0), "REV", "Revenue");
        when(documentFileService.upload(
                eq("D201"),
                eq("Income Certificate"),
                eq("Certificate"),
                eq(3),
                eq("Income verification"),
                eq("REV"),
                any(org.springframework.web.multipart.MultipartFile.class),
                eq("admin")))
                .thenReturn(saved);

        mockMvc.perform(multipart("/documents/add")
                        .file(new MockMultipartFile(
                                "file", "income.pdf", "application/pdf",
                                "%PDF-1.7".getBytes(
                                        java.nio.charset.StandardCharsets.US_ASCII)))
                        .param("id", "D201")
                        .param("title", "Income Certificate")
                        .param("category", "Certificate")
                        .param("importance", "3")
                        .param("text", "Income verification")
                        .param("departmentId", "REV")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/documents/D201"));
    }

    @Test
    void administratorCanSaveTextDocumentWithASelectedDepartment()
            throws Exception {
        when(documentCreationService.create(
                any(Document.class), eq("REV"), eq("admin")))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/documents/add")
                        .param("id", "D202")
                        .param("title", "Revenue Circular")
                        .param("category", "circular")
                        .param("importance", "3")
                        .param("text", "Document content")
                        .param("departmentId", "REV")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/documents/D202"));

        verify(documentCreationService).create(
                any(Document.class), eq("REV"), eq("admin"));
    }

    @Test
    void disabledOfficerCannotPreviewFileByChangingItsUrl() throws Exception {
        when(documentService.getDocument("D202")).thenReturn(new Document(
                "D202", "Health Record", "Health", 4, "Private record"));
        when(officerDocumentService.isEnabledOfficer("revenue-officer"))
                .thenReturn(false);

        mockMvc.perform(get(
                        "/documents/D202/files/9/preview")
                        .with(user("revenue-officer").roles("OFFICER")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(documentFileService);
        verify(auditLogService).record(
                "revenue-officer", "DOCUMENT_ACCESS_DENIED", "D202");
    }

    @Test
    void assignedOfficerCanDownloadTheOriginalFileWithSafeHeaders()
            throws Exception {
        Document document = new Document(
                "D101", "Revenue Circular", "Circular", 4, "Description");
        byte[] pdfBytes = "%PDF-1.7\nOriginal bytes"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Path storedFile = temporaryStorage.resolve("stored-file");
        Files.write(storedFile, pdfBytes);
        DocumentFile file = new DocumentFile(
                7, "D101", "7df8b935-9c2a-42d2-8d46-56350b6eb290",
                "original circular.pdf", "application/pdf", pdfBytes.length,
                "admin", LocalDateTime.of(2026, 10, 30, 12, 0),
                "REV", "Revenue");
        when(documentService.getDocument("D101")).thenReturn(document);
        when(officerDocumentService.isEnabledOfficer("revenue-officer"))
                .thenReturn(true);
        when(documentFileService.fileForDocument("D101", 7)).thenReturn(file);
        when(documentFileService.storedPath(file)).thenReturn(storedFile);

        mockMvc.perform(get("/documents/D101/files/7/download")
                        .with(user("revenue-officer").roles("OFFICER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(pdfBytes))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "private, no-store"));

        verify(auditLogService).record(
                "revenue-officer", "DOCUMENT_FILE_DOWNLOADED",
                "D101 (file 7)");
    }

    @Test
    void administratorCanOpenOfficerDashboard() throws Exception {
        when(officerAccountService.officers()).thenReturn(List.of());
        when(officerAccountService.departments()).thenReturn(List.of());
        when(auditLogService.recent()).thenReturn(List.of());

        mockMvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Administration")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Create an officer account")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Create a department"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Department ID"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"departmentId\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Registered officers"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Document assignments"))));
    }

    @Test
    void invalidCredentialsAreRejectedAndDisabledAccountsCannotSignIn()
            throws Exception {
        when(userDetailsService.loadUserByUsername("disabled-officer"))
                .thenReturn(User.withUsername("disabled-officer")
                        .password(passwordEncoder.encode("valid-password-123"))
                        .roles("OFFICER")
                        .disabled(true)
                        .build());

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "disabled-officer")
                        .param("password", "valid-password-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        when(userDetailsService.loadUserByUsername("office-admin"))
                .thenReturn(User.withUsername("office-admin")
                        .password(passwordEncoder.encode("correct-password-123"))
                        .roles("ADMIN")
                        .build());

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "office-admin")
                        .param("password", "correct-password-123"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
        mockMvc.perform(get("/").session(session))
                .andExpect(status().is3xxRedirection());
    }
}
