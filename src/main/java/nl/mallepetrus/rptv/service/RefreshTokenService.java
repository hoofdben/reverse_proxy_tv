package nl.mallepetrus.rptv.service;

import nl.mallepetrus.rptv.domain.RefreshToken;
import nl.mallepetrus.rptv.domain.User;
import nl.mallepetrus.rptv.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public RefreshToken create(User user, String tokenId, String tokenHash, java.time.Instant expiresAt) {
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenId(tokenId);
        rt.setTokenHash(tokenHash);
        rt.setExpiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        return refreshTokenRepository.save(rt);
    }

    public Optional<RefreshToken> findByTokenId(String tokenId) { return refreshTokenRepository.findByTokenId(tokenId); }

    @Transactional
    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    @Transactional
    public void revokeAll(User user) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUser(user);
        for (RefreshToken t : tokens) {
            t.setRevoked(true);
        }
        refreshTokenRepository.saveAll(tokens);
    }
}
