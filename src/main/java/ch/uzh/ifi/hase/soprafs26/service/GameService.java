package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages game sessions: creating lobbies, starting games (which fetches
 * and stores the deck), and drawing/revealing cards.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameRepository gameRepository;
    private final WikidataService wikidataService;
    private final Random random = new Random();

    public GameService(GameRepository gameRepository, WikidataService wikidataService) {
        this.gameRepository = gameRepository;
        this.wikidataService = wikidataService;
    }

    //  Lobby/Game-lifecycle

    /**
     * Creates a new game in WAITING status with a random 6-char lobby code.
     */
    public Game createGame(HistoricalEra era) {
        Game game = new Game();
        game.setLobbyCode(generateLobbyCode());
        game.setEra(era);
        game.setStatus("WAITING");
        game.setNextCardIndex(0);
        game.setDeckSize(0);

        game = gameRepository.save(game);
        log.info("Created game {} with lobby code {} for era {}",
                game.getId(), game.getLobbyCode(), era);
        return game;
    }

    /**
     * Starts the game: fetches cards from Wikidata once and stores the
     * deck on the Game entity. After this call the deck is fixed.
     *
     * @param gameId the game to start
     * @param deckSize how many cards to fetch (e.g. players × 5 + buffer)
     * @return the updated Game
     */
    public Game startGame(Long gameId, int deckSize) {
        Game game = findGameOrThrow(gameId);

        if (!"WAITING".equals(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Game is already " + game.getStatus());
        }

        log.info("Starting game {} – fetching {} cards for era {}",
                gameId, deckSize, game.getEra());

        List<EventCard> deck = wikidataService.fetchEvents(game.getEra(), deckSize);

        game.setDeckJson(serializeDeck(deck));
        game.setDeckSize(deck.size());
        game.setNextCardIndex(0);
        game.setStatus("IN_PROGRESS");

        game = gameRepository.save(game);
        log.info("Game {} started with {} cards", gameId, deck.size());
        return game;
    }

    // Card-operations

    /**
     * Draws the next card from the deck (hidden – no year).
     * Advances {@code nextCardIndex} by 1.
     */
    public EventCard drawCard(Long gameId) {
        Game game = findGameOrThrow(gameId);
        assertInProgress(game);

        List<EventCard> deck = deserializeDeck(game.getDeckJson());
        int index = game.getNextCardIndex();

        if (index >= deck.size()) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "No cards left in the deck");
        }

        game.setNextCardIndex(index + 1);
        gameRepository.save(game);

        return deck.get(index);
    }

    /**
     * Returns a specific card from the deck by its position (0-based).
     * Use this to reveal a card's year after placement.
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
     * Returns ALL cards in the deck with years visible.
     * Useful for debugging or end-of-game reveal.
     */
    public List<EventCard> getAllCards(Long gameId) {
        Game game = findGameOrThrow(gameId);
        return deserializeDeck(game.getDeckJson());
    }

    /**
     * Returns basic game info.
     */
    public Game getGame(Long gameId) {
        return findGameOrThrow(gameId);
    }

    // Deck-serialization

    /**
     * Converts a list of EventCards to a JSON array string.
     * Example output: [{"title":"Moon Landing","year":1969,"imageUrl":null,"wikidataId":"Q123"}]
     */
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

    /**
     * Parses the JSON array string back into a list of EventCards.
     */
    List<EventCard> deserializeDeck(String json) {
        List<EventCard> cards = new ArrayList<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return cards;
        }

        // Walk through the JSON array, extracting each {...} block
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

    /** Extracts a string value for "key":"value" */
    private String extractJsonString(String block, String key) {
        String search = "\"" + key + "\":";
        int pos = block.indexOf(search);
        if (pos == -1) return null;

        int valueStart = pos + search.length();
        // skip whitespace
        while (valueStart < block.length() && block.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= block.length()) return null;
        if (block.charAt(valueStart) == 'n') return null; // "null"

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

    /** Extracts a number value for "key":123 */
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

    // Helper-functions

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
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1 to avoid confusion
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
}
