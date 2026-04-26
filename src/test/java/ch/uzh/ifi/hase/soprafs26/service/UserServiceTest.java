package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testUsername");
        testUser.setPassword("testPassword");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void createUser_validInputs_success() {
        when(userRepository.findByUsername("testUsername")).thenReturn(null);

        User createdUser = userService.createUser(testUser);

        verify(userRepository, times(1)).save(any(User.class));
        verify(userRepository, times(1)).flush();

        assertEquals("testUsername", createdUser.getUsername());
        assertNotEquals("testPassword", createdUser.getPassword()); // password must be hashed
        assertNotNull(createdUser.getSalt());
        assertNotNull(createdUser.getToken());
        assertEquals(UserStatus.ONLINE, createdUser.getStatus());
        assertNotNull(createdUser.getCreationDate());
        assertEquals("", createdUser.getBio());
        assertEquals(0, createdUser.getTotalGamesPlayed());
        assertEquals(0, createdUser.getTotalWins());
        assertEquals(0, createdUser.getTotalPoints());
    }

    @Test
    public void createUser_duplicateUsername_throwsException() {
        when(userRepository.findByUsername("testUsername")).thenReturn(testUser);

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> userService.createUser(testUser));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    public void getUserById_existingUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        User foundUser = userService.getUserById(1L);

        assertEquals(testUser.getId(), foundUser.getId());
        assertEquals(testUser.getUsername(), foundUser.getUsername());
    }

    @Test
    public void getUserById_userNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> userService.getUserById(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    public void updateUserProfile_validInput_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setToken("valid-token");
        requester.setUsername("oldUsername");
        requester.setPassword("pw");
        requester.setStatus(UserStatus.ONLINE);
        requester.setBio("old bio");
        requester.setCreationDate(Instant.now());

        when(userRepository.findByToken("valid-token")).thenReturn(requester);
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findByUsername("newUsername")).thenReturn(null);

        userService.updateUserProfile("valid-token", 1L, "newUsername", "new bio");

        assertEquals("newUsername", requester.getUsername());
        assertEquals("new bio", requester.getBio());
        verify(userRepository, times(1)).save(requester);
        verify(userRepository, times(1)).flush();
    }

    @Test
    public void updateUserProfile_invalidToken_throwsException() {
        when(userRepository.findByToken("invalid-token")).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.updateUserProfile("invalid-token", 1L, "newUsername", "new bio")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    public void updateUserProfile_otherUserProfile_throwsException() {
        User requester = new User();
        requester.setId(1L);
        requester.setToken("valid-token");

        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("other");
        otherUser.setPassword("pw");
        otherUser.setCreationDate(Instant.now());
        otherUser.setStatus(UserStatus.OFFLINE);
        otherUser.setBio("");

        when(userRepository.findByToken("valid-token")).thenReturn(requester);
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.updateUserProfile("valid-token", 2L, "newUsername", "new bio")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    public void updateUserProfile_duplicateUsername_throwsException() {
        User requester = new User();
        requester.setId(1L);
        requester.setToken("valid-token");
        requester.setUsername("oldUsername");
        requester.setPassword("pw");
        requester.setStatus(UserStatus.ONLINE);
        requester.setBio("bio");
        requester.setCreationDate(Instant.now());

        User existingUser = new User();
        existingUser.setId(2L);
        existingUser.setUsername("takenUsername");

        when(userRepository.findByToken("valid-token")).thenReturn(requester);
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findByUsername("takenUsername")).thenReturn(existingUser);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.updateUserProfile("valid-token", 1L, "takenUsername", "new bio")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void createUser_nullFields_defaultsApplied() {
        User newUser = new User();
        newUser.setUsername("newUser");
        newUser.setPassword("pw");
        newUser.setBio(null);              // → soll "" werden
        newUser.setTotalGamesPlayed(null); // → soll 0 werden
        newUser.setTotalWins(null);        // → soll 0 werden
        newUser.setTotalPoints(null);      // → soll 0 werden

        when(userRepository.findByUsername("newUser")).thenReturn(null);

        User result = userService.createUser(newUser);

        assertEquals("", result.getBio());
        assertEquals(0, result.getTotalGamesPlayed());
        assertEquals(0, result.getTotalWins());
        assertEquals(0, result.getTotalPoints());
    }

    @Test
    public void updateUserProfile_blankUsername_usernameNotUpdated() {
        User requester = new User();
        requester.setId(1L);
        requester.setToken("valid-token");
        requester.setUsername("oldUsername");
        requester.setPassword("pw");
        requester.setStatus(UserStatus.ONLINE);
        requester.setBio("bio");
        requester.setCreationDate(Instant.now());

        when(userRepository.findByToken("valid-token")).thenReturn(requester);
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));

        userService.updateUserProfile("valid-token", 1L, "  ", "new bio");

        // Username bleibt unverändert
        assertEquals("oldUsername", requester.getUsername());
        assertEquals("new bio", requester.getBio());
    }

    @Test
    public void updateUserProfile_nullUsername_usernameNotUpdated() {
        User requester = new User();
        requester.setId(1L);
        requester.setToken("valid-token");
        requester.setUsername("oldUsername");
        requester.setPassword("pw");
        requester.setStatus(UserStatus.ONLINE);
        requester.setBio("bio");
        requester.setCreationDate(Instant.now());

        when(userRepository.findByToken("valid-token")).thenReturn(requester);
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));

        userService.updateUserProfile("valid-token", 1L, null, "new bio");

        assertEquals("oldUsername", requester.getUsername());
        assertEquals("new bio", requester.getBio());
    }

    @Test
    public void getFriends_validUser_returnsFriendList() {
        User friend = new User();
        friend.setId(2L);
        friend.setUsername("bob");

        testUser.setFriends(new java.util.HashSet<>(java.util.List.of(friend)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        java.util.List<User> friends = userService.getFriends(1L);

        assertEquals(1, friends.size());
        assertTrue(friends.contains(friend));
    }

    @Test
    public void removeFriend_validInput_removesBothSides() {
        User friend = new User();
        friend.setId(2L);
        friend.setUsername("bob");
        friend.setFriends(new java.util.HashSet<>());

        testUser.setFriends(new java.util.HashSet<>(java.util.List.of(friend)));
        friend.getFriends().add(testUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));

        userService.removeFriend(1L, 2L);

        assertFalse(testUser.getFriends().contains(friend));
        assertFalse(friend.getFriends().contains(testUser));
        verify(userRepository, times(1)).save(testUser);
        verify(userRepository, times(1)).save(friend);
    }

    @Test
    public void removeFriend_notFriends_throwsNotFound() {
        User stranger = new User();
        stranger.setId(2L);
        stranger.setFriends(new java.util.HashSet<>());
        testUser.setFriends(new java.util.HashSet<>());

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(stranger));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.removeFriend(1L, 2L)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}