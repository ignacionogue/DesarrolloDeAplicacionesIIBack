package com.example.demo;

import com.example.demo.config.AuthProperties;
import com.example.demo.model.AppUser;
import com.example.demo.repository.AppUserRepository;
import com.example.demo.security.UserBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.auth.username=", "app.auth.password=",
    "app.auth.bootstrap-users=[{\"username\":\"multi.personal\",\"password\":\"only-test-password-personal\",\"role\":\"PERSONAL_OBRAS\"},"
      + "{\"username\":\"multi.responsable\",\"password\":\"only-test-password-responsable\",\"role\":\"RESPONSABLE_AUTORIZADO\"},"
      + "{\"username\":\"multi.jefe\",\"password\":\"only-test-password-jefe\",\"role\":\"JEFE_CUADRILLA\"},"
      + "{\"username\":\"multi.operario\",\"password\":\"only-test-password-operario\",\"role\":\"OPERARIO_CONTRATISTA\"},"
      + "{\"username\":\"multi.inspector\",\"password\":\"only-test-password-inspector\",\"role\":\"INSPECTOR_OBRA\"}]"
})
class MultiUserAuthTests {
    @LocalServerPort int port;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthProperties properties;
    @Autowired UserBootstrap bootstrap;

    private HttpResponse<String> call(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        return call("POST", "/api/auth/login", json.writeValueAsString(Map.of("username", username, "password", password)), null);
    }

    private String token(String username, String password) throws Exception {
        var response = login(username, password);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).get("accessToken").asText();
    }

    private AppUser create(String name) {
        return users.save(new AppUser(name, passwords.encode("only-test-password"), "PERSONAL_OBRAS"));
    }

    @Test void fiveUsersLogInSimultaneouslyWithTheirOwnRolesAndHashedPasswords() throws Exception {
        var roles = Map.of("personal", "PERSONAL_OBRAS", "responsable", "RESPONSABLE_AUTORIZADO",
                "jefe", "JEFE_CUADRILLA", "operario", "OPERARIO_CONTRATISTA", "inspector", "INSPECTOR_OBRA");
        for (var entry : roles.entrySet()) {
            String name = "multi." + entry.getKey();
            String password = "only-test-password-" + entry.getKey();
            var response = login(name, password);
            assertEquals(200, response.statusCode());
            var data = json.readTree(response.body());
            assertEquals(entry.getValue(), data.get("role").asText());
            assertFalse(response.body().contains("password"));
            var user = users.findByUsername(name).orElseThrow();
            assertNotEquals(password, user.getPasswordHash());
            assertTrue(user.getPasswordHash().startsWith("$2"));
            assertTrue(passwords.matches(password, user.getPasswordHash()));
            var me = call("GET", "/api/auth/me", null, data.get("accessToken").asText());
            assertEquals(200, me.statusCode());
            assertEquals(name, json.readTree(me.body()).get("username").asText());
            assertEquals(entry.getValue(), json.readTree(me.body()).get("role").asText());
            assertFalse(me.body().contains("password"));
        }
    }

    @Test void usernameIsCanonicalAndPasswordRemainsCaseSensitive() throws Exception {
        assertEquals(200, login(" MULTI.PERSONAL ", "only-test-password-personal").statusCode());
        assertEquals(401, login("multi.personal", "ONLY-TEST-PASSWORD-PERSONAL").statusCode());
    }

    @Test void wrongPasswordAndUnknownAccountHaveTheSameError() throws Exception {
        var wrong = login("multi.personal", "wrong-password");
        var unknown = login("missing.account", "wrong-password");
        assertEquals(401, wrong.statusCode());
        assertEquals(401, unknown.statusCode());
        assertEquals(wrong.body(), unknown.body());
        assertEquals("UNAUTHORIZED", json.readTree(wrong.body()).get("code").asText());
    }

    @Test void clientCannotChooseItsRoleInLogin() throws Exception {
        var response = call("POST", "/api/auth/login",
                "{\"username\":\"multi.personal\",\"password\":\"only-test-password-personal\",\"role\":\"RESPONSABLE_AUTORIZADO\"}", null);
        assertEquals(200, response.statusCode());
        var data = json.readTree(response.body());
        assertEquals("PERSONAL_OBRAS", data.get("role").asText());
        assertEquals(403, call("PATCH", "/api/public-works/projects/1/approve", "{}", data.get("accessToken").asText()).statusCode());
    }

    @Test void logoutRevokesAllAccountSessionsButNotOtherUsers() throws Exception {
        create("logout.account");
        String first = token("logout.account", "only-test-password");
        String second = token("logout.account", "only-test-password");
        String other = token("multi.personal", "only-test-password-personal");
        var logout = call("POST", "/api/auth/logout", null, first);
        assertEquals(204, logout.statusCode());
        assertTrue(logout.body().isEmpty());
        assertEquals(401, call("GET", "/api/auth/me", null, first).statusCode());
        assertEquals(401, call("GET", "/api/public-works/projects", null, second).statusCode());
        assertEquals(200, call("GET", "/api/auth/me", null, other).statusCode());
        assertEquals(200, call("GET", "/api/auth/me", null, token("logout.account", "only-test-password")).statusCode());
    }

    @Test void disabledOrDeletedAccountsCannotUseExistingTokens() throws Exception {
        var disabled = create("disabled.account");
        var token = token(disabled.getUsername(), "only-test-password");
        disabled.setEnabled(false);
        users.save(disabled);
        assertEquals(401, login(disabled.getUsername(), "only-test-password").statusCode());
        assertEquals(401, call("GET", "/api/auth/me", null, token).statusCode());
        var deleted = create("deleted.account");
        var deletedToken = token(deleted.getUsername(), "only-test-password");
        users.delete(deleted);
        assertEquals(401, call("GET", "/api/auth/me", null, deletedToken).statusCode());
        create("deleted.account");
        assertEquals(401, call("GET", "/api/auth/me", null, deletedToken).statusCode());
    }

    @Test void changedDatabaseRoleInvalidatesTheOldClaim() throws Exception {
        create("role.changed");
        var token = token("role.changed", "only-test-password");
        jdbc.update("UPDATE app_user SET role='INSPECTOR_OBRA' WHERE username='role.changed'");
        assertEquals(401, call("GET", "/api/auth/me", null, token).statusCode());
        var newLogin = login("role.changed", "only-test-password");
        assertEquals(200, newLogin.statusCode());
        assertEquals("INSPECTOR_OBRA", json.readTree(newLogin.body()).get("role").asText());
    }

    @Test void anonymousMeLogoutAndRegistrationAreNotAllowed() throws Exception {
        assertEquals(401, call("GET", "/api/auth/me", null, null).statusCode());
        assertEquals(401, call("POST", "/api/auth/logout", null, null).statusCode());
        assertEquals(401, call("POST", "/api/auth/register", "{}", null).statusCode());
        assertEquals(403, call("POST", "/api/auth/register", "{}", token("multi.personal", "only-test-password-personal")).statusCode());
    }

    @Test void bootstrapIsIdempotentAndDoesNotOverwriteExistingCredentialsOrRoles() throws Exception {
        var user = create("bootstrap.existing");
        var originalHash = user.getPasswordHash();
        user.setEnabled(false);
        users.save(user);
        String config = "[{\"username\":\"bootstrap.existing\",\"password\":\"new-password-unused\",\"role\":\"INSPECTOR_OBRA\"}]";
        var runner = bootstrap(config);
        long count = users.count();
        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());
        var saved = users.findByUsername("bootstrap.existing").orElseThrow();
        assertEquals(count, users.count());
        assertEquals(originalHash, saved.getPasswordHash());
        assertEquals("PERSONAL_OBRAS", saved.getRole());
        assertFalse(saved.isEnabled());
    }

    @Test void bootstrapRejectsInvalidConfigurationWithoutPartialWritesOrLeakingPasswords() {
        long count = users.count();
        String valid = "{\"username\":\"never.partial\",\"password\":\"only-test-password\",\"role\":\"PERSONAL_OBRAS\"}";
        for (var value : new String[]{"broken-secret-json", "null", "[null]", "[" + valid + "," + valid + "]",
                "[" + valid + "," + valid.replace("never.partial", "invalid.role").replace("PERSONAL_OBRAS", "ADMIN") + "]",
                "[" + valid.replace("only-test-password", "short") + "]"}) {
            var ex = assertThrows(IllegalStateException.class, () -> bootstrap(value).run(new DefaultApplicationArguments()));
            assertFalse(ex.getMessage().contains("only-test-password"));
            assertFalse(ex.getMessage().contains("broken-secret-json"));
            assertEquals(count, users.count());
        }
        assertFalse(properties.toString().contains("only-test-password"));
    }

    private UserBootstrap bootstrap(String value) {
        return new UserBootstrap(new AuthProperties(properties.jwtSecret(), 30, "", "", "", value), users, passwords, json);
    }
}
