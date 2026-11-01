package com.aramoffice.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CitizenAccessService {

    private final JdbcTemplate jdbcTemplate;

    public CitizenAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAdministrator(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    public boolean canAccessCitizen(
            Authentication authentication,
            String citizenId) {
        if (isAdministrator(authentication)) {
            return true;
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM citizen_user_access WHERE username = ? AND citizen_id = ?)",
                Boolean.class,
                authentication.getName(),
                citizenId));
    }

    public List<String> accessibleCitizenIds(Authentication authentication) {
        if (isAdministrator(authentication)) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT citizen_id FROM citizen_user_access WHERE username = ?",
                String.class,
                authentication.getName());
    }

    public Set<String> accessibleCitizenIdSet(Authentication authentication) {
        return accessibleCitizenIds(authentication).stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean canAccessDocument(
            Authentication authentication,
            Document document) {
        return document.getCitizen() != null
                && canAccessCitizen(
                authentication, document.getCitizen().getCitizenId());
    }
}
