package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "game_player")
public class GamePlayer implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer cardsInHand = 0;

    @Column
    private Integer currentCardIndex;

    @Column(columnDefinition = "TEXT")
    private String handIndicesJson;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(nullable = false)
    private Integer turnOrder;

    @Column(nullable = false)
    private Boolean activeTurn = false;

    @Column
    private Instant turnStartedAt;
    @Column(nullable = false)
    private Integer correctPlacements = 0;

    @Column(nullable = false)
    private Integer incorrectPlacements = 0;

    @Column(nullable = false)
    private Integer correctStreak = 0;

    @Column(nullable = false)
    private Integer bestStreak = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public Integer getCurrentCardIndex() {
        return currentCardIndex;
    }

    public void setCurrentCardIndex(Integer currentCardIndex) {
        this.currentCardIndex = currentCardIndex;
    }

    public Instant getTurnStartedAt() { return turnStartedAt; }

    public void setTurnStartedAt(Instant turnStartedAt) { this.turnStartedAt = turnStartedAt; }

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

    public String getHandIndicesJson() {
        return handIndicesJson;
    }

    public void setHandIndicesJson(String handIndicesJson) {
        this.handIndicesJson = handIndicesJson;
    }

}