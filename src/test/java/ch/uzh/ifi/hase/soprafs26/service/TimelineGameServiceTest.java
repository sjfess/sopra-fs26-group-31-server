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
}