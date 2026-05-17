package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private String plainPassword;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        plainPassword = "secretPassword";

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("alex");
        testUser.setSalt(PasswordUtil.generateSalt());
        testUser.setPassword(PasswordUtil.hash(plainPassword, testUser.getSalt()));
        testUser.setToken("valid-token");
        testUser.setStatus(UserStatus.OFFLINE);
        testUser.setBio("");
    }

    @Test
    public void login_validCredentials_success() {
        when(userRepository.findByUsername("alex")).thenReturn(testUser);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User loggedInUser = authService.login("alex", plainPassword);

        assertNotNull(loggedInUser);
        assertEquals("alex", loggedInUser.getUsername());
        assertEquals(UserStatus.ONLINE, loggedInUser.getStatus());
        assertNotNull(loggedInUser.getLastSeenAt());

        verify(userRepository, times(1)).findByUsername("alex");
        verify(userRepository, times(1)).save(testUser);
        verify(userRepository, times(1)).flush();
    }

    @Test
    public void login_unknownUsername_throwsUnauthorized() {
        when(userRepository.findByUsername("unknown")).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login("unknown", "whatever")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(userRepository, times(1)).findByUsername("unknown");
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).flush();
    }

    @Test
    public void login_wrongPassword_throwsUnauthorized() {
        when(userRepository.findByUsername("alex")).thenReturn(testUser);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login("alex", "wrongPassword")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(userRepository, times(1)).findByUsername("alex");
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).flush();
    }

    @Test
    public void logout_validToken_success() {
        testUser.setStatus(UserStatus.ONLINE);

        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.logout("valid-token");

        assertEquals(UserStatus.OFFLINE, testUser.getStatus());
        verify(userRepository, times(1)).findByToken("valid-token");
        verify(userRepository, times(1)).save(testUser);
        verify(userRepository, times(1)).flush();
    }

    @Test
    public void logout_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("invalid-token")).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.logout("invalid-token")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(userRepository, times(1)).findByToken("invalid-token");
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).flush();
    }

    @Test
    public void heartbeat_validToken_updatesLastSeenAndSetsOnline() {
        testUser.setStatus(UserStatus.OFFLINE);
        testUser.setLastSeenAt(Instant.now().minusSeconds(120));

        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.heartbeat("valid-token");

        assertEquals(UserStatus.ONLINE, testUser.getStatus());
        assertNotNull(testUser.getLastSeenAt());
        assertTrue(testUser.getLastSeenAt().isAfter(Instant.now().minusSeconds(10)));
        verify(userRepository, times(1)).findByToken("valid-token");
        verify(userRepository, times(1)).save(testUser);
        verify(userRepository, times(1)).flush();
    }

    @Test
    public void heartbeat_inGameUser_preservesInGameStatus() {
        testUser.setStatus(UserStatus.IN_GAME);

        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.heartbeat("valid-token");

        assertEquals(UserStatus.IN_GAME, testUser.getStatus());
        assertNotNull(testUser.getLastSeenAt());
        verify(userRepository, times(1)).save(testUser);
        verify(userRepository, times(1)).flush();
    }

    @Test
    public void heartbeat_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("invalid-token")).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.heartbeat("invalid-token")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(userRepository, times(1)).findByToken("invalid-token");
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).flush();
    }

    @Test
    public void markStaleOnlineUsersOffline_staleUsers_setOffline() {
        User staleUser = new User();
        staleUser.setStatus(UserStatus.ONLINE);
        staleUser.setLastSeenAt(Instant.now().minusSeconds(120));

        when(userRepository.findStaleUsersByStatus(eq(UserStatus.ONLINE), any(Instant.class)))
                .thenReturn(List.of(staleUser));

        authService.markStaleOnlineUsersOffline();

        assertEquals(UserStatus.OFFLINE, staleUser.getStatus());
        verify(userRepository, times(1)).save(staleUser);
        verify(userRepository, times(1)).flush();
    }

    @Test
    public void markStaleOnlineUsersOffline_noStaleUsers_doesNothing() {
        when(userRepository.findStaleUsersByStatus(eq(UserStatus.ONLINE), any(Instant.class)))
                .thenReturn(List.of());

        authService.markStaleOnlineUsersOffline();

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).flush();
    }
}
