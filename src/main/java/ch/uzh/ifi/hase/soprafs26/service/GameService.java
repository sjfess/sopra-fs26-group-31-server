package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameInviteGetDTO;

import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;

import java.util.List;
import java.util.Optional;



@Service
public class GameService {

    private final GameFinalizationService gameFinalizationService;

    public record PlacementResult(EventCard card, boolean correct, int timelineSize) {}

    private final TimelineGameService timelineGameService;
    private final GameLobbyService gameLobbyService;
    private final GameStartService gameStartService;
    private final GameInviteService gameInviteService;
    private final GameChatService gameChatService;





    public GameService(
            GameLobbyService gameLobbyService,
            GameStartService gameStartService,
            TimelineGameService timelineGameService,
            GameInviteService gameInviteService,
            GameChatService gameChatService,
            GameFinalizationService gameFinalizationService) {
        this.gameLobbyService = gameLobbyService;
        this.gameStartService = gameStartService;
        this.timelineGameService = timelineGameService;
        this.gameInviteService = gameInviteService;
        this.gameChatService = gameChatService;
        this.gameFinalizationService = gameFinalizationService;
    }


    public Game createGame(HistoricalEra era, Difficulty difficulty, Long userId) {
        return gameLobbyService.createGame(era, difficulty, userId);
    }

    public Game joinGame(String lobbyCode, Long userId) {
        return  gameLobbyService.joinGame(lobbyCode, userId);
    }

    @Transactional
    public Game createRematch(Long finishedGameId, Long requestingUserId) {
        return gameLobbyService.createRematch(finishedGameId, requestingUserId);
    }



    @Transactional
    public Game createRematchAndCloseOldGame(Long finishedGameId, Long requestingUserId) {
        return gameLobbyService.createRematchAndCloseOldGame(finishedGameId, requestingUserId);
    }
    public Optional<Long> findWaitingRematchId(Long gameId) {
        return gameLobbyService.findWaitingRematchId(gameId);
    }

    @Transactional
    public void closeFinishedGame(Long finishedGameId, Long requestingUserId) {
        gameLobbyService.closeFinishedGame(finishedGameId, requestingUserId);
    }


    @Transactional
    public Game startGame(Long gameId, int deckSize) {
       return gameStartService.startGame(gameId, deckSize);
    }

    public EventCard drawCard(Long gameId, Long userId, int deckIndex) {
        return timelineGameService.drawCard(gameId, userId, deckIndex);
    }

    public List<EventCard> getAllCards(Long gameId) {
        return timelineGameService.getAllCards(gameId);
    }

    public Game getGame(Long gameId) {
        return gameLobbyService.getGame(gameId);
    }

    @Transactional
    public PlacementResult placeCard(Long gameId, int cardIndex, int position) {
        return timelineGameService.placeCard(gameId, cardIndex, position);
    }

    public List<EventCard> getTimeline(Long gameId) {
        return timelineGameService.getTimeline(gameId);
    }

    public List<GamePlayerScoreDTO> getLiveScores(Long gameId) {
        return timelineGameService.getLiveScores(gameId);
    }

    @Transactional
    public void leaveGame(String lobbyCode, Long userId) {
        gameLobbyService.leaveGame(lobbyCode, userId);
    }

    public Game updateSettings(Long gameId, GameSettingsPutDTO dto) {
        return gameLobbyService.updateSettings(gameId, dto);
    }

    @Transactional
    public ChatMessageGetDTO addChatMessage(Long gameId, Long playerId, String message) {
        return gameChatService.addChatMessage(gameId, playerId, message);
    }

    public List<ChatMessageGetDTO> getChatMessages(Long gameId) {
        List<ChatMessageGetDTO> chatMessages;
        chatMessages = gameChatService.getChatMessages(gameId);
        return chatMessages;
    }

    @Transactional
    public List<FinalResultDTO> finalizeGame(Long gameId) {
        return gameFinalizationService.finalizeGame(gameId);
    }

    public void invitePlayer(Long gameId, Long fromUserId, String toUsername){
        gameInviteService.invitePlayer(gameId, fromUserId, toUsername);
    }

    public List<GameInviteGetDTO> getInvitesForUser(Long userId) {
        return gameInviteService.getInvitesForUser(userId);
    }

    public void deleteInvite(Long inviteId) {
        gameInviteService.deleteInvite(inviteId);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void checkTurnTimeouts() {
        gameLobbyService.checkTurnTimeouts();
    }

    @Scheduled(fixedDelay = 1800000)
    @Transactional
    public void cleanupAbandonedGames() {
        gameLobbyService.cleanupAbandonedGames();
    }

}
