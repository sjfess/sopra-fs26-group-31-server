package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class GameStartService {

    private static final Logger log = LoggerFactory.getLogger(GameStartService.class);

    private static final int INITIAL_HAND_SIZE = 5;
    private static final int EXTRA_CARDS_PER_PLAYER_FOR_WRONG_PLACEMENTS = 5;
    private static final int MINIMUM_DECK_SIZE = 20;

    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final WikidataService wikidataService;
    private final GameCardHelper gameCardHelper;

    public GameStartService(GameRepository gameRepository,
                            GamePlayerRepository gamePlayerRepository,
                            WikidataService wikidataService,
                            GameCardHelper gameCardHelper) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.wikidataService = wikidataService;
        this.gameCardHelper = gameCardHelper;
    }

    @Transactional
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

        return startTimelineGame(game, gamePlayers, deckSize);
    }

    private Game startTimelineGame(Game game, List<GamePlayer> gamePlayers, int deckSize) {
        int effectiveDeckSize = calculateRequiredDeckSize(gamePlayers.size(), deckSize);

        log.info("Starting Timeline game {} – fetching {} cards for era {} (requested: {}, players: {})",
                game.getId(), effectiveDeckSize, game.getEra(), deckSize, gamePlayers.size());

        int timelineSeedCount = getTimelineSeedCount(game.getDifficulty());

        List<EventCard> allCurated = new ArrayList<>(wikidataService.getCuratedCards(game.getEra()));
        Collections.shuffle(allCurated);

        List<EventCard> timelineSeedCards = new ArrayList<>();
        for (int i = 0; i < Math.min(timelineSeedCount, allCurated.size()); i++) {
            timelineSeedCards.add(allCurated.get(i));
        }

        Set<String> excludedTitles = new HashSet<>();
        for (EventCard seed : timelineSeedCards) {
            excludedTitles.add(seed.getTitle().toLowerCase());
        }

        List<EventCard> rawDeck = wikidataService.fetchEvents(game.getEra(), effectiveDeckSize + timelineSeedCount);
        List<EventCard> deck = new ArrayList<>();
        Set<String> deckTitles = new HashSet<>();

        for (EventCard card : rawDeck) {
            String titleKey = card.getTitle().toLowerCase();
            if (!excludedTitles.contains(titleKey) && deckTitles.add(titleKey)) {
                deck.add(card);
                if (deck.size() >= effectiveDeckSize) break;
            }
        }

        if (deck.size() < effectiveDeckSize) {
            for (EventCard card : allCurated) {
                String titleKey = card.getTitle().toLowerCase();
                if (!excludedTitles.contains(titleKey) && deckTitles.add(titleKey)) {
                    deck.add(card);
                    if (deck.size() >= effectiveDeckSize) break;
                }
            }
        }

        if (deck.size() < effectiveDeckSize) {
            log.warn("Game {} starts with only {} cards although {} were requested for {} players in era {}",
                    game.getId(), deck.size(), effectiveDeckSize, gamePlayers.size(), game.getEra());
        }

        timelineSeedCards.sort(Comparator.comparingInt(EventCard::getYear));

        game.setDeckJson(gameCardHelper.serializeDeck(deck));
        game.setDeckSize(deck.size());
        game.setNextCardIndex(0);
        game.setStatus("IN_PROGRESS");
        game.setTimelineJson(gameCardHelper.serializeDeck(timelineSeedCards));

        log.info("Game {} seeded timeline with {} curated cards (difficulty: {})",
                game.getId(), timelineSeedCards.size(), game.getDifficulty());

        for (int i = 0; i < gamePlayers.size(); i++) {
            GamePlayer gp = gamePlayers.get(i);
            gp.setScore(0);
            gp.setCorrectPlacements(0);
            gp.setIncorrectPlacements(0);
            gp.setCorrectStreak(0);
            gp.setBestStreak(0);
            gp.setCurrentCardIndex(null);
            gp.setHandIndicesJson("[]");
            gp.setTurnStartedAt(null);
            gameCardHelper.dealCardsToPlayer(gp, game, INITIAL_HAND_SIZE);
            if (i == 0) {
                gp.setActiveTurn(true);
                gp.setTurnStartedAt(Instant.now());
            } else {
                gp.setActiveTurn(false);
            }
            gamePlayerRepository.save(gp);
        }

        gameRepository.save(game);
        return game;
    }

    private int calculateRequiredDeckSize(int playerCount, int requestedDeckSize) {
        int cardsNeededForInitialHands = playerCount * INITIAL_HAND_SIZE;
        int bufferForWrongPlacements = playerCount * EXTRA_CARDS_PER_PLAYER_FOR_WRONG_PLACEMENTS;
        int requiredDeckSize = cardsNeededForInitialHands + bufferForWrongPlacements;
        return Math.max(Math.max(requestedDeckSize, requiredDeckSize), MINIMUM_DECK_SIZE);
    }

    private int getTimelineSeedCount(Difficulty difficulty) {
        if (difficulty == null) return 0;
        return switch (difficulty) {
            case EASY -> 1;
            case MEDIUM -> 3;
            case HARD -> 5;
        };
    }

    Game findGameOrThrow(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game " + gameId + " not found"));
    }
}
