package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Authentication is implemented by the persisted application users and JWT filter.
// Excluding the default user prevents Spring from generating and logging a password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
