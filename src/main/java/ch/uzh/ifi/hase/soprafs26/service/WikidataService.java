package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.repository.EventCardRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class WikidataService {

    private static final Logger log = LoggerFactory.getLogger(WikidataService.class);
    private static final String SPARQL_ENDPOINT = "https://query.wikidata.org/sparql";

    private static final String EVENTS_QUERY = """
            #%s
            SELECT DISTINCT ?event ?eventLabel (YEAR(?date) AS ?year) ?image WHERE {
              VALUES ?type {
                wd:Q1190554  wd:Q124757  wd:Q625994  wd:Q11023
                wd:Q132241   wd:Q2627975 wd:Q8065    wd:Q188451
                wd:Q180684   wd:Q131569  wd:Q1656682 wd:Q3024240
                wd:Q7283     wd:Q15275719
              }
              ?event wdt:P31 ?type .
              ?event wdt:P585 ?date .
              FILTER(YEAR(?date) >= %d && YEAR(?date) <= %d)
              FILTER NOT EXISTS { ?event wdt:P31 wd:Q577 . }
              FILTER NOT EXISTS { ?event wdt:P31 wd:Q578 . }
              FILTER NOT EXISTS { ?event wdt:P31 wd:Q36507 . }
              FILTER NOT EXISTS { ?event wdt:P31 wd:Q21199 . }
              FILTER NOT EXISTS { ?event wdt:P31 wd:Q178561 . }
              OPTIONAL { ?event wdt:P18 ?image . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en" . }
              BIND(SHA512(CONCAT(STR(RAND()), STR(?event))) AS ?random)
            }
            ORDER BY ?random
            LIMIT %d
            """;

    private static final String START_DATE_QUERY = """
            #%s
            SELECT DISTINCT ?event ?eventLabel (YEAR(?date) AS ?year) ?image WHERE {
              VALUES ?type { wd:Q198 wd:Q3505845 wd:Q7278 wd:Q49773 }
              ?event wdt:P31 ?type .
              ?event wdt:P580 ?date .
              FILTER(YEAR(?date) >= %d && YEAR(?date) <= %d)
              OPTIONAL { ?event wdt:P18 ?image . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en" . }
              BIND(SHA512(CONCAT(STR(RAND()), STR(?event))) AS ?random)
            }
            ORDER BY ?random
            LIMIT %d
            """;

    private static final String HISTORICAL_EVENT_QUERY = """
            #%s
            SELECT DISTINCT ?event ?eventLabel (YEAR(?date) AS ?year) ?image WHERE {
              ?event wdt:P31 wd:Q13418847 .
              { ?event wdt:P585 ?date . } UNION { ?event wdt:P580 ?date . }
              FILTER(YEAR(?date) >= %d && YEAR(?date) <= %d)
              OPTIONAL { ?event wdt:P18 ?image . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en" . }
              BIND(SHA512(CONCAT(STR(RAND()), STR(?event))) AS ?random)
            }
            ORDER BY ?random
            LIMIT %d
            """;

    private static final String CULTURAL_QUERY = """
            #%s
            SELECT DISTINCT ?event ?eventLabel (YEAR(?date) AS ?year) ?image WHERE {
              VALUES ?type {
                wd:Q35140   wd:Q2110    wd:Q7688    wd:Q35127
                wd:Q3024240 wd:Q131569  wd:Q2065736 wd:Q189004
                wd:Q476300  wd:Q58415929
              }
              ?event wdt:P31 ?type .
              { ?event wdt:P585 ?date . } UNION { ?event wdt:P580 ?date . }
              FILTER(YEAR(?date) >= %d && YEAR(?date) <= %d)
              OPTIONAL { ?event wdt:P18 ?image . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en" . }
              BIND(SHA512(CONCAT(STR(RAND()), STR(?event))) AS ?random)
            }
            ORDER BY ?random
            LIMIT %d
            """;

    private static final double BATTLE_CAP = 0.2;
    private static final int CACHE_SIZE_PER_ERA = 300;
    private static final double CURATED_RATIO = 1.0 / 3.0;

    private final RestClient restClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final EventCardRepository eventCardRepository;  // NEU


    public WikidataService(EventCardRepository eventCardRepository) {
        this.eventCardRepository = eventCardRepository;
        this.restClient = RestClient.builder()
                .baseUrl(SPARQL_ENDPOINT)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "HistoricalReconstruction/1.0 (UZH SoPra FS26 Group 31)")
                .build();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Layer 1: Caffeine In-Memory Cache (@Cacheable) — 0ms
     * Layer 2: Datenbank (existsByEra / findByEra) — ~5ms
     * Layer 3: Wikidata API (nur beim allerersten Aufruf) — ~20s
     */
// Diese Methode wird gecacht — gibt IMMER die grosse Liste zurück
    @Cacheable(value = "eventCards", key = "#era.name()")
    public List<EventCard> getCachedCards(HistoricalEra era) {

        if (eventCardRepository.existsByEra(era)) {
            List<EventCard> cached = eventCardRepository.findByEra(era);
            log.info("Serving {} cards for era {} from DATABASE", cached.size(), era);
            return cached;
        }

        log.info("No DB cache for era {}. Fetching from Wikidata...", era);
        List<EventCard> fresh = fetchFromWikidata(era, CACHE_SIZE_PER_ERA);
        fresh.forEach(card -> card.setEra(era));
        eventCardRepository.saveAll(fresh);
        log.info("Saved {} cards for era {} to database", fresh.size(), era);
        return fresh;
    }

    public List<EventCard> fetchEvents(HistoricalEra era, int limit) {
        List<EventCard> allCards = new ArrayList<>(getCachedCards(era));
        Collections.shuffle(allCards);
        return allCards.stream().limit(limit).collect(Collectors.toList());
    }

// =========================================================================
// Builds the cached event pool for one era.
// Target mix: 1/3 curated well-known events, 2/3 Wikidata events.
// =========================================================================

    private List<EventCard> fetchFromWikidata(HistoricalEra era, int limit) {
        String tag = era.name();

        int curatedTarget = Math.max(1, (int) Math.round(limit * CURATED_RATIO));
        int wikidataTarget = limit - curatedTarget;

        int q2Limit = Math.max(8, (int) (wikidataTarget * BATTLE_CAP));

        CompletableFuture<List<EventCard>> f1 = CompletableFuture.supplyAsync(() -> {
            try {
                return runSparql(String.format(EVENTS_QUERY, tag,
                        era.getStartYear(), era.getEndYear(), wikidataTarget * 5));
            } catch (Exception e) {
                log.warn("Query1 failed: {}", e.getMessage());
                return List.of();
            }
        }, executor);

        CompletableFuture<List<EventCard>> f2 = CompletableFuture.supplyAsync(() -> {
            try {
                return runSparql(String.format(START_DATE_QUERY, tag,
                        era.getStartYear(), era.getEndYear(), q2Limit));
            } catch (Exception e) {
                log.warn("Query2 failed: {}", e.getMessage());
                return List.of();
            }
        }, executor);

        CompletableFuture<List<EventCard>> f3 = CompletableFuture.supplyAsync(() -> {
            try {
                return runSparql(String.format(HISTORICAL_EVENT_QUERY, tag,
                        era.getStartYear(), era.getEndYear(), wikidataTarget * 3));
            } catch (Exception e) {
                log.warn("Query3 failed: {}", e.getMessage());
                return List.of();
            }
        }, executor);

        CompletableFuture<List<EventCard>> f4 = CompletableFuture.supplyAsync(() -> {
            try {
                return runSparql(String.format(CULTURAL_QUERY, tag,
                        era.getStartYear(), era.getEndYear(), wikidataTarget * 3));
            } catch (Exception e) {
                log.warn("Query4 failed: {}", e.getMessage());
                return List.of();
            }
        }, executor);

        CompletableFuture.allOf(f1, f2, f3, f4).join();

        Set<String> seenGlobal = new HashSet<>();
        List<EventCard> q1Pool = new ArrayList<>();
        List<EventCard> q2Pool = new ArrayList<>();
        List<EventCard> q3Pool = new ArrayList<>();
        List<EventCard> q4Pool = new ArrayList<>();

        for (EventCard c : f1.join()) {
            if (seenGlobal.add(normalizeKey(c))) q1Pool.add(c);
        }
        for (EventCard c : f2.join()) {
            if (seenGlobal.add(normalizeKey(c))) q2Pool.add(c);
        }
        for (EventCard c : f3.join()) {
            if (seenGlobal.add(normalizeKey(c))) q3Pool.add(c);
        }
        for (EventCard c : f4.join()) {
            if (seenGlobal.add(normalizeKey(c))) q4Pool.add(c);
        }

        log.info("Pool sizes – Q1:{} Q2:{} Q3:{} Q4:{}",
                q1Pool.size(), q2Pool.size(), q3Pool.size(), q4Pool.size());

        Collections.shuffle(q1Pool);
        Collections.shuffle(q2Pool);
        Collections.shuffle(q3Pool);
        Collections.shuffle(q4Pool);

        double q1Weight, q2Weight, q3Weight, q4Weight;
        if (era == HistoricalEra.ANCIENT) {
            q1Weight = 0.15;
            q2Weight = 0.05;
            q3Weight = 0.40;
            q4Weight = 0.40;
        } else if (era == HistoricalEra.MEDIEVAL) {
            q1Weight = 0.20;
            q2Weight = 0.10;
            q3Weight = 0.35;
            q4Weight = 0.35;
        } else {
            q1Weight = 0.25;
            q2Weight = 0.10;
            q3Weight = 0.35;
            q4Weight = 0.30;
        }

        List<EventCard> wikidataCards = new ArrayList<>();
        Set<String> seenWikidata = new HashSet<>();

        addUpTo(wikidataCards, seenWikidata, q4Pool, (int) (wikidataTarget * q4Weight), wikidataTarget);
        addUpTo(wikidataCards, seenWikidata, q3Pool, (int) (wikidataTarget * q3Weight), wikidataTarget);
        addUpTo(wikidataCards, seenWikidata, q1Pool, (int) (wikidataTarget * q1Weight), wikidataTarget);
        addUpTo(wikidataCards, seenWikidata, q2Pool, (int) (wikidataTarget * q2Weight), wikidataTarget);

        List<EventCard> fallback = new ArrayList<>();
        fallback.addAll(q4Pool);
        fallback.addAll(q3Pool);
        fallback.addAll(q1Pool);
        fallback.addAll(q2Pool);
        Collections.shuffle(fallback);

        for (EventCard c : fallback) {
            if (wikidataCards.size() >= wikidataTarget) break;
            if (seenWikidata.add(normalizeKey(c))) {
                wikidataCards.add(c);
            }
        }

        List<EventCard> filteredWikidataCards = applyDiversityFilter(wikidataCards, wikidataTarget);

        List<EventCard> allCurated = new ArrayList<>(getCuratedCards(era));
        Collections.shuffle(allCurated);

        List<EventCard> curatedCards = new ArrayList<>();
        Set<String> seenCurated = new HashSet<>();

        for (EventCard c : allCurated) {
            if (curatedCards.size() >= curatedTarget) break;
            if (seenCurated.add(normalizeKey(c))) {
                curatedCards.add(c);
            }
        }

        List<EventCard> merged = new ArrayList<>();
        Set<String> seenFinal = new HashSet<>();

        for (EventCard c : curatedCards) {
            if (seenFinal.add(normalizeKey(c))) {
                merged.add(c);
            }
        }

        for (EventCard c : filteredWikidataCards) {
            if (seenFinal.add(normalizeKey(c))) {
                merged.add(c);
            }
        }

        // Falls zu wenig Wikidata kam, mit weiteren curated Cards auffüllen.
        for (EventCard c : allCurated) {
            if (merged.size() >= limit) break;
            if (seenFinal.add(normalizeKey(c))) {
                merged.add(c);
            }
        }

        // Falls zu wenig curated Cards existieren, mit weiteren Wikidata Cards auffüllen.
        for (EventCard c : fallback) {
            if (merged.size() >= limit) break;
            if (seenFinal.add(normalizeKey(c))) {
                merged.add(c);
            }
        }

        Collections.shuffle(merged);

        if (merged.size() > limit) {
            merged = new ArrayList<>(merged.subList(0, limit));
        }

        log.info("Returning {} event cards for era {} (target: {} Wikidata, {} curated)",
                merged.size(), era, wikidataTarget, curatedTarget);

        return merged;
    }

    private String normalizeKey(EventCard card) {
        if (card == null || card.getTitle() == null) {
            return "";
        }

        return (card.getTitle() + "|" + card.getYear())
                .toLowerCase()
                .replaceAll("[^a-z0-9|\\-]", "")
                .trim();
    }

    private void addUpTo(
            List<EventCard> target,
            Set<String> seen,
            List<EventCard> source,
            int maxFromSource,
            int maxTotal
    ) {
        int addedFromSource = 0;

        for (EventCard card : source) {
            if (target.size() >= maxTotal || addedFromSource >= maxFromSource) {
                break;
            }

            if (seen.add(normalizeKey(card))) {
                target.add(card);
                addedFromSource++;
            }
        }
    }
    // =========================================================================
    // SPARQL call
    // =========================================================================

    List<EventCard> runSparql(String sparql) {
        String encodedQuery = URLEncoder.encode(sparql, StandardCharsets.UTF_8);
        URI uri = URI.create(SPARQL_ENDPOINT + "?query=" + encodedQuery);
        String responseBody;
        try {
            responseBody = restClient.get()
                    .uri(uri)
                    .header("Cache-Control", "no-cache")
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Wikidata SPARQL request failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Wikidata error: " + e.getClass().getName() + ": " + e.getMessage());
        }
        return parseSparqlResponse(responseBody);
    }

    // =========================================================================
    // Diversity filter — unverändert
    // =========================================================================

    private List<EventCard> applyDiversityFilter(List<EventCard> cards, int limit) {
        List<EventCard> military = new ArrayList<>();
        List<EventCard> nonMilitary = new ArrayList<>();
        for (EventCard card : cards) {
            if (isMilitaryEvent(card.getTitle())) military.add(card);
            else nonMilitary.add(card);
        }
        Collections.shuffle(military);
        Collections.shuffle(nonMilitary);

        int maxMilitary = Math.max(2, (int) (limit * BATTLE_CAP));
        List<EventCard> result = new ArrayList<>();
        int i = 0, j = 0;

        while (result.size() < limit && (i < nonMilitary.size() || j < military.size())) {
            if (i < nonMilitary.size()) { result.add(nonMilitary.get(i++)); continue; }
            if (j < military.size() && countMilitary(result) < maxMilitary) result.add(military.get(j++));
            else break;
        }

        List<EventCard> remaining = new ArrayList<>();
        remaining.addAll(nonMilitary); remaining.addAll(military);
        Collections.shuffle(remaining);
        for (EventCard c : remaining) {
            if (result.size() >= limit) break;
            if (!result.contains(c)) result.add(c);
        }

        Collections.shuffle(result);
        return result;
    }

    private int countMilitary(List<EventCard> cards) {
        int count = 0;
        for (EventCard c : cards) if (isMilitaryEvent(c.getTitle())) count++;
        return count;
    }

    private boolean isMilitaryEvent(String title) {
        String t = title.toLowerCase();
        return t.startsWith("battle of ") || t.startsWith("siege of ")
                || t.startsWith("sack of ") || t.startsWith("fall of ")
                || t.startsWith("capture of ") || t.startsWith("raid on ")
                || t.startsWith("conquest of ") || t.startsWith("invasion of ")
                || t.contains(" war") || t.contains(" revolt")
                || t.contains(" rebellion") || t.contains(" uprising")
                || t.contains(" crusade") || t.contains(" campaign");
    }

    // =========================================================================
    // Curated cards — unverändert
    // =========================================================================

    public List<EventCard> getCuratedCards(HistoricalEra era) {
        List<EventCard> all = new ArrayList<>();

        add(all, "Construction of the Great Pyramid of Giza", -2560, null);
        add(all, "Hammurabi's Code of Laws", -1754, null);
        add(all, "Founding of Rome (traditional date)", -753, null);
        add(all, "First Olympic Games in Greece", -776, null);
        add(all, "Construction of the Parthenon", -447, null);
        add(all, "Birth of Siddhartha Gautama (Buddha)", -563, null);
        add(all, "Birth of Confucius", -551, null);
        add(all, "Birth of Socrates", -470, null);
        add(all, "Birth of Aristotle", -384, null);
        add(all, "Birth of Alexander the Great", -356, null);
        add(all, "Birth of Archimedes", -287, null);
        add(all, "Birth of Julius Caesar", -100, null);
        add(all, "Birth of Cleopatra VII", -69, null);
        add(all, "Birth of Jesus Christ (traditional date)", 0, null);
        add(all, "Eruption of Mount Vesuvius (Pompeii)", 79, null);
        add(all, "Invention of Writing in Mesopotamia", -3400, null);
        add(all, "Construction of Stonehenge", -2500, null);
        add(all, "Reign of Pharaoh Tutankhamun begins", -1332, null);
        add(all, "Trojan War (traditional date)", -1184, null);
        add(all, "Founding of Carthage", -814, null);
        add(all, "Birth of Hippocrates (Father of Medicine)", -460, null);
        add(all, "Birth of Plato", -428, null);
        add(all, "Alexander the Great conquers Persia", -331, null);
        add(all, "Construction of the Library of Alexandria", -283, null);
        add(all, "Construction of the Great Wall of China begins", -221, null);
        add(all, "Assassination of Julius Caesar", -44, null);
        add(all, "Birth of Augustus (First Roman Emperor)", -63, null);
        add(all, "Destruction of the Temple in Jerusalem", 70, null);
        add(all, "Construction of the Colosseum in Rome", 80, null);
        add(all, "Splitting of the Roman Empire", 395, null);
        add(all, "Fall of the Western Roman Empire", 476, null);
        add(all, "Birth of Muhammad (Prophet of Islam)", 570, null);
        add(all, "Coronation of Charlemagne", 800, null);
        add(all, "Birth of Genghis Khan", 1162, null);
        add(all, "Signing of the Magna Carta", 1215, null);
        add(all, "Birth of Thomas Aquinas", 1225, null);
        add(all, "Birth of Dante Alighieri", 1265, null);
        add(all, "Marco Polo arrives in China", 1275, null);
        add(all, "The Black Death reaches Europe", 1347, null);
        add(all, "Justinian's Plague devastates Byzantine Empire", 541, null);
        add(all, "Construction of Hagia Sophia", 537, null);
        add(all, "The Hegira (Muhammad's migration to Medina)", 622, null);
        add(all, "Viking discovery of Iceland", 870, null);
        add(all, "Founding of the University of Bologna", 1088, null);
        add(all, "Leif Erikson reaches North America", 1000, null);
        add(all, "Construction of Notre-Dame de Paris begins", 1163, null);
        add(all, "Founding of the University of Oxford", 1096, null);
        add(all, "Birth of William Wallace", 1270, null);
        add(all, "Travels of Ibn Battuta begin", 1325, null);
        add(all, "Construction of the Alhambra", 1238, null);
        add(all, "Foundation of the Aztec capital Tenochtitlan", 1325, null);
        add(all, "Mongol Empire reaches its greatest extent", 1279, null);
        add(all, "Great Schism between Eastern and Western Churches", 1054, null);
        add(all, "Birth of Joan of Arc", 1412, null);
        add(all, "Gutenberg invents the Printing Press", 1440, null);
        add(all, "Birth of Leonardo da Vinci", 1452, null);
        add(all, "Birth of Nicolaus Copernicus", 1473, null);
        add(all, "Birth of Michelangelo", 1475, null);
        add(all, "Columbus reaches the Americas", 1492, null);
        add(all, "Magellan's expedition circumnavigates the Earth", 1522, null);
        add(all, "Birth of Galileo Galilei", 1564, null);
        add(all, "Birth of William Shakespeare", 1564, null);
        add(all, "Fall of Constantinople", 1453, null);
        add(all, "Spanish Inquisition established", 1478, null);
        add(all, "Treaty of Tordesillas", 1494, null);
        add(all, "Michelangelo paints the Sistine Chapel ceiling", 1512, null);
        add(all, "Hernan Cortes conquers the Aztec Empire", 1521, null);
        add(all, "Francisco Pizarro conquers the Inca Empire", 1533, null);
        add(all, "Edict of Nantes grants religious tolerance", 1598, null);
        add(all, "Birth of Martin Luther", 1483, null);
        add(all, "Birth of Henry VIII of England", 1491, null);
        add(all, "Birth of Suleiman the Magnificent", 1494, null);
        add(all, "Birth of Isaac Newton", 1643, null);
        add(all, "Birth of Johann Sebastian Bach", 1685, null);
        add(all, "Birth of Wolfgang Amadeus Mozart", 1756, null);
        add(all, "Birth of Napoleon Bonaparte", 1769, null);
        add(all, "Birth of Ludwig van Beethoven", 1770, null);
        add(all, "Birth of Charles Darwin", 1809, null);
        add(all, "Birth of Abraham Lincoln", 1809, null);
        add(all, "Birth of Otto von Bismarck", 1815, null);
        add(all, "Birth of Thomas Edison", 1847, null);
        add(all, "Birth of Nikola Tesla", 1856, null);
        add(all, "Birth of Marie Curie", 1867, null);
        add(all, "Birth of Mahatma Gandhi", 1869, null);
        add(all, "Birth of Albert Einstein", 1879, null);
        add(all, "Invention of the Telephone (Alexander Graham Bell)", 1876, null);
        add(all, "Invention of the Light Bulb (Edison)", 1879, null);
        add(all, "Opening of the Suez Canal", 1869, null);
        add(all, "Signing of the Declaration of Independence", 1776, null);
        add(all, "US Constitution ratified", 1788, null);
        add(all, "Abolition of Slavery in the British Empire", 1833, null);
        add(all, "First photograph ever taken (Niepce)", 1826, null);
        add(all, "Completion of the First Transcontinental Railroad", 1869, null);
        add(all, "Invention of the Steam Engine (James Watt)", 1769, null);
        add(all, "Publication of The Communist Manifesto", 1848, null);
        add(all, "Unification of Italy", 1861, null);
        add(all, "Unification of Germany", 1871, null);
        add(all, "Invention of the Automobile (Karl Benz)", 1886, null);
        add(all, "Birth of Queen Victoria", 1819, null);
        add(all, "Emancipation Proclamation by Lincoln", 1863, null);
        add(all, "Meiji Restoration in Japan", 1868, null);
        add(all, "First modern Olympic Games in Athens", 1896, null);
        add(all, "Wright Brothers First Flight", 1903, null);
        add(all, "Sinking of the Titanic", 1912, null);
        add(all, "Discovery of Penicillin (Alexander Fleming)", 1928, null);
        add(all, "Birth of Martin Luther King Jr.", 1929, null);
        add(all, "First human in space (Yuri Gagarin)", 1961, null);
        add(all, "Moon Landing (Apollo 11)", 1969, null);
        add(all, "Invention of the World Wide Web (Tim Berners-Lee)", 1989, null);
        add(all, "Fall of the Berlin Wall", 1989, null);
        add(all, "Launch of the iPhone", 2007, null);
        add(all, "COVID-19 Pandemic begins", 2020, null);

        // Ancient Era additions
        add(all, "Invention of the Wheel in Mesopotamia", -3500, null);
        add(all, "Unification of Upper and Lower Egypt by Narmer", -3100, null);
        add(all, "Rise of the Indus Valley Civilization", -2600, null);
        add(all, "Founding of the Akkadian Empire by Sargon of Akkad", -2334, null);
        add(all, "Code of Ur-Nammu — earliest surviving law code", -2100, null);
        add(all, "Battle of Kadesh between Egypt and the Hittites", -1274, null);
        add(all, "Fall of the Shang Dynasty in China", -1046, null);
        add(all, "Birth of Lao Tzu, founder of Taoism", -604, null);
        add(all, "Birth of Pythagoras", -570, null);
        add(all, "Founding of the Roman Republic", -509, null);
        add(all, "Sun Tzu composes The Art of War", -500, null);
        add(all, "Battle of Marathon", -490, null);
        add(all, "Battle of Thermopylae", -480, null);
        add(all, "Death of Socrates", -399, null);
        add(all, "Battle of Gaugamela", -331, null);
        add(all, "Death of Alexander the Great", -323, null);
        add(all, "Ashoka the Great converts to Buddhism", -260, null);
        add(all, "Qin Shi Huang becomes first Emperor of unified China", -221, null);
        add(all, "Hannibal Barca crosses the Alps", -218, null);
        add(all, "Han Dynasty founded in China", -206, null);
        add(all, "Birth of Cicero", -106, null);
        add(all, "Spartacus leads slave revolt against Rome", -73, null);
        add(all, "Battle of Actium — Octavian defeats Mark Antony", -31, null);
        add(all, "Great Fire of Rome under Emperor Nero", 64, null);
        add(all, "Hadrian's Wall construction begins", 122, null);
        add(all, "Edict of Milan: Constantine grants religious tolerance", 313, null);
        add(all, "First Council of Nicaea", 325, null);
        add(all, "Theodosius I makes Christianity the Roman state religion", 380, null);
        add(all, "Sack of Rome by the Visigoths", 410, null);
        add(all, "Death of Attila the Hun", 453, null);

        // Medieval Era additions
        add(all, "Battle of Tours — Charles Martel halts Muslim advance into Europe", 732, null);
        add(all, "Viking raids on England begin at Lindisfarne", 793, null);
        add(all, "Treaty of Verdun divides the Carolingian Empire", 843, null);
        add(all, "Battle of Hastings and Norman Conquest of England", 1066, null);
        add(all, "Battle of Manzikert: Seljuk Turks defeat the Byzantine Empire", 1071, null);
        add(all, "First Crusade captures Jerusalem", 1099, null);
        add(all, "Founding of the University of Paris", 1150, null);
        add(all, "Death of Thomas Becket in Canterbury Cathedral", 1170, null);
        add(all, "Saladin recaptures Jerusalem from the Crusaders", 1187, null);
        add(all, "Genghis Khan unifies the Mongol tribes", 1206, null);
        add(all, "Battle of Ain Jalut: Mamluks defeat the Mongols", 1260, null);
        add(all, "Founding of the Ottoman Empire by Osman I", 1299, null);
        add(all, "Dante Alighieri begins writing the Divine Comedy", 1308, null);
        add(all, "Battle of Bannockburn — Scotland defeats England", 1314, null);
        add(all, "Great Famine strikes Northern Europe", 1315, null);
        add(all, "Hundred Years' War between France and England begins", 1337, null);
        add(all, "Black Death kills one third of Europe's population", 1349, null);
        add(all, "Peasants' Revolt in England", 1381, null);
        add(all, "St. Benedict founds the monastery of Monte Cassino", 529, null);
        add(all, "Muslim conquest of Spain begins", 711, null);
        add(all, "Alfred the Great defeats the Vikings in England", 878, null);
        add(all, "Capetian dynasty begins with Hugh Capet as King of France", 987, null);
        add(all, "Otto I crowned Holy Roman Emperor", 962, null);
        add(all, "Investiture Controversy between Pope Gregory VII and Emperor Henry IV begins", 1075, null);
        add(all, "Second Crusade launched to the Holy Land", 1147, null);
        add(all, "Third Crusade led by Richard the Lionheart and Saladin", 1189, null);
        add(all, "Fourth Crusade sacks Constantinople", 1204, null);
        add(all, "Mongols overrun eastern Europe", 1241, null);
        add(all, "Great Schism of the Western Church (Avignon Papacy and rival popes) begins", 1378, null);

        // Renaissance Era additions
        add(all, "Battle of Agincourt: English longbowmen defeat French army", 1415, null);
        add(all, "Brunelleschi designs the dome of Florence Cathedral", 1419, null);
        add(all, "Death of Joan of Arc", 1431, null);
        add(all, "Medici family takes power in Florence", 1434, null);
        add(all, "Lorenzo de Medici becomes ruler of Florence", 1469, null);
        add(all, "Birth of Raphael", 1483, null);
        add(all, "Henry VII becomes King of England (start of Tudor dynasty)", 1485, null);
        add(all, "Botticelli completes The Birth of Venus", 1486, null);
        add(all, "Leonardo da Vinci paints The Last Supper", 1495, null);
        add(all, "Vasco da Gama reaches India by sea", 1498, null);
        add(all, "Michelangelo begins work on David", 1501, null);
        add(all, "Amerigo Vespucci explores South America", 1501, null);
        add(all, "Leonardo da Vinci paints the Mona Lisa", 1503, null);
        add(all, "Michelangelo begins painting the Sistine Chapel ceiling", 1508, null);
        add(all, "Henry VIII becomes King of England", 1509, null);
        add(all, "Erasmus writes Praise of Folly", 1509, null);
        add(all, "Raphael paints The School of Athens", 1511, null);
        add(all, "Machiavelli writes The Prince", 1513, null);
        add(all, "Thomas More publishes Utopia", 1516, null);
        add(all, "Martin Luther posts his 95 Theses in Wittenberg", 1517, null);
        add(all, "Magellan begins his circumnavigation voyage", 1519, null);
        add(all, "Death of Leonardo da Vinci", 1519, null);
        add(all, "Sack of Rome by troops of Charles V", 1527, null);
        add(all, "Birth of Queen Elizabeth I", 1533, null);
        add(all, "Henry VIII breaks from the Catholic Church", 1534, null);
        add(all, "Copernicus publishes On the Revolutions of the Heavenly Spheres", 1543, null);
        add(all, "Council of Trent begins (Catholic Counter-Reformation)", 1545, null);
        add(all, "Birth of Tycho Brahe", 1546, null);
        add(all, "Birth of Miguel de Cervantes", 1547, null);
        add(all, "Vasari publishes Lives of the Artists", 1550, null);
        add(all, "Elizabeth I becomes Queen of England", 1558, null);
        add(all, "Birth of Francis Bacon", 1561, null);
        add(all, "Birth of Johannes Kepler", 1571, null);
        add(all, "St. Bartholomew's Day Massacre in France", 1572, null);
        add(all, "Spanish Armada defeated by the English navy", 1588, null);
        add(all, "Birth of René Descartes", 1596, null);
        add(all, "Globe Theatre is built in London", 1599, null);
        add(all, "Birth of Oliver Cromwell", 1599, null);

        // Modern Era additions
        add(all, "Death of Queen Elizabeth I", 1603, null);
        add(all, "Guy Fawkes Gunpowder Plot against English Parliament", 1605, null);
        add(all, "Galileo Galilei discovers Jupiter's moons", 1610, null);
        add(all, "King James Bible published", 1611, null);
        add(all, "Thirty Years' War begins in Europe", 1618, null);
        add(all, "Pilgrims land at Plymouth Rock", 1620, null);
        add(all, "Birth of John Locke", 1632, null);
        add(all, "Taj Mahal construction begins", 1632, null);
        add(all, "Galileo's trial by the Inquisition", 1633, null);
        add(all, "English Civil War begins", 1642, null);
        add(all, "Great Plague of London", 1665, null);
        add(all, "Great Fire of London", 1666, null);
        add(all, "Newton publishes Principia Mathematica", 1687, null);
        add(all, "Glorious Revolution in England", 1688, null);
        add(all, "Birth of Voltaire", 1694, null);
        add(all, "Peter the Great founds Saint Petersburg", 1703, null);
        add(all, "Birth of Benjamin Franklin", 1706, null);
        add(all, "Birth of Jean-Jacques Rousseau", 1712, null);
        add(all, "Birth of Adam Smith", 1723, null);
        add(all, "Birth of Immanuel Kant", 1724, null);
        add(all, "Birth of George Washington", 1732, null);
        add(all, "Seven Years' War begins", 1756, null);
        add(all, "American Revolution begins at Lexington and Concord", 1775, null);
        add(all, "Adam Smith publishes The Wealth of Nations", 1776, null);
        add(all, "French Revolution begins", 1789, null);
        add(all, "Storming of the Bastille", 1789, null);
        add(all, "Washington becomes first President of the United States", 1789, null);
        add(all, "Reign of Terror in France", 1793, null);
        add(all, "Napoleon crowned Emperor of France", 1804, null);
        add(all, "Battle of Trafalgar", 1805, null);
        add(all, "Birth of Charles Dickens", 1812, null);
        add(all, "Battle of Waterloo", 1815, null);
        add(all, "Birth of Karl Marx", 1818, null);
        add(all, "Death of Napoleon Bonaparte", 1821, null);
        add(all, "Birth of Louis Pasteur", 1822, null);
        add(all, "Birth of Mark Twain", 1835, null);
        add(all, "First successful telegraph message sent by Samuel Morse", 1844, null);
        add(all, "California Gold Rush begins", 1848, null);
        add(all, "Birth of Sigmund Freud", 1856, null);
        add(all, "Charles Darwin publishes On the Origin of Species", 1859, null);
        add(all, "American Civil War begins", 1861, null);
        add(all, "Assassination of Abraham Lincoln", 1865, null);
        add(all, "Gregor Mendel publishes laws of heredity", 1866, null);
        add(all, "Alfred Nobel invents dynamite", 1867, null);
        add(all, "Birth of Vladimir Lenin", 1870, null);
        add(all, "Birth of Winston Churchill", 1874, null);
        add(all, "Louis Pasteur develops rabies vaccine", 1885, null);
        add(all, "Eiffel Tower completed", 1889, null);
        add(all, "Discovery of X-rays by Wilhelm Röntgen", 1895, null);
        add(all, "Nobel Prize established by Alfred Nobel", 1895, null);
        add(all, "Radioactivity discovered by Henri Becquerel", 1896, null);
        add(all, "Spanish-American War", 1898, null);
        add(all, "Sigmund Freud publishes The Interpretation of Dreams", 1899, null);

        // Information Era additions
        add(all, "Einstein publishes Theory of Special Relativity", 1905, null);
        add(all, "First World War begins", 1914, null);
        add(all, "Russian Revolution brings Bolsheviks to power", 1917, null);
        add(all, "Treaty of Versailles signed, ending World War I", 1919, null);
        add(all, "Women gain right to vote in the United States", 1920, null);
        add(all, "League of Nations founded", 1920, null);
        add(all, "Discovery of insulin by Banting and Best", 1921, null);
        add(all, "Charles Lindbergh completes first solo transatlantic flight", 1927, null);
        add(all, "Great Depression begins with Wall Street Crash", 1929, null);
        add(all, "Adolf Hitler becomes Chancellor of Germany", 1933, null);
        add(all, "Spanish Civil War begins", 1936, null);
        add(all, "World War II begins", 1939, null);
        add(all, "D-Day Normandy Landings", 1944, null);
        add(all, "Atomic bomb dropped on Hiroshima", 1945, null);
        add(all, "United Nations founded", 1945, null);
        add(all, "Indian Independence from Britain", 1947, null);
        add(all, "Universal Declaration of Human Rights adopted", 1948, null);
        add(all, "People's Republic of China founded", 1949, null);
        add(all, "Korean War begins", 1950, null);
        add(all, "DNA double helix structure discovered by Watson and Crick", 1953, null);
        add(all, "Rosa Parks refuses to give up her bus seat", 1955, null);
        add(all, "Cuban Missile Crisis", 1962, null);
        add(all, "Civil Rights Act passed in the United States", 1964, null);
        add(all, "First heart transplant performed by Christiaan Barnard", 1967, null);
        add(all, "Martin Luther King Jr. assassinated", 1968, null);
        add(all, "Watergate scandal breaks", 1972, null);
        add(all, "End of the Vietnam War", 1975, null);
        add(all, "Apple Computer Company founded", 1976, null);
        add(all, "First test-tube baby born", 1978, null);
        add(all, "Chernobyl nuclear disaster", 1986, null);
        add(all, "End of the Cold War", 1991, null);
        add(all, "Apartheid ends in South Africa", 1994, null);
        add(all, "Cloning of Dolly the sheep", 1996, null);
        add(all, "September 11 terrorist attacks", 2001, null);
        add(all, "Facebook founded by Mark Zuckerberg", 2004, null);
        add(all, "Barack Obama elected as first African-American US President", 2008, null);
        add(all, "Arab Spring begins", 2010, null);
        add(all, "Discovery of the Higgs boson at CERN", 2012, null);
        add(all, "Brexit referendum — UK votes to leave the EU", 2016, null);
        add(all, "James Webb Space Telescope launched", 2021, null);
        add(all, "Russia invades Ukraine", 2022, null);

        //Information Era easter egg cards (compsci, tech, gaming)
        add(all, "First commercial radio broadcast (KDKA in Pittsburgh)", 1920, null);
        add(all, "First public demonstration of television", 1926, null);
        add(all, "First programmable computer (Z3) completed by Konrad Zuse", 1941, null);
        add(all, "ENIAC, one of the first general-purpose electronic computers, unveiled", 1946, null);
        add(all, "Transistor invented at Bell Labs", 1947, null);
        add(all, "Claude Shannon publishes 'A Mathematical Theory of Communication'", 1948, null);
        add(all, "UNIVAC I, first commercial computer in the U.S., delivered", 1951, null);
        add(all, "First home video game console (Magnavox Odyssey) released", 1972, null);
        add(all, "First arcade video game (Computer Space) released", 1971, null);
        add(all, "First commercial home gaming hit (Atari 2600) released", 1977, null);
        add(all, "First major home computer game hit (Pong) released", 1972, null);
        add(all, "First video game (Tennis for Two) demonstrated", 1958, null);
        add(all, "First mobile phone call made by Martin Cooper", 1973, null);
        add(all, "First email sent by Ray Tomlinson", 1971, null);
        add(all, "First personal computer (Kenbak-1) released", 1971, null);
        add(all, "First commercial personal computer (IBM 5100) launched", 1975, null);
        add(all, "UNIX created at Bell Labs", 1969, null);
        add(all, "First eSports tournament held for Space Invaders", 1980, null);
        add(all, "First laptop computer (Osborne 1) released", 1981, null);
        add(all, "First laptop with mass-market success (Compaq Portable)", 1983, null);
        add(all, "GNU Project announced by Richard Stallman", 1983, null);
        add(all, "First 3D printer developed by Chuck Hull", 1983, null);
        add(all, "ARPANET adopts TCP/IP as its standard protocol", 1983, null);
        add(all, "First domain name (symbolics.com) registered", 1985, null);
        add(all, "First search engine (Archie) launched", 1990, null);
        add(all, "First website goes online at CERN", 1991, null);
        add(all, "First Linux kernel released by Linus Torvalds", 1991, null);
        add(all, "First laptop-style Macintosh (PowerBook series) released", 1991, null);
        add(all, "First release of Doom", 1993, null);
        add(all, "NCSA Mosaic web browser released", 1993, null);
        add(all, "First smartphone (IBM Simon) released", 1994, null);
        add(all, "First internet café (Cyberia) opens in London", 1994, null);
        add(all, "First blog (Links.net) started by Justin Hall", 1994, null);
        add(all, "Netscape Navigator released", 1994, null);
        add(all, "Java programming language released", 1995, null);
        add(all, "First social media site (SixDegrees.com) launched", 1997, null);
        add(all, "Wikipedia launched", 2001, null);
        add(all, "First smartphone with a true multi-touch interface", 2007, null);
        add(all, "First Android phone (HTC Dream) released", 2008, null);
        add(all, "Git created by Linus Torvalds", 2005, null);
        add(all, "GitHub launched", 2008, null);
        add(all, "First major AR mobile game hit (Pokémon GO) released", 2016, null);
        add(all, "First video uploaded to YouTube (Me at the zoo)", 2005, null);
        add(all, "First 3G mobile network launched in Japan", 2001, null);
        add(all, "First 4G LTE network launched in Stockholm, Sweden", 2009, null);

        List<EventCard> matching = new ArrayList<>();
        for (EventCard c : all) {
            if (c.getYear() >= era.getStartYear() && c.getYear() <= era.getEndYear()) matching.add(c);
        }
        Collections.shuffle(matching);
        return matching;
    }

    private void add(List<EventCard> list, String title, int year, String imageUrl) {
        EventCard card = new EventCard();
        card.setTitle(title);
        card.setYear(year);
        if (imageUrl != null) card.setImageUrl(imageUrl);
        list.add(card);
    }

    // =========================================================================
    // SPARQL response parser
    // =========================================================================

    private List<EventCard> parseSparqlResponse(String json) {
        List<EventCard> cards = new ArrayList<>();
        try {
            int bindingsStart = json.indexOf("\"bindings\"");
            if (bindingsStart == -1) { log.warn("No 'bindings' found in Wikidata response"); return cards; }
            int arrayStart = json.indexOf('[', bindingsStart);
            if (arrayStart == -1) return cards;

            int depth = 0, bindingStart = -1;
            for (int i = arrayStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') {
                    depth++;
                    if (depth == 1) bindingStart = i;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && bindingStart != -1) {
                        EventCard card = parseBinding(json.substring(bindingStart, i + 1));
                        if (card != null) cards.add(card);
                        bindingStart = -1;
                    }
                    if (depth < 0) break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Wikidata response: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Parse error: " + e.getClass().getName() + ": " + e.getMessage());
        }
        Collections.shuffle(cards);
        log.info("Parsed {} valid event cards from Wikidata response", cards.size());
        return cards;
    }

    private EventCard parseBinding(String block) {
        String label    = extractValue(block, "eventLabel");
        String yearStr  = extractValue(block, "year");
        String imageUrl = extractValue(block, "image");
        String eventUri = extractValue(block, "event");

        if (label == null || label.isEmpty() || label.matches("Q\\d+")) return null;
        if (label.matches("\\d{1,4}(\\s?(BC|AD|CE|BCE))?")
                || label.matches("\\d+s(\\s?(BC|AD|CE|BCE))?")
                || label.matches("\\d+(st|nd|rd|th) century(\\s?(BC|AD|CE|BCE))?")
                || label.matches("\\d+(st|nd|rd|th) millennium(\\s?(BC|AD|CE|BCE))?")
                || label.matches("\\d+ in \\w+")
                || label.matches("(January|February|March|April|May|June|July|August|September|October|November|December).*\\d{3,4}")) {
            return null;
        }

        label = label.replaceAll("\\s*\\(\\s*\\d{3,4}\\s*\\)", "");
        label = label.replaceAll("\\s+of\\s+\\d{1,4}[\\u2013\\-]\\d{1,4}", "");
        label = label.replaceAll("\\s+of\\s+\\d{1,4}(\\s?(BC|BCE|AD|CE))?", "");
        label = label.replaceAll("\\s+in\\s+\\d{3,4}", "");
        label = label.replaceAll("\\s+\\d{3,4}$", "");
        label = label.replaceAll("^\\d{1,4}\\s+", "");
        label = label.replaceAll("\\s+of$", "");
        label = label.replaceAll("\\b\\d{1,4}(\\s?(BC|BCE|AD|CE))?\\b", "");
        label = label.replaceAll("\\s{2,}", " ").trim();
        if (label.isEmpty()) return null;

        String t = label.toLowerCase();
        if (t.matches(".*\\brape\\b.*") || t.matches(".*\\bsexual assault\\b.*")
                || t.matches(".*\\btorture\\b.*") || t.matches(".*\\bcannibalism\\b.*")) {
            return null;
        }

        if (yearStr == null || yearStr.isEmpty()) return null;
        int year;
        try { year = Integer.parseInt(yearStr); } catch (NumberFormatException e) { return null; }

        EventCard card = new EventCard();
        card.setTitle(label);
        card.setYear(year);
        if (imageUrl != null && !imageUrl.isEmpty()) card.setImageUrl(imageUrl);
        if (eventUri != null && eventUri.contains("/"))
            card.setWikidataId(eventUri.substring(eventUri.lastIndexOf('/') + 1));
        return card;
    }

    private String extractValue(String block, String fieldName) {
        String search = "\"" + fieldName + "\"";
        int fieldPos = block.indexOf(search);
        if (fieldPos == -1) return null;
        int valuePos = block.indexOf("\"value\"", fieldPos);
        if (valuePos == -1) return null;
        int colonPos = block.indexOf(':', valuePos + 7);
        if (colonPos == -1) return null;
        int openQuote = block.indexOf('"', colonPos + 1);
        if (openQuote == -1) return null;
        int closeQuote = openQuote + 1;
        while (closeQuote < block.length()) {
            if (block.charAt(closeQuote) == '"' && block.charAt(closeQuote - 1) != '\\') break;
            closeQuote++;
        }
        if (closeQuote >= block.length()) return null;
        return block.substring(openQuote + 1, closeQuote);
    }
}