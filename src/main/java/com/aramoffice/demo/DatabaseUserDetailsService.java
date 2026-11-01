package com.aramoffice.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        List<UserDetails> accounts = jdbcTemplate.query(
                "SELECT u.username, u.password_hash, u.user_role, "
                        + "o.enabled AS officer_enabled "
                        + "FROM app_users u LEFT JOIN officers o "
                        + "ON o.username = u.username WHERE u.username = ?",
                (resultSet, rowNumber) -> {
                    String role = resultSet.getString("user_role");
                    if (!"ADMIN".equals(role)
                            && !"USER".equals(role)
                            && !"OFFICER".equals(role)) {
                        throw new IllegalStateException("Unsupported account role");
                    }
                    String accountUsername = resultSet.getString("username");
                    User.UserBuilder account = User.withUsername(accountUsername)
                            .password(resultSet.getString("password_hash"))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
                    if ("OFFICER".equals(role)) {
                        boolean officerEnabled =
                                resultSet.getBoolean("officer_enabled")
                                        && !resultSet.wasNull();
                        account.disabled(!officerEnabled);
                    }
                    return account.build();
                },
                username);

        if (accounts.isEmpty()) {
            throw new UsernameNotFoundException("Account not found");
        }
        return accounts.get(0);
    }
}
