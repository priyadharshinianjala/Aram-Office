package com.aramoffice.demo;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, String> {
    @EntityGraph(attributePaths = "citizen")
    @Override
    List<Document> findAll();

    @EntityGraph(attributePaths = "citizen")
    @Override
    Optional<Document> findById(String id);

    List<Document> findByCitizen_CitizenIdIgnoreCase(String citizenId);
}