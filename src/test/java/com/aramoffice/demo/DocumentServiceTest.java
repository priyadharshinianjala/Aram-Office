package com.aramoffice.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentServiceTest {
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        List<Document> documents = List.of(
                new Document("D101", "Property Tax Circular", "Tax", 5,
                        "Updated property tax instructions"),
                new Document("D103", "Property Tax Notice", "Tax", 3,
                        "Property tax payment notice"),
                new Document("D104", "Scholarship Application", "Scholarships", 4,
                        "Application for a scholarship"));
        when(documentRepository.findAll()).thenReturn(documents);
        when(documentRepository.findById("D101"))
                .thenReturn(Optional.of(documents.get(0)));
        when(documentRepository.findById("D103"))
                .thenReturn(Optional.of(documents.get(1)));
        when(documentRepository.findById("D104"))
                .thenReturn(Optional.of(documents.get(2)));
        documentService = new DocumentService(
                documentRepository, mock(DocumentVersionService.class));
    }

    @Test
    void findsDocumentByFullId() {
        var results = documentService.search("D101");

        assertEquals(1, results.size());
        assertEquals("D101", results.get(0).getDocument().getId());
    }

    @Test
    void findsDocumentByNumericIdWithoutPrefix() {
        var results = documentService.search("101");

        assertEquals(1, results.size());
        assertEquals("D101", results.get(0).getDocument().getId());
    }

    @Test
    void findsDocumentsByTitleAndRanksTextMatches() {
        var results = documentService.search("property");

        assertTrue(results.size() > 1);
        assertTrue(results.stream().anyMatch(result ->
                result.getDocument().getId().equals("D101")));
    }

    @Test
    void filtersBrowseResultsByCategory() {
        var results = documentService.search("", "Scholarships");

        assertTrue(!results.isEmpty());
        assertTrue(results.stream().allMatch(result ->
                result.getDocument().getCategory().equals("Scholarships")));
    }
}
