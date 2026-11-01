package com.aramoffice.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class InitialAdminAccount implements ApplicationRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(InitialAdminAccount.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String userUsername;
    private final String userPassword;
    private final String userCitizenIds;

    public InitialAdminAccount(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.initial-admin.username:}") String username,
            @Value("${app.security.initial-admin.password:}") String password,
            @Value("${app.security.initial-user.username:}") String userUsername,
            @Value("${app.security.initial-user.password:}") String userPassword,
            @Value("${app.security.initial-user.citizen-ids:}") String userCitizenIds) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.username = username.trim();
        this.password = password;
        this.userUsername = userUsername.trim();
        this.userPassword = userPassword;
        this.userCitizenIds = userCitizenIds;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isEmpty() && password.isEmpty()) {
            logger.warn("No initial administrator configured. Set ARAM_ADMIN_USERNAME and ARAM_ADMIN_PASSWORD.");
        } else if (username.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException(
                    "Both ARAM_ADMIN_USERNAME and ARAM_ADMIN_PASSWORD must be configured.");
        } else {
            validateCredentials(username, password, "admin");
            createAccountIfMissing(username, password, "ADMIN");
        }

        boolean userConfigurationEmpty = userUsername.isEmpty()
                && userPassword.isEmpty()
                && userCitizenIds.isBlank();
        if (userConfigurationEmpty) {
            return;
        }
        if (userUsername.isEmpty() || userPassword.isEmpty()
                || userCitizenIds.isBlank()) {
            throw new IllegalStateException(
                    "Configure ARAM_USER_USERNAME, ARAM_USER_PASSWORD, and ARAM_USER_CITIZEN_IDS together.");
        }
        if (!username.isEmpty() && username.equalsIgnoreCase(userUsername)) {
            throw new IllegalStateException(
                    "The initial administrator and limited user must have different usernames.");
        }
        validateCredentials(userUsername, userPassword, "limited user");
        List<String> citizenIds = Arrays.stream(userCitizenIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (citizenIds.isEmpty()
                || citizenIds.stream().anyMatch(
                id -> id.length() > 64
                        || id.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalStateException(
                    "ARAM_USER_CITIZEN_IDS must contain valid citizen IDs of at most 64 characters.");
        }

        Integer existing = accountCount(userUsername);
        if (existing != null && existing > 0) {
            logger.info("The configured limited account already exists; its password and grants were not changed.");
            return;
        }

        for (String citizenId : citizenIds) {
            Integer citizenExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM citizens WHERE citizen_id = ?",
                    Integer.class,
                    citizenId);
            if (citizenExists == null || citizenExists == 0) {
                throw new IllegalStateException(
                        "A configured limited-user citizen ID does not exist.");
            }
        }

        jdbcTemplate.update(
                "INSERT INTO app_users (username, password_hash, user_role) VALUES (?, ?, 'USER')",
                userUsername,
                passwordEncoder.encode(userPassword));
        for (String citizenId : citizenIds) {
            jdbcTemplate.update(
                    "INSERT INTO citizen_user_access (username, citizen_id) VALUES (?, ?)",
                    userUsername,
                    citizenId);
        }
        logger.info("Created the configured limited user and its citizen access grants.");
    }

    private void validateCredentials(
            String accountUsername,
            String accountPassword,
            String accountType) {
        if (accountUsername.length() > 100 || accountPassword.length() < 12
                || accountPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Initial " + accountType
                            + " username must be at most 100 characters and password 12 to 72 UTF-8 bytes.");
        }
    }

    private Integer accountCount(String accountUsername) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE username = ?",
                Integer.class,
                accountUsername);
    }

    private void createAccountIfMissing(
            String accountUsername,
            String accountPassword,
            String role) {
        Integer existing = accountCount(accountUsername);
        if (existing == null || existing == 0) {
            jdbcTemplate.update(
                    "INSERT INTO app_users (username, password_hash, user_role) VALUES (?, ?, ?)",
                    accountUsername,
                    passwordEncoder.encode(accountPassword),
                    role);
            logger.info("Created the configured initial administrator account.");
        } else {
            logger.info("The configured administrator account already exists; its password was not changed.");
        }
    }
}
