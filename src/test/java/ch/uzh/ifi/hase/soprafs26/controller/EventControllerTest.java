package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.service.WikidataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private WikidataService wikidataService;

    @InjectMocks
    private EventController eventController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(eventController).build();
    }

    private EventCard makeCard(Long id, String title, int year, String imageUrl) {
        EventCard card = new EventCard();
        card.setId(id);
        card.setTitle(title);
        card.setYear(year);
        card.setImageUrl(imageUrl);
        return card;
    }

    // ── GET /events ───────────────────────────────────────────────────────────

    @Test
    void getEvents_returnsOkWithCards() throws Exception {
        EventCard card = makeCard(1L, "Moon Landing", 1969, "https://example.com/moon.jpg");
        when(wikidataService.fetchEvents(HistoricalEra.MODERN, 30)).thenReturn(List.of(card));

        mockMvc.perform(get("/events")
                        .param("era", "MODERN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Moon Landing")))
                .andExpect(jsonPath("$[0].imageUrl", is("https://example.com/moon.jpg")));
    }

    @Test
    void getEvents_doesNotExposeYear() throws Exception {
        EventCard card = makeCard(1L, "Moon Landing", 1969, null);
        when(wikidataService.fetchEvents(HistoricalEra.MODERN, 30)).thenReturn(List.of(card));

        mockMvc.perform(get("/events")
                        .param("era", "MODERN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].year").doesNotExist());
    }

    @Test
    void getEvents_defaultLimitIs30() throws Exception {
        when(wikidataService.fetchEvents(HistoricalEra.MEDIEVAL, 30)).thenReturn(List.of());

        mockMvc.perform(get("/events")
                        .param("era", "MEDIEVAL")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(wikidataService, times(1)).fetchEvents(HistoricalEra.MEDIEVAL, 30);
    }

    @Test
    void getEvents_customLimitIsForwardedToService() throws Exception {
        when(wikidataService.fetchEvents(HistoricalEra.ANCIENT, 10)).thenReturn(List.of());

        mockMvc.perform(get("/events")
                        .param("era", "ANCIENT")
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(wikidataService, times(1)).fetchEvents(HistoricalEra.ANCIENT, 10);
    }

    @Test
    void getEvents_missingEraParam_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/events")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEvents_emptyList_returnsEmptyJsonArray() throws Exception {
        when(wikidataService.fetchEvents(any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/events")
                        .param("era", "RENAISSANCE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getEvents_multipleCards_allReturned() throws Exception {
        List<EventCard> cards = List.of(
                makeCard(1L, "French Revolution", 1789, null),
                makeCard(2L, "Fall of the Berlin Wall", 1989, null)
        );
        when(wikidataService.fetchEvents(HistoricalEra.MODERN, 30)).thenReturn(cards);

        mockMvc.perform(get("/events")
                        .param("era", "MODERN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("French Revolution")))
                .andExpect(jsonPath("$[1].title", is("Fall of the Berlin Wall")));
    }

    // ── GET /events/reveal ────────────────────────────────────────────────────

    @Test
    void getEventsRevealed_returnsOkWithYear() throws Exception {
        EventCard card = makeCard(1L, "Moon Landing", 1969, "https://example.com/moon.jpg");
        when(wikidataService.fetchEvents(HistoricalEra.MODERN, 30)).thenReturn(List.of(card));

        mockMvc.perform(get("/events/reveal")
                        .param("era", "MODERN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title", is("Moon Landing")))
                .andExpect(jsonPath("$[0].year", is(1969)))
                .andExpect(jsonPath("$[0].imageUrl", is("https://example.com/moon.jpg")));
    }

    @Test
    void getEventsRevealed_defaultLimitIs30() throws Exception {
        when(wikidataService.fetchEvents(HistoricalEra.INFORMATION, 30)).thenReturn(List.of());

        mockMvc.perform(get("/events/reveal")
                        .param("era", "INFORMATION")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(wikidataService, times(1)).fetchEvents(HistoricalEra.INFORMATION, 30);
    }

    @Test
    void getEventsRevealed_customLimit_forwarded() throws Exception {
        when(wikidataService.fetchEvents(HistoricalEra.RENAISSANCE, 5)).thenReturn(List.of());

        mockMvc.perform(get("/events/reveal")
                        .param("era", "RENAISSANCE")
                        .param("limit", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(wikidataService, times(1)).fetchEvents(HistoricalEra.RENAISSANCE, 5);
    }

    @Test
    void getEventsRevealed_missingEraParam_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/events/reveal")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEventsRevealed_emptyList_returnsEmptyJsonArray() throws Exception {
        when(wikidataService.fetchEvents(any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/events/reveal")
                        .param("era", "MEDIEVAL")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}