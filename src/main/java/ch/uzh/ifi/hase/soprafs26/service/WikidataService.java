package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.UUID;

/**
 * Service responsible for fetching historical event data from the
 * Wikidata SPARQL endpoint and converting it into EventCard objects.
 *
 * Five data sources are merged for variety:
 * 1. SPARQL – non-military point-in-time events (treaties, disasters, coronations …)
 * 2. SPARQL – wars & revolutions (capped, kept small for diversity)
 * 3. SPARQL – "historical event" items (declarations, milestones …)
 * 4. SPARQL – cultural, scientific & religious events
 * 5. Curated famous people & inventions that cannot be reliably
 *    queried via SPARQL
 *
 * Required external API integration Endpoint: https://query.wikidata.org/sparql
 *
 */
@Service
public class WikidataService {

    private static final Logger log = LoggerFactory.getLogger(WikidataService.class);

    private static final String SPARQL_ENDPOINT = "https://query.wikidata.org/sparql";

    // SPARQL-query 1: NON-MILITARY point-in-time events (P585)
    // (treaties, disasters, coronations, elections, epidemics,
    // religious events, diplomatic events, discoveries)
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

    // SPARQL-query 2: wars & revolutions (P580)
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

    // SPARQL-query 3: notable "historical event" items (Q13418847)
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

    // SPARQL-query 4: cultural, scientific & religious events
    // Q35140 (coronation), Q2110 (epidemic), Q7688 (famine),
    // Q132241 (festival/fair), Q15275719 (recurring sporting event),
    // Q8261 (novel, published), Q7725634 (literary work),
    // Q35127 (religious event), Q2977 (cathedral), Q12271 (architecture)
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

    /** Maximum fraction of results that can be military events. */
    private static final double BATTLE_CAP = 0.2;

    private final RestClient restClient;

    public WikidataService() {
        this.restClient = RestClient.builder()
                .baseUrl(SPARQL_ENDPOINT)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent",
                        "HistoricalReconstruction/1.0 (UZH SoPra FS26 Group 31)")
                .build();
    }

    // Public API-call

    /**
     * Fetches historical events from Wikidata for the given era.
     * Merges four SPARQL sources plus curated cards.
     *
     * @param era   the historical era to fetch events for
     * @param limit maximum number of events to return
     * @return a shuffled, diverse list of EventCard objects
     */
    public List<EventCard> fetchEvents(HistoricalEra era, int limit) {
        log.info("Fetching {} events for era {} ({}-{})",
                limit, era, era.getStartYear(), era.getEndYear());

        // Separate pools per source
        List<EventCard> q1Pool = new ArrayList<>();
        List<EventCard> q2Pool = new ArrayList<>();
        List<EventCard> q3Pool = new ArrayList<>();
        List<EventCard> q4Pool = new ArrayList<>();
        List<EventCard> curatedPool = new ArrayList<>();

        Set<String> seenGlobal = new HashSet<>();

        // Each query gets a unique cache-buster comment so Wikidata
        // doesn't serve cached results from previous calls.
        String cacheBuster = UUID.randomUUID().toString();

        // Query 1: Non-military point-in-time events (P585)
        try {
            List<EventCard> res = runSparql(
                    String.format(EVENTS_QUERY, cacheBuster,
                            era.getStartYear(), era.getEndYear(), limit * 5));
            for (EventCard c : res) {
                if (seenGlobal.add(c.getTitle().toLowerCase())) {
                    q1Pool.add(c);
                }
            }
        } catch (Exception e) {
            log.warn("Query1 failed: {}", e.getMessage());
        }

        // Query 2: Wars & revolutions (hard-capped to limit military presence)
        int q2Limit = Math.max(8, (int) (limit * BATTLE_CAP));
        try {
            List<EventCard> res = runSparql(
                    String.format(START_DATE_QUERY, cacheBuster,
                            era.getStartYear(), era.getEndYear(), q2Limit));
            for (EventCard c : res) {
                if (seenGlobal.add(c.getTitle().toLowerCase())) {
                    q2Pool.add(c);
                }
            }
        } catch (Exception e) {
            log.warn("Query2 failed: {}", e.getMessage());
        }

        // Query 3: General historical events (Q13418847)
        try {
            List<EventCard> res = runSparql(
                    String.format(HISTORICAL_EVENT_QUERY, cacheBuster,
                            era.getStartYear(), era.getEndYear(), limit * 3));
            for (EventCard c : res) {
                if (seenGlobal.add(c.getTitle().toLowerCase())) {
                    q3Pool.add(c);
                }
            }
        } catch (Exception e) {
            log.warn("Query3 failed: {}", e.getMessage());
        }

        // Query 4: Cultural, scientific & religious events
        try {
            List<EventCard> res = runSparql(
                    String.format(CULTURAL_QUERY, cacheBuster,
                            era.getStartYear(), era.getEndYear(), limit * 3));
            for (EventCard c : res) {
                if (seenGlobal.add(c.getTitle().toLowerCase())) {
                    q4Pool.add(c);
                }
            }
        } catch (Exception e) {
            log.warn("Query4 (cultural) failed: {}", e.getMessage());
        }

        log.info("Pool sizes – Q1(non-mil):{} Q2(wars):{} Q3(hist):{} Q4(cultural):{}",
                q1Pool.size(), q2Pool.size(), q3Pool.size(), q4Pool.size());

        // Curated events – separate dedup so they're always available
        Set<String> seenCurated = new HashSet<>();
        for (EventCard c : getCuratedCards(era)) {
            if (seenCurated.add(c.getTitle().toLowerCase())) {
                curatedPool.add(c);
            }
        }

        // Shuffle all pools
        Collections.shuffle(q1Pool);
        Collections.shuffle(q2Pool);
        Collections.shuffle(q3Pool);
        Collections.shuffle(q4Pool);
        Collections.shuffle(curatedPool);

        // Era-based weighting – prioritize cultural/historical over military
        double q1Weight, q2Weight, q3Weight, q4Weight;

        if (era == HistoricalEra.ANCIENT) {
            q1Weight = 0.15; q2Weight = 0.05; q3Weight = 0.40; q4Weight = 0.40;
        } else if (era == HistoricalEra.MEDIEVAL) {
            q1Weight = 0.20; q2Weight = 0.10; q3Weight = 0.35; q4Weight = 0.35;
        } else {
            q1Weight = 0.25; q2Weight = 0.10; q3Weight = 0.35; q4Weight = 0.30;
        }

        int q1Target = (int) (limit * q1Weight);
        int q2Target = (int) (limit * q2Weight);
        int q3Target = (int) (limit * q3Weight);
        int q4Target = (int) (limit * q4Weight);

        List<EventCard> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Helper
        java.util.function.BiConsumer<List<EventCard>, Integer> addUpTo = (source, max) -> {
            for (EventCard c : source) {
                if (merged.size() >= limit || max <= 0) break;
                if (seen.add(c.getTitle().toLowerCase())) {
                    merged.add(c);
                    max--;
                }
            }
        };

        // Priority filling: cultural & historical first, then general, wars last
        addUpTo.accept(q4Pool, q4Target);
        addUpTo.accept(q3Pool, q3Target);
        addUpTo.accept(q1Pool, q1Target);
        addUpTo.accept(q2Pool, q2Target);

        // Filler to hit card limit – bias toward non-military sources
        List<EventCard> fallback = new ArrayList<>();
        fallback.addAll(q4Pool);
        fallback.addAll(q3Pool);
        fallback.addAll(q4Pool); // double weight for cultural
        fallback.addAll(q1Pool);
        fallback.addAll(q2Pool);
        Collections.shuffle(fallback);

        for (EventCard c : fallback) {
            if (merged.size() >= limit) break;
            if (seen.add(c.getTitle().toLowerCase())) {
                merged.add(c);
            }
        }

        // Insert curated cards
        int sparqlCount = merged.size();
        int gap = Math.max(0, limit - sparqlCount);
        int curatedMin = 3;
        int curatedLimit = Math.min(
                curatedPool.size(),
                Math.max(gap, curatedMin)
        );

        log.info("SPARQL produced {} cards, inserting up to {} curated cards (gap={}, pool={})",
                sparqlCount, curatedLimit, gap, curatedPool.size());

        for (int i = 0; i < curatedLimit; i++) {
            EventCard curated = curatedPool.get(i);
            String key = curated.getTitle().toLowerCase();
            boolean alreadyPresent = false;
            for (EventCard m : merged) {
                if (m.getTitle().toLowerCase().equals(key)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (alreadyPresent) {
                curatedLimit = Math.min(curatedLimit + 1, curatedPool.size());
                continue;
            }
            int pos = (int) (Math.random() * (merged.size() + 1));
            merged.add(pos, curated);

            if (merged.size() > limit) {
                merged.remove(merged.size() - 1);
            }
        }

        // Apply diversity filter
        List<EventCard> result = applyDiversityFilter(merged, limit);

        log.info("Returning {} diverse event cards for era {}", result.size(), era);
        return result;
    }

    // SPARQL-call

    private List<EventCard> runSparql(String sparql) {
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
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Wikidata error: " + e.getClass().getName() + ": " + e.getMessage()
            );
        }

        return parseSparqlResponse(responseBody);
    }

    //  Diversity-filter

    /**
     * Caps military events at ~20% of the final list
     * so the card game feels varied and educational.
     */
    private List<EventCard> applyDiversityFilter(List<EventCard> cards, int limit) {
        List<EventCard> military = new ArrayList<>();
        List<EventCard> nonMilitary = new ArrayList<>();

        for (EventCard card : cards) {
            if (isMilitaryEvent(card.getTitle())) {
                military.add(card);
            } else {
                nonMilitary.add(card);
            }
        }

        Collections.shuffle(military);
        Collections.shuffle(nonMilitary);

        int maxMilitary = Math.max(2, (int) (limit * BATTLE_CAP));

        List<EventCard> result = new ArrayList<>();

        int i = 0, j = 0;

        // Phase 1: fill with non-military first, then military up to cap
        while (result.size() < limit && (i < nonMilitary.size() || j < military.size())) {
            if (i < nonMilitary.size()) {
                result.add(nonMilitary.get(i++));
                continue;
            }
            if (j < military.size() && countMilitary(result) < maxMilitary) {
                result.add(military.get(j++));
            } else {
                break;
            }
        }

        // Phase 2: ensure we always reach 'limit' if possible
        List<EventCard> remaining = new ArrayList<>();
        remaining.addAll(nonMilitary);
        remaining.addAll(military);
        Collections.shuffle(remaining);

        for (EventCard c : remaining) {
            if (result.size() >= limit) break;
            if (!result.contains(c)) {
                result.add(c);
            }
        }

        Collections.shuffle(result);

        return result;
    }

    private int countMilitary(List<EventCard> cards) {
        int count = 0;
        for (EventCard c : cards) {
            if (isMilitaryEvent(c.getTitle())) {
                count++;
            }
        }
        return count;
    }

    /** Checks if a title looks like a battle, siege, war, or military conflict. */
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

    // Curated cards (famous people, milestones etc.)

    /**
     * Returns a curated pool of famous people (birth years) and landmark
     * inventions / constructions that cannot be reliably fetched from
     * Wikidata's public SPARQL endpoint.
     *
     * Why hardcoded?
     * - People (Q5): queries time out because there are millions of humans.
     * - Inventions (P575): results are drowned in asteroid discoveries.
     * - Major constructions (P571): only works with exact type IDs, fragile.
     */
    public List<EventCard> getCuratedCards(HistoricalEra era) {
        List<EventCard> all = new ArrayList<>();

        // Ancient: famous people, constructions & milestones
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

        // Medieval: famous people & milestones
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

        // Renaissance: famous people, inventions & milestones
        add(all, "Birth of Joan of Arc", 1412, null);
        add(all, "Gutenberg invents the Printing Press", 1440, null);
        add(all, "Birth of Leonardo da Vinci", 1452, null);
        add(all, "Birth of Nicolaus Copernicus", 1473, null);
        add(all, "Birth of Michelangelo", 1475, null);
        add(all, "Columbus reaches the Americas", 1492, null);
        add(all, "Vasco da Gama reaches India", 1498, null);
        add(all, "Magellan's expedition circumnavigates the Earth", 1522, null);
        add(all, "Birth of Galileo Galilei", 1564, null);
        add(all, "Birth of William Shakespeare", 1564, null);
        add(all, "Fall of Constantinople", 1453, null);
        add(all, "Spanish Inquisition established", 1478, null);
        add(all, "Treaty of Tordesillas", 1494, null);
        add(all, "Michelangelo paints the Sistine Chapel ceiling", 1512, null);
        add(all, "Martin Luther's Reformation begins", 1517, null);
        add(all, "Hernán Cortés conquers the Aztec Empire", 1521, null);
        add(all, "Francisco Pizarro conquers the Inca Empire", 1533, null);
        add(all, "Council of Trent begins", 1545, null);
        add(all, "Copernicus publishes heliocentric model", 1543, null);
        add(all, "Defeat of the Spanish Armada", 1588, null);
        add(all, "Edict of Nantes grants religious tolerance", 1598, null);
        add(all, "Birth of Martin Luther", 1483, null);
        add(all, "Birth of Henry VIII of England", 1491, null);
        add(all, "Birth of Suleiman the Magnificent", 1494, null);

        // Modern: famous people, inventions & milestones
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
        add(all, "First photograph ever taken (Niépce)", 1826, null);
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

        // Information: famous people & inventions
        add(all, "Wright Brothers' First Flight", 1903, null);
        add(all, "Sinking of the Titanic", 1912, null);
        add(all, "Discovery of Penicillin (Alexander Fleming)", 1928, null);
        add(all, "Birth of Martin Luther King Jr.", 1929, null);
        add(all, "First human in space (Yuri Gagarin)", 1961, null);
        add(all, "Moon Landing (Apollo 11)", 1969, null);
        add(all, "Invention of the World Wide Web (Tim Berners-Lee)", 1989, null);
        add(all, "Fall of the Berlin Wall", 1989, null);
        add(all, "Launch of the iPhone", 2007, null);
        add(all, "COVID-19 Pandemic begins", 2020, null);

        // Filter to matching era
        List<EventCard> matching = new ArrayList<>();
        for (EventCard c : all) {
            if (c.getYear() >= era.getStartYear() && c.getYear() <= era.getEndYear()) {
                matching.add(c);
            }
        }
        Collections.shuffle(matching);
        return matching;
    }

    /** Helper to build a curated EventCard. */
    private void add(List<EventCard> list, String title, int year, String imageUrl) {
        EventCard card = new EventCard();
        card.setTitle(title);
        card.setYear(year);
        if (imageUrl != null) {
            card.setImageUrl(imageUrl);
        }
        list.add(card);
    }

    //  SPARQL responser-parser

    /**
     * Parses the SPARQL JSON response into a list of EventCard objects.
     * Uses manual string parsing to avoid external JSON library dependencies.
     */
    private List<EventCard> parseSparqlResponse(String json) {
        List<EventCard> cards = new ArrayList<>();

        try {
            int bindingsStart = json.indexOf("\"bindings\"");
            if (bindingsStart == -1) {
                log.warn("No 'bindings' found in Wikidata response");
                return cards;
            }

            int arrayStart = json.indexOf('[', bindingsStart);
            if (arrayStart == -1) {
                return cards;
            }

            int depth = 0;
            int bindingStart = -1;

            for (int i = arrayStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') {
                    depth++;
                    if (depth == 1) {
                        bindingStart = i;
                    }
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && bindingStart != -1) {
                        String block = json.substring(bindingStart, i + 1);
                        EventCard card = parseBinding(block);
                        if (card != null) {
                            cards.add(card);
                        }
                        bindingStart = -1;
                    }
                    if (depth < 0) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Wikidata SPARQL response: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Parse error: " + e.getClass().getName() + ": " + e.getMessage()
            );
        }

        Collections.shuffle(cards);
        log.info("Parsed {} valid event cards from Wikidata response", cards.size());
        return cards;
    }

    /**
     * Parses a single SPARQL result binding block into an EventCard.
     * Returns null if the binding is invalid or has a junk label.
     */
    private EventCard parseBinding(String block) {
        String label = extractValue(block, "eventLabel");
        String yearStr = extractValue(block, "year");
        String imageUrl = extractValue(block, "image");
        String eventUri = extractValue(block, "event");

        // Skip Q-number labels
        if (label == null || label.isEmpty() || label.matches("Q\\d+")) {
            return null;
        }
        // Skip month/year/century/decade/millennium-entities
        if (label.matches("\\d{1,4}(\\s?(BC|AD|CE|BCE))?")
                || label.matches("\\d+s(\\s?(BC|AD|CE|BCE))?")
                || label.matches("\\d+(st|nd|rd|th) century(\\s?(BC|AD|CE|BCE))?")
                || label.matches("\\d+(st|nd|rd|th) millennium(\\s?(BC|AD|CE|BCE))?")
                || label.matches("\\d+ in \\w+")
                || label.matches("(January|February|March|April|May|June|July|August|September|October|November|December).*\\d{3,4}")) {
            return null;
        }
        // Strip years from titles so players can't read the answer.
        // Covers: "(1306)", "of 58–63", "of 363–371", trailing "1306",
        //         leading "365 ", "in 1306", "Antioch earthquake of"
        label = label.replaceAll("\\s*\\(\\s*\\d{3,4}\\s*\\)", "");              // (1306)
        label = label.replaceAll("\\s+of\\s+\\d{1,4}[–\\-]\\d{1,4}", "");        // of 58–63, of 363–371
        label = label.replaceAll("\\s+of\\s+\\d{1,4}(\\s?(BC|BCE|AD|CE))?", ""); // of 1306
        label = label.replaceAll("\\s+in\\s+\\d{3,4}", "");                      // in 1306
        label = label.replaceAll("\\s+\\d{3,4}$", "");                           // trailing 1306
        label = label.replaceAll("^\\d{1,4}\\s+", "");                           // leading "365 "
        label = label.replaceAll("\\s+of$", "");                                 // dangling "of" after strip
        label = label.replaceAll("\\b\\d{1,4}(\\s?(BC|BCE|AD|CE))?\\b", "");     // 58 BCE
        label = label.replaceAll("\\s{2,}", " ").trim();
        if (label.isEmpty()) {
            return null;
        }

        String t = label.toLowerCase();

        // Filter out certain words
        if (t.matches(".*\\brape\\b.*") ||
                t.matches(".*\\bsexual assault\\b.*") ||
                t.matches(".*\\btorture\\b.*") ||
                t.matches(".*\\bcannibalism\\b.*")) {
            return null;
        }

        if (yearStr == null || yearStr.isEmpty()) {
            return null;
        }
        int year;
        try {
            year = Integer.parseInt(yearStr);
        } catch (NumberFormatException e) {
            return null;
        }

        EventCard card = new EventCard();
        card.setTitle(label);
        card.setYear(year);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            card.setImageUrl(imageUrl);
        }

        if (eventUri != null && eventUri.contains("/")) {
            String wikidataId = eventUri.substring(eventUri.lastIndexOf('/') + 1);
            card.setWikidataId(wikidataId);
        }

        return card;
    }

    /**
     * Extracts the "value" string for a given field name from a JSON block.
     */
    private String extractValue(String block, String fieldName) {
        String search = "\"" + fieldName + "\"";
        int fieldPos = block.indexOf(search);
        if (fieldPos == -1) {
            return null;
        }

        int valuePos = block.indexOf("\"value\"", fieldPos);
        if (valuePos == -1) {
            return null;
        }

        int colonPos = block.indexOf(':', valuePos + 7);
        if (colonPos == -1) {
            return null;
        }

        int openQuote = block.indexOf('"', colonPos + 1);
        if (openQuote == -1) {
            return null;
        }

        int closeQuote = openQuote + 1;
        while (closeQuote < block.length()) {
            if (block.charAt(closeQuote) == '"' && block.charAt(closeQuote - 1) != '\\') {
                break;
            }
            closeQuote++;
        }

        if (closeQuote >= block.length()) {
            return null;
        }

        return block.substring(openQuote + 1, closeQuote);
    }
}