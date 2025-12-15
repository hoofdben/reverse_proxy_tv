package nl.mallepetrus.rptv.domain;

import jakarta.persistence.*;
import nl.mallepetrus.rptv.persistence.EncryptedStringConverter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "xtream_accounts", indexes = {
        @Index(name = "ix_xtream_accounts_user", columnList = "user_id")
})
public class XtreamAccount {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "api_url", nullable = false, length = 512)
    private String apiUrl;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "username_enc", nullable = false, length = 2048)
    private String usernameEnc;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "password_enc", nullable = false, length = 2048)
    private String passwordEnc;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getUsernameEnc() { return usernameEnc; }
    public void setUsernameEnc(String usernameEnc) { this.usernameEnc = usernameEnc; }
    public String getPasswordEnc() { return passwordEnc; }
    public void setPasswordEnc(String passwordEnc) { this.passwordEnc = passwordEnc; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
