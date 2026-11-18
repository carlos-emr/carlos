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
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A saved eForm instance (an {@code eform_data} row) on the REST API.
 *
 * <p>The same shape serves as request and response. In a request, only {@code fid} and
 * {@code demographicNo} (create only), {@code subject} and {@code values} are read; every other
 * field is set by the server and ignored if sent. {@code values} maps field names to values: a
 * JSON {@code null} keeps the template default, an empty string clears it. {@code formHtml} is
 * filled only when a single instance is read.</p>
 *
 * @since 2026-11-17
 */
// A read response can be sent straight back as a revision; its server-set fields are ignored.
@JsonIgnoreProperties(ignoreUnknown = true)
public class EFormInstanceTo1 implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer fdid;
    private Integer fid;
    private Integer demographicNo;
    private String formName;
    private String subject;
    private String providerNo;
    private Date formDate;
    private Date formTime;
    private boolean current;
    private Map<String, String> values = new LinkedHashMap<>();
    private String formHtml;

    public Integer getFdid() {
        return fdid;
    }

    public void setFdid(Integer fdid) {
        this.fdid = fdid;
    }

    public Integer getFid() {
        return fid;
    }

    public void setFid(Integer fid) {
        this.fid = fid;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Date getFormDate() {
        return formDate;
    }

    public void setFormDate(Date formDate) {
        this.formDate = formDate;
    }

    public Date getFormTime() {
        return formTime;
    }

    public void setFormTime(Date formTime) {
        this.formTime = formTime;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }

    public String getFormHtml() {
        return formHtml;
    }

    public void setFormHtml(String formHtml) {
        this.formHtml = formHtml;
    }
}
