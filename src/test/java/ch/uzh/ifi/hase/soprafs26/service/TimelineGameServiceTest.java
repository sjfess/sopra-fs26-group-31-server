package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HandCardDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TimelineGameServiceTest {

    @Mock private GameRepository gameRepository;
    @Mock private GamePlayerRepository gamePlayerRepository;
    @Mock private UserRepository userRepository;
    @Mock private GameCardHelper gameCardHelper;
    @Mock private GameFinalizationService gameFinalizationService;
    @Mock private GameStartService gameStartService;

    private TimelineGameService timelineGameService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        timelineGameService = new TimelineGameService(
                gameRepository,
                gamePlayerRepository,
                userRepository,
                gameCardHelper,
                gameFinalizationService,
                gameStartService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    /** card-drawing tests */

    @Test
    void drawCard_gameNotInProgress_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.drawCard(1L, 2L, 0));
    }

    @Test
    void drawCard_notTimelineMode_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.HISTORY_UNO);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.drawCard(1L, 2L, 0));
    }

    @Test
    void drawCard_userNotFound_throwsNotFound() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.drawCard(1L, 2L, 0));
    }

    @Test
    void drawCard_notActiveTurn_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        User user = new User();
        user.setId(2L);
        GamePlayer player = new GamePlayer();
        player.setActiveTurn(false);
        player.setHandIndicesJson("[0]");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices("[0]")).thenReturn(List.of(0));
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.drawCard(1L, 2L, 0));
    }

    @Test
    void drawCard_cardNotInHand_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        User user = new User();
        user.setId(2L);
        GamePlayer player = new GamePlayer();
        player.setActiveTurn(true);
        player.setHandIndicesJson("[1]");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices("[1]")).thenReturn(List.of(1));

        // trying to draw index 0, but hand only contains index 1
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.drawCard(1L, 2L, 0));
    }

    @Test
    void drawCard_happyPath_returnsCard() {
        EventCard card = new EventCard();
        card.setTitle("Battle of Hastings");
        card.setYear(1066);

        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckJson("[{\"title\":\"Battle of Hastings\",\"year\":1066,\"imageUrl\":null,\"wikidataId\":null}]");

        User user = new User();
        user.setId(2L);
        GamePlayer player = new GamePlayer();
        player.setActiveTurn(true);
        player.setHandIndicesJson("[0]");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices("[0]")).thenReturn(List.of(0));
        when(gameCardHelper.deserializeDeck(anyString())).thenReturn(List.of(card));

        EventCard result = timelineGameService.drawCard(1L, 2L, 0);

        assertSame(card, result);
        verify(gamePlayerRepository).save(player);
        assertEquals(0, player.getCurrentCardIndex());
    }

    /** get timeline tests */

    @Test
    void getTimeline_gameNotFound_throwsNotFound() {
        when(gameRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.getTimeline(99L));
    }

    @Test
    void getTimeline_happyPath_returnsDeserializedCards() {
        EventCard card = new EventCard();
        Game game = new Game();
        game.setTimelineJson("[{}]");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gameCardHelper.deserializeDeck("[{}]")).thenReturn(List.of(card));

        List<EventCard> result = timelineGameService.getTimeline(1L);

        assertEquals(1, result.size());
        assertSame(card, result.get(0));
    }

    /** timeline finished tests */

    @Test
    void isTimelineGameFinished_wrongMode_returnsFalse() {
        Game game = new Game();
        game.setGameMode(GameMode.HISTORY_UNO);
        assertFalse(timelineGameService.isTimelineGameFinished(game));
    }

    @Test
    void isTimelineGameFinished_deckExhausted_returnsTrue() {
        Game game = new Game();
        game.setGameMode(GameMode.TIMELINE);
        game.setNextCardIndex(10);
        game.setDeckSize(10);
        assertTrue(timelineGameService.isTimelineGameFinished(game));
    }

    @Test
    void isTimelineGameFinished_playersStillHaveCards_returnsFalse() {
        Game game = new Game();
        game.setGameMode(GameMode.TIMELINE);
        game.setNextCardIndex(5);
        game.setDeckSize(20);
        GamePlayer player = new GamePlayer();
        player.setCardsInHand(3);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(player));
        assertFalse(timelineGameService.isTimelineGameFinished(game));
    }

    @Test
    void isTimelineGameFinished_allPlayersEmptyHands_returnsTrue() {
        Game game = new Game();
        game.setGameMode(GameMode.TIMELINE);
        game.setNextCardIndex(5);
        game.setDeckSize(20);
        GamePlayer player = new GamePlayer();
        player.setCardsInHand(0);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(player));
        assertTrue(timelineGameService.isTimelineGameFinished(game));
    }

    /** turn-advancing tests */

    @Test
    void advanceTurn_noPlayers_doesNothing() {
        Game game = new Game();
        GamePlayer current = new GamePlayer();
        current.setId(1L);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of());
        assertDoesNotThrow(() -> timelineGameService.advanceTurn(game, current));
    }

    @Test
    void advanceTurn_singlePlayer_wrapsAround() {
        Game game = new Game();
        GamePlayer player = new GamePlayer();
        player.setId(1L);
        player.setActiveTurn(true);
        player.setTurnStartedAt(Instant.now());
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(player));

        timelineGameService.advanceTurn(game, player);

        verify(gamePlayerRepository, atLeastOnce()).save(player);
        assertTrue(player.getActiveTurn()); // wraps back to itself
    }

    @Test
    void advanceTurn_twoPlayers_nextPlayerActivated() {
        Game game = new Game();
        GamePlayer p1 = new GamePlayer();
        p1.setId(1L);
        p1.setActiveTurn(true);
        GamePlayer p2 = new GamePlayer();
        p2.setId(2L);
        p2.setActiveTurn(false);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(p1, p2));

        timelineGameService.advanceTurn(game, p1);

        assertFalse(p1.getActiveTurn());
        assertTrue(p2.getActiveTurn());
        assertNotNull(p2.getTurnStartedAt());
    }

    /** get hand tests */

    @Test
    void getHand_gameNotInProgress_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.getHand(1L, 2L));
    }

    @Test
    void getHand_userNotFound_throwsNotFound() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.getHand(1L, 2L));
    }

    @Test
    void getHand_happyPath_returnsDTOs() {
        EventCard card = new EventCard();
        card.setTitle("Moon Landing");
        card.setImageUrl("https://example.com/moon.jpg");

        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setDeckJson("[...]");
        User user = new User();
        user.setId(2L);
        GamePlayer player = new GamePlayer();
        player.setHandIndicesJson("[0]");

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices("[0]")).thenReturn(List.of(0));
        when(gameCardHelper.deserializeDeck(anyString())).thenReturn(List.of(card));

        List<HandCardDTO> result = timelineGameService.getHand(1L, 2L);

        assertEquals(1, result.size());
        assertEquals("Moon Landing", result.get(0).getTitle());
        assertEquals(0, result.get(0).getDeckIndex());
    }

    /** get card tests */

    @Test
    void getCard_gameNotInProgress_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.getCard(1L, 0));
    }

    @Test
    void getCard_indexOutOfRange_throwsNotFound() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gameCardHelper.deserializeDeck(any())).thenReturn(List.of(new EventCard()));
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.getCard(1L, 5));
    }

    @Test
    void getCard_happyPath_returnsCard() {
        EventCard card = new EventCard();
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setDeckJson("[{}]");
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gameCardHelper.deserializeDeck("[{}]")).thenReturn(List.of(card));

        EventCard result = timelineGameService.getCard(1L, 0);

        assertSame(card, result);
    }

    /** get scores test */

    @Test
    void getLiveScores_happyPath_returnsDTOs() {
        Game game = new Game();
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        GamePlayer gp = new GamePlayer();
        gp.setUser(user);
        gp.setScore(200);
        gp.setTurnOrder(0);
        gp.setActiveTurn(true);
        gp.setCorrectStreak(2);
        gp.setBestStreak(3);
        gp.setCardsInHand(2);
        gp.setCorrectPlacements(5);
        gp.setIncorrectPlacements(1);

        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game))
                .thenReturn(List.of(gp));

        List<GamePlayerScoreDTO> result = timelineGameService.getLiveScores(1L);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).getUsername());
        assertEquals(200, result.get(0).getScore());
    }

    /** get card test */

    @Test
    void getAllCards_happyPath_returnsAllDeckCards() {
        Game game = new Game();
        game.setDeckJson("[{}]");
        EventCard card = new EventCard();
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(gameCardHelper.deserializeDeck("[{}]")).thenReturn(List.of(card));

        List<EventCard> result = timelineGameService.getAllCards(1L);

        assertEquals(1, result.size());
        assertSame(card, result.get(0));
    }

    /** placeCard guard-clause tests */

    @Test
    void placeCard_gameNotFound_throwsNotFound() {
        when(gameRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(99L, 0, 0));
    }

    @Test
    void placeCard_gameNotInProgress_throwsConflict() {
        Game game = new Game();
        game.setStatus("WAITING");
        game.setGameMode(GameMode.TIMELINE);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(1L, 0, 0));
    }

    @Test
    void placeCard_wrongGameMode_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.HISTORY_UNO);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(1L, 0, 0));
    }

    @Test
    void placeCard_noActivePlayer_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(1L, 0, 0));
    }

    @Test
    void placeCard_noCardSelected_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        GamePlayer player = new GamePlayer();
        player.setCurrentCardIndex(null);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(player));
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(1L, 0, 0));
    }

    @Test
    void placeCard_wrongCardSelected_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        GamePlayer player = new GamePlayer();
        player.setCurrentCardIndex(3); // drew card 3
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(player));
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(1L, 0, 0)); // trying to place card 0
    }

    @Test
    void placeCard_cardNotInHand_throwsConflict() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckJson("[]");
        GamePlayer player = new GamePlayer();
        player.setCurrentCardIndex(0);
        player.setHandIndicesJson("[1]");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices("[1]")).thenReturn(List.of(1)); // hand has 1, not 0
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(1L, 0, 0));
    }

    @Test
    void placeCard_cardIndexOutOfRange_throwsNotFound() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckJson("[]");
        GamePlayer player = new GamePlayer();
        player.setCurrentCardIndex(5);
        player.setHandIndicesJson("[5]");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices("[5]")).thenReturn(List.of(5));
        when(gameCardHelper.deserializeDeck(game.getDeckJson())).thenReturn(List.of()); // empty deck
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(1L, 5, 0));
    }

    @Test
    void placeCard_positionOutOfRange_throwsBadRequest() {
        Game game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckJson("[]");
        game.setTimelineJson("[]");
        EventCard card = new EventCard(); card.setYear(1900);
        GamePlayer player = new GamePlayer();
        player.setCurrentCardIndex(0);
        player.setHandIndicesJson("[0]");
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices("[0]")).thenReturn(List.of(0));
        when(gameCardHelper.deserializeDeck(game.getDeckJson())).thenReturn(List.of(card));
        when(gameCardHelper.deserializeDeck(game.getTimelineJson())).thenReturn(List.of()); // empty timeline
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.placeCard(1L, 0, 5)); // position 5 > timeline.size() 0
    }

    /** placeCard known input: correct placement */

    @Test
    void placeCard_correctPlacement_updatesScoreAndTimeline() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckJson("[]");
        game.setTimelineJson("[]");
        game.setDeckSize(10);
        game.setNextCardIndex(3);

        EventCard card = new EventCard(); card.setTitle("Event A"); card.setYear(1500);

        GamePlayer player = new GamePlayer();
        player.setId(1L);
        player.setCurrentCardIndex(0);
        player.setHandIndicesJson("[0]");
        player.setScore(0);
        player.setCorrectStreak(0);
        player.setCorrectPlacements(0);
        player.setIncorrectPlacements(0);
        player.setCardsInHand(1);
        player.setTurnStartedAt(Instant.now());
        player.setActiveTurn(true);
        player.setBestStreak(0);

        // timeline has one card before position 0 — place at position 1 (after it)
        EventCard existing = new EventCard(); existing.setYear(1000);
        List<EventCard> timeline = new ArrayList<>(List.of(existing));
        List<EventCard> deck = new ArrayList<>(List.of(card));
        List<Integer> hand = new ArrayList<>(List.of(0));

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices(any())).thenReturn(hand);
        when(gameCardHelper.deserializeDeck(game.getDeckJson())).thenReturn(deck);
        when(gameCardHelper.deserializeDeck(any())).thenAnswer(invocation -> {
            String arg = invocation.getArgument(0);
            if (arg.equals(game.getDeckJson())) return deck;
            return timeline;
        });
        when(gameCardHelper.serializeDeck(any())).thenReturn("[]");
        when(gameCardHelper.serializeHandIndices(any())).thenReturn("[]");
        // game not finished: players all still have cards
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(player));

        GameService.PlacementResult result = timelineGameService.placeCard(1L, 0, 1);

        assertTrue(result.correct());
        assertEquals(card, result.card());
        assertTrue(player.getScore() > 0);
        assertEquals(1, player.getCorrectPlacements());
        assertEquals(1, player.getCorrectStreak());
    }

    /** placeCard with known input: incorrect placement */

    @Test
    void placeCard_incorrectPlacement_dealsPenaltyCard() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckJson("[\"deck\"]");
        game.setTimelineJson("[\"timeline\"]");
        game.setDeckSize(10);
        game.setNextCardIndex(3);

        // card year 500, placed AFTER existing year 1000 → incorrect
        EventCard card = new EventCard(); card.setTitle("Ancient"); card.setYear(500);
        EventCard existing1 = new EventCard(); existing1.setYear(1000);
        EventCard existing2 = new EventCard(); existing2.setYear(2000);
        List<EventCard> timeline = new ArrayList<>(List.of(existing1, existing2));
        List<EventCard> deck = new ArrayList<>(List.of(card));
        List<Integer> hand = new ArrayList<>(List.of(0));

        GamePlayer player = new GamePlayer();
        player.setId(1L);
        player.setCurrentCardIndex(0);
        player.setHandIndicesJson("[0]");
        player.setScore(100);
        player.setCorrectStreak(2);
        player.setCorrectPlacements(2);
        player.setIncorrectPlacements(0);
        player.setCardsInHand(1);
        player.setTurnStartedAt(Instant.now());
        player.setActiveTurn(true);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices(any())).thenReturn(hand);
        when(gameCardHelper.deserializeDeck(game.getDeckJson())).thenReturn(deck);
        when(gameCardHelper.deserializeDeck(any())).thenAnswer(invocation -> {
            String arg = invocation.getArgument(0);
            if (arg.equals(game.getDeckJson())) return deck;
            return timeline;
        });
        when(gameCardHelper.serializeHandIndices(any())).thenReturn("[]");
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(player));

        GameService.PlacementResult result = timelineGameService.placeCard(1L, 0, 2);

        assertFalse(result.correct());
        assertEquals(0, player.getCorrectStreak());          // streak reset
        assertEquals(1, player.getIncorrectPlacements());
        verify(gameCardHelper).dealCardsToPlayer(eq(player), eq(game), eq(1));
    }

    /** placeCard: game finishes on this move */

    @Test
    void placeCard_gameFinishesAfterMove_finalizationCalled() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckJson("[]");
        game.setTimelineJson("[]");
        game.setDeckSize(1);
        game.setNextCardIndex(1); // deck exhausted → game finished

        EventCard card = new EventCard(); card.setTitle("Last"); card.setYear(2000);
        List<EventCard> deck = new ArrayList<>(List.of(card));
        List<EventCard> timeline = new ArrayList<>();
        List<Integer> hand = new ArrayList<>(List.of(0));

        GamePlayer player = new GamePlayer();
        player.setId(1L);
        player.setCurrentCardIndex(0);
        player.setHandIndicesJson("[0]");
        player.setScore(0);
        player.setCorrectStreak(0);
        player.setCorrectPlacements(0);
        player.setIncorrectPlacements(0);
        player.setCardsInHand(1);
        player.setTurnStartedAt(Instant.now());
        player.setActiveTurn(true);
        player.setBestStreak(0);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(player));
        when(gameCardHelper.deserializeHandIndices(any())).thenReturn(hand);
        when(gameCardHelper.deserializeDeck(game.getDeckJson())).thenReturn(deck);
        when(gameCardHelper.deserializeDeck(any())).thenAnswer(invocation -> {
            String arg = invocation.getArgument(0);
            if (arg.equals(game.getDeckJson())) return deck;
            return timeline;
        });
        when(gameCardHelper.serializeDeck(any())).thenReturn("[]");
        when(gameCardHelper.serializeHandIndices(any())).thenReturn("[]");

        timelineGameService.placeCard(1L, 0, 0);

        verify(gameFinalizationService).finalizeGame(1L);
    }
}