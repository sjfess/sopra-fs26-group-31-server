package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ch.uzh.ifi.hase.soprafs26.repository.ChatMessageRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameInviteRepository;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GameServiceTest {

    private GameRepository gameRepository;
    private GamePlayerRepository gamePlayerRepository;
    private WikidataService wikidataService;
    private GameService gameService;
    private UserRepository userRepository;
    private ChatMessageRepository chatMessageRepository;
    private GameInviteRepository gameInviteRepository;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        gameRepository = Mockito.mock(GameRepository.class);
        gamePlayerRepository = Mockito.mock(GamePlayerRepository.class);
        wikidataService = Mockito.mock(WikidataService.class);
        userRepository = Mockito.mock(UserRepository.class);
        chatMessageRepository = Mockito.mock(ChatMessageRepository.class);
        gameInviteRepository = Mockito.mock(GameInviteRepository.class);
        gameService = new GameService(
                gameRepository,
                gamePlayerRepository,
                userRepository,
                wikidataService,
                chatMessageRepository,
                gameInviteRepository
        );

        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gamePlayerRepository.save(any(GamePlayer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void startGame_validInputs_initializesDeckAndActiveTurn() {
        Game game = new Game();
        game.setId(1L);
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("WAITING");
        game.setTimelineJson("[]");
        game.setGameMode(GameMode.TIMELINE);

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer gp1 = new GamePlayer();
        gp1.setId(100L);
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setTurnOrder(0);
        gp1.setScore(5);
        gp1.setActiveTurn(false);

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(3);
        gp2.setActiveTurn(false);

        List<EventCard> deck = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            EventCard card = new EventCard();
            card.setTitle("Event " + i);
            card.setYear(1900 + i);
            deck.add(card);
        }

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));
        when(wikidataService.fetchEvents(HistoricalEra.MODERN, 10)).thenReturn(deck);

        Game startedGame = gameService.startGame(1L, 10);

        assertEquals("IN_PROGRESS", startedGame.getStatus());
        assertEquals(10, startedGame.getDeckSize());
        assertEquals(10, startedGame.getNextCardIndex());
        assertNotNull(startedGame.getDeckJson());

        assertEquals(0, gp1.getScore());
        assertTrue(gp1.getActiveTurn());
        assertEquals(0, gp2.getScore());
        assertFalse(gp2.getActiveTurn());

        assertNotNull(gp1.getTurnStartedAt());
        assertNull(gp2.getTurnStartedAt());

        assertEquals(5, gp1.getCardsInHand());
        assertEquals(5, gp2.getCardsInHand());

        assertEquals("[0,1,2,3,4]", gp1.getHandIndicesJson());
        assertEquals("[5,6,7,8,9]", gp2.getHandIndicesJson());

        assertEquals(0, gp1.getCorrectStreak());
        assertEquals(0, gp1.getBestStreak());
        assertEquals(0, gp2.getCorrectStreak());
        assertEquals(0, gp2.getBestStreak());
    }

    @Test
    public void startGame_notEnoughPlayers_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("WAITING");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> gameService.startGame(1L, 10));
    }

    @Test
    public void getLiveScores_returnsSortedScores() {
        Game game = new Game();
        game.setId(1L);

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer gp1 = new GamePlayer();
        gp1.setUser(user1);
        gp1.setScore(3);
        gp1.setTurnOrder(0);
        gp1.setActiveTurn(false);
        gp1.setCorrectStreak(1);
        gp1.setBestStreak(2);
        gp1.setCardsInHand(2);
        gp1.setCurrentCardIndex(null);

        GamePlayer gp2 = new GamePlayer();
        gp2.setUser(user2);
        gp2.setScore(5);
        gp2.setTurnOrder(1);
        gp2.setActiveTurn(true);
        gp2.setCorrectStreak(3);
        gp2.setBestStreak(4);
        gp2.setCardsInHand(4);
        gp2.setCurrentCardIndex(7);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game)).thenReturn(List.of(gp2, gp1));

        List<GamePlayerScoreDTO> scores = gameService.getLiveScores(1L);

        assertEquals(2, scores.size());

        assertEquals("mia", scores.get(0).getUsername());
        assertEquals(5, scores.get(0).getScore());
        assertTrue(scores.get(0).getActiveTurn());
        assertEquals(3, scores.get(0).getCorrectStreak());
        assertEquals(4, scores.get(0).getBestStreak());
        assertEquals(4, scores.get(0).getCardsInHand());
        assertEquals(7, scores.get(0).getCurrentCardIndex());

        assertEquals("alex", scores.get(1).getUsername());
        assertEquals(3, scores.get(1).getScore());
        assertEquals(1, scores.get(1).getCorrectStreak());
        assertEquals(2, scores.get(1).getBestStreak());
        assertEquals(2, scores.get(1).getCardsInHand());
        assertNull(scores.get(1).getCurrentCardIndex());
    }

    @Test
    public void placeCard_correctPlacement_increasesScoreAndAdvancesTurn() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckSize(10);
        game.setNextCardIndex(5);

        List<EventCard> deck = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            EventCard c = new EventCard();
            c.setTitle("Event " + i);
            c.setYear(1900 + i);
            deck.add(c);
        }
        game.setDeckJson(gameService.serializeDeck(deck));

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer gp1 = new GamePlayer();
        gp1.setId(100L);
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setTurnOrder(0);
        gp1.setScore(0);
        gp1.setActiveTurn(true);
        gp1.setCurrentCardIndex(0);
        gp1.setCardsInHand(5);
        gp1.setHandIndicesJson("[0,1,2,3,4]");
        gp1.setTurnStartedAt(Instant.now().minusSeconds(0));

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(0);
        gp2.setActiveTurn(false);
        gp2.setCardsInHand(5);
        gp2.setHandIndicesJson("[5,6,7,8,9]");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));

        Object[] result = gameService.placeCard(1L, 0, 0);

        assertTrue((Boolean) result[1]);
        assertEquals(160, gp1.getScore());
        assertEquals(1, gp1.getCorrectStreak());
        assertEquals(1, gp1.getBestStreak());
        assertEquals(4, gp1.getCardsInHand());
        assertEquals("[1,2,3,4]", gp1.getHandIndicesJson());
        assertNull(gp1.getCurrentCardIndex());
        assertFalse(gp1.getActiveTurn());
        assertTrue(gp2.getActiveTurn());
    }

    @Test
    public void placeCard_wrongPlacement_doesNotIncreaseScoreAndAdvancesTurn() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);

        EventCard wrongCard = new EventCard();
        wrongCard.setTitle("Modern Event");
        wrongCard.setYear(2000);

        EventCard existingTimelineCard = new EventCard();
        existingTimelineCard.setTitle("Older Event");
        existingTimelineCard.setYear(1000);

        EventCard replacement = new EventCard();
        replacement.setTitle("Replacement");
        replacement.setYear(1500);

        EventCard extraCard = new EventCard();
        extraCard.setTitle("Extra");
        extraCard.setYear(1700);

        game.setDeckJson(gameService.serializeDeck(List.of(wrongCard, replacement, extraCard)));
        game.setTimelineJson(gameService.serializeDeck(List.of(existingTimelineCard)));
        game.setDeckSize(3);
        game.setNextCardIndex(1);

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer gp1 = new GamePlayer();
        gp1.setId(100L);
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setTurnOrder(0);
        gp1.setScore(2);
        gp1.setActiveTurn(true);
        gp1.setCurrentCardIndex(0);
        gp1.setCorrectStreak(3);
        gp1.setBestStreak(3);
        gp1.setCardsInHand(1);
        gp1.setHandIndicesJson("[0]");

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(0);
        gp2.setActiveTurn(false);
        gp2.setCardsInHand(1);
        gp2.setHandIndicesJson("[1]");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));

        Object[] result = gameService.placeCard(1L, 0, 0);

        assertFalse((Boolean) result[1]);
        assertEquals(2, gp1.getScore());
        assertEquals(0, gp1.getCorrectStreak());
        assertEquals(3, gp1.getBestStreak());
        assertEquals(1, gp1.getCardsInHand());
        assertEquals("[1]", gp1.getHandIndicesJson());
        assertNull(gp1.getCurrentCardIndex());
        assertFalse(gp1.getActiveTurn());
        assertTrue(gp2.getActiveTurn());
    }

    @Test
    public void joinGame_validInputs_success() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setStatus("WAITING");

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        when(gameRepository.findByLobbyCode("ABC123")).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.existsByGameAndUser(game, user)).thenReturn(false);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of());

        Game joinedGame = gameService.joinGame("ABC123", 10L);

        assertEquals(1L, joinedGame.getId());
        verify(gamePlayerRepository, times(1)).save(any(GamePlayer.class));
    }

    @Test
    public void joinGame_duplicateJoin_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setStatus("WAITING");

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        when(gameRepository.findByLobbyCode("ABC123")).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.existsByGameAndUser(game, user)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.joinGame("ABC123", 10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(gamePlayerRepository, never()).save(any(GamePlayer.class));
    }

    @Test
    public void joinGame_gameAlreadyStarted_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setStatus("IN_PROGRESS");

        when(gameRepository.findByLobbyCode("ABC123")).thenReturn(Optional.of(game));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.joinGame("ABC123", 10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void joinGame_gameNotFound_throwsException() {
        when(gameRepository.findByLobbyCode("NOPE")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.joinGame("NOPE", 10L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    public void joinGame_userNotFound_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setStatus("WAITING");

        when(gameRepository.findByLobbyCode("ABC123")).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.joinGame("ABC123", 10L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(gamePlayerRepository, never()).save(any(GamePlayer.class));
    }

    @Test
    public void joinGame_setsNextTurnOrder_success() {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setStatus("WAITING");

        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setUsername("mia");

        GamePlayer existingPlayer = new GamePlayer();
        existingPlayer.setId(100L);
        existingPlayer.setGame(game);
        existingPlayer.setUser(existingUser);
        existingPlayer.setTurnOrder(0);

        User newUser = new User();
        newUser.setId(10L);
        newUser.setUsername("alex");

        when(gameRepository.findByLobbyCode("ABC123")).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.of(newUser));
        when(gamePlayerRepository.existsByGameAndUser(game, newUser)).thenReturn(false);
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(existingPlayer));

        gameService.joinGame("ABC123", 10L);

        verify(gamePlayerRepository).save(argThat(savedPlayer ->
                savedPlayer.getGame() != null &&
                        savedPlayer.getUser() != null &&
                        savedPlayer.getGame().getId().equals(1L) &&
                        savedPlayer.getUser().getId().equals(10L) &&
                        savedPlayer.getTurnOrder().equals(1) &&
                        savedPlayer.getScore().equals(0) &&
                        savedPlayer.getActiveTurn().equals(false)
        ));
    }

    @Test
    public void drawCard_activePlayerGetsCurrentCardIndex_success() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        EventCard card0 = new EventCard();
        card0.setTitle("Moon Landing");
        card0.setYear(1969);

        EventCard card1 = new EventCard();
        card1.setTitle("Berlin Wall");
        card1.setYear(1989);

        game.setDeckJson(gameService.serializeDeck(List.of(card0, card1)));

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer activePlayer = new GamePlayer();
        activePlayer.setId(100L);
        activePlayer.setGame(game);
        activePlayer.setUser(user);
        activePlayer.setActiveTurn(true);
        activePlayer.setCurrentCardIndex(null);
        activePlayer.setHandIndicesJson("[0,1]");
        activePlayer.setCardsInHand(2);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.of(activePlayer));

        EventCard drawnCard = gameService.drawCard(1L, 10L, 1);

        assertEquals("Berlin Wall", drawnCard.getTitle());
        assertEquals(1, activePlayer.getCurrentCardIndex());
        assertNotNull(activePlayer.getTurnStartedAt());
    }

    @Test
    public void drawCard_selectedAgain_updatesCurrentCardIndex() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        EventCard c1 = new EventCard();
        c1.setTitle("A");
        c1.setYear(1000);

        EventCard c2 = new EventCard();
        c2.setTitle("B");
        c2.setYear(1500);

        game.setDeckJson(gameService.serializeDeck(List.of(c1, c2)));

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer player = new GamePlayer();
        player.setId(100L);
        player.setGame(game);
        player.setUser(user);
        player.setActiveTurn(true);
        player.setCurrentCardIndex(0);
        player.setHandIndicesJson("[0,1]");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.of(player));

        EventCard selected = gameService.drawCard(1L, 10L, 1);

        assertEquals("B", selected.getTitle());
        assertEquals(1, player.getCurrentCardIndex());
    }

    @Test
    public void placeCard_wrongCardIndexForActivePlayer_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");

        EventCard card1 = new EventCard();
        card1.setTitle("A");
        card1.setYear(1000);

        EventCard card2 = new EventCard();
        card2.setTitle("B");
        card2.setYear(1500);

        game.setDeckJson(gameService.serializeDeck(List.of(card1, card2)));

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer activePlayer = new GamePlayer();
        activePlayer.setId(100L);
        activePlayer.setGame(game);
        activePlayer.setUser(user);
        activePlayer.setActiveTurn(true);
        activePlayer.setCurrentCardIndex(0);
        activePlayer.setHandIndicesJson("[0,1]");
        activePlayer.setCardsInHand(2);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(activePlayer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.placeCard(1L, 1, 0)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void placeCard_withoutDrawnCard_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");

        EventCard card = new EventCard();
        card.setTitle("A");
        card.setYear(1000);
        game.setDeckJson(gameService.serializeDeck(List.of(card)));

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer activePlayer = new GamePlayer();
        activePlayer.setId(100L);
        activePlayer.setGame(game);
        activePlayer.setUser(user);
        activePlayer.setActiveTurn(true);
        activePlayer.setCurrentCardIndex(null);
        activePlayer.setHandIndicesJson("[0]");
        activePlayer.setCardsInHand(1);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(activePlayer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.placeCard(1L, 0, 0)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void placeCard_success_clearsCurrentCardIndex() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckSize(10);
        game.setNextCardIndex(5);

        List<EventCard> deck = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            EventCard c = new EventCard();
            c.setTitle("Event " + i);
            c.setYear(1900 + i);
            deck.add(c);
        }
        game.setDeckJson(gameService.serializeDeck(deck));

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer gp1 = new GamePlayer();
        gp1.setId(100L);
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setTurnOrder(0);
        gp1.setScore(0);
        gp1.setActiveTurn(true);
        gp1.setCurrentCardIndex(0);
        gp1.setCardsInHand(5);
        gp1.setHandIndicesJson("[0,1,2,3,4]");
        gp1.setTurnStartedAt(Instant.now());

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(0);
        gp2.setActiveTurn(false);
        gp2.setCurrentCardIndex(null);
        gp2.setCardsInHand(5);
        gp2.setHandIndicesJson("[5,6,7,8,9]");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));

        gameService.placeCard(1L, 0, 0);

        assertNull(gp1.getCurrentCardIndex());
        assertEquals(4, gp1.getCardsInHand());
    }

    @Test
    public void finalizeGame_validGame_updatesUserAggregatesAndReturnsResults() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");
        user1.setTotalGamesPlayed(2);
        user1.setTotalWins(1);
        user1.setTotalPoints(20);
        user1.setTotalCorrectPlacements(5);
        user1.setTotalIncorrectPlacements(2);

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");
        user2.setTotalGamesPlayed(3);
        user2.setTotalWins(2);
        user2.setTotalPoints(30);
        user2.setTotalCorrectPlacements(7);
        user2.setTotalIncorrectPlacements(1);

        GamePlayer gp1 = new GamePlayer();
        gp1.setId(100L);
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setScore(5);
        gp1.setCorrectPlacements(5);
        gp1.setIncorrectPlacements(1);

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setScore(3);
        gp2.setCorrectPlacements(3);
        gp2.setIncorrectPlacements(2);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<FinalResultDTO> results = gameService.finalizeGame(1L);

        assertEquals(2, results.size());

        assertEquals("alex", results.get(0).getUsername());
        assertEquals(5, results.get(0).getScore());
        assertEquals(5, results.get(0).getCorrectPlacements());
        assertEquals(1, results.get(0).getIncorrectPlacements());
        assertTrue(results.get(0).getWinner());

        assertEquals("mia", results.get(1).getUsername());
        assertEquals(3, results.get(1).getScore());
        assertFalse(results.get(1).getWinner());

        assertEquals("FINISHED", game.getStatus());

        assertEquals(3, user1.getTotalGamesPlayed());
        assertEquals(2, user1.getTotalWins());
        assertEquals(25, user1.getTotalPoints());
        assertEquals(10, user1.getTotalCorrectPlacements());
        assertEquals(3, user1.getTotalIncorrectPlacements());

        assertEquals(4, user2.getTotalGamesPlayed());
        assertEquals(2, user2.getTotalWins());
        assertEquals(33, user2.getTotalPoints());
        assertEquals(10, user2.getTotalCorrectPlacements());
        assertEquals(3, user2.getTotalIncorrectPlacements());

        verify(userRepository, times(2)).save(any(User.class));
        verify(gameRepository, times(1)).save(game);
        verify(userRepository, times(1)).flush();
        verify(gameRepository, times(1)).flush();
    }

    @Test
    public void finalizeGame_tie_marksMultipleWinners() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");
        user1.setTotalGamesPlayed(0);
        user1.setTotalWins(0);
        user1.setTotalPoints(0);
        user1.setTotalCorrectPlacements(0);
        user1.setTotalIncorrectPlacements(0);

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");
        user2.setTotalGamesPlayed(0);
        user2.setTotalWins(0);
        user2.setTotalPoints(0);
        user2.setTotalCorrectPlacements(0);
        user2.setTotalIncorrectPlacements(0);

        GamePlayer gp1 = new GamePlayer();
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setScore(4);
        gp1.setCorrectPlacements(4);
        gp1.setIncorrectPlacements(1);

        GamePlayer gp2 = new GamePlayer();
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setScore(4);
        gp2.setCorrectPlacements(4);
        gp2.setIncorrectPlacements(0);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<FinalResultDTO> results = gameService.finalizeGame(1L);

        assertEquals(2, results.size());
        assertTrue(results.get(0).getWinner());
        assertTrue(results.get(1).getWinner());

        assertEquals(1, user1.getTotalWins());
        assertEquals(1, user2.getTotalWins());
    }

    @Test
    public void finalizeGame_notInProgress_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("WAITING");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.finalizeGame(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void placeCard_secondCorrectPlacement_appliesStreakBonus() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckSize(10);
        game.setNextCardIndex(4);

        EventCard card1 = new EventCard();
        card1.setTitle("Moon Landing");
        card1.setYear(1969);

        EventCard card2 = new EventCard();
        card2.setTitle("Berlin Wall");
        card2.setYear(1989);

        EventCard card3 = new EventCard();
        card3.setTitle("C");
        card3.setYear(1990);

        EventCard card4 = new EventCard();
        card4.setTitle("D");
        card4.setYear(1991);

        EventCard card5 = new EventCard();
        card5.setTitle("E");
        card5.setYear(1992);

        EventCard card6 = new EventCard();
        card6.setTitle("F");
        card6.setYear(1993);

        EventCard card7 = new EventCard();
        card7.setTitle("G");
        card7.setYear(1994);

        EventCard card8 = new EventCard();
        card8.setTitle("H");
        card8.setYear(1995);

        EventCard card9 = new EventCard();
        card9.setTitle("I");
        card9.setYear(1996);

        EventCard card10 = new EventCard();
        card10.setTitle("J");
        card10.setYear(1997);

        game.setDeckJson(gameService.serializeDeck(List.of(card1, card2, card3, card4, card5, card6, card7, card8, card9, card10)));

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer gp1 = new GamePlayer();
        gp1.setId(100L);
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setTurnOrder(0);
        gp1.setScore(50);
        gp1.setActiveTurn(true);
        gp1.setCurrentCardIndex(1);
        gp1.setCorrectStreak(1);
        gp1.setBestStreak(1);
        gp1.setCardsInHand(4);
        gp1.setHandIndicesJson("[0,1,2,3]");
        gp1.setTurnStartedAt(Instant.now());

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(0);
        gp2.setActiveTurn(false);
        gp2.setCardsInHand(5);
        gp2.setHandIndicesJson("[5,6,7,8,9]");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));

        Object[] result = gameService.placeCard(1L, 1, 0);

        assertTrue((Boolean) result[1]);
        assertEquals(220, gp1.getScore());
        assertEquals(2, gp1.getCorrectStreak());
        assertEquals(2, gp1.getBestStreak());
        assertEquals(3, gp1.getCardsInHand());
    }

    @Test
    public void checkTurnTimeouts_resetsStreakClearsCardAndAdvancesTurn() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckSize(10);
        game.setNextCardIndex(5);

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer gp1 = new GamePlayer();
        gp1.setId(100L);
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setTurnOrder(0);
        gp1.setActiveTurn(true);
        gp1.setCurrentCardIndex(5);
        gp1.setCorrectStreak(3);
        gp1.setBestStreak(3);
        gp1.setTurnStartedAt(Instant.now().minusSeconds(31));
        gp1.setCardsInHand(3);

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setActiveTurn(false);
        gp2.setCurrentCardIndex(null);
        gp2.setCardsInHand(5);

        when(gamePlayerRepository.findByActiveTurnTrue()).thenReturn(List.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));

        gameService.checkTurnTimeouts();

        assertEquals(0, gp1.getCorrectStreak());
        assertNull(gp1.getCurrentCardIndex());
        assertFalse(gp1.getActiveTurn());
        assertTrue(gp2.getActiveTurn());
        assertNotNull(gp2.getTurnStartedAt());
    }

    @Test
    public void drawCard_cardNotInHand_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        EventCard card0 = new EventCard();
        card0.setTitle("A");
        card0.setYear(1000);

        EventCard card1 = new EventCard();
        card1.setTitle("B");
        card1.setYear(1500);

        game.setDeckJson(gameService.serializeDeck(List.of(card0, card1)));

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer player = new GamePlayer();
        player.setId(100L);
        player.setGame(game);
        player.setUser(user);
        player.setActiveTurn(true);
        player.setCurrentCardIndex(null);
        player.setHandIndicesJson("[0]");
        player.setCardsInHand(1);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.of(player));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.drawCard(1L, 10L, 1)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void placeCard_lastHandCard_finishesTimelineGame() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");
        game.setGameMode(GameMode.TIMELINE);
        game.setDeckSize(10);
        game.setNextCardIndex(5);

        List<EventCard> deck = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            EventCard c = new EventCard();
            c.setTitle("Event " + i);
            c.setYear(1900 + i);
            deck.add(c);
        }
        game.setDeckJson(gameService.serializeDeck(deck));

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");
        user1.setTotalGamesPlayed(0);
        user1.setTotalWins(0);
        user1.setTotalPoints(0);
        user1.setTotalCorrectPlacements(0);
        user1.setTotalIncorrectPlacements(0);

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");
        user2.setTotalGamesPlayed(0);
        user2.setTotalWins(0);
        user2.setTotalPoints(0);
        user2.setTotalCorrectPlacements(0);
        user2.setTotalIncorrectPlacements(0);

        GamePlayer gp1 = new GamePlayer();
        gp1.setId(100L);
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setTurnOrder(0);
        gp1.setScore(0);
        gp1.setActiveTurn(true);
        gp1.setCurrentCardIndex(0);
        gp1.setCardsInHand(1);
        gp1.setHandIndicesJson("[0]");
        gp1.setTurnStartedAt(Instant.now());

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(0);
        gp2.setActiveTurn(false);
        gp2.setCardsInHand(5);
        gp2.setHandIndicesJson("[5,6,7,8,9]");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Object[] result = gameService.placeCard(1L, 0, 0);

        assertTrue((Boolean) result[1]);
        assertEquals("FINISHED", game.getStatus());
        assertEquals(0, gp1.getCardsInHand());
        assertFalse(gp1.getActiveTurn());
    }

    @Test
    public void createRematch_finishedGame_createsNewWaitingGameWithSamePlayersAndSettings() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("FINISHED");
        oldGame.setEra(HistoricalEra.MODERN);
        oldGame.setDifficulty(Difficulty.EASY);
        oldGame.setGameMode(GameMode.TIMELINE);
        oldGame.setHostId(10L);
        oldGame.setLobbyCode("OLD123");
        oldGame.setDeckSize(80);
        oldGame.setNextCardIndex(80);
        oldGame.setTimelineJson("[{}]");

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer oldGp1 = new GamePlayer();
        oldGp1.setId(100L);
        oldGp1.setGame(oldGame);
        oldGp1.setUser(user1);
        oldGp1.setTurnOrder(0);
        oldGp1.setScore(300);
        oldGp1.setCardsInHand(0);
        oldGp1.setCorrectPlacements(5);
        oldGp1.setIncorrectPlacements(1);
        oldGp1.setCorrectStreak(2);
        oldGp1.setBestStreak(4);

        GamePlayer oldGp2 = new GamePlayer();
        oldGp2.setId(101L);
        oldGp2.setGame(oldGame);
        oldGp2.setUser(user2);
        oldGp2.setTurnOrder(1);
        oldGp2.setScore(250);
        oldGp2.setCardsInHand(2);
        oldGp2.setCorrectPlacements(4);
        oldGp2.setIncorrectPlacements(2);
        oldGp2.setCorrectStreak(1);
        oldGp2.setBestStreak(3);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(oldGame));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame)).thenReturn(List.of(oldGp1, oldGp2));

        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(2L);
            }
            return saved;
        });

        Game rematch = gameService.createRematch(1L, 10L);

        assertNotNull(rematch);
        assertEquals(2L, rematch.getId());
        assertEquals("WAITING", rematch.getStatus());
        assertEquals(HistoricalEra.MODERN, rematch.getEra());
        assertEquals(Difficulty.EASY, rematch.getDifficulty());
        assertEquals(GameMode.TIMELINE, rematch.getGameMode());
        assertEquals(10L, rematch.getHostId());
        assertEquals(0, rematch.getDeckSize());
        assertEquals(0, rematch.getNextCardIndex());
        assertEquals("[]", rematch.getTimelineJson());
        assertEquals(1L, rematch.getRematchFromGameId());

        verify(gamePlayerRepository, times(2)).save(argThat(newGp ->
                newGp.getGame() != null &&
                        newGp.getGame().getId().equals(2L) &&
                        newGp.getScore().equals(0) &&
                        newGp.getActiveTurn().equals(false) &&
                        newGp.getCurrentCardIndex() == null &&
                        newGp.getCardsInHand().equals(0) &&
                        newGp.getCorrectPlacements().equals(0) &&
                        newGp.getIncorrectPlacements().equals(0) &&
                        newGp.getCorrectStreak().equals(0) &&
                        newGp.getBestStreak().equals(0) &&
                        newGp.getTurnStartedAt() == null
        ));
    }

    @Test
    public void createRematch_gameNotFinished_throwsException() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("IN_PROGRESS");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(oldGame));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.createRematch(1L, 10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(gamePlayerRepository, never()).save(any(GamePlayer.class));
    }

    @Test
    public void createRematch_requestingUserNotInOldGame_throwsException() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("FINISHED");

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        GamePlayer oldGp1 = new GamePlayer();
        oldGp1.setId(100L);
        oldGp1.setGame(oldGame);
        oldGp1.setUser(user1);
        oldGp1.setTurnOrder(0);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(oldGame));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame)).thenReturn(List.of(oldGp1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.createRematch(1L, 999L)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(gamePlayerRepository, never()).save(any(GamePlayer.class));
    }

    @Test
    public void finalizeGame_validInput_includesBestStreakInResults() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");
        user1.setTotalGamesPlayed(0);
        user1.setTotalWins(0);
        user1.setTotalPoints(0);
        user1.setTotalCorrectPlacements(0);
        user1.setTotalIncorrectPlacements(0);

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");
        user2.setTotalGamesPlayed(0);
        user2.setTotalWins(0);
        user2.setTotalPoints(0);
        user2.setTotalCorrectPlacements(0);
        user2.setTotalIncorrectPlacements(0);

        GamePlayer gp1 = new GamePlayer();
        gp1.setGame(game);
        gp1.setUser(user1);
        gp1.setScore(5);
        gp1.setCorrectPlacements(5);
        gp1.setIncorrectPlacements(1);
        gp1.setBestStreak(4);

        GamePlayer gp2 = new GamePlayer();
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setScore(3);
        gp2.setCorrectPlacements(3);
        gp2.setIncorrectPlacements(2);
        gp2.setBestStreak(2);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<FinalResultDTO> results = gameService.finalizeGame(1L);

        assertEquals(2, results.size());
        assertEquals(4, results.get(0).getBestStreak());
        assertEquals(2, results.get(1).getBestStreak());
    }

    @Test
    public void createRematch_preservesTurnOrderOfPlayers() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("FINISHED");
        oldGame.setEra(HistoricalEra.MODERN);
        oldGame.setDifficulty(Difficulty.EASY);
        oldGame.setGameMode(GameMode.TIMELINE);
        oldGame.setHostId(10L);

        User user1 = new User();
        user1.setId(10L);
        user1.setUsername("alex");

        User user2 = new User();
        user2.setId(11L);
        user2.setUsername("mia");

        GamePlayer oldGp1 = new GamePlayer();
        oldGp1.setGame(oldGame);
        oldGp1.setUser(user1);
        oldGp1.setTurnOrder(0);

        GamePlayer oldGp2 = new GamePlayer();
        oldGp2.setGame(oldGame);
        oldGp2.setUser(user2);
        oldGp2.setTurnOrder(1);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(oldGame));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame)).thenReturn(List.of(oldGp1, oldGp2));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        gameService.createRematch(1L, 10L);

        verify(gamePlayerRepository).save(argThat(gp ->
                gp.getUser().getId().equals(10L) && gp.getTurnOrder().equals(0)
        ));
        verify(gamePlayerRepository).save(argThat(gp ->
                gp.getUser().getId().equals(11L) && gp.getTurnOrder().equals(1)
        ));
    }

    @Test
    public void createRematch_finishedGameWithoutPlayers_throwsException() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("FINISHED");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(oldGame));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame)).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.createRematch(1L, 10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void createRematch_preservesOriginalHost_notRequestingUser() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("FINISHED");
        oldGame.setEra(HistoricalEra.MODERN);
        oldGame.setDifficulty(Difficulty.EASY);
        oldGame.setGameMode(GameMode.TIMELINE);
        oldGame.setHostId(10L);

        User host = new User();
        host.setId(10L);
        host.setUsername("alex");

        User requester = new User();
        requester.setId(11L);
        requester.setUsername("mia");

        GamePlayer gp1 = new GamePlayer();
        gp1.setGame(oldGame);
        gp1.setUser(host);
        gp1.setTurnOrder(0);

        GamePlayer gp2 = new GamePlayer();
        gp2.setGame(oldGame);
        gp2.setUser(requester);
        gp2.setTurnOrder(1);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(oldGame));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame)).thenReturn(List.of(gp1, gp2));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        Game rematch = gameService.createRematch(1L, 11L);

        assertEquals(10L, rematch.getHostId());
    }

    @Test
    public void createRematch_resetsHandIndicesJson() {
        Game oldGame = new Game();
        oldGame.setId(1L);
        oldGame.setStatus("FINISHED");
        oldGame.setEra(HistoricalEra.MODERN);
        oldGame.setDifficulty(Difficulty.EASY);
        oldGame.setGameMode(GameMode.TIMELINE);
        oldGame.setHostId(10L);

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer oldGp = new GamePlayer();
        oldGp.setGame(oldGame);
        oldGp.setUser(user);
        oldGp.setTurnOrder(0);
        oldGp.setHandIndicesJson("[1,2,3]");
        oldGp.setCardsInHand(3);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(oldGame));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame)).thenReturn(List.of(oldGp));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        gameService.createRematch(1L, 10L);

        verify(gamePlayerRepository).save(argThat(newGp ->
                newGp.getHandIndicesJson() == null &&
                        newGp.getCardsInHand().equals(0)
        ));
    }

    @Test
    public void drawCard_userNotInGame_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(gamePlayerRepository.findByGameAndUser(game, user)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.drawCard(1L, 10L, 0)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    public void drawCard_userNotFound_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.drawCard(1L, 10L, 0)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    public void drawCard_gameNotInProgress_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("WAITING");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.drawCard(1L, 10L, 0)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void placeCard_invalidPosition_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setTimelineJson("[]");

        EventCard card = new EventCard();
        card.setTitle("A");
        card.setYear(1000);
        game.setDeckJson(gameService.serializeDeck(List.of(card)));

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer activePlayer = new GamePlayer();
        activePlayer.setGame(game);
        activePlayer.setUser(user);
        activePlayer.setActiveTurn(true);
        activePlayer.setCurrentCardIndex(0);
        activePlayer.setHandIndicesJson("[0]");
        activePlayer.setCardsInHand(1);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(activePlayer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.placeCard(1L, 0, 2)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    public void placeCard_noActivePlayer_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.placeCard(1L, 0, 0)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    public void finalizeGame_noPlayers_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game)).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.finalizeGame(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
