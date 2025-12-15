package nl.mallepetrus.rptv.testutil;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest extends PostgresTestContainer {

    private static Path publicKeyPem;
    private static Path privateKeyPem;

    @BeforeAll
    static void genKeys() throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        RSAPrivateCrtKey priv = (RSAPrivateCrtKey) kp.getPrivate();

        publicKeyPem = Files.createTempFile("jwt-pub", ".pem");
        privateKeyPem = Files.createTempFile("jwt-priv", ".pem");

        writePem(publicKeyPem, "PUBLIC KEY", kp.getPublic().getEncoded());
        writePem(privateKeyPem, "PRIVATE KEY", kp.getPrivate().getEncoded());
    }

    private static void writePem(Path path, String type, byte[] der) throws IOException {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        String content = "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
        Files.writeString(path, content);
    }

    @DynamicPropertySource
    static void testProps(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key-path", () -> publicKeyPem.toAbsolutePath().toString());
        registry.add("security.jwt.private-key-path", () -> privateKeyPem.toAbsolutePath().toString());
        // 32 bytes zero key for tests (Base64)
        registry.add("app.enc.master-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("security.jwt.issuer", () -> "rptv-test");
        registry.add("security.jwt.access-token-ttl", () -> "300");
        registry.add("security.jwt.refresh-token-ttl", () -> "3600");
    }
}
