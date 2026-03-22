package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * JAXB DTO representing a client match result with an associated confidence score.
 *
 * <p>Used by the Health Number Registry (HNR) web service to return ranked client
 * matches when searching for patients by demographic criteria. Higher scores
 * indicate a stronger match.
 *
 * @since 2012-08-13
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "matchingClientScore", propOrder = { "client", "score" })
public class MatchingClientScore implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected Client client;
    protected int score;
    
    /**
     * Returns the matched client.
     *
     * @return Client the matched client record
     */
    public Client getClient() {
        return this.client;
    }
    
    /**
     * Sets the matched client.
     *
     * @param client Client the matched client record
     */
    public void setClient(final Client client) {
        this.client = client;
    }
    
    /**
     * Returns the match confidence score.
     *
     * @return int the confidence score where higher values indicate a stronger match
     */
    public int getScore() {
        return this.score;
    }
    
    /**
     * Sets the match confidence score.
     *
     * @param score int the confidence score to set
     */
    public void setScore(final int score) {
        this.score = score;
    }
}
