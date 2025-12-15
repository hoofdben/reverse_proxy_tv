package nl.mallepetrus.rptv.repository;

import nl.mallepetrus.rptv.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {
    Optional<InviteCode> findByCode(String code);
}
