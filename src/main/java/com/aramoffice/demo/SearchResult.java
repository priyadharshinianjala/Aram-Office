package com.aramoffice.demo;

public class SearchResult {
    private final Document document;
    private final int matchCount;

    public SearchResult(Document document, int matchCount) {
        this.document = document;
        this.matchCount = matchCount;
    }

    public Document getDocument() {
        return document;
    }

    public int getMatchCount() {
        return matchCount;
    }
}
