package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@WebAppConfiguration
@SpringBootTest
public class UserServiceIntegrationTest {

    @Qualifier("userRepository")
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
    }

    @Test
    public void createUser_validInputs_success() {
        assertNull(userRepository.findByUsername("testUsername"));

        User testUser = new User();
        testUser.setUsername("testUsername");
        testUser.setPassword("testPassword");

        User createdUser = userService.createUser(testUser);

        assertNotNull(createdUser.getId());
        assertEquals("testUsername", createdUser.getUsername());

        assertNotEquals("testPassword", createdUser.getPassword());
        assertNotNull(createdUser.getSalt());
        assertTrue(PasswordUtil.matches("testPassword", createdUser.getPassword(), createdUser.getSalt()));

        assertNotNull(createdUser.getToken());
        assertEquals(UserStatus.OFFLINE, createdUser.getStatus());
        assertNotNull(createdUser.getCreationDate());
        assertEquals("", createdUser.getBio());
        assertEquals(0, createdUser.getTotalGamesPlayed());
        assertEquals(0, createdUser.getTotalWins());
        assertEquals(0, createdUser.getTotalPoints());
    }

    @Test
    public void createUser_duplicateUsername_throwsException() {
        assertNull(userRepository.findByUsername("testUsername"));

        User testUser = new User();
        testUser.setUsername("testUsername");
        testUser.setPassword("testPassword");
        userService.createUser(testUser);

        User testUser2 = new User();
        testUser2.setUsername("testUsername");
        testUser2.setPassword("anotherPassword");

        assertThrows(ResponseStatusException.class, () -> userService.createUser(testUser2));
    }

    @Test
    public void getUserById_success() {
        User user = new User();
        user.setUsername("user1");
        user.setPassword("hashed-secret");
        user.setSalt("salt");
        user.setToken("token-1");
        user.setStatus(UserStatus.OFFLINE);
        user.setBio("bio");
        user.setCreationDate(Instant.now());
        user.setTotalGamesPlayed(0);
        user.setTotalWins(0);
        user.setTotalPoints(0);
        user.setTotalCorrectPlacements(0);
        user.setTotalIncorrectPlacements(0);

        User savedUser = userRepository.saveAndFlush(user);

        User foundUser = userService.getUserById(savedUser.getId());

        assertEquals(savedUser.getId(), foundUser.getId());
        assertEquals(savedUser.getUsername(), foundUser.getUsername());
    }

    @Test
    public void updateUserProfile_success() {
        User user = new User();
        user.setUsername("oldUsername");
        user.setPassword("hashed-secret");
        user.setSalt("salt");
        user.setToken("valid-token");
        user.setStatus(UserStatus.ONLINE);
        user.setBio("old bio");
        user.setCreationDate(Instant.now());
        user.setTotalGamesPlayed(0);
        user.setTotalWins(0);
        user.setTotalPoints(0);
        user.setTotalCorrectPlacements(0);
        user.setTotalIncorrectPlacements(0);

        User savedUser = userRepository.saveAndFlush(user);

        userService.updateUserProfile("valid-token", savedUser.getId(), "newUsername", "new bio");

        User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();

        assertEquals("newUsername", updatedUser.getUsername());
        assertEquals("new bio", updatedUser.getBio());
    }
}