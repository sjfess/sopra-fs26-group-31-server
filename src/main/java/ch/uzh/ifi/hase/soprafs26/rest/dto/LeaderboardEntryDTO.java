package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class LeaderboardEntryDTO {

    private Integer rank;
    private Long userId;
    private String username;
    private Integer totalPoints;
    private Integer totalWins;
    private Integer totalGamesPlayed;
    private Integer totalCorrectPlacements;
    private Integer totalIncorrectPlacements;

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

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

    public Integer getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints;
    }

    public Integer getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(Integer totalWins) {
        this.totalWins = totalWins;
    }

    public Integer getTotalGamesPlayed() {
        return totalGamesPlayed;
    }

    public void setTotalGamesPlayed(Integer totalGamesPlayed) {
        this.totalGamesPlayed = totalGamesPlayed;
    }

    public Integer getTotalCorrectPlacements() {
        return totalCorrectPlacements;
    }

    public void setTotalCorrectPlacements(Integer totalCorrectPlacements) {
        this.totalCorrectPlacements = totalCorrectPlacements;
    }

    public Integer getTotalIncorrectPlacements() {
        return totalIncorrectPlacements;
    }

    public void setTotalIncorrectPlacements(Integer totalIncorrectPlacements) {
        this.totalIncorrectPlacements = totalIncorrectPlacements;
    }
}