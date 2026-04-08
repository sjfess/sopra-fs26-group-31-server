package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class FriendRequestPostDTO {

    private Long senderId;
    private String receiverUsername;

    public Long getSenderId() {return senderId;}
    public void setSenderId(Long senderId) {this.senderId = senderId;}

    public String getReceiverUsername() {return receiverUsername;}
    public void setReceiverUsername(String receiverUsername) {this.receiverUsername = receiverUsername;}
}