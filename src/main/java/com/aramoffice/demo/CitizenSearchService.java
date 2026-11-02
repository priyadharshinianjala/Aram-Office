package com.aramoffice.demo;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CitizenSearchService {

    private final CitizenRepository citizenRepository;
    private final DocumentRepository documentRepository;
    private final CitizenAccessService citizenAccessService;

    public CitizenSearchService(
            CitizenRepository citizenRepository,
            DocumentRepository documentRepository,
            CitizenAccessService citizenAccessService) {
        this.citizenRepository = citizenRepository;
        this.documentRepository = documentRepository;
        this.citizenAccessService = citizenAccessService;
    }

    @Transactional(readOnly = true)
    public List<Citizen> citizensForDocumentLinking() {
        return citizenRepository
                .findAllByOrderByFullNameAscCitizenIdAsc();
    }

    @Transactional(readOnly = true)
    public List<Citizen> search(
            String searchTerm,
            Authentication authentication) {
        String term = searchTerm == null ? "" : searchTerm.trim();
        if (term.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, Citizen> matches = new LinkedHashMap<>();
        citizenRepository.findByCitizenIdIgnoreCase(term)
                .ifPresent(citizen -> matches.put(citizen.getCitizenId(), citizen));
        for (Citizen citizen : citizenRepository
                .findByFullNameContainingIgnoreCaseOrderByFullNameAscCitizenIdAsc(term)) {
            matches.putIfAbsent(citizen.getCitizenId(), citizen);
        }

        boolean administrator = citizenAccessService.isAdministrator(authentication);
        Set<String> accessibleCitizenIds = administrator
                ? Set.of()
                : citizenAccessService.accessibleCitizenIdSet(authentication);
        List<Citizen> authorized = new ArrayList<>();
        for (Citizen citizen : matches.values()) {
            if (administrator || accessibleCitizenIds.contains(citizen.getCitizenId())) {
                authorized.add(citizen);
            }
        }
        return List.copyOf(authorized);
    }

    @Transactional(readOnly = true)
    public Citizen findAuthorized(
            String citizenId,
            Authentication authentication) {
        Citizen citizen = citizenRepository.findByCitizenIdIgnoreCase(citizenId)
                .orElse(null);
        if (citizen == null || !citizenAccessService.canAccessCitizen(
                authentication, citizen.getCitizenId())) {
            return null;
        }
        return citizen;
    }

    @Transactional(readOnly = true)
    public List<Document> documentsFor(Citizen citizen, String category) {
        String selectedCategory = category == null
                ? "" : category.trim().toLowerCase(Locale.ROOT);
        return documentRepository.findByCitizen_CitizenIdIgnoreCase(
                        citizen.getCitizenId()).stream()
                .filter(document -> selectedCategory.isEmpty()
                        || (document.getCategory() != null
                        && document.getCategory().toLowerCase(Locale.ROOT)
                        .equals(selectedCategory)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> categoriesFor(Citizen citizen) {
        return documentRepository.findByCitizen_CitizenIdIgnoreCase(
                        citizen.getCitizenId()).stream()
                .map(Document::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
