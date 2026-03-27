package ch.uzh.ifi.hase.soprafs26.repository;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void findByUsername_success() {
        User user = new User();
        user.setUsername("firstname@lastname");
        user.setPassword("secret");
        user.setStatus(UserStatus.OFFLINE);
        user.setToken("token-1");
        user.setBio("bio");
        user.setCreationDate(Instant.now());
        user.setTotalGamesPlayed(0);
        user.setTotalWins(0);
        user.setTotalPoints(0);
        user.setTotalCorrectPlacements(0);
        user.setTotalIncorrectPlacements(0);

        userRepository.saveAndFlush(user);

        User found = userRepository.findByUsername(user.getUsername());

        assertNotNull(found);
        assertNotNull(found.getId());
        assertEquals(user.getUsername(), found.getUsername());
        assertEquals(user.getPassword(), found.getPassword());
        assertEquals(user.getToken(), found.getToken());
        assertEquals(user.getStatus(), found.getStatus());
        assertEquals(user.getBio(), found.getBio());
    }

    @Test
    public void findByToken_success() {
        User user = new User();
        user.setUsername("user2");
        user.setPassword("secret");
        user.setStatus(UserStatus.ONLINE);
        user.setToken("token-2");
        user.setBio("bio2");
        user.setCreationDate(Instant.now());
        user.setTotalGamesPlayed(1);
        user.setTotalWins(1);
        user.setTotalPoints(10);
        user.setTotalCorrectPlacements(0);
        user.setTotalIncorrectPlacements(0);

        userRepository.saveAndFlush(user);

        User found = userRepository.findByToken("token-2");

        assertNotNull(found);
        assertEquals(user.getUsername(), found.getUsername());
        assertEquals(user.getToken(), found.getToken());
    }
}