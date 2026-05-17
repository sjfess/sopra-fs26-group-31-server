package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.*;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class GameFinalizationService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GameInviteRepository gameInviteRepository;
    private final GameStartService gameStartService;

    public GameFinalizationService(
            GameRepository gameRepository,
            GamePlayerRepository gamePlayerRepository,
            UserRepository userRepository,
            ChatMessageRepository chatMessageRepository,
            GameInviteRepository gameInviteRepository,
            GameStartService gameStartService) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.gameInviteRepository = gameInviteRepository;
        this.gameStartService = gameStartService;
    }



    @Transactional
    public List<FinalResultDTO> finalizeGame(Long gameId) {
        Game game = gameStartService.findGameOrThrow(gameId);

        if ("FINISHED".equals(game.getStatus())) {
            return buildFinalResultDTOs(game);
        }

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

        boolean shouldUpdateGlobalStats = game.getGameMode() == GameMode.TIMELINE;

        for (GamePlayer gamePlayer : gamePlayers) {
            User user = gamePlayer.getUser();

            int score = gamePlayer.getScore() != null ? gamePlayer.getScore() : 0;
            int correctPlacements = gamePlayer.getCorrectPlacements() != null ? gamePlayer.getCorrectPlacements() : 0;
            int incorrectPlacements = gamePlayer.getIncorrectPlacements() != null ? gamePlayer.getIncorrectPlacements() : 0;
            boolean winner = game.getGameMode() == GameMode.HISTORY_UNO || score == highestScore;

            if (shouldUpdateGlobalStats) {
                user.setTotalGamesPlayed(nullToZero(user.getTotalGamesPlayed()) + 1);
                user.setTotalPoints(nullToZero(user.getTotalPoints()) + score);
                user.setTotalCorrectPlacements(nullToZero(user.getTotalCorrectPlacements()) + correctPlacements);
                user.setTotalIncorrectPlacements(nullToZero(user.getTotalIncorrectPlacements()) + incorrectPlacements);

                if (winner) {
                    user.setTotalWins(nullToZero(user.getTotalWins()) + 1);
                }
            }

            user.setStatus(UserStatus.ONLINE);
            userRepository.save(user);

            FinalResultDTO dto = new FinalResultDTO();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setAvatarUrl(user.getAvatarUrl());
            dto.setScore(score);
            dto.setCorrectPlacements(correctPlacements);
            dto.setIncorrectPlacements(incorrectPlacements);
            dto.setWinner(winner);
            dto.setBestStreak(gamePlayer.getBestStreak());
            finalResults.add(dto);
        }

        game.setStatus("FINISHED");
        chatMessageRepository.deleteAllByGameId(game.getId());
        gameInviteRepository.deleteAllByGameId(game.getId());
        gameRepository.save(game);
        userRepository.flush();
        gameRepository.flush();


        return finalResults;
    }

    private List<FinalResultDTO> buildFinalResultDTOs(Game game) {
        List<GamePlayer> gamePlayers = gamePlayerRepository
                .findAllByGameOrderByScoreDescTurnOrderAsc(game);
        int highestScore = gamePlayers.isEmpty() ? 0
                : (gamePlayers.get(0).getScore() != null ? gamePlayers.get(0).getScore() : 0);

        List<FinalResultDTO> results = new ArrayList<>();
        for (GamePlayer gp : gamePlayers) {
            int score = gp.getScore() != null ? gp.getScore() : 0;
            FinalResultDTO dto = new FinalResultDTO();
            dto.setUserId(gp.getUser().getId());
            dto.setUsername(gp.getUser().getUsername());
            dto.setAvatarUrl(gp.getUser().getAvatarUrl());
            dto.setScore(score);
            dto.setCorrectPlacements(gp.getCorrectPlacements() != null ? gp.getCorrectPlacements() : 0);
            dto.setIncorrectPlacements(gp.getIncorrectPlacements() != null ? gp.getIncorrectPlacements() : 0);
            dto.setWinner(game.getGameMode() == GameMode.HISTORY_UNO || score == highestScore);
            dto.setBestStreak(gp.getBestStreak());
            results.add(dto);
        }
        return results;
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
