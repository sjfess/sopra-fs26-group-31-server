package ch.uzh.ifi.hase.soprafs26.repository;


import ch.uzh.ifi.hase.soprafs26.entity.GameInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameInviteRepository extends JpaRepository<GameInvite, Long> {
    List<GameInvite> findAllByToUserId(Long toUserId);
    boolean existsByGameIdAndToUserId(Long gameId, Long toUserId);
}