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
package io.github.carlos_emr.carlos.model.security;

import java.io.Serializable;
import java.util.Objects;

@jakarta.persistence.Entity
@org.hibernate.annotations.Immutable
@jakarta.persistence.Table(name = "v_user_access")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
@jakarta.persistence.IdClass(UserAccessValue.JpaId.class)
public class UserAccessValue implements Serializable {

    String providerNo;
    String orgCd;
    String orgCdcsv;
    String functionCd;
    String privilege;
    boolean orgApplicable;
    @jakarta.persistence.Column(name = "privilege")

    public String getPrivilege() {
        return privilege;
    }

    public void setPrivilege(String privilege) {
        this.privilege = privilege;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.Column(name = "objectname")

    public String getFunctionCd() {
        return functionCd;
    }

    public void setFunctionCd(String cd) {
        functionCd = cd;
    }
    @jakarta.persistence.Column(name = "orgapplicable")

    public boolean isOrgApplicable() {
        return orgApplicable;
    }

    public void setOrgApplicable(boolean orgApplicable) {
        this.orgApplicable = orgApplicable;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.Column(name = "orgcd")

    public String getOrgCd() {
        return orgCd;
    }

    public void setOrgCd(String cd) {
        orgCd = cd;
    }
    @jakarta.persistence.Column(name = "orgcdcsv")

    public String getOrgCdcsv() {
        return orgCdcsv;
    }

    public void setOrgCdcsv(String cdcsv) {
        orgCdcsv = cdcsv;
    }
    @jakarta.persistence.Column(name = "provider_no")

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public int hashCode() {
        return Objects.hash(functionCd, orgCd);
    }

    public boolean equals(Object uv) {
        if (this == uv) return true;
        if (!(uv instanceof UserAccessValue uv1)) return false;
        return Objects.equals(this.functionCd, uv1.functionCd) && Objects.equals(this.orgCd, uv1.orgCd);
    }

    public static class JpaId implements java.io.Serializable {
        public String functionCd;
        public String orgCd;

        public JpaId() {
        }

        public String getFunctionCd() {
            return functionCd;
        }

        public void setFunctionCd(String functionCd) {
            this.functionCd = functionCd;
        }

        public String getOrgCd() {
            return orgCd;
        }

        public void setOrgCd(String orgCd) {
            this.orgCd = orgCd;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JpaId other)) return false;
            return java.util.Objects.equals(functionCd, other.functionCd) && java.util.Objects.equals(orgCd, other.orgCd);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(functionCd, orgCd);
        }
    }
}
