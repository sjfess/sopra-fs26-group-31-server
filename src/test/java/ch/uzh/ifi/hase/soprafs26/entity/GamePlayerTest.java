package ch.uzh.ifi.hase.soprafs26.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class GamePlayerTest {

    @Test
    public void gamePlayerEntity_settersAndGetters_success() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer gamePlayer = new GamePlayer();
        gamePlayer.setId(100L);
        gamePlayer.setGame(game);
        gamePlayer.setUser(user);
        gamePlayer.setScore(7);
        gamePlayer.setTurnOrder(1);
        gamePlayer.setActiveTurn(true);
        gamePlayer.setCurrentCardIndex(4);

        assertEquals(100L, gamePlayer.getId());
        assertNotNull(gamePlayer.getGame());
        assertEquals(1L, gamePlayer.getGame().getId());
        assertEquals("ABC123", gamePlayer.getGame().getLobbyCode());

        assertNotNull(gamePlayer.getUser());
        assertEquals(10L, gamePlayer.getUser().getId());
        assertEquals("alex", gamePlayer.getUser().getUsername());

        assertEquals(7, gamePlayer.getScore());
        assertEquals(1, gamePlayer.getTurnOrder());
        assertTrue(gamePlayer.getActiveTurn());
        assertEquals(4, gamePlayer.getCurrentCardIndex());
    }

    @Test
    public void gamePlayerEntity_nullCurrentCardIndex_success() {
        GamePlayer gamePlayer = new GamePlayer();
        gamePlayer.setCurrentCardIndex(null);

        assertNull(gamePlayer.getCurrentCardIndex());
    }

    @Test
    public void gamePlayerEntity_defaultBooleanAndNumericWrapperValues_canBeSet_success() {
        GamePlayer gamePlayer = new GamePlayer();

        gamePlayer.setScore(0);
        gamePlayer.setTurnOrder(0);
        gamePlayer.setActiveTurn(false);

        assertEquals(0, gamePlayer.getScore());
        assertEquals(0, gamePlayer.getTurnOrder());
        assertFalse(gamePlayer.getActiveTurn());
    }

    @Test
    public void gamePlayerEntity_turnStartedAt_setAndGet() {
        GamePlayer gamePlayer = new GamePlayer();
        Instant now = Instant.now();
        gamePlayer.setTurnStartedAt(now);
        assertEquals(now, gamePlayer.getTurnStartedAt());
    }

    @Test
    public void gamePlayerEntity_turnStartedAt_defaultIsNull() {
        GamePlayer gamePlayer = new GamePlayer();
        assertNull(gamePlayer.getTurnStartedAt());
    }
}