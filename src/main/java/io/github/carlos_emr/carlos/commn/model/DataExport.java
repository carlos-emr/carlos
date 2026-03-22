/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.commn.model;

import java.io.Serializable;
import jakarta.persistence.*;

import java.sql.Timestamp;


/**
 * Represents a data export record in the CARLOS EMR system.
 *
 * <p>Maps to the {@code dataExport} table and tracks data export operations
 * performed on clinical data. Each record captures the export file, type,
 * executing user, run timestamp, and contact information for the requestor.</p>
 *
 * @since 2001-01-01
 */
@Entity
@Table(name = "dataExport")
public class DataExport extends AbstractModel<Integer> implements Serializable, Comparable<DataExport> {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private Timestamp daterun;
    private String file;
    private String type;
    private String user;
    private String contactLName;
    private String contactFName;
    private String contactEmail;
    private String contactPhone;

    public DataExport() {
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public Integer getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Timestamp getDaterun() {
        return this.daterun;
    }

    public void setDaterun(Timestamp daterun) {
        this.daterun = daterun;
    }


    public String getFile() {
        return this.file;
    }

    public void setFile(String file) {
        this.file = file;
    }


    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }


    public String getUser() {
        return this.user;
    }

    public void setUser(String user) {
        this.user = user;
    }


    public String getContactLName() {
        return contactLName;
    }


    public void setContactLName(String contactLName) {
        this.contactLName = contactLName;
    }


    public String getContactFName() {
        return contactFName;
    }


    public void setContactFName(String contactFName) {
        this.contactFName = contactFName;
    }


    public String getContactEmail() {
        return contactEmail;
    }


    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }


    public String getContactPhone() {
        return contactPhone;
    }


    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }


    @Override
    public int compareTo(DataExport o) {
        return this.getDaterun().compareTo(o.getDaterun());
    }

}
