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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        gameRepository = Mockito.mock(GameRepository.class);
        gamePlayerRepository = Mockito.mock(GamePlayerRepository.class);
        wikidataService = Mockito.mock(WikidataService.class);
        userRepository = Mockito.mock(UserRepository.class);
        gameService = new GameService(gameRepository, gamePlayerRepository, userRepository, wikidataService);

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

        EventCard c1 = new EventCard();
        c1.setTitle("Moon Landing");
        c1.setYear(1969);

        EventCard c2 = new EventCard();
        c2.setTitle("Fall of Berlin Wall");
        c2.setYear(1989);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));
        when(wikidataService.fetchEvents(HistoricalEra.MODERN, 2)).thenReturn(List.of(c1, c2));

        Game startedGame = gameService.startGame(1L, 2);

        assertEquals("IN_PROGRESS", startedGame.getStatus());
        assertEquals(2, startedGame.getDeckSize());
        assertEquals(0, startedGame.getNextCardIndex());
        assertNotNull(startedGame.getDeckJson());

        assertEquals(0, gp1.getScore());
        assertTrue(gp1.getActiveTurn());
        assertEquals(0, gp2.getScore());
        assertFalse(gp2.getActiveTurn());
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

        GamePlayer gp2 = new GamePlayer();
        gp2.setUser(user2);
        gp2.setScore(5);
        gp2.setTurnOrder(1);
        gp2.setActiveTurn(true);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game)).thenReturn(List.of(gp2, gp1));

        List<GamePlayerScoreDTO> scores = gameService.getLiveScores(1L);

        assertEquals(2, scores.size());
        assertEquals("mia", scores.get(0).getUsername());
        assertEquals(5, scores.get(0).getScore());
        assertTrue(scores.get(0).getActiveTurn());

        assertEquals("alex", scores.get(1).getUsername());
        assertEquals(3, scores.get(1).getScore());
    }

    @Test
    public void placeCard_correctPlacement_increasesScoreAndAdvancesTurn() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");

        EventCard card = new EventCard();
        card.setTitle("Moon Landing");
        card.setYear(1969);

        String deckJson = gameService.serializeDeck(List.of(card));
        game.setDeckJson(deckJson);

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

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(0);
        gp2.setActiveTurn(false);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));

        Object[] result = gameService.placeCard(1L, 0, 0);

        assertTrue((Boolean) result[1]);
        assertEquals(1, gp1.getScore());
        assertFalse(gp1.getActiveTurn());
        assertTrue(gp2.getActiveTurn());
    }

    @Test
    public void placeCard_wrongPlacement_doesNotIncreaseScoreAndAdvancesTurn() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");

        EventCard wrongCard = new EventCard();
        wrongCard.setTitle("Modern Event");
        wrongCard.setYear(2000);

        EventCard existingTimelineCard = new EventCard();
        existingTimelineCard.setTitle("Older Event");
        existingTimelineCard.setYear(1000);

        game.setDeckJson(gameService.serializeDeck(List.of(wrongCard)));
        game.setTimelineJson(gameService.serializeDeck(List.of(existingTimelineCard)));

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

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(0);
        gp2.setActiveTurn(false);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));

        Object[] result = gameService.placeCard(1L, 0, 0);

        assertFalse((Boolean) result[1]);
        assertEquals(2, gp1.getScore());
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

        EventCard card = new EventCard();
        card.setTitle("Moon Landing");
        card.setYear(1969);

        game.setDeckJson(gameService.serializeDeck(List.of(card)));
        game.setNextCardIndex(0);

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer activePlayer = new GamePlayer();
        activePlayer.setId(100L);
        activePlayer.setGame(game);
        activePlayer.setUser(user);
        activePlayer.setActiveTurn(true);
        activePlayer.setCurrentCardIndex(null);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(activePlayer));

        EventCard drawnCard = gameService.drawCard(1L);

        assertEquals("Moon Landing", drawnCard.getTitle());
        assertEquals(0, activePlayer.getCurrentCardIndex());
        assertEquals(1, game.getNextCardIndex());
    }

    @Test
    public void drawCard_activePlayerAlreadyHasCard_throwsException() {
        Game game = new Game();
        game.setId(1L);
        game.setStatus("IN_PROGRESS");
        game.setDeckJson("[]");
        game.setNextCardIndex(0);

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer activePlayer = new GamePlayer();
        activePlayer.setId(100L);
        activePlayer.setGame(game);
        activePlayer.setUser(user);
        activePlayer.setActiveTurn(true);
        activePlayer.setCurrentCardIndex(5);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(activePlayer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> gameService.drawCard(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
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
        game.setDeckJson("[]");

        User user = new User();
        user.setId(10L);
        user.setUsername("alex");

        GamePlayer activePlayer = new GamePlayer();
        activePlayer.setId(100L);
        activePlayer.setGame(game);
        activePlayer.setUser(user);
        activePlayer.setActiveTurn(true);
        activePlayer.setCurrentCardIndex(null);

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

        EventCard card = new EventCard();
        card.setTitle("Moon Landing");
        card.setYear(1969);

        game.setDeckJson(gameService.serializeDeck(List.of(card)));

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

        GamePlayer gp2 = new GamePlayer();
        gp2.setId(101L);
        gp2.setGame(game);
        gp2.setUser(user2);
        gp2.setTurnOrder(1);
        gp2.setScore(0);
        gp2.setActiveTurn(false);
        gp2.setCurrentCardIndex(null);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameAndActiveTurnTrue(game)).thenReturn(Optional.of(gp1));
        when(gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game)).thenReturn(List.of(gp1, gp2));

        gameService.placeCard(1L, 0, 0);

        assertNull(gp1.getCurrentCardIndex());
    }
}