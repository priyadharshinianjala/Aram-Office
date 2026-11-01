package com.aramoffice.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCreationService {

    private final DocumentService documentService;
    private final DocumentDepartmentService documentDepartmentService;

    public DocumentCreationService(
            DocumentService documentService,
            DocumentDepartmentService documentDepartmentService) {
        this.documentService = documentService;
        this.documentDepartmentService = documentDepartmentService;
    }

    @Transactional
    public Document create(
            Document document,
            String departmentId,
            String actor) {
        Document created = documentService.addDocument(document);
        documentDepartmentService.setDepartment(
                created.getId(), departmentId, actor);
        return created;
    }
}
