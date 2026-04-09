package ch.uzh.ifi.hase.soprafs26.service;


import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.ChatMessage;
import ch.uzh.ifi.hase.soprafs26.entity.GameInvite;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.ChatMessageRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameInviteRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HandCardDTO;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameInviteGetDTO;


import java.time.Duration;
import java.time.Instant;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final UserRepository userRepository;
    private final WikidataService wikidataService;
    private final Random random = new Random();
    private final ChatMessageRepository chatMessageRepository;
    private final GameInviteRepository gameInviteRepository;

    // helpers for bonus: streak and timer calculation
    private static final int TURN_LIMIT_SECONDS = 9999;
    private static final int BASE_CORRECT_POINTS = 100;
    private static final int TIME_BONUS_PER_SECOND = 2;
    private static final int STREAK_BONUS_PER_STEP = 10;
    private static final int INITIAL_HAND_SIZE = 5;

    public GameService(
            GameRepository gameRepository,
            GamePlayerRepository gamePlayerRepository,
            UserRepository userRepository,
            WikidataService wikidataService,
            ChatMessageRepository chatMessageRepository,
            GameInviteRepository gameInviteRepository
    ) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.userRepository = userRepository;
        this.wikidataService = wikidataService;
        this.chatMessageRepository = chatMessageRepository;
        this.gameInviteRepository = gameInviteRepository;
    }

    /**
     * Creates a new game in WAITING status with a random 6-char lobby code.
     */
    public Game createGame(HistoricalEra era, Difficulty difficulty, Long userId) {
        if (era == null || difficulty == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Era and difficulty are required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found"));

        Game game = new Game();
        game.setLobbyCode(generateLobbyCode());
        game.setEra(era);
        game.setHostId(userId);
        game.setStatus("WAITING");
        game.setDifficulty(difficulty);
        game.setNextCardIndex(0);
        game.setDeckSize(0);
        game.setTimelineJson("[]");
        game.setGameMode(GameMode.TIMELINE);

        game = gameRepository.save(game);
        log.info("Created game {} with lobby code {} for era {}",
                game.getId(), game.getLobbyCode(), era);
        GamePlayer hostPlayer = new GamePlayer();
        hostPlayer.setGame(game);
        hostPlayer.setUser(user);
        hostPlayer.setScore(0);
        hostPlayer.setTurnOrder(0);
        hostPlayer.setActiveTurn(false);
        hostPlayer.setCurrentCardIndex(null);
        hostPlayer.setCorrectPlacements(0);
        hostPlayer.setIncorrectPlacements(0);
        hostPlayer.setCorrectStreak(0);
        hostPlayer.setBestStreak(0);
        hostPlayer.setCardsInHand(0);
        hostPlayer.setTurnStartedAt(null);
        gamePlayerRepository.save(hostPlayer);
        return game;
    }

    /**
     * Adds a user to a WAITING game lobby via lobby code.
     */
    public Game joinGame(String lobbyCode, Long userId) {
        Game game = gameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game with lobby code " + lobbyCode + " not found"));

        if (!"WAITING".equals(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot join a game that is already " + game.getStatus());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found"));

        if (gamePlayerRepository.existsByGameAndUser(game, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User is already part of this game");
        }

        List<GamePlayer> existingPlayers = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);

        GamePlayer gamePlayer = new GamePlayer();
        gamePlayer.setGame(game);
        gamePlayer.setUser(user);
        gamePlayer.setScore(0);
        gamePlayer.setTurnOrder(existingPlayers.size());
        gamePlayer.setActiveTurn(false);
        gamePlayer.setCurrentCardIndex(null);

        gamePlayer.setCorrectPlacements(0);
        gamePlayer.setIncorrectPlacements(0);
        gamePlayer.setCorrectStreak(0);
        gamePlayer.setBestStreak(0);
        gamePlayer.setCardsInHand(0);
        gamePlayer.setTurnStartedAt(null);

        gamePlayerRepository.save(gamePlayer);
        return game;
    }

    /**
     * Starts the game and initializes turn order and scores.
     */
    public Game startGame(Long gameId, int deckSize) {
        Game game = findGameOrThrow(gameId);

        if (!"WAITING".equals(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Game is already " + game.getStatus());
        }

        List<GamePlayer> gamePlayers = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);
        if (gamePlayers.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Not enough players or settings incomplete");
        }

        log.info("Starting game {} – fetching {} cards for era {}", gameId, deckSize, game.getEra());

        List<EventCard> deck = wikidataService.fetchEvents(game.getEra(), deckSize);

        game.setDeckJson(serializeDeck(deck));
        game.setDeckSize(deck.size());
        game.setNextCardIndex(0);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson("[]");

        for (int i = 0; i < gamePlayers.size(); i++) {
            GamePlayer gamePlayer = gamePlayers.get(i);
            gamePlayer.setScore(0);

            if (i == 0) {
                gamePlayer.setActiveTurn(true);
                gamePlayer.setTurnStartedAt(Instant.now());
            } else {
                gamePlayer.setActiveTurn(false);
                gamePlayer.setTurnStartedAt(null);
            }

            gamePlayer.setCurrentCardIndex(null);
            gamePlayer.setHandIndicesJson(null);
            gamePlayer.setCorrectPlacements(0);
            gamePlayer.setIncorrectPlacements(0);
            gamePlayer.setCorrectStreak(0);
            gamePlayer.setBestStreak(0);
            dealCardsToPlayer(gamePlayer, game, INITIAL_HAND_SIZE);

            gamePlayerRepository.save(gamePlayer);
        }

        game = gameRepository.save(game);
        log.info("Game {} started with {} cards", gameId, deck.size());
        return game;
    }

    /**
     * Draws the next card for the active player.
     */
    public EventCard drawCard(Long gameId) {
        Game game = findGameOrThrow(gameId);
        assertInProgress(game);

        GamePlayer activePlayer = gamePlayerRepository.findByGameAndActiveTurnTrue(game)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No active player found for this game"));

        if (activePlayer.getCurrentCardIndex() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Active player already has a drawn card that must be placed first");
        }

        List<EventCard> deck = deserializeDeck(game.getDeckJson());
        int index = game.getNextCardIndex();

        if (index >= deck.size()) {
            if (isTimelineGameFinished(game)) {
                finalizeGame(game.getId());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Game finished because no cards are left in the deck");
            }

            throw new ResponseStatusException(HttpStatus.GONE,
                    "No cards left in the deck");
        }

        activePlayer.setCurrentCardIndex(index);
        activePlayer.setTurnStartedAt(Instant.now()); // reset timer when new card is drawn
        gamePlayerRepository.save(activePlayer);

        game.setNextCardIndex(index + 1);
        gameRepository.save(game);

        return deck.get(index);
    }

    /**
     * Returns a specific card from the deck by deck index.
     */
    public EventCard getCard(Long gameId, int cardIndex) {
        Game game = findGameOrThrow(gameId);
        assertInProgress(game);

        List<EventCard> deck = deserializeDeck(game.getDeckJson());

        if (cardIndex < 0 || cardIndex >= deck.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Card index " + cardIndex + " out of range (deck has " + deck.size() + " cards)");
        }

        return deck.get(cardIndex);
    }

    /**
     * Returns all cards in the deck with years visible.
     */
    public List<EventCard> getAllCards(Long gameId) {
        Game game = findGameOrThrow(gameId);
        return deserializeDeck(game.getDeckJson());
    }

    public Game getGame(Long gameId) {
        return findGameOrThrow(gameId);
    }
    /**
     *  Helper method to determine how many players are done.
     */

    private int getFinishedPlayerCount(Game game) {
        List<GamePlayer> players = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);

        int finishedCount = 0;
        for (GamePlayer player : players) {
            if (player.getCardsInHand() != null && player.getCardsInHand() <= 0) {
                finishedCount++;
            }
        }
        return finishedCount;
    }

    /**
     *  Helper method to determine how many players have to be done.
     */
    private int getRequiredFinishedPlayers(Game game) {
        List<GamePlayer> players = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);
        int playerCount = players.size();

        if (playerCount <= 3) {
            return playerCount;
        }
        return 3;
    }

    /**
     * Places the active player's currently drawn card at the chosen timeline position.
     */
    public Object[] placeCard(Long gameId, int cardIndex, int position) {
        Game game = findGameOrThrow(gameId);
        assertInProgress(game);

        GamePlayer activePlayer = gamePlayerRepository.findByGameAndActiveTurnTrue(game)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No active player found for this game"));

        List<Integer> hand = deserializeHandIndices(activePlayer.getHandIndicesJson());
        if (!hand.contains(cardIndex)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Card index " + cardIndex + " is not in player's hand");
        }

        List<EventCard> deck = deserializeDeck(game.getDeckJson());
        if (cardIndex < 0 || cardIndex >= deck.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Card index " + cardIndex + " out of range");
        }
        EventCard card = deck.get(cardIndex);

        List<EventCard> timeline = deserializeDeck(game.getTimelineJson());

        if (position < 0 || position > timeline.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Position " + position + " out of range (timeline has " + timeline.size() + " cards)");
        }

        boolean correct = true;

        long elapsedSeconds = 0;

        if (activePlayer.getTurnStartedAt() != null) {
            elapsedSeconds = Duration.between(activePlayer.getTurnStartedAt(), Instant.now()).getSeconds();
        }

        long remainingSeconds = Math.max(0, TURN_LIMIT_SECONDS - elapsedSeconds);
        int timeBonus = (int) remainingSeconds * TIME_BONUS_PER_SECOND;

        if (position > 0) {
            EventCard before = timeline.get(position - 1);
            if (card.getYear() < before.getYear()) {
                correct = false;
            }
        }

        if (correct && position < timeline.size()) {
            EventCard after = timeline.get(position);
            if (card.getYear() > after.getYear()) {
                correct = false;
            }
        }

        if (correct) {
            timeline.add(position, card);
            game.setTimelineJson(serializeDeck(timeline));

            int newStreak = activePlayer.getCorrectStreak() + 1;
            activePlayer.setCorrectStreak(newStreak);

            if (activePlayer.getBestStreak() == null || newStreak > activePlayer.getBestStreak()) {
                activePlayer.setBestStreak(newStreak);
            }

            int streakBonus = Math.max(0, newStreak - 1) * STREAK_BONUS_PER_STEP;
            int pointsAwarded = BASE_CORRECT_POINTS + timeBonus + streakBonus;

            activePlayer.setScore(activePlayer.getScore() + pointsAwarded);
            activePlayer.setCorrectPlacements(activePlayer.getCorrectPlacements() + 1);
            // Remove the placed card from the hand; correct placements do not draw a replacement.
            hand.remove(Integer.valueOf(cardIndex));
            activePlayer.setHandIndicesJson(serializeHandIndices(hand));
            activePlayer.setCardsInHand(hand.size());
        } else {
            activePlayer.setIncorrectPlacements(activePlayer.getIncorrectPlacements() + 1);
            activePlayer.setCorrectStreak(0);
            // Discard card and deal replacement — net: 0 change
            hand.remove(Integer.valueOf(cardIndex));
            activePlayer.setHandIndicesJson(serializeHandIndices(hand));
            activePlayer.setCardsInHand(hand.size());
            dealCardsToPlayer(activePlayer, game, 1);
        }

        activePlayer.setCurrentCardIndex(null);

        if (isTimelineGameFinished(game)) {
            activePlayer.setActiveTurn(false);
            gamePlayerRepository.save(activePlayer);
            gameRepository.save(game);
            finalizeGame(game.getId());
        } else {
            gamePlayerRepository.save(activePlayer);
            gameRepository.save(game);
            advanceTurn(game, activePlayer);
            gameRepository.save(game);
        }

        log.info("Game {} – player {} placed card '{}' ({}) at position {}: {}",
                gameId,
                activePlayer.getUser().getUsername(),
                card.getTitle(),
                card.getYear(),
                position,
                correct ? "CORRECT" : "WRONG");

        return new Object[]{card, correct, timeline.size()};
    }

    public List<EventCard> getTimeline(Long gameId) {
        Game game = findGameOrThrow(gameId);
        return deserializeDeck(game.getTimelineJson());
    }

    public List<GamePlayerScoreDTO> getLiveScores(Long gameId) {
        Game game = findGameOrThrow(gameId);

        List<GamePlayer> gamePlayers = gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game);
        List<GamePlayerScoreDTO> scores = new ArrayList<>();

        for (GamePlayer gamePlayer : gamePlayers) {
            GamePlayerScoreDTO dto = new GamePlayerScoreDTO();
            dto.setUserId(gamePlayer.getUser().getId());
            dto.setUsername(gamePlayer.getUser().getUsername());
            dto.setScore(gamePlayer.getScore());
            dto.setTurnOrder(gamePlayer.getTurnOrder());
            dto.setActiveTurn(gamePlayer.getActiveTurn());
            dto.setCorrectStreak(gamePlayer.getCorrectStreak());
            dto.setBestStreak(gamePlayer.getBestStreak());
            dto.setCardsInHand(gamePlayer.getCardsInHand());
            dto.setCurrentCardIndex(gamePlayer.getCurrentCardIndex());
            scores.add(dto);
        }

        return scores;
    }

    String serializeDeck(List<EventCard> deck) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < deck.size(); i++) {
            if (i > 0) sb.append(",");
            EventCard c = deck.get(i);
            sb.append("{");
            sb.append("\"title\":\"").append(escapeJson(c.getTitle())).append("\"");
            sb.append(",\"year\":").append(c.getYear());
            sb.append(",\"imageUrl\":");
            if (c.getImageUrl() != null) {
                sb.append("\"").append(escapeJson(c.getImageUrl())).append("\"");
            } else {
                sb.append("null");
            }
            sb.append(",\"wikidataId\":");
            if (c.getWikidataId() != null) {
                sb.append("\"").append(escapeJson(c.getWikidataId())).append("\"");
            } else {
                sb.append("null");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    List<EventCard> deserializeDeck(String json) {
        List<EventCard> cards = new ArrayList<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return cards;
        }

        int depth = 0;
        int blockStart = -1;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[' || c == ']') continue;
            if (c == '{') {
                depth++;
                if (depth == 1) blockStart = i;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && blockStart != -1) {
                    String block = json.substring(blockStart, i + 1);
                    EventCard card = parseCardBlock(block);
                    if (card != null) {
                        cards.add(card);
                    }
                    blockStart = -1;
                }
            }
        }

        return cards;
    }

    private EventCard parseCardBlock(String block) {
        String title = extractJsonString(block, "title");
        String yearStr = extractJsonNumber(block, "year");
        String imageUrl = extractJsonString(block, "imageUrl");
        String wikidataId = extractJsonString(block, "wikidataId");

        if (title == null || yearStr == null) return null;

        EventCard card = new EventCard();
        card.setTitle(title);
        try {
            card.setYear(Integer.parseInt(yearStr));
        } catch (NumberFormatException e) {
            return null;
        }
        card.setImageUrl(imageUrl);
        card.setWikidataId(wikidataId);
        return card;
    }

    private String extractJsonString(String block, String key) {
        String search = "\"" + key + "\":";
        int pos = block.indexOf(search);
        if (pos == -1) return null;

        int valueStart = pos + search.length();
        while (valueStart < block.length() && block.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= block.length()) return null;
        if (block.charAt(valueStart) == 'n') return null;
        if (block.charAt(valueStart) != '"') return null;

        int openQuote = valueStart;
        int closeQuote = openQuote + 1;
        while (closeQuote < block.length()) {
            if (block.charAt(closeQuote) == '"' && block.charAt(closeQuote - 1) != '\\') break;
            closeQuote++;
        }
        if (closeQuote >= block.length()) return null;

        return unescapeJson(block.substring(openQuote + 1, closeQuote));
    }

    private String extractJsonNumber(String block, String key) {
        String search = "\"" + key + "\":";
        int pos = block.indexOf(search);
        if (pos == -1) return null;

        int valueStart = pos + search.length();
        while (valueStart < block.length() && block.charAt(valueStart) == ' ') valueStart++;

        StringBuilder num = new StringBuilder();
        for (int i = valueStart; i < block.length(); i++) {
            char c = block.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) {
                num.append(c);
            } else {
                break;
            }
        }
        return !num.isEmpty() ? num.toString() : null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private String serializeHandIndices(List<Integer> indices) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < indices.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(indices.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private List<Integer> deserializeHandIndices(String json) {
        List<Integer> indices = new ArrayList<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) return indices;
        String inner = json.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        for (String part : inner.split(",")) {
            part = part.trim();
            if (!part.isEmpty()) {
                try { indices.add(Integer.parseInt(part)); } catch (NumberFormatException ignored) {}
            }
        }
        return indices;
    }

    private void dealCardsToPlayer(GamePlayer player, Game game, int count) {
        List<Integer> hand = deserializeHandIndices(player.getHandIndicesJson());
        int nextIndex = game.getNextCardIndex();
        int deckSize = game.getDeckSize();
        for (int i = 0; i < count && nextIndex < deckSize; i++) {
            hand.add(nextIndex++);
        }
        game.setNextCardIndex(nextIndex);
        player.setHandIndicesJson(serializeHandIndices(hand));
        player.setCardsInHand(hand.size());
    }

    public List<HandCardDTO> getHand(Long gameId, Long userId) {
        Game game = findGameOrThrow(gameId);
        assertInProgress(game);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        GamePlayer player = gamePlayerRepository.findByGameAndUser(game, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not in game"));

        List<Integer> handIndices = deserializeHandIndices(player.getHandIndicesJson());
        List<EventCard> deck = deserializeDeck(game.getDeckJson());
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

    private void advanceTurn(Game game, GamePlayer currentPlayer) {
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

    private String generateLobbyCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
    @Transactional
    public void leaveGame(String lobbyCode, Long userId) {
        Game game = gameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game with lobby code " + lobbyCode + " not found"));

        if (!"WAITING".equals(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot leave a game that is already " + game.getStatus());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found"));

        if (!gamePlayerRepository.existsByGameAndUser(game, user)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User is not part of this game");
        }

        gamePlayerRepository.deleteByGameAndUser(game, user);

        List<GamePlayer> remaining =
                gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);

        // ── Fall 1: Letzter Spieler hat verlassen → Lobby löschen ──────────────
        if (remaining.isEmpty()) {
            gameRepository.delete(game);
            return;
        }

        // ── Fall 2: Host hat verlassen → zufälligen neuen Host bestimmen ───────
        if (game.getHostId().equals(userId)) {
            GamePlayer newHost = remaining.get(random.nextInt(remaining.size()));
            game.setHostId(newHost.getUser().getId());
            gameRepository.save(game);
        }
    }

    @Scheduled(fixedDelay = 5000) // runs every 5 seconds
    public void checkTurnTimeouts() {
        long turnLimitSeconds = TURN_LIMIT_SECONDS; // configure as needed

        List<GamePlayer> activePlayers = gamePlayerRepository.findByActiveTurnTrue();

        for (GamePlayer player : activePlayers) {
            if (player.getTurnStartedAt() == null) continue;

            Game game = player.getGame();
            if (!"IN_PROGRESS".equals(game.getStatus())) continue;

            long elapsed = Duration.between(player.getTurnStartedAt(), Instant.now()).getSeconds();

            if (elapsed >= turnLimitSeconds) {
                log.info("Turn timeout for player {} in game {}",
                        player.getUser().getUsername(), game.getId());

                player.setCorrectStreak(0);
                player.setCurrentCardIndex(null);

                if (isTimelineGameFinished(game)) {
                    player.setActiveTurn(false);
                    gamePlayerRepository.save(player);
                    gameRepository.save(game);
                    finalizeGame(game.getId());
                }
                else {
                    gamePlayerRepository.save(player);
                    gameRepository.save(game);
                    advanceTurn(game, player);
                    gameRepository.save(game);
                }
            }
        }
    }
    public Game updateSettings(Long gameId, GameSettingsPutDTO dto) {
        Game game = findGameOrThrow(gameId);

        if (!"WAITING".equals(game.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot change settings after the game has started");
        }

        if (dto.getDifficulty() != null) {
            game.setDifficulty(dto.getDifficulty());
        }
        if (dto.getEra() != null) {
            game.setEra(dto.getEra());
        }
        if (dto.getGameMode() != null) {
            game.setGameMode(dto.getGameMode());
        }

        return gameRepository.save(game);
    }

    @Transactional
    public ChatMessageGetDTO addChatMessage(Long gameId, Long playerId, String message) {
        // Game existiert?
        findGameOrThrow(gameId);

        User user = userRepository.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        ChatMessage msg = new ChatMessage();
        msg.setGameId(gameId);
        msg.setPlayerId(playerId);
        msg.setUsername(user.getUsername());
        msg.setMessage(message);
        msg.setTimestamp(String.valueOf(System.currentTimeMillis()));
        chatMessageRepository.save(msg);

        ChatMessageGetDTO dto = new ChatMessageGetDTO();
        dto.setPlayerId(playerId);
        dto.setUsername(user.getUsername());
        dto.setMessage(message);
        dto.setTimestamp(msg.getTimestamp());
        return dto;
    }

    public List<ChatMessageGetDTO> getChatMessages(Long gameId) {
        findGameOrThrow(gameId); // 404 falls Game nicht existiert
        return chatMessageRepository.findAllByGameIdOrderByTimestampAsc(gameId)
                .stream()
                .map(m -> {
                    ChatMessageGetDTO dto = new ChatMessageGetDTO();
                    dto.setPlayerId(m.getPlayerId());
                    dto.setUsername(m.getUsername());
                    dto.setMessage(m.getMessage());
                    dto.setTimestamp(m.getTimestamp());
                    return dto;
                })
                .toList();
    }

    @Transactional
    public List<FinalResultDTO> finalizeGame(Long gameId) {
        Game game = findGameOrThrow(gameId);

        if (!"IN_PROGRESS".equals(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Game is " + game.getStatus() + ", not IN_PROGRESS");
        }

        List<GamePlayer> gamePlayers = gamePlayerRepository.findAllByGameOrderByScoreDescTurnOrderAsc(game);

        if (gamePlayers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game has no players");
        }

        int highestScore = gamePlayers.get(0).getScore() != null ? gamePlayers.get(0).getScore() : 0;

        List<FinalResultDTO> finalResults = new ArrayList<>();

        for (GamePlayer gamePlayer : gamePlayers) {
            User user = gamePlayer.getUser();

            int score = gamePlayer.getScore() != null ? gamePlayer.getScore() : 0;
            int correctPlacements = gamePlayer.getCorrectPlacements() != null ? gamePlayer.getCorrectPlacements() : 0;
            int incorrectPlacements = gamePlayer.getIncorrectPlacements() != null ? gamePlayer.getIncorrectPlacements() : 0;
            boolean winner = score == highestScore;

            user.setTotalGamesPlayed(user.getTotalGamesPlayed() + 1);
            user.setTotalPoints(user.getTotalPoints() + score);
            user.setTotalCorrectPlacements(user.getTotalCorrectPlacements() + correctPlacements);
            user.setTotalIncorrectPlacements(user.getTotalIncorrectPlacements() + incorrectPlacements);

            if (winner) {
                user.setTotalWins(user.getTotalWins() + 1);
            }

            userRepository.save(user);

            FinalResultDTO dto = new FinalResultDTO();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setScore(score);
            dto.setCorrectPlacements(correctPlacements);
            dto.setIncorrectPlacements(incorrectPlacements);
            dto.setWinner(winner);
            dto.setBestStreak(gamePlayer.getBestStreak());
            finalResults.add(dto);
        }

        game.setStatus("FINISHED");
        gameRepository.save(game);
        userRepository.flush();
        gameRepository.flush();

        //gameRepository.delete(game);

        return finalResults;
    }


    private boolean isTimelineGameFinished(Game game) {
        if (game.getGameMode() != GameMode.TIMELINE) {
            return false;
        }

        if (game.getNextCardIndex() >= game.getDeckSize()) {
            return true;
        }

        int finishedPlayers = getFinishedPlayerCount(game);
        int requiredFinishedPlayers = getRequiredFinishedPlayers(game);

        return finishedPlayers >= requiredFinishedPlayers;
    }


    public GameInvite invitePlayer(Long gameId, Long fromUserId, String toUsername){
        Game game = findGameOrThrow(gameId);

        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + fromUserId + " not found"));

        User toUser = userRepository.findByUsername(toUsername);
        if (toUser == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User " + toUsername + " not found");
        }
        if (fromUser.getUsername().equalsIgnoreCase(toUser.getUsername())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You cannot invite yourself");
        }

        if (gameInviteRepository.existsByGameIdAndToUserId(gameId, toUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "User already has an invite for this game");
        }
        GameInvite gameInvite = new GameInvite();
        gameInvite.setGameId(gameId);
        gameInvite.setLobbyCode(game.getLobbyCode());
        gameInvite.setFromUserId(fromUser.getId());
        gameInvite.setFromUsername(fromUser.getUsername());
        gameInvite.setToUserId(toUser.getId());

        gameInviteRepository.save(gameInvite);

        return gameInvite;
    }

    public List<GameInviteGetDTO> getInvitesForUser(Long userId) {
        List<GameInvite> invites = gameInviteRepository.findAllByToUserId(userId);
        List<GameInviteGetDTO> inviteDTOs = new ArrayList<>();

        for (GameInvite invite : invites) {
            GameInviteGetDTO dto = new GameInviteGetDTO();
            dto.setId(invite.getId());
            dto.setGameId(invite.getGameId());
            dto.setLobbyCode(invite.getLobbyCode());
            dto.setFromUsername(invite.getFromUsername());

            inviteDTOs.add(dto);
        }

        return inviteDTOs;
    }

    public void deleteInvite(Long inviteId) {
        gameInviteRepository.deleteById(inviteId);
    }

}
