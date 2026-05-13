package ch.uzh.ifi.hase.soprafs26.repository;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository("userRepository")
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
    User findByToken(String token);

    List<User> findAllByOrderByTotalPointsDescTotalWinsDescUsernameAsc();

    @Query("SELECT u FROM User u WHERE u.status = :status AND (u.lastSeenAt IS NULL OR u.lastSeenAt < :cutoff)")
    List<User> findStaleUsersByStatus(@Param("status") UserStatus status, @Param("cutoff") Instant cutoff);
}
