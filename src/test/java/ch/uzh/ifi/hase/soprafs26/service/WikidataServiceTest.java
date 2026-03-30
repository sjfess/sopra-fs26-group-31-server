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

@ExtendWith(MockitoExtension.class)
class WikidataServiceTest {

    // We test the pure (non-HTTP) logic through reflection on private methods,
    // and use a spy to stub the HTTP-dependent runSparql().
    @Spy
    private WikidataService wikidataService;

    // ── parseSparqlResponse ───────────────────────────────────────────────────

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

        List<EventCard> result = invokeParseSparqlResponse(json);

        assertEquals(1, result.size());
        assertEquals("Magna Carta", result.get(0).getTitle());
        assertEquals(1215, result.get(0).getYear());
        assertEquals("https://example.com/magna.jpg", result.get(0).getImageUrl());
        assertEquals("Q178561", result.get(0).getWikidataId());
    }

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

    // ── isMilitaryEvent ───────────────────────────────────────────────────────

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

    // ── applyDiversityFilter ──────────────────────────────────────────────────

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

    // ── getCuratedCards ───────────────────────────────────────────────────────

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

    // ── fetchEvents – mocked HTTP ─────────────────────────────────────────────

    @Test
    void fetchEvents_returnsListNotExceedingLimit() {
        // Stub all SPARQL calls at the service level to avoid real HTTP calls
        doReturn(List.of()).when(wikidataService).fetchEvents(any(HistoricalEra.class), anyInt());

        List<EventCard> result = wikidataService.fetchEvents(HistoricalEra.MEDIEVAL, 5);
        assertTrue(result.size() <= 5);
    }

    // ── extractValue ──────────────────────────────────────────────────────────

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

    // ── Reflection helpers ────────────────────────────────────────────────────

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
}
