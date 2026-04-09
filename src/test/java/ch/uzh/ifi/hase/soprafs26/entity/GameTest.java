package ch.uzh.ifi.hase.soprafs26.entity;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameTest {

    @Test
    public void gameEntity_settersAndGetters_success() {
        Game game = new Game();

        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("WAITING");
        game.setDeckJson("[{\"title\":\"Moon Landing\",\"year\":1969}]");
        game.setNextCardIndex(2);
        game.setDeckSize(10);
        game.setTimelineJson("[{\"title\":\"Printing Press\",\"year\":1440}]");
        game.setHostId(99L);

        assertEquals(1L, game.getId());
        assertEquals("ABC123", game.getLobbyCode());
        assertEquals(HistoricalEra.MODERN, game.getEra());
        assertEquals("WAITING", game.getStatus());
        assertEquals("[{\"title\":\"Moon Landing\",\"year\":1969}]", game.getDeckJson());
        assertEquals(2, game.getNextCardIndex());
        assertEquals(10, game.getDeckSize());
        assertEquals("[{\"title\":\"Printing Press\",\"year\":1440}]", game.getTimelineJson());
        assertEquals(99L, game.getHostId());
    }

    @Test
    public void gameEntity_setGamePlayers_success() {
        Game game = new Game();
        game.setId(1L);

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer gamePlayer = new GamePlayer();
        gamePlayer.setId(100L);
        gamePlayer.setGame(game);
        gamePlayer.setUser(user);
        gamePlayer.setScore(0);
        gamePlayer.setTurnOrder(0);
        gamePlayer.setActiveTurn(true);
        gamePlayer.setCurrentCardIndex(null);

        List<GamePlayer> gamePlayers = new ArrayList<>();
        gamePlayers.add(gamePlayer);

        game.setGamePlayers(gamePlayers);

        assertNotNull(game.getGamePlayers());
        assertEquals(1, game.getGamePlayers().size());
        assertEquals(100L, game.getGamePlayers().get(0).getId());
        assertEquals("alex", game.getGamePlayers().get(0).getUser().getUsername());
        assertTrue(game.getGamePlayers().get(0).getActiveTurn());
    }

    @Test
    public void gameEntity_emptyGamePlayersList_success() {
        Game game = new Game();
        game.setGamePlayers(new ArrayList<>());

        assertNotNull(game.getGamePlayers());
        assertTrue(game.getGamePlayers().isEmpty());
    }
}
