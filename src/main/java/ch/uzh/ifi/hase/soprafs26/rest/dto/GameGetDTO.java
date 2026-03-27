package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;

public class GameGetDTO {

    private Long id;
    private String lobbyCode;
    private String era;
    private String status;
    private int deckSize;
    private int cardsRemaining;
    private Long hostId;
    private List<Long> playerIds;
    private int timelineSize;

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

    public Long getHostId() { return hostId; }
    public void setHostId(Long hostId) { this.hostId = hostId; }

    public List<Long> getPlayerIds() { return playerIds; }
    public void setPlayerIds(List<Long> playerIds) { this.playerIds = playerIds; }
  
    public int getTimelineSize() { return timelineSize; }
    public void setTimelineSize(int timelineSize) { this.timelineSize = timelineSize; }


}
