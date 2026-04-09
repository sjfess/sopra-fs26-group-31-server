package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class FriendRequestPutDTO {
    private Long receiverId;
    private String action;

    public void setReceiverId(Long receiverId) {this.receiverId = receiverId;}
    public Long getReceiverId() {return receiverId;}
    public void setAction(String action) {this.action = action;}
    public String getAction() {return action;}
}
