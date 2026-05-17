package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.*;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GameLobbyServiceTest {

    @Mock private GamePlayerRepository gamePlayerRepository;
    @Mock private GameRepository gameRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private GameInviteRepository gameInviteRepository;
    @Mock private GameFinalizationService gameFinalizationService;
    @Mock private GameStartService gameStartService;
    @Mock private TimelineGameService timelineGameService;

    private GameLobbyService gameLobbyService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        gameLobbyService = new GameLobbyService(
                gamePlayerRepository,
                gameRepository,
                userRepository,
                chatMessageRepository,
                gameInviteRepository,
                gameFinalizationService,
                gameStartService,
                timelineGameService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    /** game creation tests */

    @Test
    void createGame_nullEra_throwsBadRequest() {
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.createGame(null, Difficulty.EASY, 1L));
    }

    @Test
    void createGame_nullDifficulty_throwsBadRequest() {
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.createGame(HistoricalEra.MODERN, null, 1L));
    }

    @Test
    void createGame_userNotFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, 1L));
    }

    @Test
    void createGame_happyPath_savesGameAndPlayer() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Game saved = new Game();
        saved.setId(10L);
        saved.setLobbyCode("ABC123");
        // stub findByLobbyCode so the unique-code generator finds no collision
        when(gameRepository.findByLobbyCode(anyString())).thenReturn(Optional.empty());
        when(gameRepository.save(any(Game.class))).thenReturn(saved);

        Game result = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, 1L);

        assertNotNull(result);
        verify(gameRepository).save(any(Game.class));
        verify(gamePlayerRepository).save(any(GamePlayer.class));
        assertEquals(UserStatus.IN_GAME, user.getStatus());
    }

    /** game joining tests */

    @Test
    void joinGame_gameNotFound_throwsNotFound() {
        when(gameRepository.findByLobbyCode("XYZ")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.joinGame("XYZ", 1L));
    }

    @Test
    void joinGame_notWaiting_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.joinGame("ABC", 1L));
    }

    @Test
    void joinGame_lobbyFull_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        // 8 existing players
        List<GamePlayer> players = List.of(
                new GamePlayer(), new GamePlayer(), new GamePlayer(), new GamePlayer(),
                new GamePlayer(), new GamePlayer(), new GamePlayer(), new GamePlayer()
        );
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(players);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.joinGame("ABC", 1L));
    }

    @Test
    void joinGame_userNotFound_throwsNotFound() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.joinGame("ABC", 1L));
    }

    @Test
    void joinGame_alreadyInGame_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        User user = new User();
        user.setId(1L);
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.existsByGameAndUser(game, user)).thenReturn(true);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.joinGame("ABC", 1L));
    }

    @Test
    void joinGame_happyPath_savesPlayer() {
        Game game = new Game();
        game.setStatus("WAITING");
        User user = new User();
        user.setId(1L);
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.existsByGameAndUser(game, user)).thenReturn(false);

        Game result = gameLobbyService.joinGame("ABC", 1L);

        assertSame(game, result);
        verify(gamePlayerRepository).save(any(GamePlayer.class));
        assertEquals(UserStatus.IN_GAME, user.getStatus());
    }

    /** create rematch tests */

    @Test
    void createRematch_gameNotFinished_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.createRematch(1L, 2L));
    }

    @Test
    void createRematch_requesterNotPlayer_throwsForbidden() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("FINISHED");
        User otherUser = new User();
        otherUser.setId(99L);
        GamePlayer gp = new GamePlayer();
        gp.setUser(otherUser);
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp));
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.createRematch(1L, 2L));
    }

    @Test
    void createRematch_existingRematch_returnsExisting() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("FINISHED");
        User user = new User();
        user.setId(2L);
        GamePlayer gp = new GamePlayer();
        gp.setUser(user);
        Game existingRematch = new Game();
        existingRematch.setId(99L);
        when(gameStartService.findGameOrThrow(1L)).thenReturn(oldGame);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame)).thenReturn(List.of(gp));
        when(gameRepository.findByRematchFromGameIdAndStatus(1L, "WAITING"))
                .thenReturn(Optional.of(existingRematch));

        Game result = gameLobbyService.createRematch(1L, 2L);

        assertSame(existingRematch, result);
        verify(gameRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRematch_happyPath_createsNewGame() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("FINISHED");
        oldGame.setEra(HistoricalEra.MODERN);
        oldGame.setDifficulty(Difficulty.EASY);
        oldGame.setGameMode(GameMode.TIMELINE);
        User user = new User();
        user.setId(2L);
        GamePlayer gp = new GamePlayer();
        gp.setUser(user);
        Game newGame = new Game();
        newGame.setId(50L);
        when(gameStartService.findGameOrThrow(1L)).thenReturn(oldGame);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame)).thenReturn(List.of(gp));
        when(gameRepository.findByRematchFromGameIdAndStatus(1L, "WAITING")).thenReturn(Optional.empty());
        when(gameRepository.saveAndFlush(any(Game.class))).thenReturn(newGame);
        when(gameRepository.findByLobbyCode(anyString())).thenReturn(Optional.empty());

        Game result = gameLobbyService.createRematch(1L, 2L);

        assertSame(newGame, result);
        verify(gameRepository).saveAndFlush(any(Game.class));
    }

    /** create rematch + close old game tests */

    @Test
    void createRematchAndCloseOldGame_notFinished_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.createRematchAndCloseOldGame(1L, 2L));
    }

    @Test
    void createRematchAndCloseOldGame_notHost_throwsForbidden() {
        Game game = new Game();
        game.setStatus("FINISHED");
        game.setHostId(99L);
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.createRematchAndCloseOldGame(1L, 2L));
    }

    /** close finished game tests */

    @Test
    void closeFinishedGame_notFinished_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.closeFinishedGame(1L, 2L));
    }

    @Test
    void closeFinishedGame_notHost_throwsForbidden() {
        Game game = new Game();
        game.setStatus("FINISHED");
        game.setHostId(99L);
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.closeFinishedGame(1L, 2L));
    }

    @Test
    void closeFinishedGame_happyPath_deletesGame() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("FINISHED");
        game.setHostId(2L);
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);

        gameLobbyService.closeFinishedGame(1L, 2L);

        verify(gameRepository).delete(game);
    }

    /** game leaving tests */

    @Test
    void leaveGame_gameNotFound_throwsNotFound() {
        when(gameRepository.findByLobbyCode("XYZ")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.leaveGame("XYZ", 1L));
    }

    @Test
    void leaveGame_invalidStatus_throwsConflict() {
        Game game = new Game();
        game.setStatus("FINISHED");
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.leaveGame("ABC", 1L));
    }

    @Test
    void leaveGame_userNotInGame_throwsNotFound() {
        Game game = new Game();
        game.setStatus("WAITING");
        User user = new User();
        user.setId(1L);
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.existsByGameAndUser(game, user)).thenReturn(false);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.leaveGame("ABC", 1L));
    }

    @Test
    void leaveGame_lastPlayer_deletesGame() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("WAITING");
        game.setHostId(1L);
        User user = new User();
        user.setId(1L);
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.existsByGameAndUser(game, user)).thenReturn(true);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of());

        gameLobbyService.leaveGame("ABC", 1L);

        verify(gameRepository).delete(game);
    }

    @Test
    void leaveGame_hostLeaves_reassignsHost() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("WAITING");
        game.setHostId(1L);
        User leavingUser = new User();
        leavingUser.setId(1L);
        User remainingUser = new User();
        remainingUser.setId(2L);
        GamePlayer remaining = new GamePlayer();
        remaining.setUser(remainingUser);
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        when(userRepository.findById(1L)).thenReturn(Optional.of(leavingUser));
        when(gamePlayerRepository.existsByGameAndUser(game, leavingUser)).thenReturn(true);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(remaining));

        gameLobbyService.leaveGame("ABC", 1L);

        assertEquals(2L, game.getHostId());
        verify(gameRepository).save(game);
    }

    /** update game settings tests */

    @Test
    void updateSettings_notWaiting_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.updateSettings(1L, new GameSettingsPutDTO()));
    }

    @Test
    void updateSettings_happyPath_updatesFields() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gameRepository.save(game)).thenReturn(game);

        GameSettingsPutDTO dto = new GameSettingsPutDTO();
        dto.setDifficulty(Difficulty.HARD);
        dto.setEra(HistoricalEra.ANCIENT);

        Game result = gameLobbyService.updateSettings(1L, dto);

        assertEquals(Difficulty.HARD, result.getDifficulty());
        assertEquals(HistoricalEra.ANCIENT, result.getEra());
    }

    /** getGame-tests */

    @Test
    void getGame_notFound_throwsNotFound() {
        when(gameRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.getGame(99L));
    }

    @Test
    void getGame_found_returnsGame() {
        Game game = new Game();
        game.setId(1L);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        assertSame(game, gameLobbyService.getGame(1L));
    }

    /** find rematch id-tests */

    @Test
    void findWaitingRematchId_present_returnsId() {
        Game rematch = new Game();
        rematch.setId(42L);
        when(gameRepository.findByRematchFromGameIdAndStatus(1L, "WAITING"))
                .thenReturn(Optional.of(rematch));
        Optional<Long> result = gameLobbyService.findWaitingRematchId(1L);
        assertTrue(result.isPresent());
        assertEquals(42L, result.get());
    }

    @Test
    void findWaitingRematchId_absent_returnsEmpty() {
        when(gameRepository.findByRematchFromGameIdAndStatus(1L, "WAITING"))
                .thenReturn(Optional.empty());
        assertTrue(gameLobbyService.findWaitingRematchId(1L).isEmpty());
    }

    /** turn-timeout tests */

    @Test
    void checkTurnTimeouts_noActivePlayers_doesNothing() {
        when(gamePlayerRepository.findByActiveTurnTrue()).thenReturn(List.of());
        assertDoesNotThrow(() -> gameLobbyService.checkTurnTimeouts());
    }

    @Test
    void checkTurnTimeouts_playerWithNullTurnStart_skips() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        User user = new User();
        user.setId(1L);
        GamePlayer player = new GamePlayer();
        player.setGame(game);
        player.setUser(user);
        player.setTurnStartedAt(null);
        when(gamePlayerRepository.findByActiveTurnTrue()).thenReturn(List.of(player));
        assertDoesNotThrow(() -> gameLobbyService.checkTurnTimeouts());
        verify(timelineGameService, never()).advanceTurn(any(), any());
    }

    @Test
    void checkTurnTimeouts_timedOutPlayer_advancesTurn() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        GamePlayer player = new GamePlayer();
        player.setGame(game);
        player.setUser(user);
        player.setTurnStartedAt(Instant.now().minusSeconds(60)); // 60s elapsed, limit is 30s
        when(gamePlayerRepository.findByActiveTurnTrue()).thenReturn(List.of(player));
        when(timelineGameService.isTimelineGameFinished(game)).thenReturn(false);

        gameLobbyService.checkTurnTimeouts();

        verify(timelineGameService).advanceTurn(game, player);
    }

    @Test
    void checkTurnTimeouts_timedOutPlayer_gameFinished_finalizesGame() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        User user = new User(); user.setUsername("alice");
        GamePlayer player = new GamePlayer();
        player.setGame(game);
        player.setUser(user);
        player.setActiveTurn(true);
        player.setCorrectStreak(2);
        player.setTurnStartedAt(Instant.now().minusSeconds(60));
        when(gamePlayerRepository.findByActiveTurnTrue()).thenReturn(List.of(player));
        when(timelineGameService.isTimelineGameFinished(game)).thenReturn(true);

        gameLobbyService.checkTurnTimeouts();

        assertFalse(player.getActiveTurn());
        assertEquals(0, player.getCorrectStreak());
        verify(gameFinalizationService).finalizeGame(1L);
        verify(timelineGameService, never()).advanceTurn(any(), any());
    }

    @Test
    void leaveGame_inProgress_lastPlayerLeaves_finalizesGame() {
        Game game = new Game(); game.setId(1L); game.setStatus("IN_PROGRESS"); game.setHostId(1L);
        User user = new User(); user.setId(1L);
        GamePlayer leavingPlayer = new GamePlayer();
        leavingPlayer.setId(1L); leavingPlayer.setUser(user);
        leavingPlayer.setActiveTurn(false); leavingPlayer.setTurnOrder(0);
        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.existsByGameAndUser(game, user)).thenReturn(true);
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.of(leavingPlayer));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(leavingPlayer));

        gameLobbyService.leaveGame("ABC", 1L);

        verify(gameRepository).delete(game);
    }

    @Test
    void leaveGame_inProgress_activeTurnPlayerLeaves_nextPlayerActivated() {
        Game game = new Game(); game.setId(1L); game.setStatus("IN_PROGRESS"); game.setHostId(99L);
        User leavingUser = new User(); leavingUser.setId(1L);
        User otherUser = new User(); otherUser.setId(2L);
        GamePlayer leavingPlayer = new GamePlayer();
        leavingPlayer.setId(1L); leavingPlayer.setUser(leavingUser);
        leavingPlayer.setActiveTurn(true); leavingPlayer.setTurnOrder(0);
        GamePlayer otherPlayer = new GamePlayer();
        otherPlayer.setId(2L); otherPlayer.setUser(otherUser);
        otherPlayer.setActiveTurn(false); otherPlayer.setTurnOrder(1);

        User thirdUser = new User(); thirdUser.setId(3L);
        GamePlayer thirdPlayer = new GamePlayer();
        thirdPlayer.setId(3L); thirdPlayer.setUser(thirdUser);
        thirdPlayer.setActiveTurn(false); thirdPlayer.setTurnOrder(2);


        when(gameRepository.findByLobbyCode("ABC")).thenReturn(Optional.of(game));
        when(userRepository.findById(1L)).thenReturn(Optional.of(leavingUser));
        when(gamePlayerRepository.existsByGameAndUser(game, leavingUser)).thenReturn(true);
        when(gamePlayerRepository.findByGameAndUser(game, leavingUser)).thenReturn(Optional.of(leavingPlayer));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(leavingPlayer, otherPlayer, thirdPlayer));

        gameLobbyService.leaveGame("ABC", 1L);

        assertTrue(otherPlayer.getActiveTurn());
        assertNotNull(otherPlayer.getTurnStartedAt());
    }
}