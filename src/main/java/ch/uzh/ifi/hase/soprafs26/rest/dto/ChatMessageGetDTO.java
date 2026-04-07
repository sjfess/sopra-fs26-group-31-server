package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ChatMessageGetDTO {
    private Long playerId;
    private String message;
    private String username;
    private String timestamp;

    public void setPlayerId(Long playerId) {this.playerId = playerId;}
    public Long getPlayerId() {return this.playerId;}

    public void setMessage(String message) {this.message = message;}
    public String getMessage() {return this.message;}

    public void setUsername(String username) {this.username = username;}
    public String getUsername() {return this.username;}

    public void setTimestamp(String timestamp) {this.timestamp = timestamp;}
    public String getTimestamp() {return this.timestamp;}

}
