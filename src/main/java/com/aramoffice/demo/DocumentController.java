package com.aramoffice.demo;

import org.springframework.http.HttpStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

@Controller
public class DocumentController {

    private final DocumentService documentService;
    private final CitizenAccessService citizenAccessService;
    private final OfficerDocumentService officerDocumentService;
    private final AuditLogService auditLogService;
    private final DocumentVersionService documentVersionService;
    private final DocumentFileService documentFileService;
    private final OfficerAccountService officerAccountService;
    private final DocumentDepartmentService documentDepartmentService;
    private final DocumentCreationService documentCreationService;
    private final CitizenSearchService citizenSearchService;

    public DocumentController(
            DocumentService documentService,
            CitizenAccessService citizenAccessService,
            OfficerDocumentService officerDocumentService,
            AuditLogService auditLogService,
            DocumentVersionService documentVersionService,
            DocumentFileService documentFileService,
            OfficerAccountService officerAccountService,
            DocumentDepartmentService documentDepartmentService,
            DocumentCreationService documentCreationService,
            CitizenSearchService citizenSearchService) {
        this.documentService = documentService;
        this.citizenAccessService = citizenAccessService;
        this.officerDocumentService = officerDocumentService;
        this.auditLogService = auditLogService;
        this.documentVersionService = documentVersionService;
        this.documentFileService = documentFileService;
        this.officerAccountService = officerAccountService;
        this.documentDepartmentService = documentDepartmentService;
        this.documentCreationService = documentCreationService;
        this.citizenSearchService = citizenSearchService;
    }

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        if (isOfficer(authentication)) {
            return officerDashboard("", "", model, authentication);
        }
        addBrowseModel(model, "", "", authentication);
        return "index";
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String category,
            Model model,
            Authentication authentication) {
        if (isOfficer(authentication)) {
            return officerDashboard(query, category, model, authentication);
        }
        addBrowseModel(model, query, category, authentication);
        return "index";
    }

    @GetMapping("/documents/{id}")
    public String documentDetail(
            @PathVariable String id,
            Model model,
            Authentication authentication) {
        Document document = documentService.getDocument(id);
        requireDocumentAccess(id, document, authentication);

        auditLogService.record(
                authentication.getName(), "DOCUMENT_VIEWED", id);
        model.addAttribute("document", document);
        model.addAttribute(
                "documentFiles", documentFileService.filesForDocument(id));
        model.addAttribute(
                "documentDepartment",
                documentDepartmentService.departmentName(id));
        model.addAttribute("linkedCitizen", document.getCitizen());
        model.addAttribute(
                "versions", documentVersionService.versions(document.getId()));
        model.addAttribute("username", authentication.getName());
        model.addAttribute(
                "isAdministrator",
                citizenAccessService.isAdministrator(authentication));
        model.addAttribute("isOfficer", isOfficer(authentication));
        return "document";
    }

    @GetMapping("/admin/documents/upload")
    public String legacyUploadPage() {
        return "redirect:/documents/add";
    }

    @GetMapping("/documents/{id}/files/{fileId}/preview")
    public ResponseEntity<Resource> previewFile(
            @PathVariable String id,
            @PathVariable long fileId,
            Authentication authentication) {
        return serveFile(id, fileId, authentication, false);
    }

    @GetMapping("/documents/{id}/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String id,
            @PathVariable long fileId,
            Authentication authentication) {
        return serveFile(id, fileId, authentication, true);
    }

    private ResponseEntity<Resource> serveFile(
            String documentId,
            long fileId,
            Authentication authentication,
            boolean download) {
        Document document = documentService.getDocument(documentId);
        requireDocumentAccess(documentId, document, authentication);
        DocumentFile file = documentFileService.fileForDocument(documentId, fileId);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!download && !file.isPdf() && !file.isImage()) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        Path storedPath = documentFileService.storedPath(file);
        if (!Files.isRegularFile(storedPath, LinkOption.NOFOLLOW_LINKS)) {
            auditLogService.record(
                    authentication.getName(), "DOCUMENT_FILE_MISSING",
                    documentId + " (file " + fileId + ")");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        auditLogService.record(
                authentication.getName(),
                download ? "DOCUMENT_FILE_DOWNLOADED"
                        : "DOCUMENT_FILE_PREVIEWED",
                documentId + " (file " + fileId + ")");
        Resource resource = new FileSystemResource(storedPath);
        String disposition = download ? "attachment" : "inline";
        ContentDisposition contentDisposition =
                ContentDisposition.builder(disposition)
                        .filename(file.originalFilename(), StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.fileType()))
                .contentLength(file.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }

    private void requireDocumentAccess(
            String id,
            Document document,
            Authentication authentication) {
        boolean authorized = document != null
                && (citizenAccessService.isAdministrator(authentication)
                || (isOfficer(authentication)
                ? officerDocumentService.isEnabledOfficer(authentication.getName())
                : citizenAccessService.canAccessDocument(authentication, document)));
        if (!authorized) {
            auditLogService.record(
                    authentication.getName(), "DOCUMENT_ACCESS_DENIED", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/documents/{id}/versions")
    public String createVersion(
            @PathVariable String id,
            @RequestParam String textContent,
            Authentication authentication,
            RedirectAttributes redirect) {
        try {
            int versionNumber = documentVersionService.createRevision(
                    id, textContent, authentication.getName());
            redirect.addFlashAttribute(
                    "successMessage",
                    "Saved document version " + versionNumber + ".");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute(
                    "errorMessage", exception.getMessage());
        }
        return "redirect:/documents/" + id;
    }

    private void addBrowseModel(
            Model model,
            String query,
            String category,
            Authentication authentication) {
        List<Document> visibleDocuments = visibleDocuments(authentication);
        List<String> categories = documentService.getCategories(visibleDocuments);
        List<SearchResult> results = documentService.search(
                query, category, visibleDocuments);

        model.addAttribute("documents", visibleDocuments);
        model.addAttribute("results", results);
        model.addAttribute("query", query);
        model.addAttribute("activeCategory", category);
        model.addAttribute("categories", categories);
        model.addAttribute("documentCount", visibleDocuments.size());
        model.addAttribute("categoryCount", categories.size());
        model.addAttribute(
                "suggestions",
                documentService.suggestions(query, visibleDocuments));
        model.addAttribute("username", authentication.getName());
        model.addAttribute(
                "isAdministrator",
                citizenAccessService.isAdministrator(authentication));
        model.addAttribute("isOfficer", false);
    }

    private List<Document> visibleDocuments(Authentication authentication) {
        if (citizenAccessService.isAdministrator(authentication)) {
            return documentService.getAllDocuments();
        }
        Set<String> accessibleCitizenIds =
                citizenAccessService.accessibleCitizenIdSet(authentication);
        return documentService.getAllDocuments().stream()
                .filter(document -> document.getCitizen() != null
                        && accessibleCitizenIds.contains(
                        document.getCitizen().getCitizenId()))
                .toList();
    }

    private String officerDashboard(
            String query,
            String category,
            Model model,
            Authentication authentication) {
        List<Document> availableDocuments =
                officerDocumentService.allDocumentsForOfficer(
                        authentication.getName());
        List<String> categories =
                documentService.getCategories(availableDocuments);
        model.addAttribute(
                "officer",
                officerDocumentService.officerProfile(
                        authentication.getName()));
        model.addAttribute("documents", availableDocuments);
        model.addAttribute(
                "results",
                documentService.search(query, category, availableDocuments));
        model.addAttribute("query", query);
        model.addAttribute("categories", categories);
        model.addAttribute("activeCategory", category);
        model.addAttribute("documentCount", availableDocuments.size());
        model.addAttribute("username", authentication.getName());
        model.addAttribute(
                "suggestions",
                documentService.suggestions(query, availableDocuments));
        return "officer-dashboard";
    }

    private boolean isOfficer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_OFFICER"));
    }

    @GetMapping("/documents/add")
    public String showAddForm(Model model, Authentication authentication) {
        populateDocumentFormModel(model, authentication);
        return "add-document";
    }

    @PostMapping("/documents/add")
    public String addDocument(
            @RequestParam String id,
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam int importance,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String departmentName,
            @RequestParam(defaultValue = "") String citizenId,
            @RequestParam(value = "file", required = false)
                    org.springframework.web.multipart.MultipartFile file,
            Model model,
            Authentication authentication) {
        try {
            if (file != null && !file.isEmpty()) {
                documentFileService.upload(
                        id, title, category, importance, text,
                        departmentName, citizenId, file,
                        authentication.getName());
                return "redirect:/documents/" + id;
            } else {
                if (text.isBlank()) {
                    throw new IllegalArgumentException(
                            "Enter document content or attach an original file.");
                }
                Document document = new Document(
                        id, title, category, importance, text);
                documentCreationService.create(
                        document, departmentName, citizenId,
                        authentication.getName());
                auditLogService.record(
                        authentication.getName(), "DOCUMENT_CREATED", id);
                return "redirect:/documents/" + id;
            }
        } catch (IllegalArgumentException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute("id", id);
            model.addAttribute("title", title);
            model.addAttribute("category", category);
            model.addAttribute("importance", importance);
            model.addAttribute("text", text);
            model.addAttribute("departmentName", departmentName);
            model.addAttribute("citizenId", citizenId);
            populateDocumentFormModel(model, authentication);
            return "add-document";
        } catch (DocumentFileStorageException exception) {
            model.addAttribute(
                    "errorMessage",
                    "The document could not be stored. Check the configured private storage directory.");
            model.addAttribute("id", id);
            model.addAttribute("title", title);
            model.addAttribute("category", category);
            model.addAttribute("importance", importance);
            model.addAttribute("text", text);
            model.addAttribute("departmentName", departmentName);
            model.addAttribute("citizenId", citizenId);
            populateDocumentFormModel(model, authentication);
            return "add-document";
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(Model model, Authentication authentication) {
        model.addAttribute(
                "errorMessage", "The selected file exceeds the 10 MB limit.");
        populateDocumentFormModel(model, authentication);
        return "add-document";
    }

    private void populateDocumentFormModel(
            Model model,
            Authentication authentication) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("departments", officerAccountService.departments());
        model.addAttribute(
                "citizens", citizenSearchService.citizensForDocumentLinking());
    }
}
