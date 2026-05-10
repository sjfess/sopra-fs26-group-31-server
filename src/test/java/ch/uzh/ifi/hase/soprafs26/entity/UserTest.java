package ch.uzh.ifi.hase.soprafs26.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    private User user;

    @BeforeEach
    void setup() {
        user = new User();
    }

    /** getAveragePointsPerGame tests */

    @Test
    void averagePointsPerGame_noGamesPlayed_returnsZero() {
        user.setTotalGamesPlayed(0);
        user.setTotalPoints(500);
        assertEquals(0.0, user.getAveragePointsPerGame());
    }

    @Test
    void averagePointsPerGame_nullGamesPlayed_returnsZero() {
        user.setTotalGamesPlayed(null);
        user.setTotalPoints(500);
        assertEquals(0.0, user.getAveragePointsPerGame());
    }

    @Test
    void averagePointsPerGame_happyPath_returnsCorrectAverage() {
        user.setTotalGamesPlayed(4);
        user.setTotalPoints(200);
        assertEquals(50.0, user.getAveragePointsPerGame());
    }

    @Test
    void averagePointsPerGame_nonDivisible_returnsDoubleResult() {
        user.setTotalGamesPlayed(3);
        user.setTotalPoints(100);
        assertEquals(100.0 / 3, user.getAveragePointsPerGame(), 0.0001);
    }

    /** getFriendCount tests */

    @Test
    void friendCount_nullFriends_returnsZero() {
        user.setFriends(null);
        assertEquals(0, user.getFriendCount());
    }

    @Test
    void friendCount_emptySet_returnsZero() {
        user.setFriends(new HashSet<>());
        assertEquals(0, user.getFriendCount());
    }

    @Test
    void friendCount_twoFriends_returnsTwo() {
        User f1 = new User(); f1.setId(1L);
        User f2 = new User(); f2.setId(2L);
        user.setFriends(Set.of(f1, f2));
        assertEquals(2, user.getFriendCount());
    }

    /** getAccuracy tests */

    @Test
    void accuracy_noPlacementsAtAll_returnsZero() {
        user.setTotalCorrectPlacements(0);
        user.setTotalIncorrectPlacements(0);
        assertEquals(0.0, user.getAccuracy());
    }

    @Test
    void accuracy_nullPlacements_returnsZero() {
        user.setTotalCorrectPlacements(null);
        user.setTotalIncorrectPlacements(null);
        assertEquals(0.0, user.getAccuracy());
    }

    @Test
    void accuracy_allCorrect_returnsOne() {
        user.setTotalCorrectPlacements(10);
        user.setTotalIncorrectPlacements(0);
        assertEquals(1.0, user.getAccuracy());
    }

    @Test
    void accuracy_allIncorrect_returnsZero() {
        user.setTotalCorrectPlacements(0);
        user.setTotalIncorrectPlacements(10);
        assertEquals(0.0, user.getAccuracy());
    }

    @Test
    void accuracy_mixed_returnsCorrectRatio() {
        user.setTotalCorrectPlacements(3);
        user.setTotalIncorrectPlacements(1);
        assertEquals(0.75, user.getAccuracy(), 0.0001);
    }
}