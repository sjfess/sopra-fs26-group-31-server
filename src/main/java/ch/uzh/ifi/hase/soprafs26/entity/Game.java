package ch.uzh.ifi.hase.soprafs26.entity;

import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Represents a game session. When the host starts the game, the deck is
 * fetched once from Wikidata and stored as a JSON string. All players
 * draw from this same fixed deck via {@code nextCardIndex}.
 */
@Entity
@Table(name = "game")
public class Game implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String lobbyCode;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private HistoricalEra era;

    /** WAITING → IN_PROGRESS → FINISHED */
    @Column(nullable = false)
    private String status;

    /**
     * The full deck serialised as a JSON array of objects, each with
     * title, year, imageUrl, wikidataId. Stored in a TEXT column so
     * H2 does not truncate it.
     */
    @Column(columnDefinition = "TEXT")
    private String deckJson;

    /** Points to the next card to be dealt from the deck. */
    @Column(nullable = false)
    private int nextCardIndex;

    /** Total number of cards in the deck (avoids deserialising just to count). */
    @Column(nullable = false)
    private int deckSize;

    // Getters & setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLobbyCode() { return lobbyCode; }
    public void setLobbyCode(String lobbyCode) { this.lobbyCode = lobbyCode; }

    public HistoricalEra getEra() { return era; }
    public void setEra(HistoricalEra era) { this.era = era; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDeckJson() { return deckJson; }
    public void setDeckJson(String deckJson) { this.deckJson = deckJson; }

    public int getNextCardIndex() { return nextCardIndex; }
    public void setNextCardIndex(int nextCardIndex) { this.nextCardIndex = nextCardIndex; }

    public int getDeckSize() { return deckSize; }
    public void setDeckSize(int deckSize) { this.deckSize = deckSize; }
}
