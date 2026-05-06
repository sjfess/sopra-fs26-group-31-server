package ch.uzh.ifi.hase.soprafs26.service;


import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import org.springframework.stereotype.Component;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HandCardDTO;


import java.util.ArrayList;
import java.util.List;

@Component
public class GameCardHelper {

    public String serializeDeck(List<EventCard> deck) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < deck.size(); i++) {
            if (i > 0) sb.append(",");
            EventCard c = deck.get(i);
            sb.append("{");
            sb.append("\"title\":\"").append(escapeJson(c.getTitle())).append("\"");
            sb.append(",\"year\":").append(c.getYear());
            sb.append(",\"imageUrl\":");
            if (c.getImageUrl() != null) {
                sb.append("\"").append(escapeJson(c.getImageUrl())).append("\"");
            } else {
                sb.append("null");
            }
            sb.append(",\"wikidataId\":");
            if (c.getWikidataId() != null) {
                sb.append("\"").append(escapeJson(c.getWikidataId())).append("\"");
            } else {
                sb.append("null");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    public List<EventCard> deserializeDeck(String json) {
        List<EventCard> cards = new ArrayList<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return cards;
        }

        int depth = 0;
        int blockStart = -1;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[' || c == ']') continue;
            if (c == '{') {
                depth++;
                if (depth == 1) blockStart = i;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && blockStart != -1) {
                    String block = json.substring(blockStart, i + 1);
                    EventCard card = parseCardBlock(block);
                    if (card != null) {
                        cards.add(card);
                    }
                    blockStart = -1;
                }
            }
        }

        return cards;
    }
    private EventCard parseCardBlock(String block) {
        String title = extractJsonString(block, "title");
        String yearStr = extractJsonNumber(block, "year");
        String imageUrl = extractJsonString(block, "imageUrl");
        String wikidataId = extractJsonString(block, "wikidataId");

        if (title == null || yearStr == null) return null;

        EventCard card = new EventCard();
        card.setTitle(title);
        try {
            card.setYear(Integer.parseInt(yearStr));
        } catch (NumberFormatException e) {
            return null;
        }
        card.setImageUrl(imageUrl);
        card.setWikidataId(wikidataId);
        return card;
    }

    public String serializeHandIndices(List<Integer> indices) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < indices.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(indices.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
    public void dealCardsToPlayer(GamePlayer player, Game game, int count) {
        List<Integer> hand = deserializeHandIndices(player.getHandIndicesJson());
        int nextIndex = game.getNextCardIndex();
        int deckSize = game.getDeckSize();
        for (int i = 0; i < count && nextIndex < deckSize; i++) {
            hand.add(nextIndex++);
        }
        game.setNextCardIndex(nextIndex);
        player.setHandIndicesJson(serializeHandIndices(hand));
        player.setCardsInHand(hand.size());
    }

    public List<Integer> deserializeHandIndices(String json) {
        List<Integer> indices = new ArrayList<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) return indices;
        String inner = json.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        for (String part : inner.split(",")) {
            part = part.trim();
            if (!part.isEmpty()) {
                try { indices.add(Integer.parseInt(part)); } catch (NumberFormatException ignored) {}
            }
        }
        return indices;
    }



    private String extractJsonString(String block, String key) {
        String search = "\"" + key + "\":";
        int pos = block.indexOf(search);
        if (pos == -1) return null;

        int valueStart = pos + search.length();
        while (valueStart < block.length() && block.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= block.length()) return null;
        if (block.charAt(valueStart) == 'n') return null;
        if (block.charAt(valueStart) != '"') return null;

        int openQuote = valueStart;
        int closeQuote = openQuote + 1;
        while (closeQuote < block.length()) {
            if (block.charAt(closeQuote) == '"' && block.charAt(closeQuote - 1) != '\\') break;
            closeQuote++;
        }
        if (closeQuote >= block.length()) return null;

        return unescapeJson(block.substring(openQuote + 1, closeQuote));
    }

    private String extractJsonNumber(String block, String key) {
        String search = "\"" + key + "\":";
        int pos = block.indexOf(search);
        if (pos == -1) return null;

        int valueStart = pos + search.length();
        while (valueStart < block.length() && block.charAt(valueStart) == ' ') valueStart++;

        StringBuilder num = new StringBuilder();
        for (int i = valueStart; i < block.length(); i++) {
            char c = block.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) {
                num.append(c);
            } else {
                break;
            }
        }
        return !num.isEmpty() ? num.toString() : null;
    }

    String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    String unescapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
