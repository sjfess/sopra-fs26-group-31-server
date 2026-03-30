package ch.uzh.ifi.hase.soprafs26.constant;

/**
 * Defines the historical eras available for game sessions.
 * Each era maps to a year range used to filter Wikidata SPARQL queries.
 */
public enum HistoricalEra {

    ANCIENT(-3000, 500),
    MEDIEVAL(500, 1400),
    RENAISSANCE(1400, 1600),
    MODERN(1600, 1900),
    INFORMATION(1900, 2025);

    private final int startYear;
    private final int endYear;

    HistoricalEra(int startYear, int endYear) {
        this.startYear = startYear;
        this.endYear = endYear;
    }

    public int getStartYear() {
        return startYear;
    }

    public int getEndYear() {
        return endYear;
    }
}
