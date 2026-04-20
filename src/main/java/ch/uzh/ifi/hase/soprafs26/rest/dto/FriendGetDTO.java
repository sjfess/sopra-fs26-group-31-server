package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class FriendGetDTO {
    private Long id;
    private String username;
    private String status;

    public void setId(Long id) {this.id = id;}
    public Long getId() {return this.id;}

    public void setUsername(String username) {this.username = username;}
    public String getUsername() {return username;}

    public void setStatus(String status) {this.status = status;}
    public String getStatus() {return status;}
}
