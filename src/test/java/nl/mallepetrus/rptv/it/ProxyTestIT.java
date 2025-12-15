package nl.mallepetrus.rptv.it;

import nl.mallepetrus.rptv.domain.InviteCode;
import nl.mallepetrus.rptv.repository.InviteCodeRepository;
import nl.mallepetrus.rptv.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyTestIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;
    @Autowired
    InviteCodeRepository inviteRepo;

    private DisposableServer upstream;
    private String baseUrl;

    @BeforeEach
    void startUpstream() {
        upstream = HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .get("/player_api.php", (req, res) -> {
                            String body = "{\"user_info\":{\"auth\":1}}";
                            return res.header("Content-Type", "application/json")
                                    .sendString(Mono.just(body), StandardCharsets.UTF_8);
                        })
                ).bindNow();
        baseUrl = "http://localhost:" + upstream.port() + "/";
    }

    @AfterEach
    void stopUpstream() {
        if (upstream != null) upstream.disposeNow();
    }

    @Test
    void xtream_test_endpoint_fetches_from_upstream() {
        // invite and register
        String code = UUID.randomUUID().toString().replace("-", "");
        var ic = new nl.mallepetrus.rptv.domain.InviteCode();
        ic.setCode(code); ic.setMaxUses(1); ic.setExpiresAt(OffsetDateTime.now().plusDays(1));
        inviteRepo.save(ic);

        String email = "u" + UUID.randomUUID() + "@ex.com";
        String pass = "Passw0rd!";
        var reg = rest.postForEntity("/api/auth/register",
                json(Map.of("email", email, "password", pass, "inviteCode", code)), Map.class);
        assertEquals(HttpStatus.CREATED, reg.getStatusCode());
        String access = (String) reg.getBody().get("accessToken");

        // create account with upstream base url
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(access); h.setContentType(MediaType.APPLICATION_JSON);
        var create = rest.postForEntity("/api/xtream", new HttpEntity<>(
                Map.of("name", "Test", "apiUrl", baseUrl, "username", "u", "password", "p"), h), Map.class);
        assertEquals(HttpStatus.OK, create.getStatusCode());
        String id = (String) create.getBody().get("id");

        // call test endpoint
        ResponseEntity<Map> test = rest.exchange("/api/xtream/" + id + "/test", HttpMethod.GET,
                new HttpEntity<>(bearer(access)), Map.class);
        assertEquals(HttpStatus.OK, test.getStatusCode());
        assertEquals(Boolean.TRUE, test.getBody().get("ok"));
        assertNotNull(test.getBody().get("response"));
    }

    private HttpHeaders bearer(String access) { HttpHeaders h=new HttpHeaders(); h.setBearerAuth(access); return h; }
    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
