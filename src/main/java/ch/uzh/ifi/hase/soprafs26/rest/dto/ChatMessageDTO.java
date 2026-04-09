package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class ChatMessageDTO {
    private Long playerId;
    private String message;

    public Long getPlayerId() {return playerId;}
    public void setPlayerId(Long playerId) {this.playerId = playerId;}

    public String getMessage() {return message;}
    public void setMessage(String message) {this.message = message;}

}
