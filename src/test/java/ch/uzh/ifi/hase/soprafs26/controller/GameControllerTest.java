package ch.uzh.ifi.hase.soprafs26.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.JoinGameDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PlaceMoveDTO;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.Mockito;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import ch.uzh.ifi.hase.soprafs26.rest.dto.*;
import ch.uzh.ifi.hase.soprafs26.entity.GameInvite;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import ch.uzh.ifi.hase.soprafs26.rest.dto.RematchRequestDTO;
import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;

@WebMvcTest(GameController.class)
public class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService gameService;

    @Test
    public void givenScores_whenGetLiveScores_thenReturnJsonArray() throws Exception {
        GamePlayerScoreDTO score1 = new GamePlayerScoreDTO();
        score1.setUserId(1L);
        score1.setUsername("mia");
        score1.setScore(5);
        score1.setTurnOrder(1);
        score1.setActiveTurn(true);

        GamePlayerScoreDTO score2 = new GamePlayerScoreDTO();
        score2.setUserId(2L);
        score2.setUsername("alex");
        score2.setScore(3);
        score2.setTurnOrder(0);
        score2.setActiveTurn(false);

        given(gameService.getLiveScores(1L)).willReturn(List.of(score1, score2));

        mockMvc.perform(get("/games/1/scores").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].userId", is(1)))
                .andExpect(jsonPath("$[0].username", is("mia")))
                .andExpect(jsonPath("$[0].score", is(5)))
                .andExpect(jsonPath("$[0].turnOrder", is(1)))
                .andExpect(jsonPath("$[0].activeTurn", is(true)))
                .andExpect(jsonPath("$[1].userId", is(2)))
                .andExpect(jsonPath("$[1].username", is("alex")))
                .andExpect(jsonPath("$[1].score", is(3)))
                .andExpect(jsonPath("$[1].turnOrder", is(0)))
                .andExpect(jsonPath("$[1].activeTurn", is(false)));
    }

    @Test
    public void joinGame_validInput_returnsGame() throws Exception {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("WAITING");
        game.setDeckSize(0);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");

        JoinGameDTO joinGameDTO = new JoinGameDTO();
        joinGameDTO.setUserId(10L);

        given(gameService.joinGame("ABC123", 10L)).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());

        mockMvc.perform(post("/games/join/ABC123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(joinGameDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.lobbyCode", is("ABC123")))
                .andExpect(jsonPath("$.era", is("MODERN")))
                .andExpect(jsonPath("$.status", is("WAITING")))
                .andExpect(jsonPath("$.deckSize", is(0)))
                .andExpect(jsonPath("$.cardsRemaining", is(0)))
                .andExpect(jsonPath("$.timelineSize", is(0)));
    }

    @Test
    public void finalizeGame_validInput_returnsFinalResults() throws Exception {
        FinalResultDTO result1 = new FinalResultDTO();
        result1.setUserId(1L);
        result1.setUsername("alex");
        result1.setScore(5);
        result1.setCorrectPlacements(5);
        result1.setIncorrectPlacements(1);
        result1.setWinner(true);
        result1.setBestStreak(4);


        FinalResultDTO result2 = new FinalResultDTO();
        result2.setUserId(2L);
        result2.setUsername("mia");
        result2.setScore(3);
        result2.setCorrectPlacements(3);
        result2.setIncorrectPlacements(2);
        result2.setWinner(false);
        result2.setBestStreak(2);

        given(gameService.finalizeGame(1L)).willReturn(List.of(result1, result2));

        mockMvc.perform(post("/games/1/finalize")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].userId", is(1)))
                .andExpect(jsonPath("$[0].username", is("alex")))
                .andExpect(jsonPath("$[0].score", is(5)))
                .andExpect(jsonPath("$[0].correctPlacements", is(5)))
                .andExpect(jsonPath("$[0].incorrectPlacements", is(1)))
                .andExpect(jsonPath("$[0].winner", is(true)))
                .andExpect(jsonPath("$[0].bestStreak", is(4)))
                .andExpect(jsonPath("$[1].userId", is(2)))
                .andExpect(jsonPath("$[1].username", is("mia")))
                .andExpect(jsonPath("$[1].score", is(3)))
                .andExpect(jsonPath("$[1].correctPlacements", is(3)))
                .andExpect(jsonPath("$[1].incorrectPlacements", is(2)))
                .andExpect(jsonPath("$[1].winner", is(false)))
                .andExpect(jsonPath("$[1].bestStreak", is(2)));
    }

    @Test
    public void placeMove_validInput_returnsPlacementResult() throws Exception {
        EventCard card = new EventCard();
        card.setTitle("Moon Landing");
        card.setYear(1969);
        card.setImageUrl("https://example.com/moon.jpg");

        PlaceMoveDTO placeMoveDTO = new PlaceMoveDTO();
        placeMoveDTO.setCardIndex(4);
        placeMoveDTO.setPosition(1);

        given(gameService.placeCard(1L, 4, 1)).willReturn(new Object[]{card, true, 3});

        mockMvc.perform(post("/games/1/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(placeMoveDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct", is(true)))
                .andExpect(jsonPath("$.title", is("Moon Landing")))
                .andExpect(jsonPath("$.year", is(1969)))
                .andExpect(jsonPath("$.imageUrl", is("https://example.com/moon.jpg")))
                .andExpect(jsonPath("$.timelineSize", is(3)));
    }

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JacksonException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("The request body could not be created.%s", e)
            );
        }
    }

    @Test
    public void createRematch_validInput_returnsCreatedGame() throws Exception {
        Game rematch = new Game();
        rematch.setId(2L);
        rematch.setLobbyCode("NEW456");
        rematch.setStatus("WAITING");
        rematch.setEra(HistoricalEra.MODERN);
        rematch.setDifficulty(Difficulty.EASY);
        rematch.setGameMode(GameMode.TIMELINE);
        rematch.setHostId(10L);
        rematch.setDeckSize(0);
        rematch.setNextCardIndex(0);
        rematch.setTimelineJson("[]");

        RematchRequestDTO dto = new RematchRequestDTO();
        dto.setUserId(10L);

        given(gameService.createRematch(1L, 10L)).willReturn(rematch);
        given(gameService.getTimeline(2L)).willReturn(List.of());

        mockMvc.perform(post("/games/1/rematch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.lobbyCode", is("NEW456")))
                .andExpect(jsonPath("$.status", is("WAITING")))
                .andExpect(jsonPath("$.era", is("MODERN")))
                .andExpect(jsonPath("$.difficulty", is("EASY")))
                .andExpect(jsonPath("$.gameMode", is("TIMELINE")))
                .andExpect(jsonPath("$.hostId", is(10)))
                .andExpect(jsonPath("$.cardsRemaining", is(0)))
                .andExpect(jsonPath("$.timelineSize", is(0)));
    }

    @Test
    public void createRematch_missingUserId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/games/1/rematch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createRematch_gameNotFinished_returnsConflict() throws Exception {
        RematchRequestDTO dto = new RematchRequestDTO();
        dto.setUserId(10L);

        given(gameService.createRematch(1L, 10L))
                .willThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Rematch can only be created from a finished game"
                ));

        mockMvc.perform(post("/games/1/rematch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    public void createRematch_requestingUserNotPlayer_returnsForbidden() throws Exception {
        RematchRequestDTO dto = new RematchRequestDTO();
        dto.setUserId(999L);

        given(gameService.createRematch(1L, 999L))
                .willThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only players from the finished game can create a rematch"
                ));

        mockMvc.perform(post("/games/1/rematch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isForbidden());
    }
    @Test
    public void createGame_validInput_returnsCreatedGame() throws Exception {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("XYZ999");
        game.setStatus("WAITING");
        game.setEra(HistoricalEra.MODERN);
        game.setDifficulty(Difficulty.EASY);
        game.setGameMode(GameMode.TIMELINE);
        game.setHostId(5L);
        game.setDeckSize(0);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");

        CreateGameDTO dto = new CreateGameDTO();
        dto.setEra(HistoricalEra.MODERN);
        dto.setDifficulty(Difficulty.EASY);
        dto.setUserId(5L);

        given(gameService.createGame(HistoricalEra.MODERN, Difficulty.EASY, 5L)).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());

        mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.lobbyCode", is("XYZ999")))
                .andExpect(jsonPath("$.status", is("WAITING")));
    }

    @Test
    public void leaveGame_validInput_returnsNoContent() throws Exception {
        Mockito.doNothing().when(gameService).leaveGame("ABC123", 5L);

        mockMvc.perform(delete("/games/leave/ABC123")
                        .param("userId", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    public void startGame_validInput_returnsGame() throws Exception {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setStatus("IN_PROGRESS");
        game.setEra(HistoricalEra.MODERN);
        game.setDifficulty(Difficulty.EASY);
        game.setGameMode(GameMode.TIMELINE);
        game.setHostId(5L);
        game.setDeckSize(80);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");

        given(gameService.startGame(1L, 80)).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());

        mockMvc.perform(put("/games/1/start")
                        .param("deckSize", "80")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    @Test
    public void getGame_validId_returnsGame() throws Exception {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setStatus("WAITING");
        game.setEra(HistoricalEra.MODERN);
        game.setDifficulty(Difficulty.EASY);
        game.setGameMode(GameMode.TIMELINE);
        game.setHostId(5L);
        game.setDeckSize(0);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");

        given(gameService.getGame(1L)).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());

        mockMvc.perform(get("/games/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.lobbyCode", is("ABC123")));
    }

    @Test
    public void drawCard_validInput_returnsCard() throws Exception {
        EventCard card = new EventCard();
        card.setTitle("French Revolution");
        card.setYear(1789);
        card.setImageUrl("https://example.com/fr.jpg");

        DrawCardDTO dto = new DrawCardDTO();
        dto.setUserId(1L);
        dto.setDeckIndex(0);

        given(gameService.drawCard(1L, 1L, 0)).willReturn(card);

        mockMvc.perform(post("/games/1/draw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("French Revolution")))
                .andExpect(jsonPath("$.imageUrl", is("https://example.com/fr.jpg")));
    }

    @Test
    public void revealCard_validInput_returnsCard() throws Exception {
        EventCard card = new EventCard();
        card.setTitle("Moon Landing");
        card.setYear(1969);
        card.setImageUrl("https://example.com/moon.jpg");

        given(gameService.getCard(1L, 0)).willReturn(card);

        mockMvc.perform(get("/games/1/cards/0").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Moon Landing")))
                .andExpect(jsonPath("$.year", is(1969)));
    }

    @Test
    public void getAllCards_validGameId_returnsCardList() throws Exception {
        EventCard card = new EventCard();
        card.setTitle("WW2");
        card.setYear(1939);
        card.setImageUrl("https://example.com/ww2.jpg");

        given(gameService.getAllCards(1L)).willReturn(List.of(card));

        mockMvc.perform(get("/games/1/cards").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("WW2")));
    }

    @Test
    public void getTimeline_validGameId_returnsTimeline() throws Exception {
        EventCard card = new EventCard();
        card.setTitle("WW1");
        card.setYear(1914);
        card.setImageUrl("https://example.com/ww1.jpg");

        given(gameService.getTimeline(1L)).willReturn(List.of(card));

        mockMvc.perform(get("/games/1/timeline").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("WW1")));
    }

    @Test
    public void getHand_validInput_returnsHandCards() throws Exception {
        HandCardDTO handCard = new HandCardDTO();
        handCard.setDeckIndex(0);
        handCard.setTitle("Waterloo");

        given(gameService.getHand(1L, 5L)).willReturn(List.of(handCard));

        mockMvc.perform(get("/games/1/hand")
                        .param("userId", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].deckIndex", is(0)));
    }

    @Test
    public void putSettings_validInput_returnsUpdatedGame() throws Exception {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setStatus("WAITING");
        game.setEra(HistoricalEra.ANCIENT);
        game.setDifficulty(Difficulty.HARD);
        game.setGameMode(GameMode.TIMELINE);
        game.setHostId(5L);
        game.setDeckSize(0);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");

        GameSettingsPutDTO settingsDTO = new GameSettingsPutDTO();
        settingsDTO.setEra(HistoricalEra.ANCIENT);
        settingsDTO.setDifficulty(Difficulty.HARD);

        given(gameService.updateSettings(Mockito.eq(1L), Mockito.any())).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());

        mockMvc.perform(put("/games/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(settingsDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.era", is("ANCIENT")))
                .andExpect(jsonPath("$.difficulty", is("HARD")));
    }

    @Test
    public void sendMessage_validInput_returnsMessage() throws Exception {
        ChatMessageGetDTO response = new ChatMessageGetDTO();
        response.setPlayerId(1L);
        response.setMessage("Hello!");

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setPlayerId(1L);
        dto.setMessage("Hello!");

        given(gameService.addChatMessage(1L, 1L, "Hello!")).willReturn(response);

        mockMvc.perform(post("/games/1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId", is(1)))
                .andExpect(jsonPath("$.message", is("Hello!")));
    }

    @Test
    public void getMessages_validGameId_returnsMessageList() throws Exception {
        ChatMessageGetDTO msg = new ChatMessageGetDTO();
        msg.setPlayerId(1L);
        msg.setMessage("Hi!");

        given(gameService.getChatMessages(1L)).willReturn(List.of(msg));

        mockMvc.perform(get("/games/1/chat").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message", is("Hi!")));
    }

    @Test
    public void invitePlayer_validInput_returnsCreated() throws Exception {
        GameInvitePostDTO dto = new GameInvitePostDTO();
        dto.setFromUserId(1L);
        dto.setToUsername("targetUser");

        // invitePlayer gibt GameInvite zurück → given() statt doNothing()
        given(gameService.invitePlayer(1L, 1L, "targetUser"))
                .willReturn(new GameInvite());

        mockMvc.perform(post("/games/1/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isCreated());
    }
    @Test
    public void getInvites_validUserId_returnsInviteList() throws Exception {
        GameInviteGetDTO invite = new GameInviteGetDTO();
        invite.setId(1L);

        given(gameService.getInvitesForUser(1L)).willReturn(List.of(invite));

        mockMvc.perform(get("/games/invites/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    public void deleteInvite_validId_returnsNoContent() throws Exception {
        Mockito.doNothing().when(gameService).deleteInvite(1L);

        mockMvc.perform(delete("/games/invites/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
