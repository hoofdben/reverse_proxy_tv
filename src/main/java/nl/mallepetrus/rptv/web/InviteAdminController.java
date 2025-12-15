package nl.mallepetrus.rptv.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import nl.mallepetrus.rptv.domain.InviteCode;
import nl.mallepetrus.rptv.service.InviteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/invites")
@PreAuthorize("hasRole('ADMIN')")
public class InviteAdminController {
    private final InviteService inviteService;

    public InviteAdminController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping
    public InviteCode create(@RequestParam @Min(1) @Max(1000) int maxUses,
                             @RequestParam(required = false) String expiresAt,
                             @RequestParam(required = false) String code) {
        InviteCode ic = new InviteCode();
        ic.setMaxUses(maxUses);
        if (expiresAt != null && !expiresAt.isBlank()) {
            ic.setExpiresAt(OffsetDateTime.parse(expiresAt));
        }
        ic.setCode(code != null && !code.isBlank() ? code : UUID.randomUUID().toString().replace("-", ""));
        return inviteService.save(ic);
    }

    @GetMapping
    public List<InviteCode> list() {
        return inviteService.list();
    }

    @PostMapping("/revoke")
    public Map<String, String> revoke(@RequestParam @NotBlank String code) {
        inviteService.revoke(code);
        return Map.of("status", "revoked");
    }
}
