/**
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.model;

import io.github.carlos_emr.Misc;

import java.util.Calendar;
import java.util.List;

@jakarta.persistence.Entity
@org.hibernate.annotations.Immutable
@jakarta.persistence.Table(name = "LookupCodeValue")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class LookupCodeValue {
    private String prefix;
    private String code;
    private String description;
    private String shortDesc;
    private String note;
    private boolean active;
    private boolean selectable;
    private String parentCode;
    private String buf1;
    private String codeTree;
    private String codecsv;
    private String buf2;
    private String lastUpdateUser;
    private Calendar lastUpdateDate;
    private String buf3;
    private String buf4;
    private String buf5;
    private String buf6;
    private String buf7;
    private String buf8;
    private String buf9;

    private int orderByIndex;
    private List associates;
    @jakarta.persistence.Transient

    public List getAssociates() {
        return associates;
    }

    public void setAssociates(List associates) {
        this.associates = associates;
    }
    @jakarta.persistence.Column(name = "active")

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.Column(name = "code")

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    @jakarta.persistence.Column(name = "codeTree")

    public String getCodeTree() {
        return codeTree;
    }

    public void setCodeTree(String codeTree) {
        this.codeTree = codeTree;
    }
    @jakarta.persistence.Column(name = "description")

    public String getDescription() {
        return description;
    }
    @jakarta.persistence.Transient

    public String getDescriptionJs() {
        return Misc.getStringJs(description);
    }

    public void setDescription(String description) {
        this.description = description;
    }
    @jakarta.persistence.Transient

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
    @jakarta.persistence.Column(name = "orderByIndex")

    public int getOrderByIndex() {
        return orderByIndex;
    }

    public void setOrderByIndex(int orderByIndex) {
        this.orderByIndex = orderByIndex;
    }
    @jakarta.persistence.Column(name = "parentCode")

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }
    @jakarta.persistence.Transient

    public boolean isSelectable() {
        return selectable;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }
    @jakarta.persistence.Transient

    public String getShortDesc() {
        return shortDesc;
    }

    public void setShortDesc(String shortDesc) {
        this.shortDesc = shortDesc;
    }
    @jakarta.persistence.Column(name = "prefix")

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    @jakarta.persistence.Transient

    public String getCodeId() {
        return this.prefix + ":" + this.code;
    }
    @jakarta.persistence.Column(name = "buf1")

    public String getBuf1() {
        return buf1;
    }

    public void setBuf1(String buf1) {
        this.buf1 = buf1;
    }
    @jakarta.persistence.Transient

    public String getBuf2() {
        return buf2;
    }

    public void setBuf2(String buf2) {
        this.buf2 = buf2;
    }
    @jakarta.persistence.Transient

    public Calendar getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Calendar lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }
    @jakarta.persistence.Transient

    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }
    @jakarta.persistence.Transient

    public String getBuf3() {
        return buf3;
    }

    public void setBuf3(String buf3) {
        this.buf3 = buf3;
    }
    @jakarta.persistence.Transient

    public String getBuf4() {
        return buf4;
    }

    public void setBuf4(String buf4) {
        this.buf4 = buf4;
    }
    @jakarta.persistence.Transient

    public String getBuf5() {
        return buf5;
    }

    public void setBuf5(String buf5) {
        this.buf5 = buf5;
    }
    @jakarta.persistence.Transient

    public String getBuf6() {
        return buf6;
    }

    public void setBuf6(String buf6) {
        this.buf6 = buf6;
    }
    @jakarta.persistence.Transient

    public String getBuf7() {
        return buf7;
    }

    public void setBuf7(String buf7) {
        this.buf7 = buf7;
    }
    @jakarta.persistence.Transient

    public String getBuf8() {
        return buf8;
    }

    public void setBuf8(String buf8) {
        this.buf8 = buf8;
    }
    @jakarta.persistence.Transient

    public String getBuf9() {
        return buf9;
    }

    public void setBuf9(String buf9) {
        this.buf9 = buf9;
    }
    @jakarta.persistence.Column(name = "codecsv")

    public String getCodecsv() {
        return codecsv;
    }

    public void setCodecsv(String codecsv) {
        this.codecsv = codecsv;
    }
}
