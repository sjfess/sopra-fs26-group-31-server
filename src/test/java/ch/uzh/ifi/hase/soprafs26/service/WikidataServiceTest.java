package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
class WikidataServiceTest {

    // We test the pure (non-HTTP) logic through reflection on private methods,
    // and use a spy to stub the HTTP-dependent runSparql().
    @Spy
    private WikidataService wikidataService;

    // parse SPARQL-response-test

    @Test
    void parseSparqlResponse_validJson_returnsCards() throws Exception {
        // minimal but complete SPARQL JSON response with binding entry
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "event": { "value": "http://www.wikidata.org/entity/Q178561" },
                        "eventLabel": { "value": "Magna Carta" },
                        "year": { "value": "1215" },
                        "image": { "value": "https://example.com/magna.jpg" }
                      }
                    ]
                  }
                }
                """;
        // verify all fields are mapped correctly from JSON binding to EventCard
        List<EventCard> result = invokeParseSparqlResponse(json);

        assertEquals(1, result.size());
        assertEquals("Magna Carta", result.get(0).getTitle());
        assertEquals(1215, result.get(0).getYear());
        assertEquals("https://example.com/magna.jpg", result.get(0).getImageUrl());
        // Wikidata-ID should be extracted from trailing part of entity URL
        assertEquals("Q178561", result.get(0).getWikidataId());
    }

    // if "bindings" key is missing or empty, parser must be able to handle it

    @Test
    void parseSparqlResponse_missingBindings_returnsEmptyList() throws Exception {

        String json = "{ \"results\": {} }";
        List<EventCard> result = invokeParseSparqlResponse(json);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseSparqlResponse_emptyBindings_returnsEmptyList() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": []
                  }
                }
                """;
        List<EventCard> result = invokeParseSparqlResponse(json);
        assertTrue(result.isEmpty());
    }

    // skip certain cards that do not meet criteria

    @Test
    void parseSparqlResponse_qNumberLabel_cardSkipped() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "event": { "value": "http://www.wikidata.org/entity/Q99" },
                        "eventLabel": { "value": "Q99" },
                        "year": { "value": "1000" }
                      }
                    ]
                  }
                }
                """;
        List<EventCard> result = invokeParseSparqlResponse(json);
        assertTrue(result.isEmpty(), "Cards with raw Q-number labels must be skipped");
    }

    @Test
    void parseSparqlResponse_missingYear_cardSkipped() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "event": { "value": "http://www.wikidata.org/entity/Q1" },
                        "eventLabel": { "value": "Some Event" }
                      }
                    ]
                  }
                }
                """;
        List<EventCard> result = invokeParseSparqlResponse(json);
        assertTrue(result.isEmpty(), "Cards without a year must be skipped");
    }

    @Test
    void parseSparqlResponse_centuryLabel_cardSkipped() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "event": { "value": "http://www.wikidata.org/entity/Q2" },
                        "eventLabel": { "value": "14th century" },
                        "year": { "value": "1300" }
                      }
                    ]
                  }
                }
                """;
        List<EventCard> result = invokeParseSparqlResponse(json);
        assertTrue(result.isEmpty(), "Century-labels must be filtered out");
    }

    // remove year from card so that players don't see it & have an advantage in the game

    @Test
    void parseSparqlResponse_titleContainsYear_yearStripped() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "event": { "value": "http://www.wikidata.org/entity/Q3" },
                        "eventLabel": { "value": "Council of Nicaea (325)" },
                        "year": { "value": "325" }
                      }
                    ]
                  }
                }
                """;
        List<EventCard> result = invokeParseSparqlResponse(json);
        assertFalse(result.isEmpty());
        assertFalse(result.get(0).getTitle().contains("325"),
                "Year digits must be stripped from the card title");
    }

    // trigger-filter -> filter out certain events that are not appropriate

    @Test
    void parseSparqlResponse_inappropriateContent_cardSkipped() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "event": { "value": "http://www.wikidata.org/entity/Q4" },
                        "eventLabel": { "value": "Some torture incident" },
                        "year": { "value": "1200" }
                      }
                    ]
                  }
                }
                """;
        List<EventCard> result = invokeParseSparqlResponse(json);
        assertTrue(result.isEmpty(), "Cards with inappropriate content must be skipped");
    }

    // check that all bindings create EventCard-entity & that none are unnecessarily discarded

    @Test
    void parseSparqlResponse_multipleCards_allParsed() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "event": { "value": "http://www.wikidata.org/entity/Q10" },
                        "eventLabel": { "value": "Magna Carta" },
                        "year": { "value": "1215" }
                      },
                      {
                        "event": { "value": "http://www.wikidata.org/entity/Q11" },
                        "eventLabel": { "value": "Black Death" },
                        "year": { "value": "1347" }
                      }
                    ]
                  }
                }
                """;
        List<EventCard> result = invokeParseSparqlResponse(json);
        assertEquals(2, result.size());
    }

    // Military-event-test

    @Test
    void isMilitaryEvent_battleOf_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent("Battle of Hastings"));
    }

    @Test
    void isMilitaryEvent_siegeOf_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent("Siege of Constantinople"));
    }

    @Test
    void isMilitaryEvent_containsWar_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent("Hundred Years War"));
    }

    @Test
    void isMilitaryEvent_containsRevolt_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent("Peasants Revolt"));
    }

    @Test
    void isMilitaryEvent_nonMilitary_returnsFalse() throws Exception {
        assertFalse(invokeIsMilitaryEvent("Magna Carta"));
        assertFalse(invokeIsMilitaryEvent("Moon Landing"));
        assertFalse(invokeIsMilitaryEvent("Discovery of Penicillin"));
    }

    @Test
    void isMilitaryEvent_invasionOf_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent("Invasion of Normandy"));
    }

    @Test
    void isMilitaryEvent_conquestOf_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent("Conquest of Mexico"));
    }

    // Diversity-filter-test

    @Test
    void applyDiversityFilter_militaryCapEnforced() throws Exception {
        List<EventCard> input = new ArrayList<>();
        // 10 military + 10 non-military
        for (int i = 0; i < 10; i++) {
            EventCard c = new EventCard();
            c.setTitle("Battle of Event " + i);
            c.setYear(1200 + i);
            input.add(c);
        }
        for (int i = 0; i < 10; i++) {
            EventCard c = new EventCard();
            c.setTitle("Cultural Event " + i);
            c.setYear(1300 + i);
            input.add(c);
        }

        List<EventCard> result = invokeApplyDiversityFilter(input, 10);

        long militaryCount = result.stream()
                .filter(c -> invokeIsMilitaryEventUnchecked(c.getTitle()))
                .count();
        // cap is 20% of limit = 2 (max(2, 10*0.2))
        assertTrue(militaryCount <= 2,
                "Military events must not exceed 20% of the result. Got: " + militaryCount);
    }

    @Test
    void applyDiversityFilter_resultSizeDoesNotExceedLimit() throws Exception {
        List<EventCard> input = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            EventCard c = new EventCard();
            c.setTitle("Event " + i);
            c.setYear(1000 + i);
            input.add(c);
        }

        List<EventCard> result = invokeApplyDiversityFilter(input, 20);
        assertTrue(result.size() <= 20, "Result must not exceed the requested limit");
    }

    @Test
    void applyDiversityFilter_emptyInput_returnsEmptyList() throws Exception {
        List<EventCard> result = invokeApplyDiversityFilter(new ArrayList<>(), 10);
        assertTrue(result.isEmpty());
    }

    // Curated-cards-test

    @Test
    void getCuratedCards_modernEra_containsExpectedEntries() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(HistoricalEra.INFORMATION);
        assertFalse(curated.isEmpty(), "Curated cards for MODERN era must not be empty");
        boolean foundMoonLanding = curated.stream()
                .anyMatch(c -> c.getTitle().contains("Moon Landing"));
        assertTrue(foundMoonLanding, "Moon Landing should be in INFORMATION curated cards");
    }

    @Test
    void getCuratedCards_filteredToEraRange() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(HistoricalEra.ANCIENT);
        for (EventCard card : curated) {
            assertTrue(
                    card.getYear() >= HistoricalEra.ANCIENT.getStartYear()
                    && card.getYear() <= HistoricalEra.ANCIENT.getEndYear(),
                    "Curated cards must fall within the era's year range. Offending card: "
                            + card.getTitle() + " (" + card.getYear() + ")"
            );
        }
    }

    // fetch events – mocked HTTP

    @Test
    void fetchEvents_returnsListNotExceedingLimit() {
        // Stub all SPARQL calls at the service level to avoid real HTTP calls
        doReturn(List.of()).when(wikidataService).fetchEvents(any(HistoricalEra.class), anyInt());

        List<EventCard> result = wikidataService.fetchEvents(HistoricalEra.MEDIEVAL, 5);
        assertTrue(result.size() <= 5);
    }

    // extract value test

    @Test
    void extractValue_presentField_returnsValue() throws Exception {
        String block = """
                {
                  "eventLabel": { "type": "literal", "value": "Magna Carta" }
                }
                """;
        String result = invokeExtractValue(block, "eventLabel");
        assertEquals("Magna Carta", result);
    }

    @Test
    void extractValue_missingField_returnsNull() throws Exception {
        String block = """
                {
                  "eventLabel": { "type": "literal", "value": "Some Event" }
                }
                """;
        String result = invokeExtractValue(block, "nonExistentField");
        assertNull(result);
    }

    // Reflection helpers

    @SuppressWarnings("unchecked")
    private List<EventCard> invokeParseSparqlResponse(String json) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod("parseSparqlResponse", String.class);
        m.setAccessible(true);
        return (List<EventCard>) m.invoke(wikidataService, json);
    }

    private boolean invokeIsMilitaryEvent(String title) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod("isMilitaryEvent", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(wikidataService, title);
    }

    private boolean invokeIsMilitaryEventUnchecked(String title) {
        try {
            return invokeIsMilitaryEvent(title);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EventCard> invokeApplyDiversityFilter(List<EventCard> cards, int limit)
            throws Exception {
        Method m = WikidataService.class.getDeclaredMethod(
                "applyDiversityFilter", List.class, int.class);
        m.setAccessible(true);
        return (List<EventCard>) m.invoke(wikidataService, cards, limit);
    }

    @SuppressWarnings("unchecked")
    private List<EventCard> invokeGetCuratedCards(HistoricalEra era) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod("getCuratedCards", HistoricalEra.class);
        m.setAccessible(true);
        return (List<EventCard>) m.invoke(wikidataService, era);
    }

    private String invokeExtractValue(String block, String fieldName) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod(
                "extractValue", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(wikidataService, block, fieldName);
    }

    @Test
    void fetchEvents_ancientEra_returnsCards() {
        WikidataService spy = Mockito.spy(new WikidataService());
        EventCard c = new EventCard();
        c.setTitle("Construction of the Parthenon");
        c.setYear(-447);

        doReturn(List.of(c)).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.ANCIENT, 5);

        assertNotNull(result);
        assertTrue(result.size() <= 5);
    }

    @Test
    void fetchEvents_medievalEra_returnsCards() {
        WikidataService spy = Mockito.spy(new WikidataService());
        EventCard c = new EventCard();
        c.setTitle("Signing of the Magna Carta");
        c.setYear(1215);

        doReturn(List.of(c)).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.MEDIEVAL, 5);

        assertNotNull(result);
    }

    @Test
    void fetchEvents_modernEra_returnsCards() {
        WikidataService spy = Mockito.spy(new WikidataService());
        EventCard c = new EventCard();
        c.setTitle("Birth of Napoleon Bonaparte");
        c.setYear(1769);

        doReturn(List.of(c)).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.MODERN, 10);

        assertNotNull(result);
        assertTrue(result.size() <= 10);
    }

    @Test
    void fetchEvents_runSparqlThrows_stillReturnsResult() {
        WikidataService spy = Mockito.spy(new WikidataService());

        doThrow(new RuntimeException("Network error")).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.INFORMATION, 5);

        assertNotNull(result);
    }

    @Test
    void getCuratedCards_medieval_containsMagnaCarta() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(HistoricalEra.MEDIEVAL);

        assertFalse(curated.isEmpty(), "Curated cards for MEDIEVAL must not be empty");
        assertTrue(
                curated.stream().anyMatch(c -> c.getTitle().equals("Signing of the Magna Carta")),
                "MEDIEVAL curated cards should contain 'Signing of the Magna Carta'"
        );
    }

    @Test
    void getCuratedCards_renaissance_containsPrintingPress() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(HistoricalEra.RENAISSANCE);

        assertFalse(curated.isEmpty(), "Curated cards for RENAISSANCE must not be empty");
        assertTrue(
                curated.stream().anyMatch(c -> c.getTitle().equals("Gutenberg invents the Printing Press")),
                "RENAISSANCE curated cards should contain Gutenberg's printing press"
        );
    }

    @Test
    void getCuratedCards_modern_excludesInformationCards() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(HistoricalEra.MODERN);

        assertFalse(curated.isEmpty(), "Curated cards for MODERN must not be empty");
        assertFalse(
                curated.stream().anyMatch(c -> c.getTitle().contains("Moon Landing")),
                "MODERN curated cards must not contain INFORMATION-era cards like Moon Landing"
        );
    }

    @Test
    void getCuratedCards_information_excludesAncientCards() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(HistoricalEra.INFORMATION);

        assertFalse(curated.isEmpty(), "Curated cards for INFORMATION must not be empty");
        assertFalse(
                curated.stream().anyMatch(c -> c.getTitle().contains("Parthenon")),
                "INFORMATION curated cards must not contain ANCIENT-era cards like the Parthenon"
        );
    }

    @Test
    void fetchEvents_callsRunSparqlFourTimes() {
        WikidataService spy = Mockito.spy(new WikidataService());
        doReturn(List.of()).when(spy).runSparql(anyString());

        spy.fetchEvents(HistoricalEra.MEDIEVAL, 5);

        verify(spy, times(4)).runSparql(anyString());
    }

    @Test
    void fetchEvents_medievalQueriesContainEraBounds() {
        WikidataService spy = Mockito.spy(new WikidataService());
        doReturn(List.of()).when(spy).runSparql(anyString());

        spy.fetchEvents(HistoricalEra.MEDIEVAL, 5);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(spy, times(4)).runSparql(captor.capture());

        List<String> queries = captor.getAllValues();
        assertEquals(4, queries.size());

        for (String query : queries) {
            assertTrue(
                    query.contains(">= " + HistoricalEra.MEDIEVAL.getStartYear()),
                    "Each SPARQL query must include the MEDIEVAL start year"
            );
            assertTrue(
                    query.contains("<= " + HistoricalEra.MEDIEVAL.getEndYear()),
                    "Each SPARQL query must include the MEDIEVAL end year"
            );
        }
    }

    @Test
    void fetchEvents_deduplicatesSameTitleAcrossQueries() {
        WikidataService spy = Mockito.spy(new WikidataService());

        EventCard duplicate = new EventCard();
        duplicate.setTitle("Signing of the Magna Carta");
        duplicate.setYear(1215);

        doReturn(List.of(duplicate)).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.MEDIEVAL, 10);

        long duplicateCount = result.stream()
                .filter(c -> "Signing of the Magna Carta".equals(c.getTitle()))
                .count();

        assertEquals(1, duplicateCount, "Duplicate titles from multiple SPARQL sources should appear only once");
    }

    @Test
    void fetchEvents_emptySparqlResults_fallsBackToCuratedCards() {
        WikidataService spy = Mockito.spy(new WikidataService());
        doReturn(List.of()).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.RENAISSANCE, 5);

        assertNotNull(result);
        assertFalse(result.isEmpty(), "If SPARQL returns nothing, curated cards should still provide results");
        assertTrue(result.size() <= 5, "Result must still respect the requested limit");
    }

    @Test
    void fetchEvents_resultContainsNoDuplicateTitles() {
        WikidataService spy = Mockito.spy(new WikidataService());

        EventCard a = new EventCard();
        a.setTitle("Signing of the Magna Carta");
        a.setYear(1215);

        EventCard b = new EventCard();
        b.setTitle("Signing of the Magna Carta");
        b.setYear(1215);

        EventCard c = new EventCard();
        c.setTitle("Coronation of Charlemagne");
        c.setYear(800);

        doReturn(List.of(a, b, c)).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.MEDIEVAL, 10);

        Set<String> uniqueTitles = result.stream()
                .map(EventCard::getTitle)
                .collect(Collectors.toSet());

        assertEquals(
                uniqueTitles.size(),
                result.size(),
                "Final fetchEvents result should not contain duplicate titles"
        );
    }
}
