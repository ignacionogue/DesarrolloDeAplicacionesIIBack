package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.EventActor;
import com.example.demo.repository.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class PersistedEventActor implements EventActor {
    private final AppUserRepository users;
    public PersistedEventActor(AppUserRepository users) { this.users = users; }
    @Override public UUID currentPersonId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) return null;
        return users.findByUsername(authentication.getName()).filter(user -> user.isEnabled())
                .map(user -> user.getIntegrationId()).orElseThrow(() -> new IllegalStateException("EVENT_ACTOR_NOT_FOUND"));
    }
}
