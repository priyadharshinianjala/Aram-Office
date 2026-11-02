package com.aramoffice.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCreationService {

    private final DocumentService documentService;
    private final DocumentDepartmentService documentDepartmentService;
    private final CitizenRepository citizenRepository;
    private final AuditLogService auditLogService;

    public DocumentCreationService(
            DocumentService documentService,
            DocumentDepartmentService documentDepartmentService,
            CitizenRepository citizenRepository,
            AuditLogService auditLogService) {
        this.documentService = documentService;
        this.documentDepartmentService = documentDepartmentService;
        this.citizenRepository = citizenRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Document create(
            Document document,
            String departmentName,
            String citizenId,
            String actor) {
        String normalizedCitizenId = citizenId == null
                ? "" : citizenId.trim();
        if (!normalizedCitizenId.isEmpty()) {
            if (normalizedCitizenId.length() > 64
                    || normalizedCitizenId.chars()
                    .anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(
                        "Select a valid person profile.");
            }
            Citizen citizen = citizenRepository.findById(normalizedCitizenId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The selected person profile was not found."));
            document.setCitizen(citizen);
        }

        Document created = documentService.addDocument(document);
        documentDepartmentService.setDepartment(
                created.getId(), departmentName, actor);
        if (!normalizedCitizenId.isEmpty()) {
            auditLogService.record(
                    actor,
                    "DOCUMENT_LINKED_TO_CITIZEN",
                    created.getId() + " -> " + normalizedCitizenId);
        }
        return created;
    }
}
