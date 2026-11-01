package com.aramoffice.demo;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuditLogEvents {

    private final AuditLogService auditLogService;

    public AuditLogEvents(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @EventListener
    public void authenticationSucceeded(AuthenticationSuccessEvent event) {
        auditLogService.record(
                event.getAuthentication().getName(), "LOGIN_SUCCEEDED", null);
    }

    @EventListener
    public void authenticationFailed(AbstractAuthenticationFailureEvent event) {
        auditLogService.record(
                event.getAuthentication().getName(), "LOGIN_FAILED", null);
    }

    @EventListener
    public void loggedOut(LogoutSuccessEvent event) {
        auditLogService.record(
                event.getAuthentication().getName(), "LOGOUT", null);
    }
}
