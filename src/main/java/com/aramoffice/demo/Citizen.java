package com.aramoffice.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "citizens")
public class Citizen {

    @Id
    @Column(name = "citizen_id", length = 64)
    private String citizenId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    protected Citizen() {
    }

    public Citizen(String citizenId, String fullName) {
        this.citizenId = citizenId;
        this.fullName = fullName;
    }

    public String getCitizenId() {
        return citizenId;
    }

    public String getFullName() {
        return fullName;
    }
}
