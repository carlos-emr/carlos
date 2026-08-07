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

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "SystemPreferences")
public class SystemPreferences extends AbstractModel<Integer> {
    //TODO upgrade to a nested Enum
    public enum RX_PREFERENCE_KEYS {
        rx_paste_provider_to_echart, rx_show_end_dates, rx_show_start_dates,
        rx_show_highest_allergy_warning,
        rx_show_refill_duration,
        rx_show_refill_quantity, rx_methadone_end_date_calc, save_rx_signature
    }

    public enum SCHEDULE_PREFERENCE_KEYS {schedule_display_type, schedule_display_custom_roster_status}

    public enum ECHART_PREFERENCE_KEYS {echart_hide_timer}

    public enum MASTER_FILE_PREFERENCE_KEYS {display_former_name, redirect_for_contact}

    public enum GENERAL_SETTINGS_KEYS {invoice_custom_clinic_info, invoice_use_custom_clinic_info}

    public enum LAB_DISPLAY_PREFERENCE_KEYS {code_show_hide_column, inboxDateSearchType}

    public enum EFORM_SETTINGS {rtl_template_document_type, patient_intake_eform, patient_intake_letter_eform}

    public enum REFERRAL_SOURCE_PREFERENCE_KEYS {enable_referral_source}

    public enum DOCUMENT_SETTINGS_KEYS {document_description_typeahead, inbox_use_fax_dropdown}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "`value`")
    private String value;

    @Column(name = "updateDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;

    public SystemPreferences() {
    }

    public SystemPreferences(final String name) {
        this.name = name;
        this.updateDate = new Date();
    }

    public SystemPreferences(String name, String value) {
        this.name = name;
        this.value = value;
        this.updateDate = new Date();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value != null ? value : "";
    }

    /**
     * Gets the system preference as a boolean
     *
     * @return true if value is "true", false otherwise
     */
    public Boolean getValueAsBoolean() {
        return "true".equals(value);
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }
}
