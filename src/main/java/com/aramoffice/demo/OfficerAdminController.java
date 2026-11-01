package com.aramoffice.demo;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OfficerAdminController {

    private final OfficerAccountService officerAccountService;
    private final AuditLogService auditLogService;

    public OfficerAdminController(
            OfficerAccountService officerAccountService,
            AuditLogService auditLogService) {
        this.officerAccountService = officerAccountService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/admin")
    public String dashboard(Model model, Authentication authentication) {
        model.addAttribute("auditEvents", auditLogService.recent());
        model.addAttribute("username", authentication.getName());
        return "admin-dashboard";
    }

    @PostMapping("/admin/officers")
    public String addOfficer(
            @RequestParam String officerId,
            @RequestParam String fullName,
            @RequestParam String username,
            @RequestParam String password,
            Authentication authentication,
            RedirectAttributes redirect) {
        officerAccountService.createOfficer(
                officerId, fullName, username, password,
                authentication.getName());
        redirect.addFlashAttribute(
                "successMessage", "Officer account created and enabled.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/officers/{username}/enabled")
    public String setEnabled(
            @PathVariable String username,
            @RequestParam boolean enabled,
            Authentication authentication,
            RedirectAttributes redirect) {
        officerAccountService.setOfficerEnabled(
                username, enabled, authentication.getName());
        redirect.addFlashAttribute(
                "successMessage",
                enabled ? "Officer account enabled."
                        : "Officer account disabled.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/officers/{username}/password")
    public String resetPassword(
            @PathVariable String username,
            @RequestParam String newPassword,
            Authentication authentication,
            RedirectAttributes redirect) {
        officerAccountService.resetPassword(
                username, newPassword, authentication.getName());
        redirect.addFlashAttribute(
                "successMessage", "Officer password reset.");
        return "redirect:/admin";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String validationError(
            IllegalArgumentException exception,
            RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", exception.getMessage());
        return "redirect:/admin";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String duplicateData(RedirectAttributes redirect) {
        redirect.addFlashAttribute(
                "errorMessage",
                "That officer ID or username already exists. Check the values and try again.");
        return "redirect:/admin";
    }
}
