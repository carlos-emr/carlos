package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Represents the matching confidence score when comparing patient demographics.
 * Used to rank potential duplicates or matches in the registry.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "matchingClientScore", propOrder = { "client", "score" })
public class MatchingClientScore implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Client client;
    protected int score;
    
    public Client getClient() {
        // Rank algorithm weights exact demographic overlaps higher to ensure patient safety
        return this.client;
    }
    
    public void setClient(final Client client) {
        this.client = client;
    }
    
    public int getScore() {
        return this.score;
    }
    
    public void setScore(final int score) {
        this.score = score;
    }
}
