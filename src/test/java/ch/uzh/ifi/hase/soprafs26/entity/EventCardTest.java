package ch.uzh.ifi.hase.soprafs26.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventCardTest {

    private EventCard eventCard;

    @BeforeEach
    void setUp() {
        eventCard = new EventCard();
    }

    @Test
    void testSetAndGetId() {
        eventCard.setId(42L);
        assertEquals(42L, eventCard.getId());
    }

    @Test
    void testSetAndGetTitle() {
        eventCard.setTitle("Battle of Hastings");
        assertEquals("Battle of Hastings", eventCard.getTitle());
    }

    @Test
    void testSetAndGetYear() {
        eventCard.setYear(1066);
        assertEquals(1066, eventCard.getYear());
    }

    @Test
    void testSetAndGetImageUrl() {
        eventCard.setImageUrl("https://example.com/image.jpg");
        assertEquals("https://example.com/image.jpg", eventCard.getImageUrl());
    }

    @Test
    void testSetAndGetWikidataId() {
        eventCard.setWikidataId("Q178561");
        assertEquals("Q178561", eventCard.getWikidataId());
    }

    @Test
    void testImageUrlNullByDefault() {
        assertNull(eventCard.getImageUrl());
    }

    @Test
    void testWikidataIdNullByDefault() {
        assertNull(eventCard.getWikidataId());
    }

    @Test
    void testIdNullByDefault() {
        assertNull(eventCard.getId());
    }

    @Test
    void testYearZeroByDefault() {
        assertEquals(0, eventCard.getYear());
    }

    @Test
    void testSetYearNegative() {
        // BC years stored as negative integers
        eventCard.setYear(-44);
        assertEquals(-44, eventCard.getYear());
    }
}
