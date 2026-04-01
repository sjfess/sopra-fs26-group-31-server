package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import ch.uzh.ifi.hase.soprafs26.rest.dto.PlacementResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlacementResultDTOTest {

    private PlacementResultDTO dto;

    @BeforeEach
    void setUp() {
        dto = new PlacementResultDTO();
    }

    @Test
    void setCorrect_true_isCorrectReturnsTrue() {
        dto.setCorrect(true);
        assertTrue(dto.isCorrect());
    }

    @Test
    void setCorrect_false_isCorrectReturnsFalse() {
        dto.setCorrect(false);
        assertFalse(dto.isCorrect());
    }

    @Test
    void correct_defaultValue_isFalse() {
        // boolean primitives default to false — verifies no accidental initialisation
        assertFalse(dto.isCorrect());
    }

    // check title/name

    @Test
    void setTitle_normalString_getsTitleReturnsValue() {
        dto.setTitle("Magna Carta");
        assertEquals("Magna Carta", dto.getTitle());
    }

    @Test
    void setTitle_null_getsTitleReturnsNull() {
        dto.setTitle(null);
        assertNull(dto.getTitle());
    }

    @Test
    void setTitle_emptyString_getsTitleReturnsEmpty() {
        dto.setTitle("");
        assertEquals("", dto.getTitle());
    }

    // check year
    @Test
    void setYear_positiveYear_getsYearReturnsValue() {
        dto.setYear(1215);
        assertEquals(1215, dto.getYear());
    }

    @Test
    void setYear_negativeYear_getsYearReturnsValue() {
        // Negative years represent BCE dates — the DTO must support them
        dto.setYear(-44);
        assertEquals(-44, dto.getYear());
    }

    @Test
    void year_defaultValue_isZero() {
        // int primitives default to 0 — no accidental initialisation
        assertEquals(0, dto.getYear());
    }

    // check imageUrl

    @Test
    void setImageUrl_validUrl_getsImageUrlReturnsValue() {
        dto.setImageUrl("https://example.com/image.jpg");
        assertEquals("https://example.com/image.jpg", dto.getImageUrl());
    }

    @Test
    void setImageUrl_null_getsImageUrlReturnsNull() {
        // imageUrl is optional — null must be accepted without throwing
        dto.setImageUrl(null);
        assertNull(dto.getImageUrl());
    }

    // check timelineSize

    @Test
    void setTimelineSize_positiveValue_getsTimelineSizeReturnsValue() {
        dto.setTimelineSize(7);
        assertEquals(7, dto.getTimelineSize());
    }

    @Test
    void setTimelineSize_zero_getsTimelineSizeReturnsZero() {
        // Zero is a valid timeline size (empty timeline at game start)
        dto.setTimelineSize(0);
        assertEquals(0, dto.getTimelineSize());
    }

    @Test
    void timelineSize_defaultValue_isZero() {
        assertEquals(0, dto.getTimelineSize());
    }

    // check state

    @Test
    void allFields_setTogether_returnCorrectValues() {
        // Verifies that fields are independent and don't interfere with each other
        dto.setCorrect(true);
        dto.setTitle("Black Death");
        dto.setYear(1347);
        dto.setImageUrl("https://example.com/blackdeath.jpg");
        dto.setTimelineSize(5);

        assertTrue(dto.isCorrect());
        assertEquals("Black Death", dto.getTitle());
        assertEquals(1347, dto.getYear());
        assertEquals("https://example.com/blackdeath.jpg", dto.getImageUrl());
        assertEquals(5, dto.getTimelineSize());
    }
}
