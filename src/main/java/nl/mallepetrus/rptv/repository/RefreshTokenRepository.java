package nl.mallepetrus.rptv.repository;

import nl.mallepetrus.rptv.domain.RefreshToken;
import nl.mallepetrus.rptv.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenId(String tokenId);
    List<RefreshToken> findAllByUser(User user);
}
