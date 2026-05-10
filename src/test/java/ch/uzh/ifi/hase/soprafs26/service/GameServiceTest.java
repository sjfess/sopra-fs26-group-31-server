package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HandCardDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GameServiceTest {

    @Mock private GameLobbyService gameLobbyService;
    @Mock private GameStartService gameStartService;
    @Mock private TimelineGameService timelineGameService;
    @Mock private GameInviteService gameInviteService;
    @Mock private GameChatService gameChatService;
    @Mock private GameFinalizationService gameFinalizationService;

    private GameService gameService;

    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        gameService = new GameService(
                gameLobbyService,
                gameStartService,
                timelineGameService,
                gameInviteService,
                gameChatService,
                gameFinalizationService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ── Lobby ────────────────────────────────────────────────────────────

    @Test
    void createGame_delegatesToGameLobbyService() {
        Game game = new Game();
        when(gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, 1L)).thenReturn(game);
        Game result = gameService.createGame(HistoricalEra.MODERN, Difficulty.EASY, 1L);
        assertSame(game, result);
        verify(gameLobbyService).createGame(HistoricalEra.MODERN, Difficulty.EASY, 1L);
    }

    @Test
    void joinGame_delegatesToGameLobbyService() {
        Game game = new Game();
        when(gameLobbyService.joinGame("ABC123", 1L)).thenReturn(game);
        Game result = gameService.joinGame("ABC123", 1L);
        assertSame(game, result);
        verify(gameLobbyService).joinGame("ABC123", 1L);
    }

    @Test
    void leaveGame_delegatesToGameLobbyService() {
        gameService.leaveGame("ABC123", 1L);
        verify(gameLobbyService).leaveGame("ABC123", 1L);
    }

    @Test
    void updateSettings_delegatesToGameLobbyService() {
        Game game = new Game();
        GameSettingsPutDTO dto = new GameSettingsPutDTO();
        when(gameLobbyService.updateSettings(1L, dto)).thenReturn(game);
        Game result = gameService.updateSettings(1L, dto);
        assertSame(game, result);
        verify(gameLobbyService).updateSettings(1L, dto);
    }

    @Test
    void createRematch_delegatesToGameLobbyService() {
        Game game = new Game();
        when(gameLobbyService.createRematch(1L, 2L)).thenReturn(game);
        Game result = gameService.createRematch(1L, 2L);
        assertSame(game, result);
        verify(gameLobbyService).createRematch(1L, 2L);
    }

    @Test
    void createRematchAndCloseOldGame_delegatesToGameLobbyService() {
        Game game = new Game();
        when(gameLobbyService.createRematchAndCloseOldGame(1L, 2L)).thenReturn(game);
        Game result = gameService.createRematchAndCloseOldGame(1L, 2L);
        assertSame(game, result);
        verify(gameLobbyService).createRematchAndCloseOldGame(1L, 2L);
    }

    @Test
    void findWaitingRematchId_delegatesToGameLobbyService() {
        when(gameLobbyService.findWaitingRematchId(1L)).thenReturn(Optional.of(2L));
        Optional<Long> result = gameService.findWaitingRematchId(1L);
        assertTrue(result.isPresent());
        assertEquals(2L, result.get());
        verify(gameLobbyService).findWaitingRematchId(1L);
    }

    @Test
    void closeFinishedGame_delegatesToGameLobbyService() {
        gameService.closeFinishedGame(1L, 2L);
        verify(gameLobbyService).closeFinishedGame(1L, 2L);
    }

    @Test
    void checkTurnTimeouts_delegatesToGameLobbyService() {
        gameService.checkTurnTimeouts();
        verify(gameLobbyService).checkTurnTimeouts();
    }

    @Test
    void cleanupAbandonedGames_delegatesToGameLobbyService() {
        gameService.cleanupAbandonedGames();
        verify(gameLobbyService).cleanupAbandonedGames();
    }

    // ── Start ────────────────────────────────────────────────────────────

    @Test
    void startGame_delegatesToGameStartService() {
        Game game = new Game();
        when(gameStartService.startGame(1L, 10)).thenReturn(game);
        Game result = gameService.startGame(1L, 10);
        assertSame(game, result);
        verify(gameStartService).startGame(1L, 10);
    }

    @Test
    void getGame_delegatesToGameLobbyService() {
        Game game = new Game();
        when(gameLobbyService.getGame(1L)).thenReturn(game);
        Game result = gameService.getGame(1L);
        assertSame(game, result);
        verify(gameLobbyService).getGame(1L);
    }

    // ── Timeline ─────────────────────────────────────────────────────────

    @Test
    void drawCard_delegatesToTimelineGameService() {
        EventCard card = new EventCard();
        when(timelineGameService.drawCard(1L, 2L, 0)).thenReturn(card);
        EventCard result = gameService.drawCard(1L, 2L, 0);
        assertSame(card, result);
        verify(timelineGameService).drawCard(1L, 2L, 0);
    }

    @Test
    void placeCard_delegatesToTimelineGameService() {
        EventCard card = new EventCard();
        GameService.PlacementResult placementResult = new GameService.PlacementResult(card, true, 3);
        when(timelineGameService.placeCard(1L, 0, 0)).thenReturn(placementResult);
        GameService.PlacementResult result = gameService.placeCard(1L, 0, 0);
        assertSame(placementResult, result);
        verify(timelineGameService).placeCard(1L, 0, 0);
    }

    @Test
    void getTimeline_delegatesToTimelineGameService() {
        List<EventCard> timeline = List.of(new EventCard());
        when(timelineGameService.getTimeline(1L)).thenReturn(timeline);
        List<EventCard> result = gameService.getTimeline(1L);
        assertSame(timeline, result);
        verify(timelineGameService).getTimeline(1L);
    }

    @Test
    void getAllCards_delegatesToTimelineGameService() {
        List<EventCard> cards = List.of(new EventCard());
        when(timelineGameService.getAllCards(1L)).thenReturn(cards);
        List<EventCard> result = gameService.getAllCards(1L);
        assertSame(cards, result);
        verify(timelineGameService).getAllCards(1L);
    }

    @Test
    void getLiveScores_delegatesToTimelineGameService() {
        List<GamePlayerScoreDTO> scores = List.of(new GamePlayerScoreDTO());
        when(timelineGameService.getLiveScores(1L)).thenReturn(scores);
        List<GamePlayerScoreDTO> result = gameService.getLiveScores(1L);
        assertSame(scores, result);
        verify(timelineGameService).getLiveScores(1L);
    }

    // ── Finalization ─────────────────────────────────────────────────────

    @Test
    void finalizeGame_delegatesToGameFinalizationService() {
        List<FinalResultDTO> results = List.of(new FinalResultDTO());
        when(gameFinalizationService.finalizeGame(1L)).thenReturn(results);
        List<FinalResultDTO> result = gameService.finalizeGame(1L);
        assertSame(results, result);
        verify(gameFinalizationService).finalizeGame(1L);
    }

    // ── Chat ─────────────────────────────────────────────────────────────

    @Test
    void addChatMessage_delegatesToGameChatService() {
        ChatMessageGetDTO dto = new ChatMessageGetDTO();
        when(gameChatService.addChatMessage(1L, 2L, "Hello")).thenReturn(dto);
        ChatMessageGetDTO result = gameService.addChatMessage(1L, 2L, "Hello");
        assertSame(dto, result);
        verify(gameChatService).addChatMessage(1L, 2L, "Hello");
    }

    @Test
    void getChatMessages_delegatesToGameChatService() {
        List<ChatMessageGetDTO> messages = List.of(new ChatMessageGetDTO());
        when(gameChatService.getChatMessages(1L)).thenReturn(messages);
        List<ChatMessageGetDTO> result = gameService.getChatMessages(1L);
        assertSame(messages, result);
        verify(gameChatService).getChatMessages(1L);
    }

    // ── Invites ──────────────────────────────────────────────────────────

    @Test
    void invitePlayer_delegatesToGameInviteService() {
        gameService.invitePlayer(1L, 2L, "alex");
        verify(gameInviteService).invitePlayer(1L, 2L, "alex");
    }

    @Test
    void getInvitesForUser_delegatesToGameInviteService() {
        List<GameInviteGetDTO> invites = List.of(new GameInviteGetDTO());
        when(gameInviteService.getInvitesForUser(1L)).thenReturn(invites);
        List<GameInviteGetDTO> result = gameService.getInvitesForUser(1L);
        assertSame(invites, result);
        verify(gameInviteService).getInvitesForUser(1L);
    }

    @Test
    void deleteInvite_delegatesToGameInviteService() {
        gameService.deleteInvite(1L);
        verify(gameInviteService).deleteInvite(1L);
    }

    /** card delegetion tests */

    @Test
    void getCard_delegatesToTimelineGameService() {
        EventCard card = new EventCard();
        when(timelineGameService.getCard(1L, 3)).thenReturn(card);
        EventCard result = gameService.getCard(1L, 3);
        assertSame(card, result);
        verify(timelineGameService).getCard(1L, 3);
    }

    @Test
    void getHand_delegatesToTimelineGameService() {
        List<HandCardDTO> hand = List.of(new HandCardDTO());
        when(timelineGameService.getHand(1L, 2L)).thenReturn(hand);
        List<HandCardDTO> result = gameService.getHand(1L, 2L);
        assertSame(hand, result);
        verify(timelineGameService).getHand(1L, 2L);
    }
}