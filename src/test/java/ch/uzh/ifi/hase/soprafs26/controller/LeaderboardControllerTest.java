package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.LeaderboardEntryDTO;
import ch.uzh.ifi.hase.soprafs26.service.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

        LeaderboardEntryDTO entry2 = new LeaderboardEntryDTO();
        entry2.setRank(2);
        entry2.setUserId(3L);
        entry2.setUsername("noah");
        entry2.setTotalPoints(100);
        entry2.setTotalWins(7);
        entry2.setTotalGamesPlayed(9);

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
                .andExpect(jsonPath("$[1].rank", is(2)))
                .andExpect(jsonPath("$[1].userId", is(3)))
                .andExpect(jsonPath("$[1].username", is("noah")))
                .andExpect(jsonPath("$[1].totalPoints", is(100)))
                .andExpect(jsonPath("$[1].totalWins", is(7)))
                .andExpect(jsonPath("$[1].totalGamesPlayed", is(9)));
    }

    @Test
    public void givenEmptyLeaderboard_whenGetLeaderboard_thenReturnEmptyJsonArray() throws Exception {
        given(leaderboardService.getLeaderboard()).willReturn(List.of());

        mockMvc.perform(get("/leaderboard").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}