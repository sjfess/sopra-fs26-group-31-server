package ch.uzh.ifi.hase.soprafs26.repository;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventCardRepository extends JpaRepository<EventCard, Long> {
    List<EventCard> findByEra(HistoricalEra era);
    boolean existsByEra(HistoricalEra era);
    long countByEra(HistoricalEra era);
    void deleteByEra(HistoricalEra era);
}

