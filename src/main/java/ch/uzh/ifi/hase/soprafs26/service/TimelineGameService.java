package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HandCardDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TimelineGameService {

    private static final int TURN_LIMIT_SECONDS = 30;
    private static final int BASE_CORRECT_POINTS = 100;
    private static final int TIME_BONUS_PER_SECOND = 2;
    private static final int STREAK_BONUS_PER_STEP = 10;

    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final UserRepository userRepository;
    private final GameCardHelper gameCardHelper;
    private final GameFinalizationService gameFinalizationService;
    private final GameStartService gameStartService;

    public TimelineGameService(GameRepository gameRepository,
                               GamePlayerRepository gamePlayerRepository,
                               UserRepository userRepository,
                               GameCardHelper gameCardHelper, GameFinalizationService gameFinalizationService, GameStartService gameStartService) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.userRepository = userRepository;
        this.gameCardHelper = gameCardHelper;
        this.gameFinalizationService = gameFinalizationService;
        this.gameStartService = gameStartService;
    }

    public EventCard drawCard(Long gameId, Long userId, int deckIndex) {
        Game game = findGameOrThrow(gameId);
        assertInProgress(game);

        if (game.getGameMode() != GameMode.TIMELINE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This action is only available in Timeline mode");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found"));

        GamePlayer player = gamePlayerRepository.findByGameAndUser(game, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User is not part of this game"));

        if (!Boolean.TRUE.equals(player.getActiveTurn())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "It is not this player's turn");
        }

        List<Integer> hand = gameCardHelper.deserializeHandIndices(player.getHandIndicesJson());
        if (!hand.contains(deckIndex)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Selected card is not in player's hand");
        }

        List<EventCard> deck = gameCardHelper.deserializeDeck(game.getDeckJson());
        if (deckIndex < 0 || deckIndex >= deck.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Card index " + deckIndex + " out of range");
        }

        player.setCurrentCardIndex(deckIndex);
        gamePlayerRepository.save(player);
        return deck.get(deckIndex);
    }

    @Transactional
    public GameService.PlacementResult placeCard(Long gameId, int cardIndex, int position) {
        Game game = findGameOrThrow(gameId);
        assertInProgress(game);

        if (game.getGameMode() != GameMode.TIMELINE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This action is only available in Timeline mode");
        }

        GamePlayer activePlayer = gamePlayerRepository.findByGameAndActiveTurnTrue(game)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No active player found for this game"));

        if (activePlayer.getCurrentCardIndex() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Active player has not selected a card yet");
        }
        if (!activePlayer.getCurrentCardIndex().equals(cardIndex)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Player may only place the card they selected most recently");
        }

        List<Integer> hand = gameCardHelper.deserializeHandIndices(activePlayer.getHandIndicesJson());
        if (!hand.contains(cardIndex)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Card index " + cardIndex + " is not in player's hand");
        }

        List<EventCard> deck = gameCardHelper.deserializeDeck(game.getDeckJson());
        if (cardIndex < 0 || cardIndex >= deck.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Card index " + cardIndex + " out of range");
        }

        EventCard card = deck.get(cardIndex);
        List<EventCard> timeline = gameCardHelper.deserializeDeck(game.getTimelineJson());

        if (position < 0 || position > timeline.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Position " + position + " out of range");
        }

        boolean correct = true;
        long elapsedSeconds = 0;
        if (activePlayer.getTurnStartedAt() != null) {
            elapsedSeconds = Duration.between(activePlayer.getTurnStartedAt(), Instant.now()).getSeconds();
        }
        long remainingSeconds = Math.max(0, TURN_LIMIT_SECONDS - elapsedSeconds);
        int timeBonus = (int) remainingSeconds * TIME_BONUS_PER_SECOND;

        if (position > 0 && card.getYear() < timeline.get(position - 1).getYear()) correct = false;
        if (correct && position < timeline.size() && card.getYear() > timeline.get(position).getYear()) correct = false;

        if (correct) {
            timeline.add(position, card);
            game.setTimelineJson(gameCardHelper.serializeDeck(timeline));
            int newStreak = activePlayer.getCorrectStreak() + 1;
            activePlayer.setCorrectStreak(newStreak);
            if (activePlayer.getBestStreak() == null || newStreak > activePlayer.getBestStreak()) {
                activePlayer.setBestStreak(newStreak);
            }
            int streakBonus = Math.max(0, newStreak - 1) * STREAK_BONUS_PER_STEP;
            activePlayer.setScore(activePlayer.getScore() + BASE_CORRECT_POINTS + timeBonus + streakBonus);
            activePlayer.setCorrectPlacements(activePlayer.getCorrectPlacements() + 1);
            hand.remove(Integer.valueOf(cardIndex));
            activePlayer.setHandIndicesJson(gameCardHelper.serializeHandIndices(hand));
            activePlayer.setCardsInHand(hand.size());
        }
        else {
            activePlayer.setIncorrectPlacements(activePlayer.getIncorrectPlacements() + 1);
            activePlayer.setCorrectStreak(0);
            hand.remove(Integer.valueOf(cardIndex));
            activePlayer.setHandIndicesJson(gameCardHelper.serializeHandIndices(hand));
            activePlayer.setCardsInHand(hand.size());
            gameCardHelper.dealCardsToPlayer(activePlayer, game, 1);
        }

        activePlayer.setCurrentCardIndex(null);
        gamePlayerRepository.save(activePlayer);
        gameRepository.save(game);
        if (isTimelineGameFinished(game)) {
            activePlayer.setActiveTurn(false);

            gameFinalizationService.finalizeGame(gameId);
        }
        else {
            advanceTurn(game, activePlayer);
            gameRepository.save(game);
        }

        return new GameService.PlacementResult(card, correct, timeline.size());
    }

    public List<EventCard> getTimeline(Long gameId) {
        Game game = findGameOrThrow(gameId);
        return gameCardHelper.deserializeDeck(game.getTimelineJson());
    }

    boolean isTimelineGameFinished(Game game) {
        if (game.getGameMode() != GameMode.TIMELINE) return false;


        if (game.getNextCardIndex() >= game.getDeckSize()) return true;

        List<GamePlayer> players = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);
        for (GamePlayer player : players) {
            if (player.getCardsInHand() != null && player.getCardsInHand() == 0) return true;
        }

        return false;
    }

    private Game findGameOrThrow(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game " + gameId + " not found"));
    }

    private void assertInProgress(Game game) {
        if (!"IN_PROGRESS".equals(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Game is " + game.getStatus() + ", not IN_PROGRESS");
        }
    }

    void advanceTurn(Game game, GamePlayer currentPlayer) {
        List<GamePlayer> players = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);

        if (players.isEmpty()) {
            return;
        }

        currentPlayer.setActiveTurn(false);
        gamePlayerRepository.save(currentPlayer);

        int currentIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(currentPlayer.getId())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Current active player not found in turn order");
        }

        int nextIndex = (currentIndex + 1) % players.size();
        GamePlayer nextPlayer = players.get(nextIndex);
        nextPlayer.setActiveTurn(true);
        nextPlayer.setTurnStartedAt(Instant.now());
        gamePlayerRepository.save(nextPlayer);
    }

    List<HandCardDTO> getHand(Long gameId, Long userId) {
        Game game = gameStartService.findGameOrThrow(gameId);
        assertInProgress(game);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        GamePlayer player = gamePlayerRepository.findByGameAndUser(game, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not in game"));

        List<Integer> handIndices = gameCardHelper.deserializeHandIndices(player.getHandIndicesJson());
        List<EventCard> deck = gameCardHelper.deserializeDeck(game.getDeckJson());
        List<HandCardDTO> result = new ArrayList<>();

        for (int idx : handIndices) {
            if (idx < 0 || idx >= deck.size()) continue;
            EventCard card = deck.get(idx);
            HandCardDTO dto = new HandCardDTO();
            dto.setDeckIndex(idx);
            dto.setTitle(card.getTitle());
            dto.setImageUrl(card.getImageUrl());
            result.add(dto);
        }
        return result;
    }

    public List<EventCard> getAllCards(Long gameId) {
        Game game = gameStartService.findGameOrThrow(gameId);
        return gameCardHelper.deserializeDeck(game.getDeckJson());
    }

    public List<GamePlayerScoreDTO> getLiveScores(Long gameId) {
        Game game = gameStartService.findGameOrThrow(gameId);

        List<GamePlayer> gamePlayers = gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game);
        List<GamePlayerScoreDTO> scores = new ArrayList<>();

        for (GamePlayer gamePlayer : gamePlayers) {
            GamePlayerScoreDTO dto = new GamePlayerScoreDTO();
            dto.setUserId(gamePlayer.getUser().getId());
            dto.setUsername(gamePlayer.getUser().getUsername());
            dto.setAvatarUrl(gamePlayer.getUser().getAvatarUrl());
            dto.setScore(gamePlayer.getScore());
            dto.setTurnOrder(gamePlayer.getTurnOrder());
            dto.setActiveTurn(gamePlayer.getActiveTurn());
            dto.setTurnStartedAt(gamePlayer.getTurnStartedAt());
            dto.setCorrectStreak(gamePlayer.getCorrectStreak());
            dto.setBestStreak(gamePlayer.getBestStreak());
            dto.setCardsInHand(gamePlayer.getCardsInHand());
            dto.setCurrentCardIndex(gamePlayer.getCurrentCardIndex());
            dto.setCorrectPlacements(gamePlayer.getCorrectPlacements());
            dto.setIncorrectPlacements(gamePlayer.getIncorrectPlacements());
            scores.add(dto);
        }

        return scores;
    }

    public EventCard getCard(Long gameId, int cardIndex) {
        Game game = gameStartService.findGameOrThrow(gameId);
        assertInProgress(game);

        List<EventCard> deck = gameCardHelper.deserializeDeck(game.getDeckJson());

        if (cardIndex < 0 || cardIndex >= deck.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Card index " + cardIndex + " out of range (deck has " + deck.size() + " cards)");
        }

        return deck.get(cardIndex);
    }
}
