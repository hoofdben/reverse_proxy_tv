package nl.mallepetrus.rptv.service;

import nl.mallepetrus.rptv.domain.InviteCode;
import nl.mallepetrus.rptv.repository.InviteCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

@Service
public class InviteService {
    private final InviteCodeRepository inviteCodeRepository;

    public InviteService(InviteCodeRepository inviteCodeRepository) {
        this.inviteCodeRepository = inviteCodeRepository;
    }

    public Optional<InviteCode> findByCode(String code) {
        return inviteCodeRepository.findByCode(code);
    }

    @Transactional
    public InviteCode consumeInvite(String code) {
        InviteCode invite = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Invalid invite code"));
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "Invite code expired");
        }
        if (invite.getUses() >= invite.getMaxUses()) {
            throw new ResponseStatusException(BAD_REQUEST, "Invite code already used up");
        }
        invite.setUses(invite.getUses() + 1);
        return inviteCodeRepository.save(invite);
    }

    @Transactional
    public InviteCode save(InviteCode inviteCode) {
        return inviteCodeRepository.save(inviteCode);
    }

    public List<InviteCode> list() { return inviteCodeRepository.findAll(); }

    @Transactional
    public void revoke(String code) {
        InviteCode invite = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Invite not found"));
        invite.setMaxUses(invite.getUses());
        inviteCodeRepository.save(invite);
    }
}
