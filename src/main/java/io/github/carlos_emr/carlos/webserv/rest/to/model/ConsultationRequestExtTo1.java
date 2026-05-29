package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.util.Date;

/**
 * Data transfer object carrying ConsultationRequestExtTo1 data across application boundaries without exposing internal domain logic.
 */
public class ConsultationRequestExtTo1 {
    private Integer id;
    private Integer requestId;
    private String key;
    private String value;
    private Date dateCreated;


    // Getid is exposed here to satisfy the external component interface contract without exposing internal state.
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRequestId() {
        return requestId;
    }

    public void setRequestId(Integer requestId) {
        this.requestId = requestId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }
}