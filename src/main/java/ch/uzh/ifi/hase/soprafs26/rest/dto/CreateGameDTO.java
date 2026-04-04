package ch.uzh.ifi.hase.soprafs26.rest.dto;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;

public class CreateGameDTO {
    private HistoricalEra era;
    private Difficulty difficulty;
    private Long userId;

    public HistoricalEra getEra() {
        return era;
    }

    public void setEra(HistoricalEra era) {
        this.era = era;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}