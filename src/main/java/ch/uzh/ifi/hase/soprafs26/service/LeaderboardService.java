package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LeaderboardEntryDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class LeaderboardService {

    private final UserRepository userRepository;

    public LeaderboardService(@Qualifier("userRepository") UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<LeaderboardEntryDTO> getLeaderboard() {
        List<User> users = new ArrayList<>(userRepository.findAll());

        users.sort(
                Comparator.comparing(User::getTotalPoints, Comparator.nullsFirst(Comparator.reverseOrder()))
                        .thenComparing(User::getTotalWins, Comparator.nullsFirst(Comparator.reverseOrder()))
                        .thenComparing(User::getUsername, Comparator.nullsFirst(String::compareTo))
        );

        List<LeaderboardEntryDTO> leaderboard = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);

            LeaderboardEntryDTO entry = new LeaderboardEntryDTO();
            entry.setRank(i + 1);
            entry.setUserId(user.getId());
            entry.setUsername(user.getUsername());
            entry.setTotalPoints(user.getTotalPoints());
            entry.setTotalWins(user.getTotalWins());
            entry.setTotalGamesPlayed(user.getTotalGamesPlayed());

            leaderboard.add(entry);
        }

        return leaderboard;
    }
}