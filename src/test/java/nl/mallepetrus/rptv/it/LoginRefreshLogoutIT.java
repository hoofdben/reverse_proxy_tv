package nl.mallepetrus.rptv.it;

import nl.mallepetrus.rptv.domain.InviteCode;
import nl.mallepetrus.rptv.repository.InviteCodeRepository;
import nl.mallepetrus.rptv.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LoginRefreshLogoutIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    InviteCodeRepository inviteRepo;

    @Test
    void login_refresh_logout_flow() {
        // invite
        String code = UUID.randomUUID().toString().replace("-", "");
        InviteCode ic = new InviteCode();
        ic.setCode(code);
        ic.setMaxUses(2);
        ic.setExpiresAt(OffsetDateTime.now().plusDays(1));
        inviteRepo.save(ic);

        String email = "user" + UUID.randomUUID() + "@ex.com";
        String password = "Passw0rd!";

        // register
        Map<String, String> reg = Map.of("email", email, "password", password, "inviteCode", code);
        ResponseEntity<Map> regResp = rest.postForEntity("/api/auth/register", json(reg), Map.class);
        assertEquals(HttpStatus.CREATED, regResp.getStatusCode());

        // login
        Map<String, String> login = Map.of("email", email, "password", password);
        ResponseEntity<Map> loginResp = rest.postForEntity("/api/auth/login", json(login), Map.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        String access = (String) loginResp.getBody().get("accessToken");
        String refresh = (String) loginResp.getBody().get("refreshToken");
        assertNotNull(access);
        assertNotNull(refresh);

        // refresh -> new pair and old revoked
        Map<String, String> refReq = Map.of("refreshToken", refresh);
        ResponseEntity<Map> refreshResp = rest.postForEntity("/api/auth/refresh", json(refReq), Map.class);
        assertEquals(HttpStatus.OK, refreshResp.getStatusCode());
        String access2 = (String) refreshResp.getBody().get("accessToken");
        String refresh2 = (String) refreshResp.getBody().get("refreshToken");
        assertNotEquals(access, access2);
        assertNotEquals(refresh, refresh2);

        // logout with new token -> revoke
        ResponseEntity<Void> logout = rest.postForEntity("/api/auth/logout", json(Map.of("refreshToken", refresh2)), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, logout.getStatusCode());

        // refreshing again with the same token should now be unauthorized
        ResponseEntity<Map> refreshAfterLogout = rest.postForEntity("/api/auth/refresh", json(Map.of("refreshToken", refresh2)), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, refreshAfterLogout.getStatusCode());
    }

    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
