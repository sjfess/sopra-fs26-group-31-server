package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class GameInviteGetDTO {
    private Long id;
    private String fromUsername;
    private String lobbyCode;
    private Long gameId;

    public void setId(Long id) {this.id = id;}
    public Long getId() {return id;}

    public void setFromUsername(String fromUsername) {this.fromUsername = fromUsername;}
    public String getFromUsername() {return fromUsername;}

    public void setLobbyCode(String lobbyCode) {this.lobbyCode = lobbyCode;}
    public String getLobbyCode() {return lobbyCode;}

    public void setGameId(Long gameId) {this.gameId = gameId;}
    public Long getGameId() {return gameId;}

}
