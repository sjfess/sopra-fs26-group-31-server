package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GamePlayerRepository;
import ch.uzh.ifi.hase.soprafs26.repository.ChatMessageRepository;
import ch.uzh.ifi.hase.soprafs26.repository.GameInviteRepository;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class GameLobbyService {
    private final static int TURN_LIMIT_SECONDS = 30;


    private final GamePlayerRepository gamePlayerRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GameInviteRepository gameInviteRepository;
    private final Random random = new Random();
    private final Logger log = LoggerFactory.getLogger(GameLobbyService.class);
    private final GameFinalizationService gameFinalizationService;
    private final GameStartService gameStartService;
    private final TimelineGameService timelineGameService;

    public GameLobbyService(
            GamePlayerRepository gamePlayerRepository,
            GameRepository gameRepository,
            UserRepository userRepository,
            ChatMessageRepository chatMessageRepository,
            GameInviteRepository gameInviteRepository, GameFinalizationService gameFinalizationService, GameStartService gameStartService, TimelineGameService timelineGameService) {
        this.gamePlayerRepository = gamePlayerRepository;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.gameInviteRepository = gameInviteRepository;
        this.gameFinalizationService = gameFinalizationService;
        this.gameStartService = gameStartService;
        this.timelineGameService = timelineGameService;
    }

    public Game createGame(HistoricalEra era, Difficulty difficulty, Long userId) {
        if (era == null || difficulty == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Era and difficulty are required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found"));

        user.setStatus(UserStatus.IN_GAME);
        userRepository.save(user);

        Game game = new Game();
        game.setLobbyCode(generateUniqueLobbyCode());
        game.setEra(era);
        game.setHostId(userId);
        game.setStatus("WAITING");
        game.setDifficulty(difficulty);
        game.setNextCardIndex(0);
        game.setDeckSize(0);
        game.setTimelineJson("[]");
        game.setGameMode(GameMode.TIMELINE);
        game.setCreatedAt(Instant.now());
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

    public Game joinGame(String lobbyCode, Long userId) {
        Game game = gameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game with lobby code " + lobbyCode + " not found"));

        if (!"WAITING".equals(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot join a game that is already " + game.getStatus());
        }

        List<GamePlayer> existingPlayers = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);
        if (existingPlayers.size() >= 5) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lobby is full (maximum 5 players)");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found"));

        if (gamePlayerRepository.existsByGameAndUser(game, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User is already part of this game");
        }

        user.setStatus(UserStatus.IN_GAME);
        userRepository.save(user);

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

    @Transactional
    public Game createRematch(Long finishedGameId, Long requestingUserId) {
        Game oldGame = gameStartService.findGameOrThrow(finishedGameId);

        if (!"FINISHED".equals(oldGame.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rematch can only be created from a finished game");
        }

        List<GamePlayer> oldPlayers = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(oldGame);
        if (oldPlayers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finished game has no players");
        }
        List<GamePlayer> activePlayers = oldPlayers.stream()
                .filter(gp -> gp.getUser().getStatus() == UserStatus.ONLINE)
                .toList();

        if (activePlayers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No active players available for rematch");
        }


        boolean requesterWasPlayer = oldPlayers.stream()
                .anyMatch(gp -> gp.getUser().getId().equals(requestingUserId));

        if (!requesterWasPlayer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only players from the finished game can create a rematch");
        }

        Optional<Game> existingRematch =
                gameRepository.findByRematchFromGameIdAndStatus(oldGame.getId(), "WAITING");
        if (existingRematch.isPresent()) {
            return existingRematch.get();
        }

        Game newGame = new Game();
        newGame.setLobbyCode(generateUniqueLobbyCode());
        newGame.setEra(oldGame.getEra());
        newGame.setDifficulty(oldGame.getDifficulty());
        newGame.setGameMode(oldGame.getGameMode());
        newGame.setCreatedAt(Instant.now());
        newGame.setHostId(requestingUserId); // sinnvoller als oldGame.getHostId()
        newGame.setStatus("WAITING");
        newGame.setDeckJson(null);
        newGame.setDeckSize(0);
        newGame.setNextCardIndex(0);
        newGame.setTimelineJson("[]");
        newGame.setRematchFromGameId(oldGame.getId());

        newGame = gameRepository.saveAndFlush(newGame);

        for (GamePlayer oldGp : activePlayers) {
            GamePlayer newGp = new GamePlayer();
            newGp.setGame(newGame);
            newGp.setUser(oldGp.getUser());
            newGp.setScore(0);
            newGp.setTurnOrder(oldGp.getTurnOrder());
            newGp.setActiveTurn(false);
            newGp.setCurrentCardIndex(null);
            newGp.setHandIndicesJson(null);
            newGp.setCardsInHand(0);
            newGp.setCorrectPlacements(0);
            newGp.setIncorrectPlacements(0);
            newGp.setCorrectStreak(0);
            newGp.setBestStreak(0);
            newGp.setTurnStartedAt(null);

            User user = oldGp.getUser();
            user.setStatus(UserStatus.IN_GAME);
            userRepository.save(user);

            gamePlayerRepository.save(newGp);
        }

        return newGame;
    }

    @Transactional
    public Game createRematchAndCloseOldGame(Long finishedGameId, Long requestingUserId) {
        Game oldGame = gameStartService.findGameOrThrow(finishedGameId);

        if (!"FINISHED".equals(oldGame.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only finished games can be rematched and closed");
        }

        if (!oldGame.getHostId().equals(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the host can create a rematch");
        }

        return createRematch(finishedGameId, requestingUserId);
    }

    public Optional<Long> findWaitingRematchId(Long gameId) {
        return gameRepository.findByRematchFromGameIdAndStatus(gameId, "WAITING")
                .map(Game::getId);
    }

    @Transactional
    public void closeFinishedGame(Long finishedGameId, Long requestingUserId) {
        Game game = gameStartService.findGameOrThrow(finishedGameId);

        if (!"FINISHED".equals(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only finished games can be closed");
        }

        if (!game.getHostId().equals(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the host can close the game");
        }

        deleteFinishedGameInternal(game);
    }

    private void deleteFinishedGameInternal(Game game) {
        chatMessageRepository.deleteAllByGameId(game.getId());
        gameInviteRepository.deleteAllByGameId(game.getId());
        gameRepository.delete(game);
    }

    private String generateUniqueLobbyCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(chars.charAt(random.nextInt(chars.length())));
            }

            String lobbyCode = code.toString();
            if (gameRepository.findByLobbyCode(lobbyCode).isEmpty()) {
                return lobbyCode;
            }
        }

        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not generate unique lobby code");
    }

    @Transactional
    public void leaveGame(String lobbyCode, Long userId) {
        Game game = gameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game with lobby code " + lobbyCode + " not found"));

        if (!"WAITING".equals(game.getStatus())
                && !"IN_PROGRESS".equals(game.getStatus())
                && !"FINISHED".equals(game.getStatus())
        ) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot leave a game with status " + game.getStatus());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found"));

        if (!gamePlayerRepository.existsByGameAndUser(game, user)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User is not part of this game");
        }
        if ("FINISHED".equals(game.getStatus())) {
            user.setStatus(UserStatus.ONLINE);
            userRepository.save(user);
            gamePlayerRepository.deleteByGameAndUser(game, user);
            gamePlayerRepository.flush();

            if (game.getHostId().equals(user.getId())) {
                List<GamePlayer> remaining =
                        gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);
                if (!remaining.isEmpty()) {
                    game.setHostId(remaining.get(0).getUser().getId());
                    gameRepository.save(game);
                }
                else {
                    deleteFinishedGameInternal(game);
                }
            }
            return;
        }

        if ("IN_PROGRESS".equals(game.getStatus())) {
            leaveInProgressGame(game, user);
            return;
        }

        user.setStatus(UserStatus.ONLINE);
        userRepository.save(user);

        gamePlayerRepository.deleteByGameAndUser(game, user);

        List<GamePlayer> remaining =
                gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);

        if (remaining.isEmpty()) {
            gameRepository.delete(game);
            return;
        }

        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setTurnOrder(i);
            gamePlayerRepository.save(remaining.get(i));
        }

        if (game.getHostId().equals(userId)) {
            GamePlayer newHost = remaining.get(random.nextInt(remaining.size()));
            game.setHostId(newHost.getUser().getId());
            gameRepository.save(game);
        }
    }

    private void leaveInProgressGame(Game game, User user) {
        GamePlayer leavingPlayer = gamePlayerRepository.findByGameAndUser(game, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Player record not found"));

        boolean wasActiveTurn = Boolean.TRUE.equals(leavingPlayer.getActiveTurn());
        int leavingTurnOrder = leavingPlayer.getTurnOrder();

        // Snapshot the full turn order before removing the leaving player
        List<GamePlayer> allPlayers = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);

        user.setStatus(UserStatus.ONLINE);
        userRepository.save(user);
        gamePlayerRepository.deleteByGameAndUser(game, user);

        List<GamePlayer> remaining = new ArrayList<>();
        for (GamePlayer p : allPlayers) {
            if (!p.getId().equals(leavingPlayer.getId())) {
                remaining.add(p);
            }
        }

        if (remaining.isEmpty()) {
            gameRepository.delete(game);
            return;
        }

        // Renumber to keep turn order contiguous
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setTurnOrder(i);
            gamePlayerRepository.save(remaining.get(i));
        }

        // Reassign host if needed
        if (game.getHostId().equals(user.getId())) {
            game.setHostId(remaining.get(0).getUser().getId());
            gameRepository.save(game);
        }

        // One player left — end the game, that player wins
        if (remaining.size() == 1) {
            gameFinalizationService.finalizeGame(game.getId());
            return;
        }

        // Give the turn to whoever was next in rotation
        if (wasActiveTurn) {
            int nextIndex = leavingTurnOrder % remaining.size();
            GamePlayer nextPlayer = remaining.get(nextIndex);
            nextPlayer.setActiveTurn(true);
            nextPlayer.setTurnStartedAt(Instant.now());
            gamePlayerRepository.save(nextPlayer);
        }
    }

    public Game updateSettings(Long gameId, GameSettingsPutDTO dto) {
        Game game = gameStartService.findGameOrThrow(gameId);

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
    public void checkTurnTimeouts() {
        List<GamePlayer> activePlayers = gamePlayerRepository.findByActiveTurnTrue();

        for (GamePlayer player : activePlayers) {
            if (player.getTurnStartedAt() == null) continue;

            Game game = player.getGame();
            if (!"IN_PROGRESS".equals(game.getStatus())) continue;

            long elapsed = Duration.between(player.getTurnStartedAt(), Instant.now()).getSeconds();

            if (elapsed >= (long) TURN_LIMIT_SECONDS) {
                log.info("Turn timeout for player {} in game {}",
                        player.getUser().getUsername(), game.getId());

                player.setCorrectStreak(0);
                player.setCurrentCardIndex(null);

                boolean gameFinished =
                        timelineGameService.isTimelineGameFinished(game);

                if (gameFinished) {
                    player.setActiveTurn(false);
                    gamePlayerRepository.save(player);
                    gameRepository.save(game);
                    gameFinalizationService.finalizeGame(game.getId());
                } else {
                    gamePlayerRepository.save(player);
                    gameRepository.save(game);
                    timelineGameService.advanceTurn(game, player);
                    gameRepository.save(game);
                }
            }
        }
    }

    @Transactional
    public void cleanupAbandonedGames() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(3));

        for (Game game : gameRepository.findAll()) {
            boolean isOldFinishedGame =
                    "FINISHED".equals(game.getStatus())
                            && game.getCreatedAt() != null
                            && game.getCreatedAt().isBefore(cutoff);

            boolean isOldWaitingGame =
                    "WAITING".equals(game.getStatus())
                            && game.getCreatedAt() != null
                            && game.getCreatedAt().isBefore(cutoff);

            if (isOldFinishedGame || isOldWaitingGame) {
                List<GamePlayer> players = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(game);

                for (GamePlayer gp : players) {
                    User u = gp.getUser();
                    u.setStatus(UserStatus.ONLINE);
                    userRepository.save(u);
                }

                deleteFinishedGameInternal(game);
            }
        }
    }

    public Game getGame(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Game " + gameId + " not found"));
    }
}
