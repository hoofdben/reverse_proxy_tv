package nl.mallepetrus.rptv.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank String inviteCode
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record TokenPair(
            String accessToken,
            String refreshToken
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}
}
