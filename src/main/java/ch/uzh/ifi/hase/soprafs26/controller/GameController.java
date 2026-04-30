package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.CreateGameDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardRevealDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.JoinGameDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PlaceMoveDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HandCardDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PlacementResultDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameInvitePostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameInviteGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.DrawCardDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RematchRequestDTO;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
public class GameController {

    private final GameService gameService;

    GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/games")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public GameGetDTO createGame(@RequestBody CreateGameDTO createGameDTO) {
        Game game = gameService.createGame(
                createGameDTO.getEra(),
                createGameDTO.getDifficulty(),
                createGameDTO.getUserId()
        );
        return toGameGetDTO(game);
    }

    @PostMapping("/games/join/{lobbyCode}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO joinGame(
            @PathVariable String lobbyCode,
            @RequestBody JoinGameDTO joinGameDTO) {
        Game game = gameService.joinGame(lobbyCode, joinGameDTO.getUserId());
        return toGameGetDTO(game);
    }

    @DeleteMapping("/games/leave/{lobbyCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveGame(
            @PathVariable String lobbyCode,
            @RequestParam Long userId) {
        gameService.leaveGame(lobbyCode, userId);
    }

    @PostMapping("/games/{gameId}/chat")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageGetDTO sendMessage(
            @PathVariable Long gameId,
            @RequestBody ChatMessageDTO dto) {
        return gameService.addChatMessage(gameId, dto.getPlayerId(), dto.getMessage());
    }

    @GetMapping("/games/{gameId}/chat")
    @ResponseStatus(HttpStatus.OK)
    public List<ChatMessageGetDTO> getMessages(@PathVariable Long gameId) {
        return gameService.getChatMessages(gameId);
    }

    @PutMapping("/games/{gameId}/start")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO startGame(
            @PathVariable Long gameId,
            @RequestParam(value = "deckSize", defaultValue = "80") int deckSize) {
        Game game = gameService.startGame(gameId, deckSize);
        return toGameGetDTO(game);
    }

    @PutMapping("/games/{gameId}/settings")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO putSettings(
            @PathVariable Long gameId,
            @RequestBody GameSettingsPutDTO gameSettingsPutDTO) {
        Game game = gameService.updateSettings(gameId, gameSettingsPutDTO);
        return toGameGetDTO(game);
    }

    @PostMapping("/games/{gameId}/draw")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public EventCardGetDTO drawCard(
            @PathVariable Long gameId,
            @RequestBody DrawCardDTO drawCardDTO) {

        EventCard card = gameService.drawCard(
                gameId,
                drawCardDTO.getUserId(),
                drawCardDTO.getDeckIndex()
        );

        return DTOMapper.INSTANCE.convertEntityToEventCardGetDTO(card);
    }

    @GetMapping("/games/{gameId}/cards/{index}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public EventCardRevealDTO revealCard(
            @PathVariable Long gameId,
            @PathVariable int index) {
        EventCard card = gameService.getCard(gameId, index);
        return DTOMapper.INSTANCE.convertEntityToEventCardRevealDTO(card);
    }

    @GetMapping("/games/{gameId}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO getGame(@PathVariable Long gameId) {
        Game game = gameService.getGame(gameId);
        return toGameGetDTO(game);
    }

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

    @PostMapping("/games/{gameId}/moves")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public PlacementResultDTO placeMove(
            @PathVariable Long gameId,
            @RequestBody PlaceMoveDTO placeMoveDTO) {

        GameService.PlacementResult result = gameService.placeCard(
                gameId,
                placeMoveDTO.getCardIndex(),
                placeMoveDTO.getPosition()
        );

        EventCard card = result.card();
        boolean correct = result.correct();
        int timelineSize = result.timelineSize();

        PlacementResultDTO dto = new PlacementResultDTO();
        dto.setCorrect(correct);
        dto.setTitle(card.getTitle());
        dto.setYear(card.getYear());
        dto.setImageUrl(card.getImageUrl());
        dto.setTimelineSize(timelineSize);
        return dto;
    }

    @GetMapping("/games/{gameId}/timeline")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<EventCardRevealDTO> getTimeline(@PathVariable Long gameId) {
        List<EventCard> timeline = gameService.getTimeline(gameId);
        List<EventCardRevealDTO> dtos = new ArrayList<>();
        for (EventCard card : timeline) {
            dtos.add(DTOMapper.INSTANCE.convertEntityToEventCardRevealDTO(card));
        }
        return dtos;
    }

    @GetMapping("/games/{gameId}/scores")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<GamePlayerScoreDTO> getLiveScores(@PathVariable Long gameId) {
        return gameService.getLiveScores(gameId);
    }

    @GetMapping("/games/{gameId}/hand")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<HandCardDTO> getHand(@PathVariable Long gameId, @RequestParam Long userId) {
        return gameService.getHand(gameId, userId);
    }

    @PostMapping("/games/{gameId}/finalize")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<FinalResultDTO> finalizeGame(@PathVariable Long gameId) {
        return gameService.finalizeGame(gameId);
    }

    private GameGetDTO toGameGetDTO(Game game) {
        GameGetDTO dto = DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
        dto.setCardsRemaining(game.getDeckSize() - game.getNextCardIndex());
        List<EventCard> timeline = gameService.getTimeline(game.getId());
        dto.setTimelineSize(timeline.size());
        dto.setRematchGameId(gameService.findWaitingRematchId(game.getId()).orElse(null));
        return dto;
    }

    @PostMapping("/games/{gameId}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public void invitePlayer(@PathVariable Long gameId,
                             @RequestBody GameInvitePostDTO dto) {
        gameService.invitePlayer(gameId, dto.getFromUserId(), dto.getToUsername());
    }

    @GetMapping("/games/invites/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public List<GameInviteGetDTO> getInvites(@PathVariable Long userId) {
        return gameService.getInvitesForUser(userId);
    }

    @DeleteMapping("/games/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInvite(@PathVariable Long inviteId) {
        gameService.deleteInvite(inviteId);
    }

    @PostMapping("/games/{gameId}/rematch")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public GameGetDTO createRematch(
            @PathVariable Long gameId,
            @RequestBody RematchRequestDTO rematchRequestDTO) {

        if (rematchRequestDTO == null || rematchRequestDTO.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }

        Game rematch = gameService.createRematchAndCloseOldGame(gameId, rematchRequestDTO.getUserId());
        return toGameGetDTO(rematch);
    }

    @DeleteMapping("/games/{gameId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeFinishedGame(
            @PathVariable Long gameId,
            @RequestBody RematchRequestDTO requestDTO) {

        if (requestDTO == null || requestDTO.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }

        gameService.closeFinishedGame(gameId, requestDTO.getUserId());
    }
}
