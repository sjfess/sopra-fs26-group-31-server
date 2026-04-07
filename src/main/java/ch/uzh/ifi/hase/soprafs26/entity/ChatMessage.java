package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_message")
public class ChatMessage {
    @Id
    @GeneratedValue
    private Long id;
    private Long gameId;
    private Long playerId;
    private String username;
    private String message;
    private String timestamp;

    public void setId(Long id) {this.id = id;}
    public Long getId() {return id;}

    public void setGameId(Long gameId) {this.gameId = gameId;}
    public Long getGameId() {return gameId;}

    public void setPlayerId(Long playerId) {this.playerId = playerId;}
    public Long getPlayerId() {return playerId;}

    public void setUsername(String username) {this.username = username;}
    public String getUsername() {return username;}

    public void setMessage(String message) {this.message = message;}
    public String getMessage() {return message;}

    public void setTimestamp(String timestamp) {this.timestamp = timestamp;}
    public String getTimestamp() {return timestamp;}
}
