package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class FinalResultDTO {

    private Long userId;
    private String username;
    private Integer score;
    private Integer correctPlacements;
    private Integer incorrectPlacements;
    private Boolean winner;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getCorrectPlacements() {
        return correctPlacements;
    }

    public void setCorrectPlacements(Integer correctPlacements) {
        this.correctPlacements = correctPlacements;
    }

    public Integer getIncorrectPlacements() {
        return incorrectPlacements;
    }

    public void setIncorrectPlacements(Integer incorrectPlacements) {
        this.incorrectPlacements = incorrectPlacements;
    }

    public Boolean getWinner() {
        return winner;
    }

    public void setWinner(Boolean winner) {
        this.winner = winner;
    }
}