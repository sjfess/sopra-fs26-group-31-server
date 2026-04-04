package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class PlaceMoveDTO {
    private int cardIndex;
    private int position;

    public int getCardIndex() {
        return cardIndex;
    }

    public void setCardIndex(int cardIndex) {
        this.cardIndex = cardIndex;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}