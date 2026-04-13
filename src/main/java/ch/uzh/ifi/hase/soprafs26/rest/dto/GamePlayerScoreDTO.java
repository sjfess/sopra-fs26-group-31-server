package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class GamePlayerScoreDTO {

    private Long userId;
    private String username;
    private Integer score;
    private Integer turnOrder;
    private Boolean activeTurn;
    private Integer correctStreak;
    private Integer bestStreak;
    private Integer cardsInHand;
    private Integer currentCardIndex;
    private Integer correctPlacements;
    private Integer incorrectPlacements;

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

    public Integer getCardsInHand() {
        return cardsInHand;
    }

    public void setCardsInHand(Integer cardsInHand) {
        this.cardsInHand = cardsInHand;
    }

    public Integer getCurrentCardIndex() {
        return currentCardIndex;
    }

    public void setCurrentCardIndex(Integer currentCardIndex) {
        this.currentCardIndex = currentCardIndex;
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

}