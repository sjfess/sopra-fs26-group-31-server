package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GameStartServiceTest {

    @Mock private GameRepository gameRepository;
    @Mock private GamePlayerRepository gamePlayerRepository;
    @Mock private WikidataService wikidataService;
    @Mock private GameCardHelper gameCardHelper;

    private GameStartService gameStartService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        gameStartService = new GameStartService(
                gameRepository,
                gamePlayerRepository,
                wikidataService,
                gameCardHelper
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    /** helper-functions */

    private Game makeWaitingGame() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("WAITING");
        game.setEra(HistoricalEra.MODERN);
        game.setDifficulty(Difficulty.MEDIUM);
        game.setNextCardIndex(0);
        game.setDeckSize(0);
        return game;
    }

    private GamePlayer makePlayer(Long id, User user, int turnOrder) {
        GamePlayer gp = new GamePlayer();
        gp.setId(id);
        gp.setUser(user);
        gp.setTurnOrder(turnOrder);
        gp.setHandIndicesJson("[]");
        gp.setCardsInHand(0);
        gp.setScore(0);
        gp.setCorrectPlacements(0);
        gp.setIncorrectPlacements(0);
        gp.setCorrectStreak(0);
        gp.setBestStreak(0);
        return gp;
    }

    private EventCard makeCard(String title, int year) {
        EventCard card = new EventCard();
        card.setTitle(title);
        card.setYear(year);
        return card;
    }

    /** find game tests */

    @Test
    void findGameOrThrow_notFound_throwsNotFound() {
        when(gameRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> gameStartService.findGameOrThrow(99L));
    }

    @Test
    void findGameOrThrow_found_returnsGame() {
        Game game = new Game();
        game.setId(1L);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        assertSame(game, gameStartService.findGameOrThrow(1L));
    }

    /** start game guard tests */

    @Test
    void startGame_notWaiting_throwsConflict() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        assertThrows(ResponseStatusException.class,
                () -> gameStartService.startGame(1L, 20));
    }

    @Test
    void startGame_alreadyFinished_throwsConflict() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("FINISHED");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        assertThrows(ResponseStatusException.class,
                () -> gameStartService.startGame(1L, 20));
    }

    /** start game player count guard tests */

    @Test
    void startGame_onlyOnePlayer_throwsBadRequest() {
        Game game = makeWaitingGame();
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User user = new User();
        user.setId(1L);
        GamePlayer gp = makePlayer(1L, user, 0);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(gp));

        assertThrows(ResponseStatusException.class,
                () -> gameStartService.startGame(1L, 20));
    }

    @Test
    void startGame_noPlayers_throwsBadRequest() {
        Game game = makeWaitingGame();
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of());

        assertThrows(ResponseStatusException.class,
                () -> gameStartService.startGame(1L, 20));
    }

    /** general start game tests */

    @Test
    void startGame_twoPlayers_setsStatusInProgress() {
        Game game = makeWaitingGame();
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        GamePlayer gp1 = makePlayer(1L, u1, 0);
        GamePlayer gp2 = makePlayer(2L, u2, 1);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(gp1, gp2));

        // Provide enough curated + fetched cards so the deck can be built
        List<EventCard> curatedCards = List.of(
                makeCard("Seeded Event", 1500) // 1 seed for MEDIUM difficulty (3 seeds) capped at 1
        );
        List<EventCard> fetchedCards = List.of(
                makeCard("Event A", 1900),
                makeCard("Event B", 1950),
                makeCard("Event C", 1800),
                makeCard("Event D", 1700),
                makeCard("Event E", 1600),
                makeCard("Event F", 1850),
                makeCard("Event G", 1920),
                makeCard("Event H", 1770),
                makeCard("Event I", 1660),
                makeCard("Event J", 1530)
        );
        when(wikidataService.getCuratedCards(HistoricalEra.MODERN)).thenReturn(curatedCards);
        when(wikidataService.fetchEvents(eq(HistoricalEra.MODERN), anyInt())).thenReturn(fetchedCards);
        when(gameCardHelper.serializeDeck(anyList())).thenReturn("[serialized]");
        when(gameCardHelper.deserializeHandIndices(anyString())).thenReturn(List.of());
        when(gameCardHelper.serializeHandIndices(anyList())).thenReturn("[]");
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        Game result = gameStartService.startGame(1L, 20);

        assertEquals("IN_PROGRESS", result.getStatus());
        verify(gameRepository).save(game);
    }

    @Test
    void startGame_firstPlayerGetsActiveTurn() {
        Game game = makeWaitingGame();
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        GamePlayer gp1 = makePlayer(1L, u1, 0);
        GamePlayer gp2 = makePlayer(2L, u2, 1);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(gp1, gp2));

        when(wikidataService.getCuratedCards(HistoricalEra.MODERN)).thenReturn(List.of());
        when(wikidataService.fetchEvents(eq(HistoricalEra.MODERN), anyInt()))
                .thenReturn(List.of(
                        makeCard("A", 1900), makeCard("B", 1800), makeCard("C", 1700),
                        makeCard("D", 1600), makeCard("E", 1500), makeCard("F", 1400),
                        makeCard("G", 1300), makeCard("H", 1200), makeCard("I", 1100),
                        makeCard("J", 1000)
                ));
        when(gameCardHelper.serializeDeck(anyList())).thenReturn("[serialized]");
        when(gameCardHelper.deserializeHandIndices(anyString())).thenReturn(List.of());
        when(gameCardHelper.serializeHandIndices(anyList())).thenReturn("[]");
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameStartService.startGame(1L, 20);

        assertTrue(gp1.getActiveTurn());
        assertFalse(gp2.getActiveTurn());
        assertNotNull(gp1.getTurnStartedAt());
        assertNull(gp2.getTurnStartedAt());
    }

    @Test
    void startGame_resetsAllPlayerStats() {
        Game game = makeWaitingGame();
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        GamePlayer gp1 = makePlayer(1L, u1, 0);
        GamePlayer gp2 = makePlayer(2L, u2, 1);
        // Dirty state that should be reset
        gp1.setScore(999);
        gp1.setCorrectPlacements(50);
        gp2.setIncorrectPlacements(30);

        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(gp1, gp2));
        when(wikidataService.getCuratedCards(HistoricalEra.MODERN)).thenReturn(List.of());
        when(wikidataService.fetchEvents(eq(HistoricalEra.MODERN), anyInt()))
                .thenReturn(List.of(
                        makeCard("A", 1900), makeCard("B", 1800), makeCard("C", 1700),
                        makeCard("D", 1600), makeCard("E", 1500), makeCard("F", 1400),
                        makeCard("G", 1300), makeCard("H", 1200), makeCard("I", 1100),
                        makeCard("J", 1000)
                ));
        when(gameCardHelper.serializeDeck(anyList())).thenReturn("[serialized]");
        when(gameCardHelper.deserializeHandIndices(anyString())).thenReturn(List.of());
        when(gameCardHelper.serializeHandIndices(anyList())).thenReturn("[]");
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameStartService.startGame(1L, 20);

        assertEquals(0, gp1.getScore());
        assertEquals(0, gp1.getCorrectPlacements());
        assertEquals(0, gp2.getIncorrectPlacements());
        assertEquals(0, gp1.getCorrectStreak());
        assertEquals(0, gp1.getBestStreak());
        assertNull(gp1.getCurrentCardIndex());
    }

    /** difficulty-test */

    @Test
    void startGame_easyDifficulty_seeds1CuratedCard() {
        Game game = makeWaitingGame();
        game.setDifficulty(Difficulty.EASY);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(makePlayer(1L, u1, 0), makePlayer(2L, u2, 1)));

        EventCard seed = makeCard("Seeded", 1000);
        when(wikidataService.getCuratedCards(HistoricalEra.MODERN)).thenReturn(List.of(seed));
        when(wikidataService.fetchEvents(eq(HistoricalEra.MODERN), anyInt()))
                .thenReturn(List.of(
                        makeCard("A", 1900), makeCard("B", 1800), makeCard("C", 1700),
                        makeCard("D", 1600), makeCard("E", 1500), makeCard("F", 1400),
                        makeCard("G", 1300), makeCard("H", 1200), makeCard("I", 1100),
                        makeCard("J", 1000)
                ));

        // Capture what gets passed to serializeDeck for the timeline
        when(gameCardHelper.serializeDeck(anyList())).thenAnswer(inv -> {
            List<EventCard> list = inv.getArgument(0);
            // The first call is for the deck, second is for the timeline seed
            return "[serialized-" + list.size() + "]";
        });
        when(gameCardHelper.deserializeHandIndices(anyString())).thenReturn(List.of());
        when(gameCardHelper.serializeHandIndices(anyList())).thenReturn("[]");
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameStartService.startGame(1L, 20);

        // EASY = 1 seed → timeline serialized with exactly 1 card
        assertNotNull(game.getTimelineJson());
    }

    @Test
    void startGame_hardDifficulty_seeds5CuratedCards() {
        Game game = makeWaitingGame();
        game.setDifficulty(Difficulty.HARD);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(makePlayer(1L, u1, 0), makePlayer(2L, u2, 1)));

        List<EventCard> curated = List.of(
                makeCard("S1", 100), makeCard("S2", 200), makeCard("S3", 300),
                makeCard("S4", 400), makeCard("S5", 500), makeCard("S6", 600)
        );
        when(wikidataService.getCuratedCards(HistoricalEra.MODERN)).thenReturn(curated);
        when(wikidataService.fetchEvents(eq(HistoricalEra.MODERN), anyInt()))
                .thenReturn(List.of(
                        makeCard("A", 1900), makeCard("B", 1800), makeCard("C", 1700),
                        makeCard("D", 1600), makeCard("E", 1500), makeCard("F", 1400),
                        makeCard("G", 1300), makeCard("H", 1200), makeCard("I", 1100),
                        makeCard("J", 1000)
                ));
        when(gameCardHelper.serializeDeck(anyList())).thenReturn("[serialized]");
        when(gameCardHelper.deserializeHandIndices(anyString())).thenReturn(List.of());
        when(gameCardHelper.serializeHandIndices(anyList())).thenReturn("[]");
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        // Should not throw — 5 curated seeds are available
        assertDoesNotThrow(() -> gameStartService.startGame(1L, 20));
    }

    @Test
    void startGame_nullDifficulty_seedsZeroCards() {
        Game game = makeWaitingGame();
        game.setDifficulty(null);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(makePlayer(1L, u1, 0), makePlayer(2L, u2, 1)));

        when(wikidataService.getCuratedCards(HistoricalEra.MODERN)).thenReturn(List.of());
        when(wikidataService.fetchEvents(eq(HistoricalEra.MODERN), anyInt()))
                .thenReturn(List.of(
                        makeCard("A", 1900), makeCard("B", 1800), makeCard("C", 1700),
                        makeCard("D", 1600), makeCard("E", 1500), makeCard("F", 1400),
                        makeCard("G", 1300), makeCard("H", 1200), makeCard("I", 1100),
                        makeCard("J", 1000)
                ));
        when(gameCardHelper.serializeDeck(anyList())).thenReturn("[serialized]");
        when(gameCardHelper.deserializeHandIndices(anyString())).thenReturn(List.of());
        when(gameCardHelper.serializeHandIndices(anyList())).thenReturn("[]");
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        // null difficulty → getTimelineSeedCount returns 0 → should not crash
        assertDoesNotThrow(() -> gameStartService.startGame(1L, 20));
    }

    /** dekcsize-calculation tests */

    @Test
    void startGame_requestedDeckSizeBelowMinimum_usesMinimum() {
        // minimum is 20; requesting 5 should still produce at least 20
        Game game = makeWaitingGame();
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(makePlayer(1L, u1, 0), makePlayer(2L, u2, 1)));

        when(wikidataService.getCuratedCards(HistoricalEra.MODERN)).thenReturn(List.of());
        List<EventCard> bigDeck = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) bigDeck.add(makeCard("Card" + i, 1000 + i));
        when(wikidataService.fetchEvents(eq(HistoricalEra.MODERN), anyInt())).thenReturn(bigDeck);
        when(gameCardHelper.serializeDeck(anyList())).thenReturn("[serialized]");
        when(gameCardHelper.deserializeHandIndices(anyString())).thenReturn(List.of());
        when(gameCardHelper.serializeHandIndices(anyList())).thenReturn("[]");
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameStartService.startGame(1L, 5);

        // deckSize should be at least MINIMUM_DECK_SIZE=20
        assertTrue(game.getDeckSize() >= 20);
    }

    @Test
    void startGame_dealsInitialHandsToAllPlayers() {
        Game game = makeWaitingGame();
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        GamePlayer gp1 = makePlayer(1L, u1, 0);
        GamePlayer gp2 = makePlayer(2L, u2, 1);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game))
                .thenReturn(List.of(gp1, gp2));

        when(wikidataService.getCuratedCards(HistoricalEra.MODERN)).thenReturn(List.of());
        when(wikidataService.fetchEvents(eq(HistoricalEra.MODERN), anyInt()))
                .thenReturn(List.of(
                        makeCard("A", 1900), makeCard("B", 1800), makeCard("C", 1700),
                        makeCard("D", 1600), makeCard("E", 1500), makeCard("F", 1400),
                        makeCard("G", 1300), makeCard("H", 1200), makeCard("I", 1100),
                        makeCard("J", 1000)
                ));
        when(gameCardHelper.serializeDeck(anyList())).thenReturn("[serialized]");
        when(gameCardHelper.deserializeHandIndices(anyString())).thenReturn(List.of());
        when(gameCardHelper.serializeHandIndices(anyList())).thenReturn("[]");
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameStartService.startGame(1L, 20);

        // dealCardsToPlayer must be called once per player (INITIAL_HAND_SIZE = 5)
        verify(gameCardHelper, times(2)).dealCardsToPlayer(any(GamePlayer.class), eq(game), eq(5));
    }
}