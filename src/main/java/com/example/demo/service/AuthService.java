package com.example.demo.service;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.response.CurrentUserResponse;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.model.AppUser;
import com.example.demo.repository.AppUserRepository;
import com.example.demo.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) throw invalidCredentials();
        var user = users.findByUsername(AppUser.canonicalUsername(request.username())).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (!matches || user == null || !user.isEnabled()) throw invalidCredentials();
        return new LoginResponse(jwt.issueToken(user), user.getUsername(), user.getRole());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(String username) {
        var user = users.findByUsername(username).filter(AppUser::isEnabled).orElseThrow(AuthService::invalidCredentials);
        return new CurrentUserResponse(user.getUsername(), user.getRole());
    }

    /** Invalidates all current sessions for this account, including on other replicas. */
    @Transactional
    public void logout(String username) {
        if (users.revokeTokens(username) != 1) throw invalidCredentials();
    }

    private static UnauthorizedException invalidCredentials() { return new UnauthorizedException("Credenciales invalidas"); }
}
