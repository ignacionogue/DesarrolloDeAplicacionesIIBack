package com.example.demo.infrastructure.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

/** H2 interprets VARCHAR -> JSON as a JSON string, while PostgreSQL parses its text. */
@Component
public class JsonSql {
    private final boolean postgres;
    public JsonSql(JdbcTemplate jdbc) {
        postgres=Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                c->c.getMetaData().getDatabaseProductName().equals("PostgreSQL")));
    }
    public Object parameter(String json) { return json==null?null:postgres?json:json.getBytes(StandardCharsets.UTF_8); }
    public boolean postgres() { return postgres; }
}
