package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LeaderboardEntryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        user1.setTotalCorrectPlacements(20);
        user1.setTotalIncorrectPlacements(4);

        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("mia");
        user2.setTotalPoints(150);
        user2.setTotalWins(3);
        user2.setTotalGamesPlayed(12);
        user2.setTotalCorrectPlacements(30);
        user2.setTotalIncorrectPlacements(5);

        User user3 = new User();
        user3.setId(3L);
        user3.setUsername("noah");
        user3.setTotalPoints(100);
        user3.setTotalWins(7);
        user3.setTotalGamesPlayed(9);
        user3.setTotalCorrectPlacements(25);
        user3.setTotalIncorrectPlacements(2);

        when(userRepository.findAllByOrderByTotalPointsDescTotalWinsDescUsernameAsc())
                .thenReturn(Arrays.asList(user2, user3, user1));

        List<LeaderboardEntryDTO> leaderboard = leaderboardService.getLeaderboard();

        assertEquals(3, leaderboard.size());

        assertEquals(1, leaderboard.get(0).getRank());
        assertEquals(2L, leaderboard.get(0).getUserId());
        assertEquals("mia", leaderboard.get(0).getUsername());
        assertEquals(150, leaderboard.get(0).getTotalPoints());
        assertEquals(3, leaderboard.get(0).getTotalWins());
        assertEquals(12, leaderboard.get(0).getTotalGamesPlayed());
        assertEquals(30, leaderboard.get(0).getTotalCorrectPlacements());
        assertEquals(5, leaderboard.get(0).getTotalIncorrectPlacements());

        assertEquals(2, leaderboard.get(1).getRank());
        assertEquals(3L, leaderboard.get(1).getUserId());
        assertEquals("noah", leaderboard.get(1).getUsername());
        assertEquals(100, leaderboard.get(1).getTotalPoints());
        assertEquals(7, leaderboard.get(1).getTotalWins());
        assertEquals(9, leaderboard.get(1).getTotalGamesPlayed());
        assertEquals(25, leaderboard.get(1).getTotalCorrectPlacements());
        assertEquals(2, leaderboard.get(1).getTotalIncorrectPlacements());

        assertEquals(3, leaderboard.get(2).getRank());
        assertEquals(1L, leaderboard.get(2).getUserId());
        assertEquals("alex", leaderboard.get(2).getUsername());
        assertEquals(100, leaderboard.get(2).getTotalPoints());
        assertEquals(5, leaderboard.get(2).getTotalWins());
        assertEquals(10, leaderboard.get(2).getTotalGamesPlayed());
        assertEquals(20, leaderboard.get(2).getTotalCorrectPlacements());
        assertEquals(4, leaderboard.get(2).getTotalIncorrectPlacements());
    }

    @Test
    public void getLeaderboard_emptyRepository_returnsEmptyList() {
        when(userRepository.findAllByOrderByTotalPointsDescTotalWinsDescUsernameAsc())
                .thenReturn(List.of());

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

        when(userRepository.findAllByOrderByTotalPointsDescTotalWinsDescUsernameAsc())
                .thenReturn(Arrays.asList(user1, user2));

        List<LeaderboardEntryDTO> leaderboard = leaderboardService.getLeaderboard();

        assertEquals(2, leaderboard.size());
        assertEquals("anna", leaderboard.get(0).getUsername());
        assertEquals("bella", leaderboard.get(1).getUsername());
    }

    @Test
    public void getLeaderboard_nullStatistics_convertsToZero_success() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alex");
        user.setTotalPoints(null);
        user.setTotalWins(null);
        user.setTotalGamesPlayed(null);
        user.setTotalCorrectPlacements(null);
        user.setTotalIncorrectPlacements(null);

        when(userRepository.findAllByOrderByTotalPointsDescTotalWinsDescUsernameAsc())
                .thenReturn(List.of(user));

        List<LeaderboardEntryDTO> leaderboard = leaderboardService.getLeaderboard();

        assertEquals(1, leaderboard.size());
        assertEquals(1, leaderboard.get(0).getRank());
        assertEquals(1L, leaderboard.get(0).getUserId());
        assertEquals("alex", leaderboard.get(0).getUsername());
        assertEquals(0, leaderboard.get(0).getTotalPoints());
        assertEquals(0, leaderboard.get(0).getTotalWins());
        assertEquals(0, leaderboard.get(0).getTotalGamesPlayed());
        assertEquals(0, leaderboard.get(0).getTotalCorrectPlacements());
        assertEquals(0, leaderboard.get(0).getTotalIncorrectPlacements());
    }

    @Test
    public void getLeaderboardEntryForUser_existingUser_returnsCorrectEntry() {
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("alex");
        user1.setTotalPoints(100);
        user1.setTotalWins(5);
        user1.setTotalGamesPlayed(10);
        user1.setTotalCorrectPlacements(20);
        user1.setTotalIncorrectPlacements(4);

        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("mia");
        user2.setTotalPoints(150);
        user2.setTotalWins(3);
        user2.setTotalGamesPlayed(12);
        user2.setTotalCorrectPlacements(30);
        user2.setTotalIncorrectPlacements(5);

        when(userRepository.findAllByOrderByTotalPointsDescTotalWinsDescUsernameAsc())
                .thenReturn(List.of(user2, user1));

        LeaderboardEntryDTO result = leaderboardService.getLeaderboardEntryForUser(1L);

        assertEquals(2, result.getRank());
        assertEquals(1L, result.getUserId());
        assertEquals("alex", result.getUsername());
        assertEquals(100, result.getTotalPoints());
        assertEquals(5, result.getTotalWins());
        assertEquals(10, result.getTotalGamesPlayed());
        assertEquals(20, result.getTotalCorrectPlacements());
        assertEquals(4, result.getTotalIncorrectPlacements());
    }

    @Test
    public void getLeaderboardEntryForUser_unknownUser_throwsNotFound() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alex");
        user.setTotalPoints(100);
        user.setTotalWins(5);
        user.setTotalGamesPlayed(10);

        when(userRepository.findAllByOrderByTotalPointsDescTotalWinsDescUsernameAsc())
                .thenReturn(List.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> leaderboardService.getLeaderboardEntryForUser(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}