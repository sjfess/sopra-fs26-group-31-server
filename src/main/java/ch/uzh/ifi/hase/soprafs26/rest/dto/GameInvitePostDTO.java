package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class GameInvitePostDTO {
    private String toUsername;
    private Long fromUserId;

    public String getToUsername() {return toUsername;}
    public void setToUsername(String toUsername) {this.toUsername = toUsername;}

    public Long getFromUserId() {return fromUserId;}
    public void setFromUserId(Long fromUserId) {this.fromUserId = fromUserId;}
}

