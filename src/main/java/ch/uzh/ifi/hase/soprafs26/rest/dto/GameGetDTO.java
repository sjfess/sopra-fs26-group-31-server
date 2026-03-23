package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class GameGetDTO {

    private Long id;
    private String lobbyCode;
    private String era;
    private String status;
    private int deckSize;
    private int cardsRemaining;

    // Getters & setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLobbyCode() { return lobbyCode; }
    public void setLobbyCode(String lobbyCode) { this.lobbyCode = lobbyCode; }

    public String getEra() { return era; }
    public void setEra(String era) { this.era = era; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getDeckSize() { return deckSize; }
    public void setDeckSize(int deckSize) { this.deckSize = deckSize; }

    public int getCardsRemaining() { return cardsRemaining; }
    public void setCardsRemaining(int cardsRemaining) { this.cardsRemaining = cardsRemaining; }
}
