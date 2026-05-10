package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;


/**
 * Represents a historical event card used in the game.
 * Cards are fetched from Wikidata and stored for the duration of a game session.
 */
@Entity
@Table(name = "event_cards")
public class EventCard implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "event_year", nullable = false)
    private int year;

    @Column(length = 1000)
    private String imageUrl;

    @Column
    private String wikidataId;

    @Column
    @Enumerated(EnumType.STRING)
    private HistoricalEra era;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getWikidataId() {
        return wikidataId;
    }

    public void setWikidataId(String wikidataId) {
        this.wikidataId = wikidataId;
    }

    public HistoricalEra getEra() {
        return era;
    }
    public void setEra(HistoricalEra era) {
        this.era = era;
    }
}
