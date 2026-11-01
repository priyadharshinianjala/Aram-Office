
package com.aramoffice.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionService documentVersionService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentVersionService documentVersionService) {
        this.documentRepository = documentRepository;
        this.documentVersionService = documentVersionService;
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public List<String> getCategories() {
        return getCategories(getAllDocuments());
    }

    public List<String> getCategories(List<Document> documents) {
        return documents.stream()
                .map(Document::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public Document getDocument(String id) {
        return documentRepository.findById(id).orElse(null);
    }

    public List<SearchResult> search(String query) {
        return search(query, "");
    }

    public List<SearchResult> search(String query, String category) {
        return search(query, category, getAllDocuments());
    }

    public List<SearchResult> search(
            String query,
            String category,
            List<Document> availableDocuments) {
        String keyword = query == null
                ? "" : query.trim().toLowerCase();

        String normalizedId = keyword.startsWith("d")
                ? keyword.substring(1) : keyword;

        String selectedCategory = category == null
                ? "" : category.trim();

        Map<String, Integer> matchMap = new HashMap<>();
        Map<String, Document> documentsById = new HashMap<>();

        for (Document document : availableDocuments) {
            documentsById.put(document.getId(), document);

            if (!selectedCategory.isBlank()
                    && (document.getCategory() == null
                    || !document.getCategory()
                    .equalsIgnoreCase(selectedCategory))) {
                continue;
            }

            if (keyword.isBlank()) {
                matchMap.put(document.getId(), 0);
                continue;
            }

            String text = document.getText() == null
                    ? "" : document.getText().toLowerCase();

            String title = document.getTitle() == null
                    ? "" : document.getTitle().toLowerCase();

            String categoryText = document.getCategory() == null
                    ? "" : document.getCategory().toLowerCase();

            String id = document.getId().toLowerCase();

            int count = countMatches(text, keyword)
                    + countMatches(title, keyword)
                    + countMatches(categoryText, keyword);

            if (id.equals(keyword)
                    || (id.length() > 1
                    && id.substring(1).equals(normalizedId))) {
                count += 1000;
            } else if (id.contains(keyword)) {
                count += 100;
            }

            if (count > 0) {
                matchMap.put(document.getId(), count);
            }
        }

        return matchMap.entrySet().stream()
                .map(entry -> new SearchResult(
                        documentsById.get(entry.getKey()),
                        entry.getValue()))
                .filter(result -> result.getDocument() != null)
                .sorted(Comparator
                        .comparingInt(SearchResult::getMatchCount)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingInt(
                                        (SearchResult result) ->
                                                result.getDocument()
                                                        .getImportance())
                                        .reversed())
                        .thenComparing(
                                result -> result.getDocument().getId()))
                .toList();
    }

    public List<String> suggestions(String prefix) {
        return suggestions(prefix, getAllDocuments());
    }

    public List<String> suggestions(
            String prefix,
            List<Document> availableDocuments) {
        String value = prefix == null
                ? "" : prefix.trim().toLowerCase();

        if (value.isBlank()) {
            return List.of();
        }

        Set<String> matches = new HashSet<>();

        for (Document document : availableDocuments) {
            String text = document.getText() == null
                    ? "" : document.getText().toLowerCase();

            for (String token : text.split("\\s+")) {
                token = token.replaceAll("[^a-z0-9]", "");

                if (!token.isBlank() && token.startsWith(value)) {
                    matches.add(token);
                }
            }
        }

        return matches.stream()
                .sorted()
                .limit(8)
                .collect(Collectors.toList());
    }

    private int countMatches(String text, String keyword) {
        int count = 0;
        int index = 0;

        while (index <= text.length() - keyword.length()) {
            if (text.regionMatches(
                    index, keyword, 0, keyword.length())) {
                count++;
                index += keyword.length();
            } else {
                index++;
            }
        }

        return count;
    }

    @Transactional
    public Document addDocument(Document document) {
        if (documentRepository.existsById(document.getId())) {
            throw new IllegalArgumentException(
                    "Document ID already exists: "
                            + document.getId());
        }

        Document saved = documentRepository.saveAndFlush(document);
        documentVersionService.createInitialVersion(saved);
        return saved;
    }

    public void deleteDocument(String id) {
        documentRepository.deleteById(id);
    }
}
