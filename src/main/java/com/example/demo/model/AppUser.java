package com.example.demo.model;

import jakarta.persistence.*;
import java.util.Locale;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String username;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 30)
    private String role;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(nullable = false)
    private long tokenVersion;

    protected AppUser() { }

    public AppUser(String username, String passwordHash, String role) {
        this.username = canonicalUsername(username);
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static String canonicalUsername(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public long getTokenVersion() { return tokenVersion; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
