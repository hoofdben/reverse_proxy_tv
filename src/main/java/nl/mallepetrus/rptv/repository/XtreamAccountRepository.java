package nl.mallepetrus.rptv.repository;

import nl.mallepetrus.rptv.domain.User;
import nl.mallepetrus.rptv.domain.XtreamAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface XtreamAccountRepository extends JpaRepository<XtreamAccount, UUID> {
    List<XtreamAccount> findAllByUser(User user);
}
