package com.example.demo;

import com.example.demo.model.AppUser;
import com.example.demo.repository.AppUserRepository;
import com.example.demo.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Test-only accounts: domain HTTP tests still pass the real JWT and database checks. */
@Component
public class TestUsers {
    private final AppUserRepository users;
    private final JwtService jwt;
    private final String passwordHash;

    public TestUsers(AppUserRepository users, JwtService jwt, PasswordEncoder passwords) {
        this.users = users;
        this.jwt = jwt;
        this.passwordHash = passwords.encode("test-fixture-password");
    }

    public String issueToken(String ignoredFixtureLabel, String role) {
        String username = "fixture." + role.toLowerCase(java.util.Locale.ROOT);
        if (!JwtService.ROLES.contains(role)) return jwt.issueToken(new AppUser(username, passwordHash, role));
        var user = users.findByUsername(username).orElseGet(() -> users.save(new AppUser(username, passwordHash, role)));
        return jwt.issueToken(user);
    }
}
