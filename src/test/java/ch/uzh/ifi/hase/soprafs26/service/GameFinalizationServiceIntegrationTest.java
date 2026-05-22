package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.*;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;



import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@WebAppConfiguration
@SpringBootTest
public class GameFinalizationServiceIntegrationTest {

    @Autowired private GameFinalizationService gameFinalizationService;
    @Autowired private GameRepository gameRepository;
    @Autowired private GamePlayerRepository gamePlayerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private GameInviteRepository gameInviteRepository;

    @MockitoBean
    private CardPreloader cardPreloader;

    private User user1;
    private User user2;
    private Game game;

    @BeforeEach
    void setup() {
        gamePlayerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();

        user1 = new User();
        user1.setUsername("finalize_user1");
        user1.setPassword("hashed");
        user1.setSalt("salt1");
        user1.setToken("token-finalize-1");
        user1.setStatus(UserStatus.IN_GAME);
        user1.setBio("");
        user1.setCreationDate(Instant.now());
        user1.setTotalGamesPlayed(0);
        user1.setTotalWins(0);
        user1.setTotalPoints(0);
        user1.setTotalCorrectPlacements(0);
        user1.setTotalIncorrectPlacements(0);
        user1 = userRepository.saveAndFlush(user1);

        user2 = new User();
        user2.setUsername("finalize_user2");
        user2.setPassword("hashed");
        user2.setSalt("salt2");
        user2.setToken("token-finalize-2");
        user2.setStatus(UserStatus.IN_GAME);
        user2.setBio("");
        user2.setCreationDate(Instant.now());
        user2.setTotalGamesPlayed(0);
        user2.setTotalWins(0);
        user2.setTotalPoints(0);
        user2.setTotalCorrectPlacements(0);
        user2.setTotalIncorrectPlacements(0);
        user2 = userRepository.saveAndFlush(user2);

        game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setEra(HistoricalEra.MODERN);
        game.setDifficulty(Difficulty.EASY);
        game.setLobbyCode("FINTEST1");
        game.setHostId(user1.getId());
        game.setDeckSize(20);
        game.setNextCardIndex(20);
        game = gameRepository.saveAndFlush(game);
    }

    @AfterEach
    void tearDown() {
        gamePlayerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();
    }

    private GamePlayer addPlayer(User user, int score, int correct, int incorrect,
                                 int bestStreak, int turnOrder) {
        GamePlayer gp = new GamePlayer();
        gp.setGame(game);
        gp.setUser(user);
        gp.setScore(score);
        gp.setCorrectPlacements(correct);
        gp.setIncorrectPlacements(incorrect);
        gp.setBestStreak(bestStreak);
        gp.setTurnOrder(turnOrder);
        gp.setCardsInHand(0);
        gp.setHandIndicesJson("[]");
        gp.setActiveTurn(false);
        gp.setCorrectStreak(0);
        return gamePlayerRepository.saveAndFlush(gp);
    }


    @Test
    void finalizeGame_setsGameStatusToFinished_persistedInDB() {
        addPlayer(user1, 300, 3, 1, 2, 0);
        addPlayer(user2, 200, 2, 2, 1, 1);

        gameFinalizationService.finalizeGame(game.getId());

        Game reloaded = gameRepository.findById(game.getId()).orElseThrow();
        assertEquals("FINISHED", reloaded.getStatus());
    }


    @Test
    void finalizeGame_updatesUserStatsInDB() {
        addPlayer(user1, 400, 5, 1, 3, 0);
        addPlayer(user2, 200, 2, 3, 1, 1);

        gameFinalizationService.finalizeGame(game.getId());

        User reloaded1 = userRepository.findById(user1.getId()).orElseThrow();
        assertEquals(1, reloaded1.getTotalGamesPlayed());
        assertEquals(400, reloaded1.getTotalPoints());
        assertEquals(5, reloaded1.getTotalCorrectPlacements());
        assertEquals(1, reloaded1.getTotalIncorrectPlacements());
        assertEquals(UserStatus.ONLINE, reloaded1.getStatus());

        User reloaded2 = userRepository.findById(user2.getId()).orElseThrow();
        assertEquals(1, reloaded2.getTotalGamesPlayed());
        assertEquals(200, reloaded2.getTotalPoints());
    }

    @Test
    void finalizeGame_winnerGetsWinInDB() {
        addPlayer(user1, 500, 5, 0, 4, 0); // winner
        addPlayer(user2, 100, 1, 3, 1, 1); // loser

        gameFinalizationService.finalizeGame(game.getId());

        User winner = userRepository.findById(user1.getId()).orElseThrow();
        User loser  = userRepository.findById(user2.getId()).orElseThrow();

        assertEquals(1, winner.getTotalWins());
        assertEquals(0, loser.getTotalWins());
    }

    @Test
    void finalizeGame_tiedPlayers_bothGetWinInDB() {
        addPlayer(user1, 300, 3, 1, 2, 0);
        addPlayer(user2, 300, 3, 1, 2, 1);

        gameFinalizationService.finalizeGame(game.getId());

        User u1 = userRepository.findById(user1.getId()).orElseThrow();
        User u2 = userRepository.findById(user2.getId()).orElseThrow();

        assertEquals(1, u1.getTotalWins());
        assertEquals(1, u2.getTotalWins());
    }

    @Test
    void finalizeGame_allUsersStatusSetToOnline_persistedInDB() {
        addPlayer(user1, 200, 2, 0, 2, 0);
        addPlayer(user2, 100, 1, 1, 1, 1);

        gameFinalizationService.finalizeGame(game.getId());

        assertEquals(UserStatus.ONLINE,
                userRepository.findById(user1.getId()).orElseThrow().getStatus());
        assertEquals(UserStatus.ONLINE,
                userRepository.findById(user2.getId()).orElseThrow().getStatus());
    }

    @Test
    void finalizeGame_accumulatesStatsAcrossMultipleGames() {
        addPlayer(user1, 200, 2, 1, 2, 0);
        addPlayer(user2, 100, 1, 2, 1, 1);

        gameFinalizationService.finalizeGame(game.getId());

        // Zweites Spiel
        Game game2 = new Game();
        game2.setStatus("IN_PROGRESS");
        game2.setGameMode(GameMode.TIMELINE);
        game2.setEra(HistoricalEra.MODERN);
        game2.setDifficulty(Difficulty.EASY);
        game2.setLobbyCode("FINTEST2");
        game2.setHostId(user1.getId());
        game2.setDeckSize(20);
        game2.setNextCardIndex(20);
        game2 = gameRepository.saveAndFlush(game2);

        GamePlayer gp3 = new GamePlayer();
        gp3.setGame(game2); gp3.setUser(user1); gp3.setScore(300);
        gp3.setCorrectPlacements(3); gp3.setIncorrectPlacements(0);
        gp3.setBestStreak(3); gp3.setTurnOrder(0); gp3.setCardsInHand(0);
        gp3.setHandIndicesJson("[]"); gp3.setActiveTurn(false); gp3.setCorrectStreak(0);
        gamePlayerRepository.saveAndFlush(gp3);

        GamePlayer gp4 = new GamePlayer();
        gp4.setGame(game2); gp4.setUser(user2); gp4.setScore(150);
        gp4.setCorrectPlacements(2); gp4.setIncorrectPlacements(1);
        gp4.setBestStreak(1); gp4.setTurnOrder(1); gp4.setCardsInHand(0);
        gp4.setHandIndicesJson("[]"); gp4.setActiveTurn(false); gp4.setCorrectStreak(0);
        gamePlayerRepository.saveAndFlush(gp4);

        gameFinalizationService.finalizeGame(game2.getId());

        User u1 = userRepository.findById(user1.getId()).orElseThrow();
        assertEquals(2, u1.getTotalGamesPlayed());
        assertEquals(500, u1.getTotalPoints());        // 200 + 300
        assertEquals(5, u1.getTotalCorrectPlacements()); // 2 + 3
    }


    @Test
    void finalizeGame_returnsCorrectDTOFields() {
        addPlayer(user1, 350, 4, 1, 5, 0);
        addPlayer(user2, 150, 2, 3, 2, 1);

        List<FinalResultDTO> results = gameFinalizationService.finalizeGame(game.getId());

        assertEquals(2, results.size());
        FinalResultDTO first = results.get(0);
        assertEquals(user1.getId(), first.getUserId());
        assertEquals(user1.getUsername(), first.getUsername());
        assertEquals(350, first.getScore());
        assertEquals(4, first.getCorrectPlacements());
        assertEquals(1, first.getIncorrectPlacements());
        assertEquals(5, first.getBestStreak());
        assertTrue(first.getWinner());
        assertFalse(results.get(1).getWinner());
    }

    @Test
    void finalizeGame_alreadyFinished_doesNotReincrementStats() {
        addPlayer(user1, 300, 3, 1, 2, 0);
        addPlayer(user2, 200, 2, 2, 1, 1);

        gameFinalizationService.finalizeGame(game.getId());

        User u1After1 = userRepository.findById(user1.getId()).orElseThrow();
        int pointsAfter1 = u1After1.getTotalPoints();
        int gamesAfter1  = u1After1.getTotalGamesPlayed();


        gameFinalizationService.finalizeGame(game.getId());

        User u1After2 = userRepository.findById(user1.getId()).orElseThrow();
        assertEquals(pointsAfter1, u1After2.getTotalPoints());
        assertEquals(gamesAfter1, u1After2.getTotalGamesPlayed());
    }


    @Test
    void finalizeGame_statusWaiting_throwsConflict() {
        game.setStatus("WAITING");
        gameRepository.saveAndFlush(game);

        assertThrows(ResponseStatusException.class,
                () -> gameFinalizationService.finalizeGame(game.getId()));
    }

    @Test
    void finalizeGame_noPlayers_throwsConflict() {
        assertThrows(ResponseStatusException.class,
                () -> gameFinalizationService.finalizeGame(game.getId()));
    }
}