package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.repository.EventCardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class WikidataServiceTest {

    // Helper: erstellt einen WikidataService mit gemocktem Repository
    private WikidataService createService() {
        EventCardRepository mockRepo = Mockito.mock(EventCardRepository.class, Mockito.withSettings().lenient());
        when(mockRepo.existsByEra(any())).thenReturn(false);
        when(mockRepo.findByEra(any())).thenReturn(List.of());
        return new WikidataService(mockRepo);
    }

    // Helper: erstellt einen Spy auf WikidataService mit gemocktem Repository
    private WikidataService createSpy() {
        return Mockito.spy(createService());
    }

    @Test
    void parseSparqlResponse_validJson_returnsCards() throws Exception {
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
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);

        assertEquals(1, result.size());
        assertEquals("Magna Carta", result.get(0).getTitle());
        assertEquals(1215, result.get(0).getYear());
        assertEquals("https://example.com/magna.jpg", result.get(0).getImageUrl());
        assertEquals("Q178561", result.get(0).getWikidataId());
    }

    @Test
    void parseSparqlResponse_missingBindings_returnsEmptyList() throws Exception {
        String json = "{ \"results\": {} }";
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);
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
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);
        assertTrue(result.isEmpty());
    }

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
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);
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
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);
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
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);
        assertTrue(result.isEmpty(), "Century-labels must be filtered out");
    }

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
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);
        assertFalse(result.isEmpty());
        assertFalse(result.get(0).getTitle().contains("325"),
                "Year digits must be stripped from the card title");
    }

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
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);
        assertTrue(result.isEmpty(), "Cards with inappropriate content must be skipped");
    }

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
        List<EventCard> result = invokeParseSparqlResponse(createService(), json);
        assertEquals(2, result.size());
    }

    @Test
    void isMilitaryEvent_battleOf_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent(createService(), "Battle of Hastings"));
    }

    @Test
    void isMilitaryEvent_siegeOf_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent(createService(), "Siege of Constantinople"));
    }

    @Test
    void isMilitaryEvent_containsWar_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent(createService(), "Hundred Years War"));
    }

    @Test
    void isMilitaryEvent_containsRevolt_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent(createService(), "Peasants Revolt"));
    }

    @Test
    void isMilitaryEvent_nonMilitary_returnsFalse() throws Exception {
        assertFalse(invokeIsMilitaryEvent(createService(), "Magna Carta"));
        assertFalse(invokeIsMilitaryEvent(createService(), "Moon Landing"));
        assertFalse(invokeIsMilitaryEvent(createService(), "Discovery of Penicillin"));
    }

    @Test
    void isMilitaryEvent_invasionOf_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent(createService(), "Invasion of Normandy"));
    }

    @Test
    void isMilitaryEvent_conquestOf_returnsTrue() throws Exception {
        assertTrue(invokeIsMilitaryEvent(createService(), "Conquest of Mexico"));
    }

    @Test
    void applyDiversityFilter_militaryCapEnforced() throws Exception {
        WikidataService service = createService();
        List<EventCard> input = new ArrayList<>();
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

        List<EventCard> result = invokeApplyDiversityFilter(service, input, 10);

        long militaryCount = result.stream()
                .filter(c -> invokeIsMilitaryEventUnchecked(service, c.getTitle()))
                .count();
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
        List<EventCard> result = invokeApplyDiversityFilter(createService(), input, 20);
        assertTrue(result.size() <= 20, "Result must not exceed the requested limit");
    }

    @Test
    void applyDiversityFilter_emptyInput_returnsEmptyList() throws Exception {
        List<EventCard> result = invokeApplyDiversityFilter(createService(), new ArrayList<>(), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCuratedCards_modernEra_containsExpectedEntries() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(createService(), HistoricalEra.INFORMATION);
        assertFalse(curated.isEmpty());
        assertTrue(curated.stream().anyMatch(c -> c.getTitle().contains("Moon Landing")));
    }

    @Test
    void getCuratedCards_filteredToEraRange() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(createService(), HistoricalEra.ANCIENT);
        for (EventCard card : curated) {
            assertTrue(
                    card.getYear() >= HistoricalEra.ANCIENT.getStartYear()
                            && card.getYear() <= HistoricalEra.ANCIENT.getEndYear(),
                    "Offending card: " + card.getTitle() + " (" + card.getYear() + ")"
            );
        }
    }

    @Test
    void fetchEvents_returnsListNotExceedingLimit() {
        WikidataService spy = createSpy();
        doReturn(List.of()).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.MEDIEVAL, 5);
        assertTrue(result.size() <= 5);
    }

    @Test
    void extractValue_presentField_returnsValue() throws Exception {
        String block = """
                {
                  "eventLabel": { "type": "literal", "value": "Magna Carta" }
                }
                """;
        String result = invokeExtractValue(createService(), block, "eventLabel");
        assertEquals("Magna Carta", result);
    }

    @Test
    void extractValue_missingField_returnsNull() throws Exception {
        String block = """
                {
                  "eventLabel": { "type": "literal", "value": "Some Event" }
                }
                """;
        String result = invokeExtractValue(createService(), block, "nonExistentField");
        assertNull(result);
    }

    @Test
    void fetchEvents_ancientEra_returnsCards() {
        WikidataService spy = createSpy();
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
        WikidataService spy = createSpy();
        EventCard c = new EventCard();
        c.setTitle("Signing of the Magna Carta");
        c.setYear(1215);
        doReturn(List.of(c)).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.MEDIEVAL, 5);
        assertNotNull(result);
    }

    @Test
    void fetchEvents_modernEra_returnsCards() {
        WikidataService spy = createSpy();
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
        WikidataService spy = createSpy();
        doThrow(new RuntimeException("Network error")).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.INFORMATION, 5);
        assertNotNull(result);
    }

    @Test
    void getCuratedCards_medieval_containsMagnaCarta() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(createService(), HistoricalEra.MEDIEVAL);
        assertFalse(curated.isEmpty());
        assertTrue(curated.stream().anyMatch(c -> c.getTitle().equals("Signing of the Magna Carta")));
    }

    @Test
    void getCuratedCards_renaissance_containsPrintingPress() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(createService(), HistoricalEra.RENAISSANCE);
        assertFalse(curated.isEmpty());
        assertTrue(curated.stream().anyMatch(c -> c.getTitle().equals("Gutenberg invents the Printing Press")));
    }

    @Test
    void getCuratedCards_modern_excludesInformationCards() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(createService(), HistoricalEra.MODERN);
        assertFalse(curated.isEmpty());
        assertFalse(curated.stream().anyMatch(c -> c.getTitle().contains("Moon Landing")));
    }

    @Test
    void getCuratedCards_information_excludesAncientCards() throws Exception {
        List<EventCard> curated = invokeGetCuratedCards(createService(), HistoricalEra.INFORMATION);
        assertFalse(curated.isEmpty());
        assertFalse(curated.stream().anyMatch(c -> c.getTitle().contains("Parthenon")));
    }

    @Test
    void fetchEvents_callsRunSparqlFourTimes() {
        WikidataService spy = createSpy();
        doReturn(List.of()).when(spy).runSparql(anyString());

        spy.fetchEvents(HistoricalEra.MEDIEVAL, 5);
        verify(spy, times(4)).runSparql(anyString());
    }

    @Test
    void fetchEvents_medievalQueriesContainEraBounds() {
        WikidataService spy = createSpy();
        doReturn(List.of()).when(spy).runSparql(anyString());

        spy.fetchEvents(HistoricalEra.MEDIEVAL, 5);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(spy, times(4)).runSparql(captor.capture());

        for (String query : captor.getAllValues()) {
            assertTrue(query.contains(">= " + HistoricalEra.MEDIEVAL.getStartYear()));
            assertTrue(query.contains("<= " + HistoricalEra.MEDIEVAL.getEndYear()));
        }
    }

    @Test
    void fetchEvents_deduplicatesSameTitleAcrossQueries() {
        WikidataService spy = createSpy();
        EventCard duplicate = new EventCard();
        duplicate.setTitle("Signing of the Magna Carta");
        duplicate.setYear(1215);
        doReturn(List.of(duplicate)).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.MEDIEVAL, 10);

        long count = result.stream()
                .filter(c -> "Signing of the Magna Carta".equals(c.getTitle()))
                .count();
        assertEquals(1, count);
    }

    @Test
    void fetchEvents_emptySparqlResults_fallsBackToCuratedCards() {
        WikidataService spy = createSpy();
        doReturn(List.of()).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.RENAISSANCE, 5);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.size() <= 5);
    }

    @Test
    void fetchEvents_resultContainsNoDuplicateTitles() {
        WikidataService spy = createSpy();
        EventCard a = new EventCard(); a.setTitle("Signing of the Magna Carta"); a.setYear(1215);
        EventCard b = new EventCard(); b.setTitle("Signing of the Magna Carta"); b.setYear(1215);
        EventCard c = new EventCard(); c.setTitle("Coronation of Charlemagne"); c.setYear(800);
        doReturn(List.of(a, b, c)).when(spy).runSparql(anyString());

        List<EventCard> result = spy.fetchEvents(HistoricalEra.MEDIEVAL, 10);
        Set<String> unique = result.stream().map(EventCard::getTitle).collect(Collectors.toSet());
        assertEquals(unique.size(), result.size());
    }

    // =========================================================================
    // Reflection helpers — jetzt mit service-Parameter
    // =========================================================================

    @SuppressWarnings("unchecked")
    private List<EventCard> invokeParseSparqlResponse(WikidataService service, String json) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod("parseSparqlResponse", String.class);
        m.setAccessible(true);
        return (List<EventCard>) m.invoke(service, json);
    }

    private boolean invokeIsMilitaryEvent(WikidataService service, String title) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod("isMilitaryEvent", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, title);
    }

    private boolean invokeIsMilitaryEventUnchecked(WikidataService service, String title) {
        try {
            return invokeIsMilitaryEvent(service, title);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EventCard> invokeApplyDiversityFilter(WikidataService service, List<EventCard> cards, int limit) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod("applyDiversityFilter", List.class, int.class);
        m.setAccessible(true);
        return (List<EventCard>) m.invoke(service, cards, limit);
    }

    @SuppressWarnings("unchecked")
    private List<EventCard> invokeGetCuratedCards(WikidataService service, HistoricalEra era) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod("getCuratedCards", HistoricalEra.class);
        m.setAccessible(true);
        return (List<EventCard>) m.invoke(service, era);
    }

    private String invokeExtractValue(WikidataService service, String block, String fieldName) throws Exception {
        Method m = WikidataService.class.getDeclaredMethod("extractValue", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, block, fieldName);
    }
}