package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GameInvite;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GameInviteRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameInviteGetDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GameInviteServiceTest {

    @Mock private GameInviteRepository gameInviteRepository;
    @Mock private UserRepository userRepository;
    @Mock private GameStartService gameStartService;

    private GameInviteService gameInviteService;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        gameInviteService = new GameInviteService(
                gameInviteRepository,
                userRepository,
                gameStartService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    /** helper-functions */

    private User makeUser(Long id, String username, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(status);
        return user;
    }

    private GameInvite makeInvite(Long id, Long gameId, String lobbyCode,
                                  Long fromId, String fromUsername, Long toId) {
        GameInvite invite = new GameInvite();
        invite.setId(id);
        invite.setGameId(gameId);
        invite.setLobbyCode(lobbyCode);
        invite.setFromUserId(fromId);
        invite.setFromUsername(fromUsername);
        invite.setToUserId(toId);
        return invite;
    }

    /** getInvitesForUser-tests */

    @Test
    void getInvitesForUser_noInvites_returnsEmptyList() {
        when(gameInviteRepository.findAllByToUserId(1L)).thenReturn(List.of());

        List<GameInviteGetDTO> result = gameInviteService.getInvitesForUser(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getInvitesForUser_oneInvite_returnsMappedDTO() {
        GameInvite invite = makeInvite(10L, 1L, "ABC123", 2L, "alice", 3L);
        when(gameInviteRepository.findAllByToUserId(3L)).thenReturn(List.of(invite));

        List<GameInviteGetDTO> result = gameInviteService.getInvitesForUser(3L);

        assertEquals(1, result.size());
        GameInviteGetDTO dto = result.get(0);
        assertEquals(10L, dto.getId());
        assertEquals(1L, dto.getGameId());
        assertEquals("ABC123", dto.getLobbyCode());
        assertEquals("alice", dto.getFromUsername());
    }

    @Test
    void getInvitesForUser_multipleInvites_allMapped() {
        GameInvite i1 = makeInvite(1L, 10L, "AAA", 2L, "alice", 5L);
        GameInvite i2 = makeInvite(2L, 20L, "BBB", 3L, "bob",   5L);
        when(gameInviteRepository.findAllByToUserId(5L)).thenReturn(List.of(i1, i2));

        List<GameInviteGetDTO> result = gameInviteService.getInvitesForUser(5L);

        assertEquals(2, result.size());
        assertEquals("AAA", result.get(0).getLobbyCode());
        assertEquals("BBB", result.get(1).getLobbyCode());
    }

    /** invitePlayer: game not found tests */

    @Test
    void invitePlayer_gameNotFound_throwsNotFound() {
        when(gameStartService.findGameOrThrow(99L))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));

        assertThrows(ResponseStatusException.class,
                () -> gameInviteService.invitePlayer(99L, 1L, "bob"));
    }

    /** invitePlayer: player not found tests */

    @Test
    void invitePlayer_fromUserNotFound_throwsNotFound() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> gameInviteService.invitePlayer(1L, 99L, "bob"));
    }

    @Test
    void invitePlayer_toUserNotFound_throwsNotFound() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC");
        User fromUser = makeUser(1L, "alice", UserStatus.ONLINE);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(1L)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByUsername("ghost")).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> gameInviteService.invitePlayer(1L, 1L, "ghost"));
    }

    /** invitePlayer: user already in-game tests */

    @Test
    void invitePlayer_toUserInGame_throwsConflict() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC");
        User fromUser = makeUser(1L, "alice", UserStatus.ONLINE);
        User toUser   = makeUser(2L, "bob",   UserStatus.IN_GAME);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(1L)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByUsername("bob")).thenReturn(toUser);

        assertThrows(ResponseStatusException.class,
                () -> gameInviteService.invitePlayer(1L, 1L, "bob"));
    }

    /** self-invite tests */

    @Test
    void invitePlayer_selfInvite_throwsBadRequest() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC");
        User fromUser = makeUser(1L, "alice", UserStatus.ONLINE);
        User toUser   = makeUser(2L, "ALICE", UserStatus.ONLINE); // equalsIgnoreCase

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(1L)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByUsername("ALICE")).thenReturn(toUser);

        assertThrows(ResponseStatusException.class,
                () -> gameInviteService.invitePlayer(1L, 1L, "ALICE"));
    }

    /** duplicate invite */

    @Test
    void invitePlayer_duplicateInvite_throwsConflict() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC");
        User fromUser = makeUser(1L, "alice", UserStatus.ONLINE);
        User toUser   = makeUser(2L, "bob",   UserStatus.ONLINE);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(1L)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByUsername("bob")).thenReturn(toUser);
        when(gameInviteRepository.existsByGameIdAndToUserId(1L, 2L)).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> gameInviteService.invitePlayer(1L, 1L, "bob"));
    }

    /** invitePlayer work tests */

    @Test
    void invitePlayer_happyPath_savesAndReturnsInvite() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("XYZ789");
        User fromUser = makeUser(1L, "alice", UserStatus.ONLINE);
        User toUser   = makeUser(2L, "bob",   UserStatus.ONLINE);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(1L)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByUsername("bob")).thenReturn(toUser);
        when(gameInviteRepository.existsByGameIdAndToUserId(1L, 2L)).thenReturn(false);

        GameInvite result = gameInviteService.invitePlayer(1L, 1L, "bob");

        verify(gameInviteRepository).save(any(GameInvite.class));
        assertEquals(1L, result.getGameId());
        assertEquals("XYZ789", result.getLobbyCode());
        assertEquals(1L, result.getFromUserId());
        assertEquals("alice", result.getFromUsername());
        assertEquals(2L, result.getToUserId());
    }

    @Test
    void invitePlayer_happyPath_lobbyCodeCopiedFromGame() {
        Game game = new Game();
        game.setId(5L);
        game.setLobbyCode("LOBBY1");
        User fromUser = makeUser(10L, "carol", UserStatus.ONLINE);
        User toUser   = makeUser(20L, "dave",  UserStatus.ONLINE);

        when(gameStartService.findGameOrThrow(5L)).thenReturn(game);
        when(userRepository.findById(10L)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByUsername("dave")).thenReturn(toUser);
        when(gameInviteRepository.existsByGameIdAndToUserId(5L, 20L)).thenReturn(false);

        GameInvite result = gameInviteService.invitePlayer(5L, 10L, "dave");

        assertEquals("LOBBY1", result.getLobbyCode());
    }

    /** delete invite tests */

    @Test
    void deleteInvite_callsRepositoryDeleteById() {
        gameInviteService.deleteInvite(42L);
        verify(gameInviteRepository).deleteById(42L);
    }

    @Test
    void deleteInvite_nonExistentId_doesNotThrow() {
        doNothing().when(gameInviteRepository).deleteById(999L);
        assertDoesNotThrow(() -> gameInviteService.deleteInvite(999L));
    }
}