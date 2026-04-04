package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class GamePlayerScoreDTO {

    private Long userId;
    private String username;
    private Integer score;
    private Integer turnOrder;
    private Boolean activeTurn;
    private Integer correctStreak;
    private Integer bestStreak;

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

    public Integer getTurnOrder() {
        return turnOrder;
    }

    public void setTurnOrder(Integer turnOrder) {
        this.turnOrder = turnOrder;
    }

    public Boolean getActiveTurn() {
        return activeTurn;
    }

    public void setActiveTurn(Boolean activeTurn) {
        this.activeTurn = activeTurn;
    }

    public Integer getCorrectStreak() {
        return correctStreak;
    }

    public void setCorrectStreak(Integer correctStreak) {
        this.correctStreak = correctStreak;
    }

    public Integer getBestStreak() {
        return bestStreak;
    }

    public void setBestStreak(Integer bestStreak) {
        this.bestStreak = bestStreak;
    }
}