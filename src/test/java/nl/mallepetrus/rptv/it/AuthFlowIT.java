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

public class AuthFlowIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    InviteCodeRepository inviteRepo;

    @Test
    void register_login_me_happyPath() {
        // Given an invite code
        String code = UUID.randomUUID().toString().replace("-", "");
        InviteCode ic = new InviteCode();
        ic.setCode(code);
        ic.setMaxUses(1);
        ic.setExpiresAt(OffsetDateTime.now().plusDays(1));
        inviteRepo.save(ic);

        // Register
        String email = "u" + UUID.randomUUID() + "@ex.com";
        String password = "Passw0rd!";
        Map<String, String> regBody = Map.of(
                "email", email,
                "password", password,
                "inviteCode", code
        );
        ResponseEntity<Map> regResp = rest.postForEntity("/api/auth/register", json(regBody), Map.class);
        assertEquals(HttpStatus.CREATED, regResp.getStatusCode());
        String access = (String) regResp.getBody().get("accessToken");
        String refresh = (String) regResp.getBody().get("refreshToken");
        assertNotNull(access);
        assertNotNull(refresh);

        // me
        HttpHeaders hdrs = new HttpHeaders();
        hdrs.setBearerAuth(access);
        ResponseEntity<Map> me = rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(hdrs), Map.class);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals(email, me.getBody().get("email"));
    }

    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
