/**
 * Copyright (c) 2023. Magenta Health Inc. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.inboxhub.query;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

public class InboxhubQuery {
    /**
     * Type filters for the inbox hub
     */
    public enum TypeFilter implements FilterEnum {
        ALL("all"),
        DOC("doc"),
        LAB("lab"),
        HRM("hrm");
    
        private final String value;
    
        TypeFilter(String value) {
            this.value = value;
        }
    
        @Override
        public String getValue() {
            return value;
        }
    
        public static TypeFilter fromValue(String value) {
            return FilterEnum.fromValue(value, TypeFilter.class, ALL);
        }
    }

    /**
     * Abnormal filters for the inbox hub
     */
    public enum AbnormalFilter implements FilterEnum {
        ALL("all"),
        NORMAL_ONLY("normalOnly"),
        ABNORMAL_ONLY("abnormalOnly");
    
        private final String value;
    
        AbnormalFilter(String value) {
            this.value = value;
        }
    
        @Override
        public String getValue() {
            return value;
        }
    
        public static AbnormalFilter fromValue(String value) {
            return FilterEnum.fromValue(value, AbnormalFilter.class, ALL);
        }
    }

    /**
     * Status filters for the inbox hub
     */
    public enum StatusFilter implements FilterEnum {
        ALL(""),
        NEW("N"),
        ACKNOWLEDGED("A"),
        FILED("F");
    
        private final String value;
    
        StatusFilter(String value) {
            this.value = value;
        }
    
        @Override
        public String getValue() {
            return value;
        }
    
        public static StatusFilter fromValue(String value) {
            return FilterEnum.fromValue(value, StatusFilter.class, ALL);
        }
    }

    /**
     * Provider search filters for the inbox hub
     */
    public enum ProviderSearchFilter implements FilterEnum {
        ANY_PROVIDER("true"),
        NO_PROVIDER("false"),
        SPECIFIC_PROVIDER("");
    
        private final String value;
    
        ProviderSearchFilter(String value) {
            this.value = value;
        }
    
        @Override
        public String getValue() {
            return value;
        }
    
        public static ProviderSearchFilter fromValue(String value) {
            return FilterEnum.fromValue(value, ProviderSearchFilter.class, SPECIFIC_PROVIDER);
        }
    }

    private Boolean viewMode = false;
    private Boolean clearFilters = false;
    private Boolean doc = false;
    private Boolean lab = false;
    private Boolean hrm = false;
    private Boolean unmatched = false;

    private String abnormal = AbnormalFilter.ALL.getValue();
    private String searchAll = ProviderSearchFilter.SPECIFIC_PROVIDER.getValue();
    private String searchProviderName = "";
    private String searchProviderNo = "-1";
    private String patientFirstName = "";
    private String patientLastName = "";
    private String patientHealthNumber = "";
    private String status = StatusFilter.NEW.getValue();
    private String startDate = "";
    private String endDate = "";
    private String demographicNo; 

    private int page;
    private int pageSize;

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public Boolean getUnmatched() {
        return unmatched;
    }

    public void setUnmatched(Boolean unmatched) {
        this.unmatched = unmatched;
    }

    public Boolean getClearFilters() {
        return clearFilters;
    }

    public void setClearFilters(Boolean clearFilters) {
        this.clearFilters = clearFilters;
    }

    public String getSearchProviderNo() {
        return searchProviderNo;
    }

    public String getSearchProviderName() {
        return searchProviderName;
    }

    public void setSearchAll(String searchAll) {
        this.searchAll = searchAll;
    }

    public String getSearchAll() {
        return searchAll;
    }

    public ProviderSearchFilter getProviderSearchFilter() {
        return ProviderSearchFilter.fromValue(searchAll);
    }

    public void setSearchProviderName(String searchProviderName) {
        this.searchProviderName = searchProviderName;
    }

    public void setSearchProviderNo(String searchProviderNo) {
        this.searchProviderNo = searchProviderNo;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    public void setPatientFirstName(String patientFirstName) {
        this.patientFirstName = patientFirstName;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    public void setPatientLastName(String patientLastName) {
        this.patientLastName = patientLastName;
    }

    public String getPatientHealthNumber() {
        return patientHealthNumber;
    }

    public void setPatientHealthNumber(String patientHealthNumber) {
        this.patientHealthNumber = patientHealthNumber;
    }

    public String getStatus() {
        return status;
    }

    public StatusFilter getStatusFilter() {
        return StatusFilter.fromValue(status);
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getDoc() {
        return doc;
    }

    public void setDoc(Boolean doc) {
        this.doc = doc;
    }

    public Boolean getLab() {
        return lab;
    }

    public void setLab(Boolean lab) {
        this.lab = lab;
    }

    public Boolean getHrm() {
        return hrm;
    }

    public void setHrm(Boolean hrm) {
        this.hrm = hrm;
    }

    public String getAbnormal() {
        return abnormal;
    }

    public void setAbnormal(String abnormal) {
        this.abnormal = abnormal;
    }

    public AbnormalFilter getAbnormalFilter() {
        return AbnormalFilter.fromValue(abnormal);
    }

    public Boolean getAbnormalBool() {
        return Objects.equals(AbnormalFilter.fromValue(abnormal), AbnormalFilter.ALL) ? null : Objects.equals(AbnormalFilter.fromValue(abnormal), AbnormalFilter.NORMAL_ONLY) ? false : true;
    }

    public Boolean getViewMode() {
        return viewMode;
    }

    public void setViewMode(Boolean viewMode) {
        this.viewMode = viewMode;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Reset all of the InboxhubQuery related variables
     * @param request
     */
    public void reset(HttpServletRequest request) {
        this.clearFilters = false;
        this.searchProviderName = "";
        this.searchProviderNo = "-1";
        this.demographicNo = null;
        this.patientFirstName = "";
        this.patientLastName = "";
        this.patientHealthNumber = "";
        this.status = StatusFilter.NEW.getValue();
        this.startDate = "";
        this.endDate = "";
        this.doc = false;
        this.lab = false;
        this.hrm = false;
        this.unmatched = false;
        this.abnormal = AbnormalFilter.ALL.getValue();
        this.searchAll = ProviderSearchFilter.SPECIFIC_PROVIDER.getValue();
        this.viewMode = false;
    }

    public interface FilterEnum {
        String getValue();
        
        static <T extends Enum<T> & FilterEnum> T fromValue(String value, Class<T> enumClass, T defaultValue) {
            if (value == null) {
                return defaultValue;
            }
            for (T filter : enumClass.getEnumConstants()) {
                if (filter.getValue().equals(value)) {
                    return filter;
                }
            }
            return defaultValue;
        }
    }
}