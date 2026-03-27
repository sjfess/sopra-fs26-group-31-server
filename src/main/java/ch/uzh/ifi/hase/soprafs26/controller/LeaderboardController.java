package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.LeaderboardEntryDTO;
import ch.uzh.ifi.hase.soprafs26.service.LeaderboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/leaderboard")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<LeaderboardEntryDTO> getLeaderboard() {
        return leaderboardService.getLeaderboard();
    }
}