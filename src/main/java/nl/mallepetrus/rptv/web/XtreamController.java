package nl.mallepetrus.rptv.web;

import jakarta.validation.constraints.NotBlank;
import nl.mallepetrus.rptv.crypto.CryptoService;
import nl.mallepetrus.rptv.domain.User;
import nl.mallepetrus.rptv.domain.XtreamAccount;
import nl.mallepetrus.rptv.repository.UserRepository;
import nl.mallepetrus.rptv.service.XtreamAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestController
@RequestMapping("/api/xtream")
public class XtreamController {
    private final XtreamAccountService service;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final WebClient webClient;

    public XtreamController(XtreamAccountService service, UserRepository userRepository,
                            CryptoService cryptoService, WebClient webClient) {
        this.service = service;
        this.userRepository = userRepository;
        this.cryptoService = cryptoService;
        this.webClient = webClient;
    }

    private User ensureUser(Jwt jwt) {
        return userRepository.findById(UUID.fromString(jwt.getSubject())).orElseThrow();
    }

    @GetMapping
    public List<XtreamAccount> list(@AuthenticationPrincipal Jwt jwt) {
        return service.listFor(ensureUser(jwt));
    }

    public record CreateReq(@NotBlank String name, @NotBlank String apiUrl,
                            @NotBlank String username, @NotBlank String password) {}

    @PostMapping
    public XtreamAccount create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateReq req) {
        User user = ensureUser(jwt);
        return service.create(user, req.name(), req.apiUrl(),
                cryptoService.encrypt(req.username()), cryptoService.encrypt(req.password()));
    }

    public record UpdateReq(String name, String apiUrl, String username, String password) {}

    @PutMapping("/{id}")
    public XtreamAccount update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody UpdateReq req) {
        User user = ensureUser(jwt);
        String u = req.username() != null ? cryptoService.encrypt(req.username()) : null;
        String p = req.password() != null ? cryptoService.encrypt(req.password()) : null;
        return service.update(user, id, req.name(), req.apiUrl(), u, p);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(ensureUser(jwt), id);
        return Map.of("status", "deleted");
    }

    @GetMapping("/{id}/test")
    public Map<String, Object> test(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        XtreamAccount xa = service.getOwned(ensureUser(jwt), id);
        String username = cryptoService.decrypt(xa.getUsernameEnc());
        String password = cryptoService.decrypt(xa.getPasswordEnc());
        String url = xa.getApiUrl();
        if (!url.endsWith("/")) url += "/";
        URI uri = URI.create(url + "player_api.php?username=" + username + "&password=" + password);

        Map<String, Object> result = webClient.get().uri(uri)
                .exchangeToMono(resp -> resp.bodyToMono(Map.class)
                        .defaultIfEmpty(Map.of())
                        .map(body -> Map.of(
                                "ok", resp.statusCode().is2xxSuccessful(),
                                "status", resp.statusCode().value(),
                                "response", body
                        )))
                .timeout(Duration.ofSeconds(15))
                .onErrorResume(throwable -> {
                    if (throwable instanceof WebClientResponseException wcre) {
                        return Mono.just(Map.of(
                                "ok", false,
                                "status", wcre.getStatusCode().value(),
                                "error", "UpstreamError",
                                "message", wcre.getMessage()
                        ));
                    }
                    return Mono.just(Map.of(
                            "ok", false,
                            "error", throwable.getClass().getSimpleName(),
                            "message", throwable.getMessage()
                    ));
                })
                .block();
        return result;
    }
}
