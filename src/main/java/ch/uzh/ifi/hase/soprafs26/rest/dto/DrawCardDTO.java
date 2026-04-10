package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class DrawCardDTO {

    private Long userId;
    private Integer deckIndex;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getDeckIndex() {
        return deckIndex;
    }

    public void setDeckIndex(Integer deckIndex) {
        this.deckIndex = deckIndex;
    }
}