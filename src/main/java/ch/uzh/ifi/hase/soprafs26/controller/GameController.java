package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardRevealDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.JoinGameDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PlacementResultDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;

import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;

import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class GameController {

    private final GameService gameService;

    GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/games")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public GameGetDTO createGame(
            @RequestParam("era") HistoricalEra era,
            @RequestParam("difficulty") Difficulty difficulty,
            @RequestParam("userId") Long userId) {
        Game game = gameService.createGame(era, difficulty, userId);
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
            @RequestBody JoinGameDTO joinGameDTO) {
        gameService.leaveGame(lobbyCode, joinGameDTO.getUserId());
    }

    @PutMapping("/games/{gameId}/start")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO startGame(
            @PathVariable Long gameId,
            @RequestParam(value = "deckSize", defaultValue = "40") int deckSize) {
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
    public EventCardGetDTO drawCard(@PathVariable Long gameId) {
        EventCard card = gameService.drawCard(gameId);
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

    @PostMapping("/games/{gameId}/place")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public PlacementResultDTO placeCard(
            @PathVariable Long gameId,
            @RequestParam("cardIndex") int cardIndex,
            @RequestParam("position") int position) {

        Object[] result = gameService.placeCard(gameId, cardIndex, position);
        EventCard card = (EventCard) result[0];
        boolean correct = (boolean) result[1];
        int timelineSize = (int) result[2];

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

    private GameGetDTO toGameGetDTO(Game game) {
        GameGetDTO dto = DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
        dto.setCardsRemaining(game.getDeckSize() - game.getNextCardIndex());
        List<EventCard> timeline = gameService.getTimeline(game.getId());
        dto.setTimelineSize(timeline.size());
        return dto;
    }

    @PostMapping("/games/{gameId}/finalize")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<FinalResultDTO> finalizeGame(@PathVariable Long gameId) {
        return gameService.finalizeGame(gameId);
    }

}