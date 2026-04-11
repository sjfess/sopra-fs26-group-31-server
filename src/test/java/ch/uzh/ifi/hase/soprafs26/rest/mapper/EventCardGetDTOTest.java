package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardGetDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventCardGetDTOTest {

    private EventCardGetDTO dto;

    @BeforeEach
    void setUp() {
        dto = new EventCardGetDTO();
    }

    @Test
    void testSetAndGetId() {
        dto.setId(1L);
        assertEquals(1L, dto.getId());
    }

    @Test
    void testSetAndGetTitle() {
        dto.setTitle("Moon Landing (Apollo 11)");
        assertEquals("Moon Landing (Apollo 11)", dto.getTitle());
    }

    @Test
    void testSetAndGetImageUrl() {
        dto.setImageUrl("https://upload.wikimedia.org/apollo.jpg");
        assertEquals("https://upload.wikimedia.org/apollo.jpg", dto.getImageUrl());
    }

    @Test
    void testDefaultsAreNull() {
        assertNull(dto.getId());
        assertNull(dto.getTitle());
        assertNull(dto.getImageUrl());
    }

    @Test
    void testYearFieldIsAbsent() {
        // EventCardGetDTO deliberately does NOT expose a year field:
        // year is hidden from players during gameplay.
        // Verify via reflection that no getYear() method exists.
        boolean hasGetYear = false;
        for (java.lang.reflect.Method m : EventCardGetDTO.class.getMethods()) {
            if (m.getName().equals("getYear")) {
                hasGetYear = true;
                break;
            }
        }
        assertFalse(hasGetYear, "EventCardGetDTO must not expose a getYear() method");
    }

    @Test
    void testSetImageUrlToNull() {
        dto.setImageUrl("https://example.com/img.jpg");
        dto.setImageUrl(null);
        assertNull(dto.getImageUrl());
    }
}
