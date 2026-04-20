package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.util.List;
import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;

public class GameGetDTO {

    private Long id;
    private String lobbyCode;
    private HistoricalEra era;
    private String status;
    private int deckSize;
    private int cardsRemaining;
    private Long hostId;
    private List<PlayerSummaryDTO> players;
    private int timelineSize;
    private Difficulty difficulty;
    private GameMode gameMode;
    private int maxPlayers;

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLobbyCode() { return lobbyCode; }
    public void setLobbyCode(String lobbyCode) { this.lobbyCode = lobbyCode; }

    public HistoricalEra getEra() { return era; }
    public void setEra(HistoricalEra era) { this.era = era; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getDeckSize() { return deckSize; }
    public void setDeckSize(int deckSize) { this.deckSize = deckSize; }

    public int getCardsRemaining() { return cardsRemaining; }
    public void setCardsRemaining(int cardsRemaining) { this.cardsRemaining = cardsRemaining; }

    public Long getHostId() { return hostId; }
    public void setHostId(Long hostId) { this.hostId = hostId; }

    public List<PlayerSummaryDTO> getPlayers() { return players; }
    public void setPlayers(List<PlayerSummaryDTO> players) { this.players = players; }

    public int getTimelineSize() { return timelineSize; }
    public void setTimelineSize(int timelineSize) { this.timelineSize = timelineSize; }

    public GameMode getGameMode() { return gameMode; }
    public void setGameMode(GameMode gameMode) { this.gameMode = gameMode; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
}
