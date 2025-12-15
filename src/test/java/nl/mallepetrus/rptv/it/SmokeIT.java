package nl.mallepetrus.rptv.it;

import nl.mallepetrus.rptv.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

public class SmokeIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void healthEndpoint_isPublic() {
        ResponseEntity<String> resp = rest.getForEntity("/actuator/health", String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    void xtreamList_requiresAuth() {
        ResponseEntity<String> resp = rest.getForEntity("/api/xtream", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }
}
