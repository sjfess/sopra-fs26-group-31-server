package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class GameInvite {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private String lobbyCode;

    @Column(nullable = false)
    private Long fromUserId;

    @Column(nullable = false)
    private String fromUsername;

    @Column(nullable = false)
    private Long toUserId;

    public Long getId() {return id;}
    public void setId(Long id) {this.id = id;}

    public Long getGameId() {return gameId;}
    public void setGameId(Long gameId) {this.gameId = gameId;}

    public String getLobbyCode() {return lobbyCode;}
    public void setLobbyCode(String lobbyCode) {this.lobbyCode = lobbyCode;}

    public Long getFromUserId() {return fromUserId;}
    public void setFromUserId(Long fromUserId) {this.fromUserId = fromUserId;}

    public String getFromUsername() {return fromUsername;}
    public void setFromUsername(String fromUsername) {this.fromUsername = fromUsername;}

    public Long getToUserId() {return toUserId;}
    public void setToUserId(Long toUserId) {this.toUserId = toUserId;}

}
