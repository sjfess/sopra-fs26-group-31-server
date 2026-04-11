package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;

import java.io.Serializable;

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

    @Column
    private Integer currentCardIndex;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(nullable = false)
    private Integer turnOrder;

    @Column(nullable = false)
    private Boolean activeTurn = false;

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
}