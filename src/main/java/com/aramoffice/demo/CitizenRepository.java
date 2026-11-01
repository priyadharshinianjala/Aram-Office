package com.aramoffice.demo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CitizenRepository extends JpaRepository<Citizen, String> {
    Optional<Citizen> findByCitizenIdIgnoreCase(String citizenId);

    List<Citizen> findByFullNameContainingIgnoreCaseOrderByFullNameAscCitizenIdAsc(
            String fullName);
}
