package ch.uzh.ifi.hase.soprafs26.service;


import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;



import java.time.Duration;
import java.time.Instant;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;
import java.util.Comparator;

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

    // helpers for bonus: streak and timer calculation
    private static final int TURN_LIMIT_SECONDS = 30;
    private static final int BASE_CORRECT_POINTS = 100;
    private static final int TIME_BONUS_PER_SECOND = 2;
    private static final int STREAK_BONUS_PER_STEP = 10;

    public GameService(
            GameRepository gameRepository,
            GamePlayerRepository gamePlayerRepository,
            UserRepository userRepository,
            WikidataService wikidataService
    ) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.userRepository = userRepository;
        this.wikidataService = wikidataService;
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
            gamePlayer.setCorrectPlacements(0);
            gamePlayer.setIncorrectPlacements(0);
            gamePlayer.setCorrectStreak(0);
            gamePlayer.setBestStreak(0);

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
     * Places the active player's currently drawn card at the chosen timeline position.
     */
    public Object[] placeCard(Long gameId, int cardIndex, int position) {
        Game game = findGameOrThrow(gameId);
        assertInProgress(game);

        GamePlayer activePlayer = gamePlayerRepository.findByGameAndActiveTurnTrue(game)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No active player found for this game"));

        if (activePlayer.getCurrentCardIndex() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Active player has not drawn a card yet");
        }

        if (!activePlayer.getCurrentCardIndex().equals(cardIndex)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Player may only place the card they most recently drew");
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
        } else {
            activePlayer.setIncorrectPlacements(activePlayer.getIncorrectPlacements() + 1);
            activePlayer.setCorrectStreak(0);
        }

        activePlayer.setCurrentCardIndex(null);
        gamePlayerRepository.save(activePlayer);

        advanceTurn(game, activePlayer);
        gameRepository.save(game);

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
        return num.length() > 0 ? num.toString() : null;
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
                gamePlayerRepository.save(player);

                advanceTurn(game, player);
                gameRepository.save(game);
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

        return finalResults;
    }
}