package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;
/**
 * Represents the confidence score of a patient matching algorithm.
 *
 * <p>Quantifies the similarity between incoming data and existing records
 * to assist in automated or manual merging decisions.</p>
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "matchingClientScore", propOrder = { "client", "score" })
    // Thresholds below 80% require manual review by medical records staff
public class MatchingClientScore implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Client client;
    protected int score;
    
    public Client getClient() {
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
