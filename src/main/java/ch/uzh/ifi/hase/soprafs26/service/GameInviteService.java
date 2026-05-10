package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GameInvite;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GameInviteRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameInviteGetDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.util.ArrayList;
import java.util.List;

@Service
public class GameInviteService {
    private final GameInviteRepository gameInviteRepository;
    private final UserRepository userRepository;
    private final GameStartService gameStartService;


    public GameInviteService(GameInviteRepository gameInviteRepository, UserRepository userRepository, GameStartService gameStartService) {
        this.gameInviteRepository = gameInviteRepository;
        this.userRepository = userRepository;
        this.gameStartService = gameStartService;
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
    public GameInvite invitePlayer(Long gameId, Long fromUserId, String toUsername){
        Game game = gameStartService.findGameOrThrow(gameId);

        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + fromUserId + " not found"));

        User toUser = userRepository.findByUsername(toUsername);
        if (toUser == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User " + toUsername + " not found");
        }

        if (toUser.getStatus() == UserStatus.IN_GAME) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "User is currently in a game and cannot receive invites");
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
    public void deleteInvite(Long inviteId) {
        gameInviteRepository.deleteById(inviteId);
    }

}
