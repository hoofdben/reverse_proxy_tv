package nl.mallepetrus.rptv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProps.JwtProps.class})
public class AppProps {

    @ConfigurationProperties(prefix = "security.jwt")
    public static class JwtProps {
        private String issuer = "rptv";
        private long accessTokenTtl = 900; // seconds
        private long refreshTokenTtl = 2_592_000; // 30 days

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public long getAccessTokenTtl() { return accessTokenTtl; }
        public void setAccessTokenTtl(long accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
        public long getRefreshTokenTtl() { return refreshTokenTtl; }
        public void setRefreshTokenTtl(long refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    }
}
