package ch.uzh.ifi.hase.soprafs26.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FinalResultDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GamePlayerScoreDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.JoinGameDTO;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        FinalResultDTO result2 = new FinalResultDTO();
        result2.setUserId(2L);
        result2.setUsername("mia");
        result2.setScore(3);
        result2.setCorrectPlacements(3);
        result2.setIncorrectPlacements(2);
        result2.setWinner(false);

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
                .andExpect(jsonPath("$[1].userId", is(2)))
                .andExpect(jsonPath("$[1].username", is("mia")))
                .andExpect(jsonPath("$[1].score", is(3)))
                .andExpect(jsonPath("$[1].correctPlacements", is(3)))
                .andExpect(jsonPath("$[1].incorrectPlacements", is(2)))
                .andExpect(jsonPath("$[1].winner", is(false)));
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
}