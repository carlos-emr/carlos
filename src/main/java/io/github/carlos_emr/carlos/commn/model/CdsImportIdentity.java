/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Protected indexed preflight identity evidence; never expose as a public model. */
@Entity
@Table(name = "cds_import_identity")
public class CdsImportIdentity extends AbstractModel<String> {
    private static final long serialVersionUID = 1L;
    @Id @Column(name="id", length=64, nullable=false) private String id;
    @Column(name="run_id", length=36, nullable=false) private String runId;
    @Column(name="patient_key", length=64, nullable=false) private String patientKey;
    @Column(name="copy_key", length=64, nullable=false) private String copyKey;
    @Column(name="first_name", length=50, nullable=false) private String firstName;
    @Column(name="last_name", length=50, nullable=false) private String lastName;
    @Column(name="birth_date", nullable=false) private LocalDate birthDate;
    @Column(name="hin", length=20) private String hin;
    @Column(name="issuer", length=5) private String issuer;
    @Column(name="comparable", nullable=false) private boolean comparable = true;
    @Override public String getId() { return id; }
    public void setId(String value) { id=value; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId=value; }
    public String getPatientKey() { return patientKey; }
    public void setPatientKey(String value) { patientKey=value; }
    public String getCopyKey() { return copyKey; }
    public void setCopyKey(String value) { copyKey=value; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String value) { firstName=value; }
    public String getLastName() { return lastName; }
    public void setLastName(String value) { lastName=value; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate value) { birthDate=value; }
    public String getHin() { return hin; }
    public void setHin(String value) { hin=value; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String value) { issuer=value; }
    public boolean isComparable() { return comparable; }
    public void setComparable(boolean value) { comparable=value; }
    @Override public String toString() { return "CdsImportIdentity[protected]"; }
}
