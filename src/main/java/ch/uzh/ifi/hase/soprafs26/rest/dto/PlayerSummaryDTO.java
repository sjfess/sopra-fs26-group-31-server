
package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class PlayerSummaryDTO{
    private String username;
    private Long id;
    private String avatarUrl;

    public void setUsername(String username) {this.username = username;}
    public String getUsername() {return username;}

    public void setId(Long id) {this.id = id;}
    public Long getId() {return id;}

    public String getAvatarUrl() {return avatarUrl;}
    public void setAvatarUrl(String avatarUrl) {this.avatarUrl = avatarUrl;}
}