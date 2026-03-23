package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardRevealDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.WikidataService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Event Controller
 * Handles REST requests related to historical event cards.
 */
@RestController
public class EventController {

    private final WikidataService wikidataService;

    EventController(WikidataService wikidataService) {
        this.wikidataService = wikidataService;
    }

    /**
     * GET /events?era=MEDIEVAL&limit=30
     *
     * Fetches historical event cards from Wikidata for the given era.
     * Returns cards WITHOUT the year (year is hidden from players).
     *
     * @param era   the historical era (ANCIENT, MEDIEVAL, RENAISSANCE, MODERN, INFORMATION)
     * @param limit how many cards to fetch (default 30)
     * @return list of event cards with hidden years
     */
    @GetMapping("/events")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<EventCardGetDTO> getEvents(
            @RequestParam("era") HistoricalEra era,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {

        List<EventCard> events = wikidataService.fetchEvents(era, limit);

        List<EventCardGetDTO> dtos = new ArrayList<>();
        for (EventCard card : events) {
            dtos.add(DTOMapper.INSTANCE.convertEntityToEventCardGetDTO(card));
        }
        return dtos;
    }

    /**
     * GET /events/reveal?era=MEDIEVAL&limit=30
     *
     * Same as /events but returns cards WITH the year visible.
     * Use this for debugging or for revealing cards after placement.
     * In your actual game logic, you'll probably reveal cards individually
     * via WebSocket messages rather than this endpoint.
     *
     * @param era   the historical era
     * @param limit how many cards to fetch (default 30)
     * @return list of event cards with years revealed
     */
    @GetMapping("/events/reveal")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<EventCardRevealDTO> getEventsRevealed(
            @RequestParam("era") HistoricalEra era,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {

        List<EventCard> events = wikidataService.fetchEvents(era, limit);

        List<EventCardRevealDTO> dtos = new ArrayList<>();
        for (EventCard card : events) {
            dtos.add(DTOMapper.INSTANCE.convertEntityToEventCardRevealDTO(card));
        }
        return dtos;
    }
}
