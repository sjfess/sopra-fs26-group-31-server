package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardRevealDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.GameService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Game Controller
 * Handles REST requests for game session lifecycle and card operations.
 *
 * Flow:
 *   1. POST /games?era=MEDIEVAL             → create game (returns lobby code)
 *   2. PUT  /games/{gameId}/start?deckSize=30 → host starts → deck fetched once
 *   3. POST /games/{gameId}/draw             → draw next card (hidden year)
 *   4. GET  /games/{gameId}/cards/{index}     → reveal a specific card (year shown)
 *   5. GET  /games/{gameId}                   → game info (status, cards remaining)
 *   6. GET  /games/{gameId}/cards             → all cards revealed (debug/endgame)
 */
@RestController
public class GameController {

    private final GameService gameService;

    GameController(GameService gameService) {
        this.gameService = gameService;
    }

    // 1.) Create game

    /**
     * POST /games?era=MEDIEVAL
     *
     * Creates a new game in WAITING status and returns the lobby info.
     */
    @PostMapping("/games")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public GameGetDTO createGame(@RequestParam("era") HistoricalEra era) {
        Game game = gameService.createGame(era);
        return toGameGetDTO(game);
    }

    // 2.) Start game

    /**
     * PUT /games/{gameId}/start?deckSize=30
     *
     * Host calls this to start the game. The server fetches exactly
     * {@code deckSize} cards from Wikidata and locks the deck.
     * Status changes from WAITING → IN_PROGRESS.
     */
    @PutMapping("/games/{gameId}/start")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO startGame(
            @PathVariable Long gameId,
            @RequestParam(value = "deckSize", defaultValue = "30") int deckSize) {
        Game game = gameService.startGame(gameId, deckSize);
        return toGameGetDTO(game);
    }

    // 3.) Draw card (hidden year)

    /**
     * POST /games/{gameId}/draw
     *
     * Draws the next card from the deck. Returns the card WITHOUT the year
     * so the player has to guess where it goes on the timeline.
     */
    @PostMapping("/games/{gameId}/draw")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public EventCardGetDTO drawCard(@PathVariable Long gameId) {
        EventCard card = gameService.drawCard(gameId);
        return DTOMapper.INSTANCE.convertEntityToEventCardGetDTO(card);
    }

    // 4.) Reveal card (year shown)

    /**
     * GET /games/{gameId}/cards/{index}
     *
     * Returns a specific card by its deck index with the year visible.
     * Call this after the player places a card to reveal the correct year.
     */
    @GetMapping("/games/{gameId}/cards/{index}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public EventCardRevealDTO revealCard(
            @PathVariable Long gameId,
            @PathVariable int index) {
        EventCard card = gameService.getCard(gameId, index);
        return DTOMapper.INSTANCE.convertEntityToEventCardRevealDTO(card);
    }

    // 5.) Get game information

    /**
     * GET /games/{gameId}
     *
     * Returns current game status, lobby code, deck size, and cards remaining.
     */
    @GetMapping("/games/{gameId}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO getGame(@PathVariable Long gameId) {
        Game game = gameService.getGame(gameId);
        return toGameGetDTO(game);
    }

    // 6.) Get all cards (debug/endgame reveal)

    /**
     * GET /games/{gameId}/cards
     *
     * Returns ALL cards in the deck with years visible.
     * Useful for end-of-game reveal or debugging.
     */
    @GetMapping("/games/{gameId}/cards")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<EventCardRevealDTO> getAllCards(@PathVariable Long gameId) {
        List<EventCard> cards = gameService.getAllCards(gameId);
        List<EventCardRevealDTO> dtos = new ArrayList<>();
        for (EventCard card : cards) {
            dtos.add(DTOMapper.INSTANCE.convertEntityToEventCardRevealDTO(card));
        }
        return dtos;
    }

    // Helper-functions

    /**
     * Maps Game entity → GameGetDTO, manually setting cardsRemaining
     * since MapStruct cannot derive it (it's deckSize - nextCardIndex).
     */
    private GameGetDTO toGameGetDTO(Game game) {
        GameGetDTO dto = DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
        dto.setCardsRemaining(game.getDeckSize() - game.getNextCardIndex());
        return dto;
    }
}
