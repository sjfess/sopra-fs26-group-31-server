package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.LeaderboardEntryDTO;
import ch.uzh.ifi.hase.soprafs26.service.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaderboardController.class)
public class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaderboardService leaderboardService;

    @Test
    public void givenLeaderboard_whenGetLeaderboard_thenReturnJsonArray() throws Exception {
        LeaderboardEntryDTO entry1 = new LeaderboardEntryDTO();
        entry1.setRank(1);
        entry1.setUserId(2L);
        entry1.setUsername("mia");
        entry1.setTotalPoints(150);
        entry1.setTotalWins(3);
        entry1.setTotalGamesPlayed(12);
        entry1.setTotalCorrectPlacements(30);
        entry1.setTotalIncorrectPlacements(5);

        LeaderboardEntryDTO entry2 = new LeaderboardEntryDTO();
        entry2.setRank(2);
        entry2.setUserId(3L);
        entry2.setUsername("noah");
        entry2.setTotalPoints(100);
        entry2.setTotalWins(7);
        entry2.setTotalGamesPlayed(9);
        entry2.setTotalCorrectPlacements(25);
        entry2.setTotalIncorrectPlacements(2);

        given(leaderboardService.getLeaderboard()).willReturn(List.of(entry1, entry2));

        MockHttpServletRequestBuilder getRequest =
                get("/leaderboard").contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].rank", is(1)))
                .andExpect(jsonPath("$[0].userId", is(2)))
                .andExpect(jsonPath("$[0].username", is("mia")))
                .andExpect(jsonPath("$[0].totalPoints", is(150)))
                .andExpect(jsonPath("$[0].totalWins", is(3)))
                .andExpect(jsonPath("$[0].totalGamesPlayed", is(12)))
                .andExpect(jsonPath("$[0].totalCorrectPlacements", is(30)))
                .andExpect(jsonPath("$[0].totalIncorrectPlacements", is(5)))
                .andExpect(jsonPath("$[1].rank", is(2)))
                .andExpect(jsonPath("$[1].userId", is(3)))
                .andExpect(jsonPath("$[1].username", is("noah")))
                .andExpect(jsonPath("$[1].totalPoints", is(100)))
                .andExpect(jsonPath("$[1].totalWins", is(7)))
                .andExpect(jsonPath("$[1].totalGamesPlayed", is(9)))
                .andExpect(jsonPath("$[1].totalCorrectPlacements", is(25)))
                .andExpect(jsonPath("$[1].totalIncorrectPlacements", is(2)));
    }

    @Test
    public void givenEmptyLeaderboard_whenGetLeaderboard_thenReturnEmptyJsonArray() throws Exception {
        given(leaderboardService.getLeaderboard()).willReturn(List.of());

        mockMvc.perform(get("/leaderboard").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void givenUserId_whenGetLeaderboardEntryForUser_thenReturnJsonObject() throws Exception {
        LeaderboardEntryDTO entry = new LeaderboardEntryDTO();
        entry.setRank(2);
        entry.setUserId(1L);
        entry.setUsername("alex");
        entry.setTotalPoints(100);
        entry.setTotalWins(5);
        entry.setTotalGamesPlayed(10);
        entry.setTotalCorrectPlacements(20);
        entry.setTotalIncorrectPlacements(4);

        given(leaderboardService.getLeaderboardEntryForUser(1L)).willReturn(entry);

        mockMvc.perform(get("/leaderboard/users/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank", is(2)))
                .andExpect(jsonPath("$.userId", is(1)))
                .andExpect(jsonPath("$.username", is("alex")))
                .andExpect(jsonPath("$.totalPoints", is(100)))
                .andExpect(jsonPath("$.totalWins", is(5)))
                .andExpect(jsonPath("$.totalGamesPlayed", is(10)))
                .andExpect(jsonPath("$.totalCorrectPlacements", is(20)))
                .andExpect(jsonPath("$.totalIncorrectPlacements", is(4)));
    }

    @Test
    public void getLeaderboardEntryForUser_notFound_returnsNotFound() throws Exception {
        given(leaderboardService.getLeaderboardEntryForUser(99L))
                .willThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User with id 99 was not found in leaderboard"
                ));

        mockMvc.perform(get("/leaderboard/users/99").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}