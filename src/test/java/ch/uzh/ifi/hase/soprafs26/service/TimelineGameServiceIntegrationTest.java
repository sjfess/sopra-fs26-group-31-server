package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HandCardDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@WebAppConfiguration
@SpringBootTest
public class TimelineGameServiceIntegrationTest {

    @Autowired private TimelineGameService timelineGameService;
    @Autowired private GameRepository gameRepository;
    @Autowired private GamePlayerRepository gamePlayerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private GameCardHelper gameCardHelper;
    @MockitoBean
    private CardPreloader cardPreloader;

    private User user1;
    private User user2;
    private Game game;
    private GamePlayer player1;
    private GamePlayer player2;

    private static final String DECK_JSON =
            "[{\"title\":\"Event A\",\"year\":1000,\"imageUrl\":null,\"wikidataId\":\"Q1\"}," +
                    "{\"title\":\"Event B\",\"year\":2000,\"imageUrl\":null,\"wikidataId\":\"Q2\"}," +
                    "{\"title\":\"Event C\",\"year\":1500,\"imageUrl\":null,\"wikidataId\":\"Q3\"}," +
                    "{\"title\":\"Event D\",\"year\":500,\"imageUrl\":null,\"wikidataId\":\"Q4\"}," +
                    "{\"title\":\"Event E\",\"year\":1800,\"imageUrl\":null,\"wikidataId\":\"Q5\"}]";

    @BeforeEach
    void setup() {
        gamePlayerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();

        user1 = new User();
        user1.setUsername("timeline_user1");
        user1.setPassword("hashed"); user1.setSalt("s1");
        user1.setToken("token-tl-1");
        user1.setStatus(UserStatus.IN_GAME); user1.setBio("");
        user1.setCreationDate(Instant.now());
        user1.setTotalGamesPlayed(0); user1.setTotalWins(0); user1.setTotalPoints(0);
        user1.setTotalCorrectPlacements(0); user1.setTotalIncorrectPlacements(0);
        user1 = userRepository.saveAndFlush(user1);

        user2 = new User();
        user2.setUsername("timeline_user2");
        user2.setPassword("hashed"); user2.setSalt("s2");
        user2.setToken("token-tl-2");
        user2.setStatus(UserStatus.IN_GAME); user2.setBio("");
        user2.setCreationDate(Instant.now());
        user2.setTotalGamesPlayed(0); user2.setTotalWins(0); user2.setTotalPoints(0);
        user2.setTotalCorrectPlacements(0); user2.setTotalIncorrectPlacements(0);
        user2 = userRepository.saveAndFlush(user2);

        game = new Game();
        game.setStatus("IN_PROGRESS");
        game.setGameMode(GameMode.TIMELINE);
        game.setEra(HistoricalEra.MODERN);
        game.setDifficulty(Difficulty.EASY);
        game.setLobbyCode("TLTEST1");
        game.setHostId(user1.getId());
        game.setDeckJson(DECK_JSON);
        game.setDeckSize(5);
        game.setNextCardIndex(2);
        game.setTimelineJson("[]");
        game = gameRepository.saveAndFlush(game);

        player1 = new GamePlayer();
        player1.setGame(game); player1.setUser(user1);
        player1.setHandIndicesJson("[0]"); player1.setCardsInHand(1);
        player1.setScore(0); player1.setCorrectPlacements(0);
        player1.setIncorrectPlacements(0); player1.setCorrectStreak(0);
        player1.setBestStreak(0); player1.setTurnOrder(0);
        player1.setActiveTurn(true);
        player1.setTurnStartedAt(Instant.now());
        player1.setCurrentCardIndex(null);
        player1 = gamePlayerRepository.saveAndFlush(player1);

        player2 = new GamePlayer();
        player2.setGame(game); player2.setUser(user2);
        player2.setHandIndicesJson("[1]"); player2.setCardsInHand(1);
        player2.setScore(0); player2.setCorrectPlacements(0);
        player2.setIncorrectPlacements(0); player2.setCorrectStreak(0);
        player2.setBestStreak(0); player2.setTurnOrder(1);
        player2.setActiveTurn(false);
        player2.setCurrentCardIndex(null);
        player2 = gamePlayerRepository.saveAndFlush(player2);
    }

    @AfterEach
    void tearDown() {
        gamePlayerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void drawCard_setsCurrentCardIndex_persistedInDB() {
        timelineGameService.drawCard(game.getId(), user1.getId(), 0);

        GamePlayer reloaded = gamePlayerRepository.findById(player1.getId()).orElseThrow();
        assertEquals(0, reloaded.getCurrentCardIndex());
    }

    @Test
    void drawCard_notActiveTurn_throwsConflict() {
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.drawCard(game.getId(), user2.getId(), 1));
    }

    @Test
    void drawCard_cardNotInHand_throwsConflict() {
        // player1 hat nur Index 0, nicht Index 1
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.drawCard(game.getId(), user1.getId(), 1));
    }

    @Test
    void drawCard_returnsCorrectCard() {
        EventCard card = timelineGameService.drawCard(game.getId(), user1.getId(), 0);
        assertEquals("Event A", card.getTitle());
        assertEquals(1000, card.getYear());
    }


    @Test
    void getTimeline_emptyTimeline_returnsEmptyList() {
        List<EventCard> timeline = timelineGameService.getTimeline(game.getId());
        assertTrue(timeline.isEmpty());
    }

    @Test
    void getTimeline_gameNotFound_throwsNotFound() {
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.getTimeline(999L));
    }


    @Test
    void getHand_returnsCorrectCards_forPlayer1() {
        List<HandCardDTO> hand = timelineGameService.getHand(game.getId(), user1.getId());
        assertEquals(1, hand.size());
        assertEquals("Event A", hand.get(0).getTitle());
        assertEquals(0, hand.get(0).getDeckIndex());
    }

    @Test
    void getHand_returnsCorrectCards_forPlayer2() {
        List<HandCardDTO> hand = timelineGameService.getHand(game.getId(), user2.getId());
        assertEquals(1, hand.size());
        assertEquals("Event B", hand.get(0).getTitle());
        assertEquals(1, hand.get(0).getDeckIndex());
    }

    @Test
    void getHand_gameNotInProgress_throwsConflict() {
        game.setStatus("WAITING");
        gameRepository.saveAndFlush(game);
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.getHand(game.getId(), user1.getId()));
    }


    @Test
    void getCard_validIndex_returnsCard() {
        EventCard card = timelineGameService.getCard(game.getId(), 2);
        assertEquals("Event C", card.getTitle());
        assertEquals(1500, card.getYear());
    }

    @Test
    void getCard_outOfRange_throwsNotFound() {
        assertThrows(ResponseStatusException.class,
                () -> timelineGameService.getCard(game.getId(), 99));
    }


    @Test
    void getLiveScores_returnsAllPlayers_sortedByScore() {
        player1.setScore(300);
        player2.setScore(100);
        gamePlayerRepository.saveAndFlush(player1);
        gamePlayerRepository.saveAndFlush(player2);

        List<GamePlayerScoreDTO> scores = timelineGameService.getLiveScores(game.getId());

        assertEquals(2, scores.size());
        assertEquals("timeline_user1", scores.get(0).getUsername());
        assertEquals(300, scores.get(0).getScore());
        assertEquals("timeline_user2", scores.get(1).getUsername());
        assertEquals(100, scores.get(1).getScore());
    }


    @Test
    void placeCard_correct_updatesScoreAndTimeline_inDB() {
        timelineGameService.drawCard(game.getId(), user1.getId(), 0);

        GameService.PlacementResult result =
                timelineGameService.placeCard(game.getId(), 0, 0);

        assertTrue(result.correct());
        assertEquals("Event A", result.card().getTitle());

        GamePlayer reloaded = gamePlayerRepository.findById(player1.getId()).orElseThrow();
        assertTrue(reloaded.getScore() > 0);
        assertEquals(1, reloaded.getCorrectPlacements());
        assertEquals(1, reloaded.getCorrectStreak());

        Game reloadedGame = gameRepository.findById(game.getId()).orElseThrow();
        List<EventCard> timeline = gameCardHelper.deserializeDeck(reloadedGame.getTimelineJson());
        assertEquals(1, timeline.size());
        assertEquals("Event A", timeline.get(0).getTitle());
    }

    @Test
    void placeCard_correct_removesCardFromHand_inDB() {
        timelineGameService.drawCard(game.getId(), user1.getId(), 0);
        timelineGameService.placeCard(game.getId(), 0, 0);

        GamePlayer reloaded = gamePlayerRepository.findById(player1.getId()).orElseThrow();
        List<Integer> hand = gameCardHelper.deserializeHandIndices(reloaded.getHandIndicesJson());
        assertFalse(hand.contains(0), "Karte muss aus der Hand entfernt werden");
    }

    @Test
    void placeCard_correct_advancesTurnToNextPlayer_inDB() {
        timelineGameService.drawCard(game.getId(), user1.getId(), 0);
        GameService.PlacementResult result = timelineGameService.placeCard(game.getId(), 0, 0);

        // advanceTurn explizit aufrufen, wie der Controller es tut
        if (result.correct()) {
            timelineGameService.advanceTurn(game, player1);
        }

        GamePlayer reloadedP1 = gamePlayerRepository.findById(player1.getId()).orElseThrow();
        GamePlayer reloadedP2 = gamePlayerRepository.findById(player2.getId()).orElseThrow();

        assertFalse(reloadedP1.getActiveTurn());
        assertTrue(reloadedP2.getActiveTurn());
        assertNotNull(reloadedP2.getTurnStartedAt());
    }


    @Test
    void placeCard_incorrect_resetsStreak_andDealsPenaltyCard_inDB() {
        game.setTimelineJson(
                "[{\"title\":\"Event B\",\"year\":2000,\"imageUrl\":null,\"wikidataId\":\"Q2\"}]");
        gameRepository.saveAndFlush(game);

        timelineGameService.drawCard(game.getId(), user1.getId(), 0);

        GameService.PlacementResult result =
                timelineGameService.placeCard(game.getId(), 0, 1);

        assertFalse(result.correct());

        GamePlayer reloaded = gamePlayerRepository.findById(player1.getId()).orElseThrow();
        assertEquals(0, reloaded.getCorrectStreak());
        assertEquals(1, reloaded.getIncorrectPlacements());
        assertTrue(reloaded.getCardsInHand() >= 1);
    }


    @Test
    void isTimelineGameFinished_deckExhaustedAndNoCards_returnsTrue() {
        game.setNextCardIndex(5); // == deckSize
        gameRepository.saveAndFlush(game);
        player1.setCardsInHand(0);
        player2.setCardsInHand(0);
        gamePlayerRepository.saveAndFlush(player1);
        gamePlayerRepository.saveAndFlush(player2);

        assertTrue(timelineGameService.isTimelineGameFinished(game));
    }

    @Test
    void isTimelineGameFinished_playersStillHaveCards_returnsFalse() {
        game.setNextCardIndex(3); // noch Karten im Deck
        gameRepository.saveAndFlush(game);
        player1.setCardsInHand(2);
        gamePlayerRepository.saveAndFlush(player1);

        assertFalse(timelineGameService.isTimelineGameFinished(game));
    }

    @Test
    void advanceTurn_switchesActivePlayer_inDB() {
        timelineGameService.advanceTurn(game, player1);

        GamePlayer reloadedP1 = gamePlayerRepository.findById(player1.getId()).orElseThrow();
        GamePlayer reloadedP2 = gamePlayerRepository.findById(player2.getId()).orElseThrow();

        assertFalse(reloadedP1.getActiveTurn());
        assertTrue(reloadedP2.getActiveTurn());
        assertNotNull(reloadedP2.getTurnStartedAt());
    }
}