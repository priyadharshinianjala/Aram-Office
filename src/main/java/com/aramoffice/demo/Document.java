package com.aramoffice.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @Column(name = "document_id")
    private String id;

    @Column(nullable = false)
    private String title;

    private String category;

    private int importance;

    @Column(name = "current_text", columnDefinition = "TEXT")
    private String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "citizen_id")
    private Citizen citizen;

    public Document() {
    }

    public Document(String id, String title, String category,
                    int importance, String text) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.importance = importance;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public int getImportance() {
        return importance;
    }

    public String getText() {
        return text;
    }

    public Citizen getCitizen() {
        return citizen;
    }

    public void setCitizen(Citizen citizen) {
        this.citizen = citizen;
    }
}