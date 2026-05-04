package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LeaderboardEntryDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class LeaderboardService {

    private final UserRepository userRepository;

    public LeaderboardService(@Qualifier("userRepository") UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<LeaderboardEntryDTO> getLeaderboard() {
        List<User> users = userRepository.findAllByOrderByTotalPointsDescTotalWinsDescUsernameAsc();
        return convertUsersToLeaderboard(users);
    }

    public LeaderboardEntryDTO getLeaderboardEntryForUser(Long userId) {
        List<LeaderboardEntryDTO> leaderboard = getLeaderboard();

        return leaderboard.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User with id " + userId + " was not found in leaderboard"
                ));
    }

    private List<LeaderboardEntryDTO> convertUsersToLeaderboard(List<User> users) {
        List<LeaderboardEntryDTO> leaderboard = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);

            LeaderboardEntryDTO entry = new LeaderboardEntryDTO();
            entry.setRank(i + 1);
            entry.setUserId(user.getId());
            entry.setUsername(user.getUsername());
            entry.setTotalPoints(nullToZero(user.getTotalPoints()));
            entry.setTotalWins(nullToZero(user.getTotalWins()));
            entry.setTotalGamesPlayed(nullToZero(user.getTotalGamesPlayed()));

            leaderboard.add(entry);
        }

        return leaderboard;
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}