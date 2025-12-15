package nl.mallepetrus.rptv.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static nl.mallepetrus.rptv.security.KeyLoader.loadPrivateKey;
import static nl.mallepetrus.rptv.security.KeyLoader.loadPublicKey;

@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${security.jwt.public-key-path:}") String publicKeyPath
    ) {
        if (publicKeyPath == null || publicKeyPath.isBlank()) {
            // Decoder will not work without key; provide a clear message
            throw new IllegalStateException("Missing security.jwt.public-key-path for JWT validation");
        }
        try {
            RSAPublicKey publicKey = loadPublicKey(Path.of(publicKeyPath));
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA public key from path: " + publicKeyPath, e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${security.jwt.public-key-path:}") String publicKeyPath,
            @Value("${security.jwt.private-key-path:}") String privateKeyPath
    ) {
        if (publicKeyPath == null || publicKeyPath.isBlank() || privateKeyPath == null || privateKeyPath.isBlank()) {
            // Encoder is only needed for issuing tokens; if missing, throw to avoid partial configuration
            throw new IllegalStateException("Missing RSA key paths. Configure security.jwt.public-key-path and security.jwt.private-key-path");
        }
        try {
            RSAPublicKey publicKey = loadPublicKey(Path.of(publicKeyPath));
            RSAPrivateKey privateKey = loadPrivateKey(Path.of(privateKeyPath));
            JWK jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("rptv-rs256").build();
            JWKSource<SecurityContext> jwkSource = (jwkSelector, securityContext) -> jwkSelector.select(new JWKSet(jwk));
            return new NimbusJwtEncoder(jwkSource);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA key pair", e);
        }
    }
}
