package com.aramoffice.demo;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;

public class BoundedBcryptPasswordEncoder implements PasswordEncoder {

    private static final int MAX_PASSWORD_BYTES = 72;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        requireSupportedLength(rawPassword);
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return rawPassword != null
                && rawPassword.toString().getBytes(StandardCharsets.UTF_8).length
                <= MAX_PASSWORD_BYTES
                && bcrypt.matches(rawPassword, encodedPassword);
    }

    private void requireSupportedLength(CharSequence rawPassword) {
        if (rawPassword == null
                || rawPassword.toString().getBytes(StandardCharsets.UTF_8).length
                > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "BCrypt passwords must not exceed 72 UTF-8 bytes.");
        }
    }
}
