package nl.mallepetrus.rptv.it;

import nl.mallepetrus.rptv.domain.InviteCode;
import nl.mallepetrus.rptv.domain.XtreamAccount;
import nl.mallepetrus.rptv.repository.InviteCodeRepository;
import nl.mallepetrus.rptv.repository.XtreamAccountRepository;
import nl.mallepetrus.rptv.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class XtreamCrudIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;
    @Autowired
    InviteCodeRepository inviteRepo;
    @Autowired
    XtreamAccountRepository xtreamRepo;

    @Test
    void xtream_crud_and_ownership() {
        String code = UUID.randomUUID().toString().replace("-", "");
        InviteCode ic = new InviteCode();
        ic.setCode(code); ic.setMaxUses(2); ic.setExpiresAt(OffsetDateTime.now().plusDays(1));
        inviteRepo.save(ic);

        String email1 = "u1" + UUID.randomUUID() + "@ex.com";
        String email2 = "u2" + UUID.randomUUID() + "@ex.com";
        String pass = "Passw0rd!";

        Map<String, String> reg1 = Map.of("email", email1, "password", pass, "inviteCode", code);
        Map<String, String> reg2 = Map.of("email", email2, "password", pass, "inviteCode", code);
        ResponseEntity<Map> r1 = rest.postForEntity("/api/auth/register", json(reg1), Map.class);
        ResponseEntity<Map> r2 = rest.postForEntity("/api/auth/register", json(reg2), Map.class);
        assertEquals(HttpStatus.CREATED, r1.getStatusCode());
        assertEquals(HttpStatus.CREATED, r2.getStatusCode());
        String access1 = (String) r1.getBody().get("accessToken");
        String access2 = (String) r2.getBody().get("accessToken");

        // create account for user1
        HttpHeaders h1 = new HttpHeaders(); h1.setBearerAuth(access1); h1.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> createReq = Map.of(
                "name", "My IPTV",
                "apiUrl", "http://example.local/",
                "username", "alice",
                "password", "secret"
        );
        ResponseEntity<Map> created = rest.postForEntity("/api/xtream", new HttpEntity<>(createReq, h1), Map.class);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        String id = (String) created.getBody().get("id");
        assertNotNull(id);

        // verify encrypted at rest (not plaintext)
        XtreamAccount stored = xtreamRepo.findById(UUID.fromString(id)).orElseThrow();
        assertNotNull(stored.getUsernameEnc());
        assertNotEquals("alice", stored.getUsernameEnc());
        assertNotEquals("secret", stored.getPasswordEnc());

        // list for user1 returns 1
        ResponseEntity<List> list1 = rest.exchange("/api/xtream", HttpMethod.GET, new HttpEntity<>(bearer(access1)), List.class);
        assertEquals(HttpStatus.OK, list1.getStatusCode());
        assertFalse(list1.getBody().isEmpty());

        // user2 cannot access user1 account -> 404
        HttpHeaders h2 = bearer(access2); h2.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> getByOther = rest.exchange("/api/xtream/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "X"), h2), String.class);
        assertEquals(HttpStatus.NOT_FOUND, getByOther.getStatusCode());

        // update by owner
        ResponseEntity<Map> upd = rest.exchange("/api/xtream/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "New Name"), h1), Map.class);
        assertEquals(HttpStatus.OK, upd.getStatusCode());
        assertEquals("New Name", upd.getBody().get("name"));

        // delete by owner
        ResponseEntity<Map> del = rest.exchange("/api/xtream/" + id, HttpMethod.DELETE,
                new HttpEntity<>(bearer(access1)), Map.class);
        assertEquals(HttpStatus.OK, del.getStatusCode());
        assertEquals("deleted", del.getBody().get("status"));
    }

    private HttpHeaders bearer(String access) { HttpHeaders h=new HttpHeaders(); h.setBearerAuth(access); return h; }
    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
