package ch.uzh.ifi.hase.soprafs26.repository;

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
        user.setToken("token");
        user.setStatus(UserStatus.ONLINE);
        user.setBio("");
        user.setCreationDate(Instant.now());
        user = userRepository.saveAndFlush(user);

        Game game = new Game();
        game.setLobbyCode("ABC123");
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("WAITING");
        game.setHostId(user.getId());
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
        user1.setToken("token1");
        user1.setStatus(UserStatus.ONLINE);
        user1.setBio("");
        user1.setCreationDate(Instant.now());
        user1 = userRepository.saveAndFlush(user1);

        User user2 = new User();
        user2.setUsername("mia");
        user2.setPassword("pw2");
        user2.setToken("token2");
        user2.setStatus(UserStatus.ONLINE);
        user2.setBio("");
        user2.setCreationDate(Instant.now());
        user2 = userRepository.saveAndFlush(user2);

        Game game = new Game();
        game.setLobbyCode("XYZ789");
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("IN_PROGRESS");
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

        GamePlayer gp2 = new GamePlayer();
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setScore(5);
        gp2.setTurnOrder(0);
        gp2.setActiveTurn(true);

        gamePlayerRepository.saveAndFlush(gp1);
        gamePlayerRepository.saveAndFlush(gp2);

        List<GamePlayer> result = gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game);

        assertEquals(2, result.size());
        assertEquals("mia", result.get(0).getUser().getUsername());
        assertEquals("alex", result.get(1).getUser().getUsername());
    }
}