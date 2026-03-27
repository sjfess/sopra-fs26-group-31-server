package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class UserPutDTO {

    private String username;
    private String bio;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}