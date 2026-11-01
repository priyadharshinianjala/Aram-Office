package com.aramoffice.demo;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class CitizenController {

    private final CitizenSearchService citizenSearchService;

    public CitizenController(CitizenSearchService citizenSearchService) {
        this.citizenSearchService = citizenSearchService;
    }

    @GetMapping("/people/search")
    public String search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "false") boolean unknown,
            Authentication authentication,
            Model model) {
        String term = query.trim();
        boolean valid = term.length() <= 100
                && term.chars().noneMatch(Character::isISOControl);
        model.addAttribute("query", term);
        model.addAttribute("citizens", valid
                ? citizenSearchService.search(term, authentication)
                : List.of());
        model.addAttribute("validationError", valid ? "" :
                "Enter a search of at most 100 characters.");
        model.addAttribute("unknownCitizen", unknown);
        model.addAttribute("username", authentication.getName());
        return "people-search";
    }

    @GetMapping("/people/{citizenId}")
    public String citizenDocuments(
            @PathVariable String citizenId,
            @RequestParam(defaultValue = "") String category,
            Authentication authentication,
            Model model) {
        if (citizenId.length() > 64
                || citizenId.chars().anyMatch(Character::isISOControl)) {
            return "redirect:/people/search?unknown=true";
        }
        Citizen citizen = citizenSearchService.findAuthorized(
                citizenId, authentication);
        if (citizen == null) {
            return "redirect:/people/search?unknown=true";
        }

        List<String> categories = citizenSearchService.categoriesFor(citizen);
        String requestedCategory = category.trim();
        boolean categoryExists = requestedCategory.isEmpty()
                || categories.stream().anyMatch(
                value -> value.equalsIgnoreCase(requestedCategory));
        String selectedCategory = categoryExists ? requestedCategory : "";
        model.addAttribute("citizen", citizen);
        model.addAttribute("documents",
                citizenSearchService.documentsFor(citizen, selectedCategory));
        model.addAttribute("categories", categories);
        model.addAttribute("activeCategory", selectedCategory);
        model.addAttribute("username", authentication.getName());
        return "citizen-documents";
    }
}
