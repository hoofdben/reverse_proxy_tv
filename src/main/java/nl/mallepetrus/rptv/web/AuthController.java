package nl.mallepetrus.rptv.web;

import jakarta.validation.Valid;
import nl.mallepetrus.rptv.domain.RefreshToken;
import nl.mallepetrus.rptv.domain.User;
import nl.mallepetrus.rptv.service.*;
import nl.mallepetrus.rptv.web.dto.AuthDtos;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final InviteService inviteService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserService userService, InviteService inviteService,
                          TokenService tokenService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.inviteService = inviteService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthDtos.TokenPair register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        inviteService.consumeInvite(req.inviteCode());
        userService.findByEmail(req.email()).ifPresent(u -> {
            throw new ResponseStatusException(CONFLICT, "Email already registered");
        });
        User user = userService.register(req.email(), req.password(), "USER");
        String access = tokenService.issueAccessToken(user);
        TokenService.GeneratedRefreshToken gen = tokenService.generateRefreshToken();
        refreshTokenService.create(user, gen.tokenId(), gen.hash(), gen.expiresAt());
        return new AuthDtos.TokenPair(access, gen.token());
    }

    @PostMapping("/login")
    public AuthDtos.TokenPair login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        User user = userService.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));
        if (!userService.matchesPassword(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }
        String access = tokenService.issueAccessToken(user);
        TokenService.GeneratedRefreshToken gen = tokenService.generateRefreshToken();
        refreshTokenService.create(user, gen.tokenId(), gen.hash(), gen.expiresAt());
        return new AuthDtos.TokenPair(access, gen.token());
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(@Valid @RequestBody AuthDtos.RefreshRequest req) {
        String tokenId = tokenService.extractTokenId(req.refreshToken());
        RefreshToken token = refreshTokenService.findByTokenId(tokenId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token"));
        if (token.isRevoked()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Refresh token revoked");
        }
        // Validate hash match
        if (!tokenService.matchesRefreshToken(req.refreshToken(), token.getTokenHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
        }
        // Rotate token
        refreshTokenService.revoke(token);
        User user = token.getUser();
        String access = tokenService.issueAccessToken(user);
        TokenService.GeneratedRefreshToken gen = tokenService.generateRefreshToken();
        refreshTokenService.create(user, gen.tokenId(), gen.hash(), gen.expiresAt());
        return Map.of("accessToken", access, "refreshToken", gen.token());
    }

    @PostMapping("/logout")
    @ResponseStatus(NO_CONTENT)
    public void logout(@Valid @RequestBody AuthDtos.RefreshRequest req) {
        String tokenId = tokenService.extractTokenId(req.refreshToken());
        refreshTokenService.findByTokenId(tokenId).ifPresent(rt -> {
            if (tokenService.matchesRefreshToken(req.refreshToken(), rt.getTokenHash())) {
                refreshTokenService.revoke(rt);
            }
        });
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(UNAUTHORIZED);
        return Map.of(
                "sub", jwt.getSubject(),
                "email", jwt.getClaimAsString("email"),
                "roles", jwt.getClaim("roles")
        );
    }
}
