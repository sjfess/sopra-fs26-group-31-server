package ch.uzh.ifi.hase.soprafs26.rest.dto;

/**
 * Returned after a player places a card on the timeline.
 * Tells the client whether the placement was correct, the card's
 * actual year, and the current timeline size.
 */
public class PlacementResultDTO {

    private boolean correct;
    private String title;
    private int year;
    private String imageUrl;
    private int timelineSize;

    // Getters & setters

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getTimelineSize() { return timelineSize; }
    public void setTimelineSize(int timelineSize) { this.timelineSize = timelineSize; }
}
