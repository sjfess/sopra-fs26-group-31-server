package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LeaderboardEntryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class LeaderboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LeaderboardService leaderboardService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void getLeaderboard_multipleUsers_sortedByPointsThenWins_success() {
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("alex");
        user1.setTotalPoints(100);
        user1.setTotalWins(5);
        user1.setTotalGamesPlayed(10);

        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("mia");
        user2.setTotalPoints(150);
        user2.setTotalWins(3);
        user2.setTotalGamesPlayed(12);

        User user3 = new User();
        user3.setId(3L);
        user3.setUsername("noah");
        user3.setTotalPoints(100);
        user3.setTotalWins(7);
        user3.setTotalGamesPlayed(9);

        when(userRepository.findAll()).thenReturn(Arrays.asList(user1, user2, user3));

        List<LeaderboardEntryDTO> leaderboard = leaderboardService.getLeaderboard();

        assertEquals(3, leaderboard.size());

        assertEquals(1, leaderboard.get(0).getRank());
        assertEquals("mia", leaderboard.get(0).getUsername());
        assertEquals(150, leaderboard.get(0).getTotalPoints());

        assertEquals(2, leaderboard.get(1).getRank());
        assertEquals("noah", leaderboard.get(1).getUsername());
        assertEquals(100, leaderboard.get(1).getTotalPoints());
        assertEquals(7, leaderboard.get(1).getTotalWins());

        assertEquals(3, leaderboard.get(2).getRank());
        assertEquals("alex", leaderboard.get(2).getUsername());
        assertEquals(100, leaderboard.get(2).getTotalPoints());
        assertEquals(5, leaderboard.get(2).getTotalWins());
    }

    @Test
    public void getLeaderboard_emptyRepository_returnsEmptyList() {
        when(userRepository.findAll()).thenReturn(List.of());

        List<LeaderboardEntryDTO> leaderboard = leaderboardService.getLeaderboard();

        assertNotNull(leaderboard);
        assertTrue(leaderboard.isEmpty());
    }

    @Test
    public void getLeaderboard_samePointsAndWins_sortedByUsername_success() {
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("anna");
        user1.setTotalPoints(100);
        user1.setTotalWins(5);
        user1.setTotalGamesPlayed(10);

        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("bella");
        user2.setTotalPoints(100);
        user2.setTotalWins(5);
        user2.setTotalGamesPlayed(8);

        when(userRepository.findAll()).thenReturn(Arrays.asList(user2, user1));

        List<LeaderboardEntryDTO> leaderboard = leaderboardService.getLeaderboard();

        assertEquals(2, leaderboard.size());
        assertEquals("anna", leaderboard.get(0).getUsername());
        assertEquals("bella", leaderboard.get(1).getUsername());
    }
}