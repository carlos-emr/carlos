/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;

/** One of the twenty visits stored in the existing Growth 0–36m table. */
@Embeddable
public class Growth0To36Visit implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @Temporal(TemporalType.DATE)
    private Date date;
    private String age;
    private String weight;
    private String length;
    private String headCirc;
    private String comment;

    /** Returns the stored date. */
    public Date getDate() { return date; }
    /** Sets the stored date. */
    public void setDate(Date date) { this.date = date; }

    /** Returns the stored age. */
    public String getAge() { return age; }
    /** Sets the stored age. */
    public void setAge(String age) { this.age = age; }

    /** Returns the stored weight. */
    public String getWeight() { return weight; }
    /** Sets the stored weight. */
    public void setWeight(String weight) { this.weight = weight; }

    /** Returns the stored length. */
    public String getLength() { return length; }
    /** Sets the stored length. */
    public void setLength(String length) { this.length = length; }

    /** Returns the stored headCirc. */
    public String getHeadCirc() { return headCirc; }
    /** Sets the stored headCirc. */
    public void setHeadCirc(String headCirc) { this.headCirc = headCirc; }

    /** Returns the stored comment. */
    public String getComment() { return comment; }
    /** Sets the stored comment. */
    public void setComment(String comment) { this.comment = comment; }

}
