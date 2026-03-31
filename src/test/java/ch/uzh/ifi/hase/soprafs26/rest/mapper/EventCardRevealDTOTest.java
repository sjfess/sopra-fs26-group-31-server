package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardRevealDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventCardRevealDTOTest {

    private EventCardRevealDTO dto;

    @BeforeEach
    void setUp() {
        dto = new EventCardRevealDTO();
    }

    @Test
    void testSetAndGetId() {
        dto.setId(7L);
        assertEquals(7L, dto.getId());
    }

    @Test
    void testSetAndGetTitle() {
        dto.setTitle("Sinking of the Titanic");
        assertEquals("Sinking of the Titanic", dto.getTitle());
    }

    @Test
    void testSetAndGetYear() {
        dto.setYear(1912);
        assertEquals(1912, dto.getYear());
    }

    @Test
    void testSetAndGetImageUrl() {
        dto.setImageUrl("https://example.com/titanic.jpg");
        assertEquals("https://example.com/titanic.jpg", dto.getImageUrl());
    }

    @Test
    void testDefaultsAreNullOrZero() {
        assertNull(dto.getId());
        assertNull(dto.getTitle());
        assertNull(dto.getImageUrl());
        assertEquals(0, dto.getYear());
    }

    @Test
    void testYearExposedUnlikeGetDTO() {
        // EventCardRevealDTO MUST expose getYear() to be able to check if a card is placed correctly
        boolean hasGetYear = false;
        for (java.lang.reflect.Method m : EventCardRevealDTO.class.getMethods()) {
            if (m.getName().equals("getYear")) {
                hasGetYear = true;
                break;
            }
        }
        assertTrue(hasGetYear, "EventCardRevealDTO must expose a getYear() method");
    }

    @Test
    void testYearNegativeForBCEvents() {
        dto.setYear(-776);
        assertEquals(-776, dto.getYear());
    }
}
