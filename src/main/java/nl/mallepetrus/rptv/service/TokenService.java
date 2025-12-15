package nl.mallepetrus.rptv.service;

import nl.mallepetrus.rptv.config.AppProps;
import nl.mallepetrus.rptv.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final AppProps.JwtProps jwtProps;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, AppProps.JwtProps jwtProps, PasswordEncoder passwordEncoder) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProps = jwtProps;
        this.passwordEncoder = passwordEncoder;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProps.getAccessTokenTtl(), ChronoUnit.SECONDS);
        List<String> roles = List.of(user.getRoles().split(","));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProps.getIssuer())
                .issuedAt(now)
                .expiresAt(exp)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public GeneratedRefreshToken generateRefreshToken() {
        // create a token id and secret part; token presented to client is tokenId.raw
        String tokenId = randomId();
        byte[] raw = new byte[32]; // 256-bit random
        secureRandom.nextBytes(raw);
        String rawPart = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String presented = tokenId + "." + rawPart;
        String hash = passwordEncoder.encode(presented);
        Instant expiresAt = Instant.now().plus(jwtProps.getRefreshTokenTtl(), ChronoUnit.SECONDS);
        return new GeneratedRefreshToken(tokenId, presented, hash, expiresAt);
    }

    public String extractTokenId(String presentedToken) {
        int idx = presentedToken.indexOf('.');
        if (idx <= 0) throw new IllegalArgumentException("Malformed refresh token");
        return presentedToken.substring(0, idx);
    }

    private String randomId() {
        byte[] id = new byte[12];
        secureRandom.nextBytes(id);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(id);
    }

    public boolean matchesRefreshToken(String rawToken, String hash) {
        return passwordEncoder.matches(rawToken, hash);
    }

    public record GeneratedRefreshToken(String tokenId, String token, String hash, Instant expiresAt) {}
}
