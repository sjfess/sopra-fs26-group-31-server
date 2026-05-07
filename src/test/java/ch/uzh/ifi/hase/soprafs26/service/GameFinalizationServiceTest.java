package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.*;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GameFinalizationServiceTest {

    @Mock private GameRepository gameRepository;
    @Mock private GamePlayerRepository gamePlayerRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private GameInviteRepository gameInviteRepository;
    @Mock private GameStartService gameStartService;

    private GameFinalizationService gameFinalizationService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        gameFinalizationService = new GameFinalizationService(
                gameRepository,
                gamePlayerRepository,
                userRepository,
                chatMessageRepository,
                gameInviteRepository,
                gameStartService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    /** helper-functions */

    private User makeUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setTotalGamesPlayed(0);
        user.setTotalPoints(0);
        user.setTotalCorrectPlacements(0);
        user.setTotalIncorrectPlacements(0);
        user.setTotalWins(0);
        return user;
    }

    private GamePlayer makePlayer(User user, int score, int correct, int incorrect, int bestStreak) {
        GamePlayer gp = new GamePlayer();
        gp.setUser(user);
        gp.setScore(score);
        gp.setCorrectPlacements(correct);
        gp.setIncorrectPlacements(incorrect);
        gp.setBestStreak(bestStreak);
        return gp;
    }

    /** finished game result return test */

    @Test
    void finalizeGame_alreadyFinished_returnsExistingResults() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("FINISHED");
        game.setGameMode(GameMode.TIMELINE);

        User user = makeUser(1L, "alice");
        GamePlayer gp = makePlayer(user, 300, 3, 1, 2);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp));

        List<FinalResultDTO> result = gameFinalizationService.finalizeGame(1L);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).getUsername());
        assertEquals(300, result.get(0).getScore());
        // must NOT touch the game status or repositories again
        verify(gameRepository, never()).save(any());
    }

    /** wrong status test */

    @Test
    void finalizeGame_statusWaiting_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);

        assertThrows(ResponseStatusException.class,
                () -> gameFinalizationService.finalizeGame(1L));
    }

    /** no players test */

    @Test
    void finalizeGame_noPlayers_throwsConflict() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of());

        assertThrows(ResponseStatusException.class,
                () -> gameFinalizationService.finalizeGame(1L));
    }

    /** user-stats update tests */

    @Test
    void finalizeGame_timelineMode_updatesGlobalStats() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        User user = makeUser(1L, "alice");
        GamePlayer gp = makePlayer(user, 200, 4, 2, 3);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp));

        gameFinalizationService.finalizeGame(1L);

        assertEquals(1, user.getTotalGamesPlayed());
        assertEquals(200, user.getTotalPoints());
        assertEquals(4, user.getTotalCorrectPlacements());
        assertEquals(2, user.getTotalIncorrectPlacements());
        assertEquals(UserStatus.ONLINE, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void finalizeGame_timelineMode_winnerGetsWinCounted() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        User winner = makeUser(1L, "alice");
        User loser  = makeUser(2L, "bob");
        GamePlayer gpWinner = makePlayer(winner, 500, 5, 0, 4);
        GamePlayer gpLoser  = makePlayer(loser,  100, 1, 3, 1);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gpWinner, gpLoser));

        List<FinalResultDTO> results = gameFinalizationService.finalizeGame(1L);

        // winner: highest score → wins++
        assertEquals(1, winner.getTotalWins());
        assertEquals(0, loser.getTotalWins());

        assertTrue(results.get(0).getWinner());
        assertFalse(results.get(1).getWinner());
    }

    @Test
    void finalizeGame_timelineMode_tiedPlayersAreBothWinners() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");
        GamePlayer gp1 = makePlayer(u1, 300, 3, 1, 2);
        GamePlayer gp2 = makePlayer(u2, 300, 3, 1, 2);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp1, gp2));

        List<FinalResultDTO> results = gameFinalizationService.finalizeGame(1L);

        assertTrue(results.get(0).getWinner());
        assertTrue(results.get(1).getWinner());
        assertEquals(1, u1.getTotalWins());
        assertEquals(1, u2.getTotalWins());
    }

    @Test
    void finalizeGame_timelineMode_nullStatsDefaultToZero() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        // all stats are null — nullToZero must handle this gracefully
        user.setTotalGamesPlayed(null);
        user.setTotalPoints(null);
        user.setTotalCorrectPlacements(null);
        user.setTotalIncorrectPlacements(null);
        user.setTotalWins(null);

        GamePlayer gp = new GamePlayer();
        gp.setUser(user);
        gp.setScore(null);
        gp.setCorrectPlacements(null);
        gp.setIncorrectPlacements(null);
        gp.setBestStreak(null);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp));

        assertDoesNotThrow(() -> gameFinalizationService.finalizeGame(1L));
        assertEquals(1, user.getTotalGamesPlayed());
        assertEquals(0, user.getTotalPoints());
    }

    /** side-effects tests */

    @Test
    void finalizeGame_setsGameStatusToFinished() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        User user = makeUser(1L, "alice");
        GamePlayer gp = makePlayer(user, 100, 1, 0, 1);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp));

        gameFinalizationService.finalizeGame(1L);

        assertEquals("FINISHED", game.getStatus());
        verify(gameRepository).save(game);
    }

    @Test
    void finalizeGame_cleansUpChatAndInvites() {
        Game game = new Game();
        game.setId(42L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        User user = makeUser(1L, "alice");
        GamePlayer gp = makePlayer(user, 100, 1, 0, 1);

        when(gameStartService.findGameOrThrow(42L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp));

        gameFinalizationService.finalizeGame(42L);

        verify(chatMessageRepository).deleteAllByGameId(42L);
        verify(gameInviteRepository).deleteAllByGameId(42L);
    }

    @Test
    void finalizeGame_setsAllUsersStatusToOnline() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");
        GamePlayer gp1 = makePlayer(u1, 200, 2, 0, 2);
        GamePlayer gp2 = makePlayer(u2, 100, 1, 1, 1);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp1, gp2));

        gameFinalizationService.finalizeGame(1L);

        assertEquals(UserStatus.ONLINE, u1.getStatus());
        assertEquals(UserStatus.ONLINE, u2.getStatus());
    }

    /** dto-fields test */

    @Test
    void finalizeGame_resultDTO_containsCorrectFields() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        User user = makeUser(7L, "charlie");
        GamePlayer gp = makePlayer(user, 350, 4, 1, 5);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp));

        List<FinalResultDTO> results = gameFinalizationService.finalizeGame(1L);

        FinalResultDTO dto = results.get(0);
        assertEquals(7L, dto.getUserId());
        assertEquals("charlie", dto.getUsername());
        assertEquals(350, dto.getScore());
        assertEquals(4, dto.getCorrectPlacements());
        assertEquals(1, dto.getIncorrectPlacements());
        assertEquals(5, dto.getBestStreak());
        assertTrue(dto.getWinner()); // only player = highest score
    }
}