package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "matchingClientScore", propOrder = { "client", "score" })
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
