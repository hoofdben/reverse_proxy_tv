package nl.mallepetrus.rptv.service;

import nl.mallepetrus.rptv.domain.User;
import nl.mallepetrus.rptv.domain.XtreamAccount;
import nl.mallepetrus.rptv.repository.UserRepository;
import nl.mallepetrus.rptv.repository.XtreamAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class XtreamAccountService {
    private final XtreamAccountRepository xtreamRepo;
    private final UserRepository userRepository;

    public XtreamAccountService(XtreamAccountRepository xtreamRepo, UserRepository userRepository) {
        this.xtreamRepo = xtreamRepo;
        this.userRepository = userRepository;
    }

    public List<XtreamAccount> listFor(User user) {
        return xtreamRepo.findAllByUser(user);
    }

    @Transactional
    public XtreamAccount create(User user, String name, String apiUrl, String usernameEnc, String passwordEnc) {
        XtreamAccount xa = new XtreamAccount();
        xa.setUser(user);
        xa.setName(name);
        xa.setApiUrl(apiUrl);
        xa.setUsernameEnc(usernameEnc);
        xa.setPasswordEnc(passwordEnc);
        return xtreamRepo.save(xa);
    }

    public XtreamAccount getOwned(User user, UUID id) {
        XtreamAccount xa = xtreamRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (!xa.getUser().getId().equals(user.getId())) throw new ResponseStatusException(NOT_FOUND);
        return xa;
    }

    @Transactional
    public XtreamAccount update(User user, UUID id, String name, String apiUrl, String usernameEnc, String passwordEnc) {
        XtreamAccount xa = getOwned(user, id);
        if (name != null) xa.setName(name);
        if (apiUrl != null) xa.setApiUrl(apiUrl);
        if (usernameEnc != null) xa.setUsernameEnc(usernameEnc);
        if (passwordEnc != null) xa.setPasswordEnc(passwordEnc);
        return xtreamRepo.save(xa);
    }

    @Transactional
    public void delete(User user, UUID id) {
        XtreamAccount xa = getOwned(user, id);
        xtreamRepo.delete(xa);
    }
}
