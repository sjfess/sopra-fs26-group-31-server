package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameCardHelperTest {

    private GameCardHelper helper;

    @BeforeEach
    void setUp() {
        helper = new GameCardHelper();
    }

    // =========================================================================
    // serializeDeck
    // =========================================================================

    @Test
    void serializeDeck_emptyList_returnsEmptyArray() {
        assertEquals("[]", helper.serializeDeck(List.of()));
    }

    @Test
    void serializeDeck_cardWithAllFields_containsAllValues() {
        EventCard card = makeCard("Battle of Hastings", 1066, "http://img.com/x.jpg", "Q12345");
        String json = helper.serializeDeck(List.of(card));
        assertTrue(json.contains("\"title\":\"Battle of Hastings\""));
        assertTrue(json.contains("\"year\":1066"));
        assertTrue(json.contains("\"imageUrl\":\"http://img.com/x.jpg\""));
        assertTrue(json.contains("\"wikidataId\":\"Q12345\""));
    }

    @Test
    void serializeDeck_cardWithNullImageUrlAndWikidataId_containsNulls() {
        EventCard card = makeCard("Moon Landing", 1969, null, null);
        String json = helper.serializeDeck(List.of(card));
        assertTrue(json.contains("\"imageUrl\":null"));
        assertTrue(json.contains("\"wikidataId\":null"));
    }

    @Test
    void serializeDeck_multipleCards_separatedByComma() {
        EventCard c1 = makeCard("Event A", 100, null, null);
        EventCard c2 = makeCard("Event B", 200, null, null);
        String json = helper.serializeDeck(List.of(c1, c2));
        assertTrue(json.startsWith("[{"));
        assertTrue(json.contains("},{"));
        assertTrue(json.endsWith("}]"));
    }

    @Test
    void serializeDeck_titleWithSpecialChars_escapedCorrectly() {
        EventCard card = makeCard("He said \"hello\"\nNew line\ttab", 1900, null, null);
        String json = helper.serializeDeck(List.of(card));
        assertTrue(json.contains("\\\"hello\\\""));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\t"));
    }

    // =========================================================================
    // deserializeDeck
    // =========================================================================

    @Test
    void deserializeDeck_null_returnsEmpty() {
        assertTrue(helper.deserializeDeck(null).isEmpty());
    }

    @Test
    void deserializeDeck_emptyString_returnsEmpty() {
        assertTrue(helper.deserializeDeck("").isEmpty());
    }

    @Test
    void deserializeDeck_emptyArray_returnsEmpty() {
        assertTrue(helper.deserializeDeck("[]").isEmpty());
    }

    @Test
    void deserializeDeck_singleCard_parsedCorrectly() {
        EventCard card = makeCard("Signing of Magna Carta", 1215, "http://img.jpg", "Q99");
        String json = helper.serializeDeck(List.of(card));
        List<EventCard> result = helper.deserializeDeck(json);
        assertEquals(1, result.size());
        assertEquals("Signing of Magna Carta", result.get(0).getTitle());
        assertEquals(1215, result.get(0).getYear());
        assertEquals("http://img.jpg", result.get(0).getImageUrl());
        assertEquals("Q99", result.get(0).getWikidataId());
    }

    @Test
    void deserializeDeck_cardWithNullOptionalFields_parsedCorrectly() {
        EventCard card = makeCard("French Revolution", 1789, null, null);
        String json = helper.serializeDeck(List.of(card));
        List<EventCard> result = helper.deserializeDeck(json);
        assertEquals(1, result.size());
        assertNull(result.get(0).getImageUrl());
        assertNull(result.get(0).getWikidataId());
    }

    @Test
    void deserializeDeck_multipleCards_allParsed() {
        List<EventCard> cards = List.of(
                makeCard("Event One", -500, null, null),
                makeCard("Event Two", 0, "http://img.com", "Q1"),
                makeCard("Event Three", 2000, null, "Q2")
        );
        String json = helper.serializeDeck(cards);
        List<EventCard> result = helper.deserializeDeck(json);
        assertEquals(3, result.size());
        assertEquals("Event One", result.get(0).getTitle());
        assertEquals(-500, result.get(0).getYear());
        assertEquals("Event Two", result.get(1).getTitle());
        assertEquals("Event Three", result.get(2).getTitle());
    }

    @Test
    void deserializeDeck_cardWithNegativeYear_parsedCorrectly() {
        EventCard card = makeCard("Birth of Caesar", -100, null, null);
        String json = helper.serializeDeck(List.of(card));
        List<EventCard> result = helper.deserializeDeck(json);
        assertEquals(-100, result.get(0).getYear());
    }

    @Test
    void deserializeDeck_blockWithMissingTitle_skipped() {
        // Manually crafted block without "title" field
        String json = "[{\"year\":1066,\"imageUrl\":null,\"wikidataId\":null}]";
        List<EventCard> result = helper.deserializeDeck(json);
        assertTrue(result.isEmpty());
    }

    @Test
    void deserializeDeck_blockWithMissingYear_skipped() {
        String json = "[{\"title\":\"No Year Event\",\"imageUrl\":null,\"wikidataId\":null}]";
        List<EventCard> result = helper.deserializeDeck(json);
        assertTrue(result.isEmpty());
    }

    @Test
    void deserializeDeck_blockWithInvalidYear_skipped() {
        String json = "[{\"title\":\"Bad Year\",\"year\":\"notanumber\",\"imageUrl\":null,\"wikidataId\":null}]";
        List<EventCard> result = helper.deserializeDeck(json);
        assertTrue(result.isEmpty());
    }

    @Test
    void deserializeDeck_titleWithEscapedQuotes_unescapedCorrectly() {
        EventCard card = makeCard("He said \"yes\"", 1800, null, null);
        String json = helper.serializeDeck(List.of(card));
        List<EventCard> result = helper.deserializeDeck(json);
        assertEquals("He said \"yes\"", result.get(0).getTitle());
    }

    // =========================================================================
    // serializeHandIndices
    // =========================================================================

    @Test
    void serializeHandIndices_emptyList_returnsEmptyArray() {
        assertEquals("[]", helper.serializeHandIndices(List.of()));
    }

    @Test
    void serializeHandIndices_singleElement_correctFormat() {
        assertEquals("[5]", helper.serializeHandIndices(List.of(5)));
    }

    @Test
    void serializeHandIndices_multipleElements_commaSeparated() {
        assertEquals("[0,3,7]", helper.serializeHandIndices(List.of(0, 3, 7)));
    }

    // =========================================================================
    // deserializeHandIndices
    // =========================================================================

    @Test
    void deserializeHandIndices_null_returnsEmpty() {
        assertTrue(helper.deserializeHandIndices(null).isEmpty());
    }

    @Test
    void deserializeHandIndices_emptyString_returnsEmpty() {
        assertTrue(helper.deserializeHandIndices("").isEmpty());
    }

    @Test
    void deserializeHandIndices_emptyArray_returnsEmpty() {
        assertTrue(helper.deserializeHandIndices("[]").isEmpty());
    }

    @Test
    void deserializeHandIndices_validArray_parsedCorrectly() {
        List<Integer> result = helper.deserializeHandIndices("[1,2,3]");
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void deserializeHandIndices_withSpaces_parsedCorrectly() {
        List<Integer> result = helper.deserializeHandIndices("[ 1 , 2 , 3 ]");
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void deserializeHandIndices_withInvalidEntry_invalidEntryIgnored() {
        List<Integer> result = helper.deserializeHandIndices("[1,abc,3]");
        assertEquals(List.of(1, 3), result);
    }

    @Test
    void deserializeHandIndices_roundTrip_preservesValues() {
        List<Integer> original = List.of(0, 5, 12, 99);
        String serialized = helper.serializeHandIndices(original);
        List<Integer> result = helper.deserializeHandIndices(serialized);
        assertEquals(original, result);
    }

    // =========================================================================
    // dealCardsToPlayer
    // =========================================================================

    @Test
    void dealCardsToPlayer_normalCase_addsCardsAndAdvancesIndex() {
        Game game = makeGame(0, 10);
        GamePlayer player = makePlayer("[]");

        helper.dealCardsToPlayer(player, game, 3);

        List<Integer> hand = helper.deserializeHandIndices(player.getHandIndicesJson());
        assertEquals(List.of(0, 1, 2), hand);
        assertEquals(3, player.getCardsInHand());
        assertEquals(3, game.getNextCardIndex());
    }

    @Test
    void dealCardsToPlayer_countExceedsRemainingCards_onlyDealsAvailable() {
        Game game = makeGame(8, 10); // only 2 cards left
        GamePlayer player = makePlayer("[]");

        helper.dealCardsToPlayer(player, game, 5); // ask for 5, only 2 available

        List<Integer> hand = helper.deserializeHandIndices(player.getHandIndicesJson());
        assertEquals(List.of(8, 9), hand);
        assertEquals(2, player.getCardsInHand());
        assertEquals(10, game.getNextCardIndex());
    }

    @Test
    void dealCardsToPlayer_countZero_handUnchanged() {
        Game game = makeGame(0, 10);
        GamePlayer player = makePlayer("[1,2]");

        helper.dealCardsToPlayer(player, game, 0);

        List<Integer> hand = helper.deserializeHandIndices(player.getHandIndicesJson());
        assertEquals(List.of(1, 2), hand);
        assertEquals(0, game.getNextCardIndex());
    }

    @Test
    void dealCardsToPlayer_appendsToExistingHand() {
        Game game = makeGame(5, 10);
        GamePlayer player = makePlayer("[0,1,2]");

        helper.dealCardsToPlayer(player, game, 2);

        List<Integer> hand = helper.deserializeHandIndices(player.getHandIndicesJson());
        assertEquals(List.of(0, 1, 2, 5, 6), hand);
        assertEquals(5, player.getCardsInHand());
    }

    @Test
    void dealCardsToPlayer_deckAlreadyExhausted_nothingAdded() {
        Game game = makeGame(10, 10); // nextIndex == deckSize
        GamePlayer player = makePlayer("[]");

        helper.dealCardsToPlayer(player, game, 3);

        assertTrue(helper.deserializeHandIndices(player.getHandIndicesJson()).isEmpty());
        assertEquals(0, player.getCardsInHand());
    }

    // =========================================================================
    // escapeJson
    // =========================================================================

    @Test
    void escapeJson_null_returnsEmptyString() {
        assertEquals("", helper.escapeJson(null));
    }

    @Test
    void escapeJson_backslash_escaped() {
        assertEquals("a\\\\b", helper.escapeJson("a\\b"));
    }

    @Test
    void escapeJson_quote_escaped() {
        assertEquals("say \\\"hi\\\"", helper.escapeJson("say \"hi\""));
    }

    @Test
    void escapeJson_newline_escaped() {
        assertEquals("line1\\nline2", helper.escapeJson("line1\nline2"));
    }

    @Test
    void escapeJson_carriageReturn_escaped() {
        assertEquals("a\\rb", helper.escapeJson("a\rb"));
    }

    @Test
    void escapeJson_tab_escaped() {
        assertEquals("a\\tb", helper.escapeJson("a\tb"));
    }

    @Test
    void escapeJson_normalString_unchanged() {
        assertEquals("Hello World", helper.escapeJson("Hello World"));
    }

    @Test
    void escapeJson_allSpecialChars_allEscaped() {
        assertEquals("\\\\\\\"\\n\\r\\t", helper.escapeJson("\\\"\n\r\t"));
    }

    // =========================================================================
    // unescapeJson
    // =========================================================================

    @Test
    void unescapeJson_null_returnsNull() {
        assertNull(helper.unescapeJson(null));
    }

    @Test
    void unescapeJson_escapedQuote_unescaped() {
        assertEquals("say \"hi\"", helper.unescapeJson("say \\\"hi\\\""));
    }

    @Test
    void unescapeJson_escapedBackslash_unescaped() {
        assertEquals("a\\b", helper.unescapeJson("a\\\\b"));
    }

    @Test
    void unescapeJson_escapedNewline_unescaped() {
        assertEquals("line1\nline2", helper.unescapeJson("line1\\nline2"));
    }

    @Test
    void unescapeJson_escapedCarriageReturn_unescaped() {
        assertEquals("a\rb", helper.unescapeJson("a\\rb"));
    }

    @Test
    void unescapeJson_escapedTab_unescaped() {
        assertEquals("a\tb", helper.unescapeJson("a\\tb"));
    }

    @Test
    void unescapeJson_normalString_unchanged() {
        assertEquals("Hello", helper.unescapeJson("Hello"));
    }

    @Test
    void escapeAndUnescape_roundTrip_preservesValue() {
        String original = "He said \"hello\"\nNew\\line\ttabbed\rreturn";
        assertEquals(original, helper.unescapeJson(helper.escapeJson(original)));
    }

    // =========================================================================
    // extractJsonString edge cases (via deserializeDeck)
    // =========================================================================

    @Test
    void deserializeDeck_imageUrlIsJsonNullLiteral_parsedAsNull() {
        // imageUrl: null in the JSON → extractJsonString sees 'n' and returns null
        String json = "[{\"title\":\"Test Event\",\"year\":1500,\"imageUrl\":null,\"wikidataId\":null}]";
        List<EventCard> result = helper.deserializeDeck(json);
        assertEquals(1, result.size());
        assertNull(result.get(0).getImageUrl());
    }

    @Test
    void deserializeDeck_fieldValueNotQuotedAndNotNull_returnsNull() {
        // imageUrl starts with a digit — not 'n', not '"' → extractJsonString returns null
        String json = "[{\"title\":\"Test\",\"year\":1500,\"imageUrl\":12345,\"wikidataId\":null}]";
        List<EventCard> result = helper.deserializeDeck(json);
        assertEquals(1, result.size());
        assertNull(result.get(0).getImageUrl());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private EventCard makeCard(String title, int year, String imageUrl, String wikidataId) {
        EventCard card = new EventCard();
        card.setTitle(title);
        card.setYear(year);
        card.setImageUrl(imageUrl);
        card.setWikidataId(wikidataId);
        return card;
    }

    private Game makeGame(int nextCardIndex, int deckSize) {
        Game game = new Game();
        game.setNextCardIndex(nextCardIndex);
        game.setDeckSize(deckSize);
        return game;
    }

    private GamePlayer makePlayer(String handIndicesJson) {
        GamePlayer player = new GamePlayer();
        player.setHandIndicesJson(handIndicesJson);
        player.setCardsInHand(0);
        return player;
    }
}