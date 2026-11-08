/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;

/**
 * One of the twenty fixed measurement slots in the existing growth table.
 *
 * @since 2026-11-06
 */
@Embeddable
public class Growth0To36Measurement implements Serializable {
    private static final long serialVersionUID = 1L;

    @Temporal(TemporalType.DATE)
    private Date date;
    private String age;
    private String weight;
    private String length;
    private String headCirc;
    private String comment;

    public Date getDate() { return date; }
    public void setDate(Date value) { date = value; }

    public String getAge() { return age; }
    public void setAge(String value) { age = value; }

    public String getWeight() { return weight; }
    public void setWeight(String value) { weight = value; }

    public String getLength() { return length; }
    public void setLength(String value) { length = value; }

    public String getHeadCirc() { return headCirc; }
    public void setHeadCirc(String value) { headCirc = value; }

    public String getComment() { return comment; }
    public void setComment(String value) { comment = value; }
}
