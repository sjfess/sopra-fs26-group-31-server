package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class AuthService {

    private static final Duration PRESENCE_TIMEOUT = Duration.ofSeconds(15);

    private final UserRepository userRepository;

    public AuthService(@Qualifier("userRepository") UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username);

        if (user == null || !PasswordUtil.matches(password, user.getPassword(), user.getSalt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        user.setStatus(UserStatus.ONLINE);
        user.setLastSeenAt(Instant.now());
        userRepository.save(user);
        userRepository.flush();

        return user;
    }

    public void logout(String token) {
        User user = userRepository.findByToken(token);

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        user.setStatus(UserStatus.OFFLINE);
        userRepository.save(user);
        userRepository.flush();
    }

    public void heartbeat(String token) {
        User user = userRepository.findByToken(token);

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        user.setLastSeenAt(Instant.now());
        if (user.getStatus() == UserStatus.OFFLINE) {
            user.setStatus(UserStatus.ONLINE);
        }

        userRepository.save(user);
        userRepository.flush();
    }

    @Scheduled(fixedDelay = 30000)
    public void markStaleOnlineUsersOffline() {
        Instant cutoff = Instant.now().minus(PRESENCE_TIMEOUT);
        List<User> staleUsers = userRepository.findStaleUsersByStatus(UserStatus.ONLINE, cutoff);

        for (User user : staleUsers) {
            user.setStatus(UserStatus.OFFLINE);
            userRepository.save(user);
        }

        if (!staleUsers.isEmpty()) {
            userRepository.flush();
        }
    }
}
