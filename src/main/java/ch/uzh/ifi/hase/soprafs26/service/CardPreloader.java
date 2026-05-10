package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.repository.EventCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CardPreloader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CardPreloader.class);
    private final WikidataService wikidataService;
    private final EventCardRepository eventCardRepository;

    public CardPreloader(WikidataService wikidataService, EventCardRepository eventCardRepository) {
        this.wikidataService = wikidataService;
        this.eventCardRepository = eventCardRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Preloading event cards from Wikidata at startup ===");

        // Nur unvollständige Eras neu laden (z.B. MEDIEVAL hatte Save-Fehler)
        for (HistoricalEra era : HistoricalEra.values()) {
            try {
                long count = eventCardRepository.countByEra(era);
                if (count < 50) {
                    // Zu wenig Karten → alte löschen und neu laden
                    log.info("Era {} has only {} cards, reloading...", era, count);
                    eventCardRepository.deleteByEra(era);
                    Thread.sleep(3000);
                    wikidataService.getCachedCards(era);
                } else {
                    log.info("✓ Era {} already has {} cards, skipping", era, count);
                }
            } catch (Exception e) {
                log.warn("✗ Era {} failed: {}", era, e.getMessage());
            }
        }
        log.info("=== Preloading complete. ===");
    }
}
