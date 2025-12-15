package nl.mallepetrus.rptv.service;

import nl.mallepetrus.rptv.domain.User;
import nl.mallepetrus.rptv.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public User register(String email, String rawPassword, String rolesCsv) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles(rolesCsv);
        return userRepository.save(user);
    }

    public boolean matchesPassword(String raw, String hash) {
        return passwordEncoder.matches(raw, hash);
    }
}
