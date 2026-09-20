package com.example.demo.repository;

import com.example.demo.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AppUser u set u.tokenVersion = u.tokenVersion + 1 where u.username = :username")
    int revokeTokens(@Param("username") String username);
}
