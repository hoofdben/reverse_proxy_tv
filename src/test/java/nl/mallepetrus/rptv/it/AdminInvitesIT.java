package nl.mallepetrus.rptv.it;

import nl.mallepetrus.rptv.service.UserService;
import nl.mallepetrus.rptv.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AdminInvitesIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserService userService;

    @Test
    void admin_only_invite_endpoints() {
        // create users
        String adminEmail = "admin" + UUID.randomUUID() + "@ex.com";
        String userEmail = "user" + UUID.randomUUID() + "@ex.com";
        String pass = "Secret123!";
        userService.register(adminEmail, pass, "ADMIN");
        userService.register(userEmail, pass, "USER");

        String adminAccess = login(adminEmail, pass);
        String userAccess = login(userEmail, pass);

        // non-admin should be forbidden
        HttpHeaders hUser = new HttpHeaders();
        hUser.setBearerAuth(userAccess);
        ResponseEntity<String> forbiddenCreate = rest.exchange(
                "/api/admin/invites?maxUses=1",
                HttpMethod.POST,
                new HttpEntity<>(hUser),
                String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenCreate.getStatusCode());

        // admin can create
        HttpHeaders hAdmin = new HttpHeaders();
        hAdmin.setBearerAuth(adminAccess);
        ResponseEntity<Map> created = rest.exchange(
                "/api/admin/invites?maxUses=5",
                HttpMethod.POST,
                new HttpEntity<>(hAdmin),
                Map.class);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        String code = (String) created.getBody().get("code");
        assertNotNull(code);

        // list
        ResponseEntity<List> list = rest.exchange(
                "/api/admin/invites",
                HttpMethod.GET,
                new HttpEntity<>(hAdmin),
                List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertFalse(list.getBody().isEmpty());

        // revoke
        ResponseEntity<Map> revoke = rest.exchange(
                "/api/admin/invites/revoke?code=" + code,
                HttpMethod.POST,
                new HttpEntity<>(hAdmin),
                Map.class);
        assertEquals(HttpStatus.OK, revoke.getStatusCode());
        assertEquals("revoked", revoke.getBody().get("status"));
    }

    private String login(String email, String pass) {
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login",
                json(Map.of("email", email, "password", pass)), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        return (String) resp.getBody().get("accessToken");
    }

    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
