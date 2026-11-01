package com.aramoffice.demo;

public record OfficerAccount(
        String officerId,
        String fullName,
        String username,
        String departmentId,
        String departmentName,
        boolean enabled) {
}
