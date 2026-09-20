package com.example.demo.security;

import com.example.demo.config.AuthProperties;
import com.example.demo.model.AppUser;
import com.example.demo.repository.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Opt-in provisioning from environment secrets. Existing accounts are never overwritten. */
@Component
public class UserBootstrap implements ApplicationRunner {
    private final AuthProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;

    public UserBootstrap(AuthProperties properties, AppUserRepository users, PasswordEncoder passwords, ObjectMapper json) {
        this.properties = properties;
        this.users = users;
        this.passwords = passwords;
        this.json = json;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        List<SeedUser> seeds = new ArrayList<>();
        if (properties.bootstrapUsers() != null && !properties.bootstrapUsers().isBlank()) {
            try {
                seeds.addAll(json.readValue(properties.bootstrapUsers(), json.getTypeFactory().constructCollectionType(List.class, SeedUser.class)));
            } catch (RuntimeException ex) {
                // Do not include parser messages: they may contain a supplied password.
                throw new IllegalStateException("AUTH_BOOTSTRAP_USERS debe ser un array JSON valido de username, password y role");
            }
        }
        var names = new HashSet<String>();
        for (var seed : seeds) {
            validate(seed, true);
            if (!names.add(AppUser.canonicalUsername(seed.username()))) {
                throw new IllegalStateException("AUTH_BOOTSTRAP_USERS contiene usuarios duplicados");
            }
        }
        // Compatibility: migrate the formerly single environment account into the database once.
        if (!blank(properties.username()) || !blank(properties.password())) {
            var legacy = new SeedUser(properties.username(), properties.password(), properties.role());
            validate(legacy, false);
            if (names.add(AppUser.canonicalUsername(legacy.username()))) seeds.add(legacy);
        }
        for (var seed : seeds) {
            String username = AppUser.canonicalUsername(seed.username());
            if (users.findByUsername(username).isEmpty()) {
                users.save(new AppUser(username, passwords.encode(seed.password()), seed.role()));
            }
        }
    }

    private static void validate(SeedUser seed, boolean requireStrongPassword) {
        if (seed == null || blank(seed.username()) || !AppUser.canonicalUsername(seed.username()).matches("[a-z0-9._-]{3,100}")
                || blank(seed.password()) || seed.password().getBytes(StandardCharsets.UTF_8).length > 72
                || (requireStrongPassword && seed.password().length() < 12)
                || seed.role() == null || !JwtService.ROLES.contains(seed.role())) {
            throw new IllegalStateException("Configuracion de usuarios invalida: username de 3 a 100 caracteres a-z/0-9/._-, rol conocido y password de 12 a 72 bytes para nuevas cuentas");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    // Not a record: no generated toString that could expose the password in logs.
    public static class SeedUser {
        private String username;
        private String password;
        private String role;
        public SeedUser() { }
        public SeedUser(String username, String password, String role) { this.username=username; this.password=password; this.role=role; }
        public String username() { return username; }
        public String password() { return password; }
        public String role() { return role; }
        public void setUsername(String username) { this.username=username; }
        public void setPassword(String password) { this.password=password; }
        public void setRole(String role) { this.role=role; }
    }
}
