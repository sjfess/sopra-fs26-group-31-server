package ch.uzh.ifi.hase.soprafs26.repository;

import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("gamePlayerRepository")
public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {

    List<GamePlayer> findAllByGameOrderByTurnOrderAsc(Game game);

    List<GamePlayer> findAllByGameOrderByScoreDescTurnOrderAsc(Game game);

    Optional<GamePlayer> findByGameAndUser(Game game, User user);

    Optional<GamePlayer> findByGameAndActiveTurnTrue(Game game);

    boolean existsByGameAndUser(Game game, User user);

    void deleteByGameAndUser(Game game, User user);
}