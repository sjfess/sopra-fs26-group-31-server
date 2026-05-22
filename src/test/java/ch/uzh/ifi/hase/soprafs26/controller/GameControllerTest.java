package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Objects;

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
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

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
        given(gameService.findWaitingRematchId(1L)).willReturn(Optional.empty());

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
    public void getGame_withWaitingRematch_returnsRematchGameId() throws Exception {
        Game game = new Game();
        game.setId(1L);
        game.setLobbyCode("ABC123");
        game.setEra(HistoricalEra.MODERN);
        game.setStatus("FINISHED");
        game.setDeckSize(0);
        game.setNextCardIndex(0);
        game.setTimelineJson("[]");

        given(gameService.getGame(1L)).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());
        given(gameService.findWaitingRematchId(1L)).willReturn(Optional.of(2L));

        mockMvc.perform(get("/games/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.rematchGameId", is(2)));
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

        given(gameService.placeCard(1L, 4, 1)).willReturn(new GameService.PlacementResult(card, true, 3));

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

        given(gameService.createRematchAndCloseOldGame(1L, 10L)).willReturn(rematch);
        given(gameService.getTimeline(2L)).willReturn(List.of());
        given(gameService.findWaitingRematchId(2L)).willReturn(Optional.empty());

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

        given(gameService.createRematchAndCloseOldGame(1L, 10L))
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
    public void createRematch_nonHost_returnsForbidden() throws Exception {
        RematchRequestDTO dto = new RematchRequestDTO();
        dto.setUserId(999L);

        given(gameService.createRematchAndCloseOldGame(1L, 999L))
                .willThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only the host can create a rematch"
                ));

        mockMvc.perform(post("/games/1/rematch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void closeFinishedGame_validInput_returnsNoContent() throws Exception {
        RematchRequestDTO dto = new RematchRequestDTO();
        dto.setUserId(10L);

        mockMvc.perform(delete("/games/1/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isNoContent());

        verify(gameService).closeFinishedGame(1L, 10L);
    }

    @Test
    public void closeFinishedGame_missingUserId_returnsBadRequest() throws Exception {
        mockMvc.perform(delete("/games/1/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void closeFinishedGame_nonHost_returnsForbidden() throws Exception {
        RematchRequestDTO dto = new RematchRequestDTO();
        dto.setUserId(99L);

        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can close the game"))
                .when(gameService).closeFinishedGame(1L, 99L);

        mockMvc.perform(delete("/games/1/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void createGame_validInput_returnsCreatedGame() throws Exception {
        Game game = new Game();
        game.setId(1L); game.setLobbyCode("AAA"); game.setEra(HistoricalEra.MODERN);
        game.setStatus("WAITING"); game.setDeckSize(0); game.setNextCardIndex(0);
        game.setTimelineJson("[]");

        CreateGameDTO dto = new CreateGameDTO();
        dto.setEra(HistoricalEra.MODERN); dto.setDifficulty(Difficulty.EASY); dto.setUserId(1L);

        given(gameService.createGame(HistoricalEra.MODERN, Difficulty.EASY, 1L)).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());
        given(gameService.findWaitingRematchId(1L)).willReturn(Optional.empty());

        mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.lobbyCode", is("AAA")));
    }

    @Test
    public void leaveGame_validInput_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/games/leave/ABC123")
                        .param("userId", "1"))
                .andExpect(status().isNoContent());
        verify(gameService).leaveGame("ABC123", 1L);
    }

    @Test
    public void leaveGame_gameNotFound_returnsNotFound() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(gameService).leaveGame("ZZZZ", 1L);

        mockMvc.perform(delete("/games/leave/ZZZZ").param("userId", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void startGame_validInput_returnsGame() throws Exception {
        Game game = new Game();
        game.setId(1L); game.setLobbyCode("ABC"); game.setEra(HistoricalEra.MODERN);
        game.setStatus("IN_PROGRESS"); game.setDeckSize(20); game.setNextCardIndex(0);
        game.setTimelineJson("[]");

        given(gameService.startGame(1L, 0)).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());
        given(gameService.findWaitingRematchId(1L)).willReturn(Optional.empty());

        mockMvc.perform(put("/games/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    @Test
    public void putSettings_validInput_returnsUpdatedGame() throws Exception {
        Game game = new Game();
        game.setId(1L); game.setLobbyCode("ABC"); game.setEra(HistoricalEra.MODERN);
        game.setStatus("WAITING"); game.setDeckSize(10); game.setNextCardIndex(0);
        game.setTimelineJson("[]"); game.setDifficulty(Difficulty.HARD);

        GameSettingsPutDTO dto = new GameSettingsPutDTO();
        dto.setDifficulty(Difficulty.HARD);

        given(gameService.updateSettings(eq(1L), any(GameSettingsPutDTO.class))).willReturn(game);
        given(gameService.getTimeline(1L)).willReturn(List.of());
        given(gameService.findWaitingRematchId(1L)).willReturn(Optional.empty());

        mockMvc.perform(put("/games/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty", is("HARD")));
    }

    @Test
    public void drawCard_notPlayersTurn_returnsConflict() throws Exception {
        DrawCardDTO dto = new DrawCardDTO();
        dto.setUserId(2L); dto.setDeckIndex(0);

        given(gameService.drawCard(1L, 2L, 0))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Not your turn"));

        mockMvc.perform(post("/games/1/draw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    public void getAllCards_returnsCardList() throws Exception {
        EventCard c1 = new EventCard(); c1.setTitle("Event A"); c1.setYear(1900);
        EventCard c2 = new EventCard(); c2.setTitle("Event B"); c2.setYear(1950);

        given(gameService.getAllCards(1L)).willReturn(List.of(c1, c2));

        mockMvc.perform(get("/games/1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Event A")))
                .andExpect(jsonPath("$[1].title", is("Event B")));
    }

    @Test
    public void getTimeline_returnsTimelineCards() throws Exception {
        EventCard c = new EventCard(); c.setTitle("French Revolution"); c.setYear(1789);

        given(gameService.getTimeline(1L)).willReturn(List.of(c));

        mockMvc.perform(get("/games/1/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("French Revolution")));
    }

    @Test
    public void getHand_returnsHandCards() throws Exception {
        HandCardDTO card = new HandCardDTO();
        card.setDeckIndex(2); card.setTitle("Magna Carta");
        card.setImageUrl("https://example.com/magna.jpg");

        given(gameService.getHand(1L, 5L)).willReturn(List.of(card));

        mockMvc.perform(get("/games/1/hand").param("userId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Magna Carta")))
                .andExpect(jsonPath("$[0].deckIndex", is(2)));
    }

    @Test
    public void sendMessage_validInput_returnsMessage() throws Exception {
        ChatMessageGetDTO response = new ChatMessageGetDTO();
        response.setPlayerId(1L); response.setMessage("Hello!");

        given(gameService.addChatMessage(eq(1L), eq(1L), eq("Hello!"))).willReturn(response);

        mockMvc.perform(post("/games/1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":1,\"message\":\"Hello!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Hello!")));
    }

    @Test
    public void getMessages_returnsMessageList() throws Exception {
        ChatMessageGetDTO m = new ChatMessageGetDTO();
        m.setPlayerId(2L); m.setMessage("Good game!");

        given(gameService.getChatMessages(1L)).willReturn(List.of(m));

        mockMvc.perform(get("/games/1/chat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message", is("Good game!")));
    }

    @Test
    public void invitePlayer_validInput_returnsCreated() throws Exception {
        mockMvc.perform(post("/games/1/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromUserId\":1,\"toUsername\":\"bob\"}"))
                .andExpect(status().isCreated());
        verify(gameService).invitePlayer(1L, 1L, "bob");
    }

    @Test
    public void getInvites_returnsInviteList() throws Exception {
        GameInviteGetDTO inv = new GameInviteGetDTO();
        inv.setId(1L); inv.setGameId(10L);
        inv.setLobbyCode("XYZ"); inv.setFromUsername("alice");

        given(gameService.getInvitesForUser(5L)).willReturn(List.of(inv));

        mockMvc.perform(get("/games/invites/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromUsername", is("alice")));
    }

    @Test
    public void deleteInvite_validInput_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/games/invites/42"))
                .andExpect(status().isNoContent());
        verify(gameService).deleteInvite(42L);
    }

    @Test
    public void revealCard_validInput_returnsRevealedCard() throws Exception {
        EventCard card = new EventCard();
        card.setId(10L);
        card.setTitle("Moon Landing");
        card.setYear(1969);
        card.setImageUrl("https://example.com/moon.jpg");

        given(gameService.getCard(1L, 0)).willReturn(card);

        mockMvc.perform(get("/games/1/cards/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.title", is("Moon Landing")))
                .andExpect(jsonPath("$.year", is(1969)))
                .andExpect(jsonPath("$.imageUrl", is("https://example.com/moon.jpg")));
    }
}
