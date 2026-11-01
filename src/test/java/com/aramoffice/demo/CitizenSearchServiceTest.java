package com.aramoffice.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitizenSearchServiceTest {

    private CitizenRepository citizenRepository;
    private DocumentRepository documentRepository;
    private CitizenAccessService citizenAccessService;
    private CitizenSearchService citizenSearchService;

    @BeforeEach
    void setUp() {
        citizenRepository = mock(CitizenRepository.class);
        documentRepository = mock(DocumentRepository.class);
        citizenAccessService = mock(CitizenAccessService.class);
        citizenSearchService = new CitizenSearchService(
                citizenRepository, documentRepository, citizenAccessService);
    }

    @Test
    void returnsDuplicateNamesAsSeparateAuthorizedProfiles() {
        Citizen first = new Citizen("C001", "Arun Kumar");
        Citizen second = new Citizen("C002", "Arun Kumar");
        when(citizenRepository.findByCitizenIdIgnoreCase("Arun Kumar"))
                .thenReturn(Optional.empty());
        when(citizenRepository
                .findByFullNameContainingIgnoreCaseOrderByFullNameAscCitizenIdAsc(
                        "Arun Kumar"))
                .thenReturn(List.of(first, second));
        when(citizenAccessService.isAdministrator(
                org.mockito.ArgumentMatchers.any(Authentication.class)))
                .thenReturn(true);

        List<Citizen> results = citizenSearchService.search(
                "Arun Kumar", administrator());

        assertEquals(List.of("C001", "C002"),
                results.stream().map(Citizen::getCitizenId).toList());
    }

    @Test
    void findsCitizenByExactIdAndHidesUnassignedCitizen() {
        Citizen citizen = new Citizen("C002", "Priya Sharma");
        when(citizenRepository.findByCitizenIdIgnoreCase("C002"))
                .thenReturn(Optional.of(citizen));
        when(citizenRepository
                .findByFullNameContainingIgnoreCaseOrderByFullNameAscCitizenIdAsc(
                        "C002"))
                .thenReturn(List.of());
        when(citizenAccessService.isAdministrator(
                org.mockito.ArgumentMatchers.any(Authentication.class)))
                .thenReturn(false);
        when(citizenAccessService.accessibleCitizenIdSet(
                org.mockito.ArgumentMatchers.any(Authentication.class)))
                .thenReturn(java.util.Set.of("C001"));
        when(citizenAccessService.canAccessCitizen(user(), "C002"))
                .thenReturn(false);

        List<Citizen> results = citizenSearchService.search("C002", user());
        Citizen directResult = citizenSearchService.findAuthorized("C002", user());

        assertTrue(results.isEmpty());
        assertNull(directResult);
        verify(citizenAccessService).canAccessCitizen(user(), "C002");
    }

    @Test
    void returnsOnlyDocumentsMatchingSelectedCategory() {
        Citizen citizen = new Citizen("C001", "Arun Kumar");
        Document tax = new Document("D101", "Income Certificate", "Certificates", 4, "Text");
        Document receipt = new Document("D102", "Property Tax Receipt", "Tax", 3, "Text");
        tax.setCitizen(citizen);
        receipt.setCitizen(citizen);
        when(documentRepository.findByCitizen_CitizenIdIgnoreCase("C001"))
                .thenReturn(List.of(tax, receipt));

        List<Document> results = citizenSearchService.documentsFor(citizen, "tax");

        assertEquals(List.of("D102"), results.stream().map(Document::getId).toList());
    }

    private Authentication administrator() {
        return new UsernamePasswordAuthenticationToken(
                "admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication user() {
        return new UsernamePasswordAuthenticationToken(
                "limited-user", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

}
