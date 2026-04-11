package ch.uzh.ifi.hase.soprafs26.repository;
import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class GamePlayerRepositoryIntegrationTest {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GamePlayerRepository gamePlayerRepository;

    @Test
    public void findByGameAndActiveTurnTrue_success() {
        User user = new User();
        user.setUsername("alex");
        user.setPassword("pw");
        user.setSalt("salt-1");
        user.setToken("token");
        user.setStatus(UserStatus.ONLINE);
        user.setBio("");
        user.setCreationDate(Instant.now());
        user.setTotalGamesPlayed(0);
        user.setTotalWins(0);
        user.setTotalPoints(0);
        user.setTotalCorrectPlacements(0);
        user.setTotalIncorrectPlacements(0);
        user = userRepository.saveAndFlush(user);

        Game game = new Game();
        game.setLobbyCode("ABC123");
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("WAITING");
        game.setHostId(user.getId());
        game.setDifficulty(Difficulty.EASY);
        game.setDeckSize(0);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");
        game = gameRepository.saveAndFlush(game);

        GamePlayer gamePlayer = new GamePlayer();
        gamePlayer.setGame(game);
        gamePlayer.setUser(user);
        gamePlayer.setScore(0);
        gamePlayer.setTurnOrder(0);
        gamePlayer.setActiveTurn(true);
        gamePlayer.setCurrentCardIndex(null);
        gamePlayerRepository.saveAndFlush(gamePlayer);

        Optional<GamePlayer> found = gamePlayerRepository.findByGameAndActiveTurnTrue(game);

        assertTrue(found.isPresent());
        assertEquals(user.getUsername(), found.get().getUser().getUsername());
    }

    @Test
    public void findAllByGameOrderByScoreDescTurnOrderAsc_success() {
        User user1 = new User();
        user1.setUsername("alex");
        user1.setPassword("pw1");
        user1.setSalt("salt-1");
        user1.setToken("token1");
        user1.setStatus(UserStatus.ONLINE);
        user1.setBio("");
        user1.setCreationDate(Instant.now());
        user1.setTotalGamesPlayed(0);
        user1.setTotalWins(0);
        user1.setTotalPoints(0);
        user1.setTotalCorrectPlacements(0);
        user1.setTotalIncorrectPlacements(0);
        user1 = userRepository.saveAndFlush(user1);

        User user2 = new User();
        user2.setUsername("mia");
        user2.setPassword("pw2");
        user2.setSalt("salt-2");
        user2.setToken("token2");
        user2.setStatus(UserStatus.ONLINE);
        user2.setBio("");
        user2.setCreationDate(Instant.now());
        user2.setTotalGamesPlayed(0);
        user2.setTotalWins(0);
        user2.setTotalPoints(0);
        user2.setTotalCorrectPlacements(0);
        user2.setTotalIncorrectPlacements(0);
        user2 = userRepository.saveAndFlush(user2);

        Game game = new Game();
        game.setLobbyCode("XYZ789");
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("IN_PROGRESS");
        game.setDifficulty(Difficulty.EASY);
        game.setHostId(user1.getId());
        game.setDeckSize(0);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");
        game = gameRepository.saveAndFlush(game);

        GamePlayer gp1 = new GamePlayer();
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setScore(3);
        gp1.setTurnOrder(1);
        gp1.setActiveTurn(false);
        gp1.setCurrentCardIndex(null);

        GamePlayer gp2 = new GamePlayer();
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setScore(5);
        gp2.setTurnOrder(0);
        gp2.setActiveTurn(true);
        gp2.setCurrentCardIndex(null);

        gamePlayerRepository.saveAndFlush(gp1);
        gamePlayerRepository.saveAndFlush(gp2);

        List<GamePlayer> result = gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game);

        assertEquals(2, result.size());
        assertEquals("mia", result.get(0).getUser().getUsername());
        assertEquals("alex", result.get(1).getUser().getUsername());
    }

    @Test
    public void findByActiveTurnTrue_success() {
        User user = new User();
        user.setUsername("bob");
        user.setPassword("pw");
        user.setSalt("testSalt");
        user.setToken("token-bob");
        user.setStatus(UserStatus.ONLINE);
        user.setBio("");
        user.setCreationDate(Instant.now());
        user = userRepository.saveAndFlush(user);

        Game game = new Game();
        game.setLobbyCode("TIM001");
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("IN_PROGRESS");
        game.setHostId(user.getId());
        game.setDeckSize(0);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");
        game = gameRepository.saveAndFlush(game);

        GamePlayer gp = new GamePlayer();
        gp.setGame(game);
        gp.setUser(user);
        gp.setScore(0);
        gp.setTurnOrder(0);
        gp.setActiveTurn(true);
        gp.setTurnStartedAt(Instant.now());
        gamePlayerRepository.saveAndFlush(gp);

        List<GamePlayer> result = gamePlayerRepository.findByActiveTurnTrue();

        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(GamePlayer::getActiveTurn));
        assertNotNull(result.get(0).getTurnStartedAt());
    }
}