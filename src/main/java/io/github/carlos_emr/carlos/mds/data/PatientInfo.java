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
 */


package io.github.carlos_emr.carlos.mds.data;

public class PatientInfo implements Comparable<PatientInfo> {
    private String firstName = "";
    private String lastName = "";
    private int id;
    private int docCount = 0;
    private int labCount = 0;
	private int hrmCount = 0;

    @Override
    public String toString() {
        // TODO Auto-generated method stub
        return lastName + ("".equals(lastName) ? "" : ", ") + firstName;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PatientInfo && ((PatientInfo) obj).id == this.id;
    }

    public PatientInfo(int id, String firstName, String lastName) {
        super();
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDocCount() {
        return docCount;
    }

    public void setDocCount(int docCount) {
        this.docCount = docCount;
    }

    public int getLabCount() {
        return labCount;
    }

    public void setLabCount(int labCount) {
        this.labCount += labCount;
    }
	public int getHrmCount() {
		return hrmCount;
	}
	public void setHrmCount(int hrmCount) {
		this.hrmCount += hrmCount;
	}
    @Override
    public int compareTo(PatientInfo that) {
        return this.lastName.equals(that.lastName) ? this.firstName.compareTo(that.firstName) : this.lastName.compareTo(that.lastName);
    }
}
