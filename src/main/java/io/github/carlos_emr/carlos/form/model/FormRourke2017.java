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


package io.github.carlos_emr.carlos.form.model;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.form.FrmRecordHelp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Entity
@Table(name = "formRourke2017")
public class FormRourke2017 extends AbstractModel<Integer> implements Serializable, BooleanValueForm {
    public static final String FORM_TABLE = "formRourke2017";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;
    @Column(name = "provider_no")
    private String providerNo;
    @Column(name = "demographic_no")
    private Integer demographicNo;
    @Column(name = "c_male")
    private String cMale;
    @Column(name = "c_female")
    private String cFemale;
    @Temporal(TemporalType.DATE)
    private Date formCreated;
    private Timestamp formEdited;
    private Integer c_APGAR1min;
    private Integer c_APGAR5min;
    @Temporal(TemporalType.DATE)
    private Date c_birthDate;
    @Lob()
    private String c_birthRemarks;
    private String c_birthWeight;
    private String c_dischargeWeight;
    @Lob()
    private String c_famHistory;
    @Column(name = "c_fsa")
    private String cFsa;
    @Column(name = "start_of_gestation")
    private Date startOfGestation;
    private String c_headCirc;
    private String c_lastVisited;
    @Column(name = "c_length")
    private String cLength;
    private String c_pName;
    @Lob()
    private String c_riskFactors;
    @Temporal(TemporalType.DATE)
    @Column(name = "p1_date1m")
    private Date p1Date1m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p1_date1w")
    private Date p1Date1w;
    @Temporal(TemporalType.DATE)
    @Column(name = "p1_date2w")
    private Date p1Date2w;
    @Lob()
    @Column(name = "p1_development1m")
    private String p1Development1m;
    @Lob()
    @Column(name = "p1_development1w")
    private String p1Development1w;
    @Lob()
    @Column(name = "p1_development2w")
    private String p1Development2w;
    @Column(name = "p1_hc1m")
    private String p1Hc1m;
    @Column(name = "p1_hc1w")
    private String p1Hc1w;
    @Column(name = "p1_hc2w")
    private String p1Hc2w;
    @Column(name = "p1_ht1m")
    private String p1Ht1m;
    @Column(name = "p1_ht1w")
    private String p1Ht1w;
    @Column(name = "p1_ht2w")
    private String p1Ht2w;
    @Lob()
    @Column(name = "p1_immunization1m")
    private String p1Immunization1m;
    @Lob()
    @Column(name = "p1_immunization1w")
    private String p1Immunization1w;
    @Lob()
    @Column(name = "p1_immunization2w")
    private String p1Immunization2w;
    @Lob()
    private String p1_pConcern1m;
    @Lob()
    private String p1_pConcern1w;
    @Lob()
    private String p1_pConcern2w;
    @Lob()
    @Column(name = "p1_education1w")
    private String p1Education1w;
    @Lob()
    @Column(name = "p1_education2w")
    private String p1Education2w;
    @Lob()
    @Column(name = "p1_education1m")
    private String p1Education1m;
    @Lob()
    private String p1_pNutrition1m;
    @Lob()
    private String p1_pNutrition1w;
    @Lob()
    private String p1_pNutrition2w;
    @Lob()
    private String p1_pPhysical1m;
    @Lob()
    private String p1_pPhysical1w;
    @Lob()
    private String p1_pPhysical2w;
    @Lob()
    @Column(name = "p1_problems1m")
    private String p1Problems1m;
    @Lob()
    @Column(name = "p1_problems1w")
    private String p1Problems1w;
    @Lob()
    @Column(name = "p1_problems2w")
    private String p1Problems2w;
    @Column(name = "p1_signature1m")
    private String p1Signature1m;
    @Column(name = "p1_signature1w")
    private String p1Signature1w;
    @Column(name = "p1_signature2w")
    private String p1Signature2w;
    @Column(name = "p1_wt1m")
    private String p1Wt1m;
    @Column(name = "p1_wt1w")
    private String p1Wt1w;
    @Column(name = "p1_wt2w")
    private String p1Wt2w;
    @Temporal(TemporalType.DATE)
    @Column(name = "p2_date2m")
    private Date p2Date2m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p2_date4m")
    private Date p2Date4m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p2_date6m")
    private Date p2Date6m;
    @Lob()
    @Column(name = "p2_development2m")
    private String p2Development2m;
    @Lob()
    @Column(name = "p2_development4m")
    private String p2Development4m;
    @Lob()
    @Column(name = "p2_development6m")
    private String p2Development6m;
    @Lob()
    @Column(name = "p2_education2m")
    private String p2Education2m;
    @Column(name = "p2_education4m")
    private String p2Education4m;
    @Column(name = "p2_education6m")
    private String p2Education6m;
    @Column(name = "p2_hc2m")
    private String p2Hc2m;
    @Column(name = "p2_hc4m")
    private String p2Hc4m;
    @Column(name = "p2_hc6m")
    private String p2Hc6m;
    @Column(name = "p2_ht2m")
    private String p2Ht2m;
    @Column(name = "p2_ht4m")
    private String p2Ht4m;
    @Column(name = "p2_ht6m")
    private String p2Ht6m;
    @Lob()
    @Column(name = "p2_immunization6m")
    private String p2Immunization6m;
    @Lob()
    @Column(name = "p2_nutrition2m")
    private String p2Nutrition2m;
    @Lob()
    @Column(name = "p2_nutrition4m")
    private String p2Nutrition4m;
    @Lob()
    @Column(name = "p2_nutrition6m")
    private String p2Nutrition6m;
    @Lob()
    private String p2_pConcern2m;
    @Lob()
    private String p2_pConcern4m;
    @Lob()
    private String p2_pConcern6m;
    @Lob()
    @Column(name = "p2_physical2m")
    private String p2Physical2m;
    @Lob()
    @Column(name = "p2_physical4m")
    private String p2Physical4m;
    @Lob()
    @Column(name = "p2_physical6m")
    private String p2Physical6m;
    @Lob()
    @Column(name = "p2_problems2m")
    private String p2Problems2m;
    @Lob()
    @Column(name = "p2_problems4m")
    private String p2Problems4m;
    @Lob()
    @Column(name = "p2_problems6m")
    private String p2Problems6m;
    @Column(name = "p2_signature2m")
    private String p2Signature2m;
    @Column(name = "p2_signature4m")
    private String p2Signature4m;
    @Column(name = "p2_signature6m")
    private String p2Signature6m;
    @Column(name = "p2_wt2m")
    private String p2Wt2m;
    @Column(name = "p2_wt4m")
    private String p2Wt4m;
    @Column(name = "p2_wt6m")
    private String p2Wt6m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p3_date12m")
    private Date p3Date12m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p3_date15m")
    private Date p3Date15m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p3_date9m")
    private Date p3Date9m;
    @Lob()
    @Column(name = "p3_development12m")
    private String p3Development12m;
    @Lob()
    @Column(name = "p3_development15m")
    private String p3Development15m;
    @Lob()
    @Column(name = "p3_development9m")
    private String p3Development9m;
    @Lob()
    @Column(name = "p3_education9m")
    private String p3Education9m;
    @Column(name = "p3_education12m")
    private String p3Education12m;
    @Column(name = "p3_education15m")
    private String p3Education15m;
    @Column(name = "p3_hc12m")
    private String p3Hc12m;
    @Column(name = "p3_hc15m")
    private String p3Hc15m;
    @Column(name = "p3_hc9m")
    private String p3Hc9m;
    @Column(name = "p3_ht12m")
    private String p3Ht12m;
    @Column(name = "p3_ht15m")
    private String p3Ht15m;
    @Column(name = "p3_ht9m")
    private String p3Ht9m;
    @Lob()
    @Column(name = "p3_nutrition12m")
    private String p3Nutrition12m;
    @Lob()
    @Column(name = "p3_nutrition15m")
    private String p3Nutrition15m;
    @Lob()
    @Column(name = "p3_nutrition9m")
    private String p3Nutrition9m;
    @Lob()
    private String p3_pConcern12m;
    @Lob()
    private String p3_pConcern15m;
    @Lob()
    private String p3_pConcern9m;
    @Lob()
    @Column(name = "p3_physical12m")
    private String p3Physical12m;
    @Lob()
    @Column(name = "p3_physical15m")
    private String p3Physical15m;
    @Lob()
    @Column(name = "p3_physical9m")
    private String p3Physical9m;
    @Lob()
    @Column(name = "p3_problems12m")
    private String p3Problems12m;
    @Lob()
    @Column(name = "p3_problems15m")
    private String p3Problems15m;
    @Lob()
    @Column(name = "p3_problems9m")
    private String p3Problems9m;
    @Column(name = "p3_signature12m")
    private String p3Signature12m;
    @Column(name = "p3_signature15m")
    private String p3Signature15m;
    @Column(name = "p3_signature9m")
    private String p3Signature9m;
    @Column(name = "p3_wt12m")
    private String p3Wt12m;
    @Column(name = "p3_wt15m")
    private String p3Wt15m;
    @Column(name = "p3_wt9m")
    private String p3Wt9m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p4_date18m")
    private Date p4Date18m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p4_date24m")
    private Date p4Date24m;
    @Temporal(TemporalType.DATE)
    @Column(name = "p4_date48m")
    private Date p4Date48m;
    @Lob()
    @Column(name = "p4_development18m")
    private String p4Development18m;
    @Lob()
    @Column(name = "p4_development24m")
    private String p4Development24m;
    @Lob()
    @Column(name = "p4_development36m")
    private String p4Development36m;
    @Lob()
    @Column(name = "p4_development48m")
    private String p4Development48m;
    @Lob()
    @Column(name = "p4_development60m")
    private String p4Development60m;
    @Lob()
    @Column(name = "p4_education18m")
    private String p4Education18m;
    @Lob()
    @Column(name = "p4_education24m")
    private String p4Education24m;
    @Lob()
    @Column(name = "p4_education48m")
    private String p4Education48m;
    @Column(name = "p4_hc18m")
    private String p4Hc18m;
    @Column(name = "p4_hc24m")
    private String p4Hc24m;
    @Column(name = "p4_ht18m")
    private String p4Ht18m;
    @Column(name = "p4_ht24m")
    private String p4Ht24m;
    @Column(name = "p4_ht48m")
    private String p4Ht48m;
    @Column(name = "p4_bmi24m")
    private String p4Bmi24m;
    @Column(name = "p4_bmi48m")
    private String p4Bmi48m;
    @Lob()
    @Column(name = "p4_nippisingattained")
    private String p4Nippisingattained;
    @Lob()
    @Column(name = "p4_nutrition18m")
    private String p4Nutrition18m;
    @Lob()
    @Column(name = "p4_nutrition24m")
    private String p4Nutrition24m;
    @Lob()
    @Column(name = "p4_nutrition48m")
    private String p4Nutrition48m;
    @Lob()
    private String p4_pConcern18m;
    @Lob()
    private String p4_pConcern24m;
    @Lob()
    private String p4_pConcern48m;
    @Lob()
    @Column(name = "p4_physical18m")
    private String p4Physical18m;
    @Lob()
    @Column(name = "p4_physical24m")
    private String p4Physical24m;
    @Lob()
    @Column(name = "p4_physical48m")
    private String p4Physical48m;
    @Lob()
    @Column(name = "p4_problems18m")
    private String p4Problems18m;
    @Lob()
    @Column(name = "p4_problems24m")
    private String p4Problems24m;
    @Lob()
    @Column(name = "p4_problems48m")
    private String p4Problems48m;
    @Column(name = "p4_signature18m")
    private String p4Signature18m;
    @Column(name = "p4_signature24m")
    private String p4Signature24m;
    @Column(name = "p4_signature48m")
    private String p4Signature48m;
    @Column(name = "p4_wt18m")
    private String p4Wt18m;
    @Column(name = "p4_wt24m")
    private String p4Wt24m;
    @Column(name = "p4_wt48m")
    private String p4Wt48m;

    @Transient
    private Map<String, FormBooleanValue> booleanValueMap = new HashMap<String, FormBooleanValue>();

    public FormRourke2017() {
    }

    public FormRourke2017(Properties props) {
        FrmRecordHelp frmRecordHelp = new FrmRecordHelp();
        frmRecordHelp.setDateFormat("dd/MM/yyyy");

        this.providerNo = props.getProperty("provider_no");
        this.demographicNo = Integer.valueOf(props.getProperty("demographic_no"));
        this.cMale = props.getProperty("c_male", "");
        this.cFemale = props.getProperty("c_female", "");
        this.formCreated = frmRecordHelp.getDateFieldOrNull(props, "formCreated");
        this.formEdited = new Timestamp(new Date().getTime());
        this.c_APGAR1min = Integer.valueOf(props.getProperty("c_APGAR1min", "-1"));
        this.c_APGAR5min = Integer.valueOf(props.getProperty("c_APGAR5min", "-1"));
        this.c_birthDate = frmRecordHelp.getDateFieldOrNull(props, "c_birthDate");
        this.c_birthRemarks = props.getProperty("c_birthRemarks", "");
        this.c_birthWeight = props.getProperty("c_birthWeight", "");
        this.c_dischargeWeight = props.getProperty("c_dischargeWeight", "");
        this.c_famHistory = props.getProperty("c_famHistory", "");
        this.cFsa = props.getProperty("c_fsa", "");
        this.startOfGestation = frmRecordHelp.getDateFieldOrNull(props, "c_startOfGestation");
        this.c_headCirc = props.getProperty("c_headCirc", "");
        this.c_lastVisited = props.getProperty("c_lastVisited", "");
        this.cLength = props.getProperty("c_length", "");
        this.c_pName = props.getProperty("c_pName", "");
        this.c_riskFactors = props.getProperty("c_riskFactors", "");
        this.p1Date1m = frmRecordHelp.getDateFieldOrNull(props, "p1_date1m");
        this.p1Date1w = frmRecordHelp.getDateFieldOrNull(props, "p1_date1w");
        this.p1Date2w = frmRecordHelp.getDateFieldOrNull(props, "p1_date2w");
        this.p1Development1m = props.getProperty("p1_development1m", "");
        this.p1Development1w = props.getProperty("p1_development1w", "");
        this.p1Development2w = props.getProperty("p1_development2w", "");
        this.p1Hc1m = props.getProperty("p1_hc1m", "");
        this.p1Hc1w = props.getProperty("p1_hc1w", "");
        this.p1Hc2w = props.getProperty("p1_hc2w", "");
        this.p1Ht1m = props.getProperty("p1_ht1m", "");
        this.p1Ht1w = props.getProperty("p1_ht1w", "");
        this.p1Ht2w = props.getProperty("p1_ht2w", "");
        this.p1Immunization1m = props.getProperty("p1_immunization1m", "");
        this.p1Immunization1w = props.getProperty("p1_immunization1w", "");
        this.p1Immunization2w = props.getProperty("p1_immunization2w", "");
        this.p1_pConcern1m = props.getProperty("p1_pConcern1m", "");
        this.p1_pConcern1w = props.getProperty("p1_pConcern1w", "");
        this.p1_pConcern2w = props.getProperty("p1_pConcern2w", "");
        this.p1Education1w = props.getProperty("p1_education1w", "");
        this.p1Education2w = props.getProperty("p1_education2w", "");
        this.p1Education1m = props.getProperty("p1_education1m", "");
        this.p1_pNutrition1m = props.getProperty("p1_pNutrition1m", "");
        this.p1_pNutrition1w = props.getProperty("p1_pNutrition1w", "");
        this.p1_pNutrition2w = props.getProperty("p1_pNutrition2w", "");
        this.p1_pPhysical1m = props.getProperty("p1_pPhysical1m", "");
        this.p1_pPhysical1w = props.getProperty("p1_pPhysical1w", "");
        this.p1_pPhysical2w = props.getProperty("p1_pPhysical2w", "");
        this.p1Problems1m = props.getProperty("p1_problems1m", "");
        this.p1Problems1w = props.getProperty("p1_problems1w", "");
        this.p1Problems2w = props.getProperty("p1_problems2w", "");
        this.p1Signature1m = props.getProperty("p1_signature1m", "");
        this.p1Signature1w = props.getProperty("p1_signature1w", "");
        this.p1Signature2w = props.getProperty("p1_signature2w", "");
        this.p1Wt1m = props.getProperty("p1_wt1m", "");
        this.p1Wt1w = props.getProperty("p1_wt1w", "");
        this.p1Wt2w = props.getProperty("p1_wt2w", "");
        this.p2Date2m = frmRecordHelp.getDateFieldOrNull(props, "p2_date2m");
        this.p2Date4m = frmRecordHelp.getDateFieldOrNull(props, "p2_date4m");
        this.p2Date6m = frmRecordHelp.getDateFieldOrNull(props, "p2_date6m");
        this.p2Development2m = props.getProperty("p2_development2m", "");
        this.p2Development4m = props.getProperty("p2_development4m", "");
        this.p2Development6m = props.getProperty("p2_development6m", "");
        this.p2Education2m = props.getProperty("p2_education2m", "");
        this.p2Education4m = props.getProperty("p2_education4m", "");
        this.p2Education6m = props.getProperty("p2_education6m", "");
        this.p2Hc2m = props.getProperty("p2_hc2m", "");
        this.p2Hc4m = props.getProperty("p2_hc4m", "");
        this.p2Hc6m = props.getProperty("p2_hc6m", "");
        this.p2Ht2m = props.getProperty("p2_ht2m", "");
        this.p2Ht4m = props.getProperty("p2_ht4m", "");
        this.p2Ht6m = props.getProperty("p2_ht6m", "");
        this.p2Immunization6m = props.getProperty("p2_immunization6m", "");
        this.p2Nutrition2m = props.getProperty("p2_nutrition2m", "");
        this.p2Nutrition4m = props.getProperty("p2_nutrition4m", "");
        this.p2Nutrition6m = props.getProperty("p2_nutrition6m", "");
        this.p2_pConcern2m = props.getProperty("p2_pConcern2m", "");
        this.p2_pConcern4m = props.getProperty("p2_pConcern4m", "");
        this.p2_pConcern6m = props.getProperty("p2_pConcern6m", "");
        this.p2Physical2m = props.getProperty("p2_physical2m", "");
        this.p2Physical4m = props.getProperty("p2_physical4m", "");
        this.p2Physical6m = props.getProperty("p2_physical6m", "");
        this.p2Problems2m = props.getProperty("p2_problems2m", "");
        this.p2Problems4m = props.getProperty("p2_problems4m", "");
        this.p2Problems6m = props.getProperty("p2_problems6m", "");
        this.p2Signature2m = props.getProperty("p2_signature2m", "");
        this.p2Signature4m = props.getProperty("p2_signature4m", "");
        this.p2Signature6m = props.getProperty("p2_signature6m", "");
        this.p2Wt2m = props.getProperty("p2_wt2m", "");
        this.p2Wt4m = props.getProperty("p2_wt4m", "");
        this.p2Wt6m = props.getProperty("p2_wt6m", "");
        this.p3Date12m = frmRecordHelp.getDateFieldOrNull(props, "p3_date12m");
        this.p3Date15m = frmRecordHelp.getDateFieldOrNull(props, "p3_date15m");
        this.p3Date9m = frmRecordHelp.getDateFieldOrNull(props, "p3_date9m");
        this.p3Development12m = props.getProperty("p3_development12m", "");
        this.p3Development15m = props.getProperty("p3_development15m", "");
        this.p3Development9m = props.getProperty("p3_development9m", "");
        this.p3Education9m = props.getProperty("p3_education9m", "");
        this.p3Education12m = props.getProperty("p3_education12m", "");
        this.p3Education15m = props.getProperty("p3_education15m", "");
        this.p3Hc12m = props.getProperty("p3_hc12m", "");
        this.p3Hc15m = props.getProperty("p3_hc15m", "");
        this.p3Hc9m = props.getProperty("p3_hc9m", "");
        this.p3Ht12m = props.getProperty("p3_ht12m", "");
        this.p3Ht15m = props.getProperty("p3_ht15m", "");
        this.p3Ht9m = props.getProperty("p3_ht9m", "");
        this.p3Nutrition12m = props.getProperty("p3_nutrition12m", "");
        this.p3Nutrition15m = props.getProperty("p3_nutrition15m", "");
        this.p3Nutrition9m = props.getProperty("p3_nutrition9m", "");
        this.p3_pConcern12m = props.getProperty("p3_pConcern12m", "");
        this.p3_pConcern15m = props.getProperty("p3_pConcern15m", "");
        this.p3_pConcern9m = props.getProperty("p3_pConcern9m", "");
        this.p3Physical12m = props.getProperty("p3_physical12m", "");
        this.p3Physical15m = props.getProperty("p3_physical15m", "");
        this.p3Physical9m = props.getProperty("p3_physical9m", "");
        this.p3Problems12m = props.getProperty("p3_problems12m", "");
        this.p3Problems15m = props.getProperty("p3_problems15m", "");
        this.p3Problems9m = props.getProperty("p3_problems9m", "");
        this.p3Signature12m = props.getProperty("p3_signature12m", "");
        this.p3Signature15m = props.getProperty("p3_signature15m", "");
        this.p3Signature9m = props.getProperty("p3_signature9m", "");
        this.p3Wt12m = props.getProperty("p3_wt12m", "");
        this.p3Wt15m = props.getProperty("p3_wt15m", "");
        this.p3Wt9m = props.getProperty("p3_wt9m", "");
        this.p4Date18m = frmRecordHelp.getDateFieldOrNull(props, "p4_date18m");
        this.p4Date24m = frmRecordHelp.getDateFieldOrNull(props, "p4_date24m");
        this.p4Date48m = frmRecordHelp.getDateFieldOrNull(props, "p4_date48m");
        this.p4Development18m = props.getProperty("p4_development18m", "");
        this.p4Development24m = props.getProperty("p4_development24m", "");
        this.p4Development36m = props.getProperty("p4_development36m", "");
        this.p4Development48m = props.getProperty("p4_development48m", "");
        this.p4Development60m = props.getProperty("p4_development60m", "");
        this.p4Education18m = props.getProperty("p4_education18m", "");
        this.p4Education24m = props.getProperty("p4_education24m", "");
        this.p4Education48m = props.getProperty("p4_education48m", "");
        this.p4Hc18m = props.getProperty("p4_hc18m", "");
        this.p4Hc24m = props.getProperty("p4_hc24m", "");
        this.p4Ht18m = props.getProperty("p4_ht18m", "");
        this.p4Ht24m = props.getProperty("p4_ht24m", "");
        this.p4Ht48m = props.getProperty("p4_ht48m", "");
        this.p4Bmi24m = props.getProperty("p4_bmi24m", "");
        this.p4Bmi48m = props.getProperty("p4_bmi48m", "");
        this.p4Nippisingattained = props.getProperty("p4_nippisingattained", "");
        this.p4Nutrition18m = props.getProperty("p4_nutrition18m", "");
        this.p4Nutrition24m = props.getProperty("p4_nutrition24m", "");
        this.p4Nutrition48m = props.getProperty("p4_nutrition48m", "");
        this.p4_pConcern18m = props.getProperty("p4_pConcern18m", "");
        this.p4_pConcern24m = props.getProperty("p4_pConcern24m", "");
        this.p4_pConcern48m = props.getProperty("p4_pConcern48m", "");
        this.p4Physical18m = props.getProperty("p4_physical18m", "");
        this.p4Physical24m = props.getProperty("p4_physical24m", "");
        this.p4Physical48m = props.getProperty("p4_physical48m", "");
        this.p4Problems18m = props.getProperty("p4_problems18m", "");
        this.p4Problems24m = props.getProperty("p4_problems24m", "");
        this.p4Problems48m = props.getProperty("p4_problems48m", "");
        this.p4Signature18m = props.getProperty("p4_signature18m", "");
        this.p4Signature24m = props.getProperty("p4_signature24m", "");
        this.p4Signature48m = props.getProperty("p4_signature48m", "");
        this.p4Wt18m = props.getProperty("p4_wt18m", "");
        this.p4Wt24m = props.getProperty("p4_wt24m", "");
        this.p4Wt48m = props.getProperty("p4_wt48m", "");
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getProviderNo() {
        return this.providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public int getDemographicNo() {
        return this.demographicNo;
    }

    public void setDemographicNo(int demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getcMale() {
        return cMale;
    }

    public void setcMale(String cMale) {
        this.cMale = cMale;
    }

    public String getcFemale() {
        return cFemale;
    }

    public void setcFemale(String cFemale) {
        this.cFemale = cFemale;
    }

    public Date getFormCreated() {
        return this.formCreated;
    }

    public void setFormCreated(Date formCreated) {
        this.formCreated = formCreated;
    }

    public Timestamp getFormEdited() {
        return this.formEdited;
    }

    public void setFormEdited(Timestamp formEdited) {
        this.formEdited = formEdited;
    }

    public Map<String, FormBooleanValue> getBooleanValueMap() {
        return booleanValueMap;
    }

    public void setBooleanValueMap(Map<String, FormBooleanValue> booleanValueMap) {
        this.booleanValueMap = booleanValueMap;
    }

    public Boolean getBooleanValue(String fieldName) {
        if (booleanValueMap.containsKey(fieldName)) {
            return booleanValueMap.get(fieldName).getValue();
        }
        return null;
    }

    public Integer getC_APGAR1min() {
        return this.c_APGAR1min;
    }

    public void setC_APGAR1min(Integer c_APGAR1min) {
        this.c_APGAR1min = c_APGAR1min;
    }

    public Integer getC_APGAR5min() {
        return this.c_APGAR5min;
    }

    public void setC_APGAR5min(Integer c_APGAR5min) {
        this.c_APGAR5min = c_APGAR5min;
    }

    public Date getC_birthDate() {
        return this.c_birthDate;
    }

    public void setC_birthDate(Date c_birthDate) {
        this.c_birthDate = c_birthDate;
    }

    public String getC_birthRemarks() {
        return this.c_birthRemarks;
    }

    public void setC_birthRemarks(String c_birthRemarks) {
        this.c_birthRemarks = c_birthRemarks;
    }

    public String getC_birthWeight() {
        return this.c_birthWeight;
    }

    public void setC_birthWeight(String c_birthWeight) {
        this.c_birthWeight = c_birthWeight;
    }

    public String getC_dischargeWeight() {
        return this.c_dischargeWeight;
    }

    public void setC_dischargeWeight(String c_dischargeWeight) {
        this.c_dischargeWeight = c_dischargeWeight;
    }

    public String getC_famHistory() {
        return this.c_famHistory;
    }

    public void setC_famHistory(String c_famHistory) {
        this.c_famHistory = c_famHistory;
    }

    public String getCFsa() {
        return this.cFsa;
    }

    public void setCFsa(String cFsa) {
        this.cFsa = cFsa;
    }

    public Date getStartOfGestation() {
        return startOfGestation;
    }

    public void setStartOfGestation(Date startOfGestation) {
        this.startOfGestation = startOfGestation;
    }

    public String getC_headCirc() {
        return this.c_headCirc;
    }

    public void setC_headCirc(String c_headCirc) {
        this.c_headCirc = c_headCirc;
    }

    public String getC_lastVisited() {
        return this.c_lastVisited;
    }

    public void setC_lastVisited(String c_lastVisited) {
        this.c_lastVisited = c_lastVisited;
    }

    public String getCLength() {
        return this.cLength;
    }

    public void setCLength(String cLength) {
        this.cLength = cLength;
    }

    public String getC_pName() {
        return this.c_pName;
    }

    public void setC_pName(String c_pName) {
        this.c_pName = c_pName;
    }

    public String getC_riskFactors() {
        return this.c_riskFactors;
    }

    public void setC_riskFactors(String c_riskFactors) {
        this.c_riskFactors = c_riskFactors;
    }

    public Date getP1Date1m() {
        return this.p1Date1m;
    }

    public void setP1Date1m(Date p1Date1m) {
        this.p1Date1m = p1Date1m;
    }

    public Date getP1Date1w() {
        return this.p1Date1w;
    }

    public void setP1Date1w(Date p1Date1w) {
        this.p1Date1w = p1Date1w;
    }

    public Date getP1Date2w() {
        return this.p1Date2w;
    }

    public void setP1Date2w(Date p1Date2w) {
        this.p1Date2w = p1Date2w;
    }

    public String getP1Development1m() {
        return this.p1Development1m;
    }

    public void setP1Development1m(String p1Development1m) {
        this.p1Development1m = p1Development1m;
    }

    public String getP1Development1w() {
        return this.p1Development1w;
    }

    public void setP1Development1w(String p1Development1w) {
        this.p1Development1w = p1Development1w;
    }

    public String getP1Development2w() {
        return this.p1Development2w;
    }

    public void setP1Development2w(String p1Development2w) {
        this.p1Development2w = p1Development2w;
    }

    public String getP1Hc1m() {
        return this.p1Hc1m;
    }

    public void setP1Hc1m(String p1Hc1m) {
        this.p1Hc1m = p1Hc1m;
    }

    public String getP1Hc1w() {
        return this.p1Hc1w;
    }

    public void setP1Hc1w(String p1Hc1w) {
        this.p1Hc1w = p1Hc1w;
    }

    public String getP1Hc2w() {
        return this.p1Hc2w;
    }

    public void setP1Hc2w(String p1Hc2w) {
        this.p1Hc2w = p1Hc2w;
    }

    public String getP1Ht1m() {
        return this.p1Ht1m;
    }

    public void setP1Ht1m(String p1Ht1m) {
        this.p1Ht1m = p1Ht1m;
    }

    public String getP1Ht1w() {
        return this.p1Ht1w;
    }

    public void setP1Ht1w(String p1Ht1w) {
        this.p1Ht1w = p1Ht1w;
    }

    public String getP1Ht2w() {
        return this.p1Ht2w;
    }

    public void setP1Ht2w(String p1Ht2w) {
        this.p1Ht2w = p1Ht2w;
    }

    public String getP1Immunization1m() {
        return this.p1Immunization1m;
    }

    public void setP1Immunization1m(String p1Immunization1m) {
        this.p1Immunization1m = p1Immunization1m;
    }

    public String getP1Immunization1w() {
        return this.p1Immunization1w;
    }

    public void setP1Immunization1w(String p1Immunization1w) {
        this.p1Immunization1w = p1Immunization1w;
    }

    public String getP1Immunization2w() {
        return this.p1Immunization2w;
    }

    public void setP1Immunization2w(String p1Immunization2w) {
        this.p1Immunization2w = p1Immunization2w;
    }

    public String getP1_pConcern1m() {
        return this.p1_pConcern1m;
    }

    public void setP1_pConcern1m(String p1_pConcern1m) {
        this.p1_pConcern1m = p1_pConcern1m;
    }

    public String getP1_pConcern1w() {
        return this.p1_pConcern1w;
    }

    public void setP1_pConcern1w(String p1_pConcern1w) {
        this.p1_pConcern1w = p1_pConcern1w;
    }

    public String getP1_pConcern2w() {
        return this.p1_pConcern2w;
    }

    public void setP1_pConcern2w(String p1_pConcern2w) {
        this.p1_pConcern2w = p1_pConcern2w;
    }

    public String getP1Education1w() {
        return p1Education1w;
    }

    public void setP1Education1w(String p1Education1w) {
        this.p1Education1w = p1Education1w;
    }

    public String getP1Education2w() {
        return p1Education2w;
    }

    public void setP1Education2w(String p1Education2w) {
        this.p1Education2w = p1Education2w;
    }

    public String getP1Education1m() {
        return p1Education1m;
    }

    public void setP1Education1m(String p1Education1m) {
        this.p1Education1m = p1Education1m;
    }

    public String getP1_pNutrition1m() {
        return this.p1_pNutrition1m;
    }

    public void setP1_pNutrition1m(String p1_pNutrition1m) {
        this.p1_pNutrition1m = p1_pNutrition1m;
    }

    public String getP1_pNutrition1w() {
        return this.p1_pNutrition1w;
    }

    public void setP1_pNutrition1w(String p1_pNutrition1w) {
        this.p1_pNutrition1w = p1_pNutrition1w;
    }

    public String getP1_pNutrition2w() {
        return this.p1_pNutrition2w;
    }

    public void setP1_pNutrition2w(String p1_pNutrition2w) {
        this.p1_pNutrition2w = p1_pNutrition2w;
    }

    public String getP1_pPhysical1m() {
        return this.p1_pPhysical1m;
    }

    public void setP1_pPhysical1m(String p1_pPhysical1m) {
        this.p1_pPhysical1m = p1_pPhysical1m;
    }

    public String getP1_pPhysical1w() {
        return this.p1_pPhysical1w;
    }

    public void setP1_pPhysical1w(String p1_pPhysical1w) {
        this.p1_pPhysical1w = p1_pPhysical1w;
    }

    public String getP1_pPhysical2w() {
        return this.p1_pPhysical2w;
    }

    public void setP1_pPhysical2w(String p1_pPhysical2w) {
        this.p1_pPhysical2w = p1_pPhysical2w;
    }

    public String getP1Problems1m() {
        return this.p1Problems1m;
    }

    public void setP1Problems1m(String p1Problems1m) {
        this.p1Problems1m = p1Problems1m;
    }

    public String getP1Problems1w() {
        return this.p1Problems1w;
    }

    public void setP1Problems1w(String p1Problems1w) {
        this.p1Problems1w = p1Problems1w;
    }

    public String getP1Problems2w() {
        return this.p1Problems2w;
    }

    public void setP1Problems2w(String p1Problems2w) {
        this.p1Problems2w = p1Problems2w;
    }

    public String getP1Signature1m() {
        return this.p1Signature1m;
    }

    public void setP1Signature1m(String p1Signature1m) {
        this.p1Signature1m = p1Signature1m;
    }

    public String getP1Signature1w() {
        return this.p1Signature1w;
    }

    public void setP1Signature1w(String p1Signature1w) {
        this.p1Signature1w = p1Signature1w;
    }

    public String getP1Signature2w() {
        return this.p1Signature2w;
    }

    public void setP1Signature2w(String p1Signature2w) {
        this.p1Signature2w = p1Signature2w;
    }

    public String getP1Wt1m() {
        return this.p1Wt1m;
    }

    public void setP1Wt1m(String p1Wt1m) {
        this.p1Wt1m = p1Wt1m;
    }

    public String getP1Wt1w() {
        return this.p1Wt1w;
    }

    public void setP1Wt1w(String p1Wt1w) {
        this.p1Wt1w = p1Wt1w;
    }

    public String getP1Wt2w() {
        return this.p1Wt2w;
    }

    public void setP1Wt2w(String p1Wt2w) {
        this.p1Wt2w = p1Wt2w;
    }

    public Date getP2Date2m() {
        return this.p2Date2m;
    }

    public void setP2Date2m(Date p2Date2m) {
        this.p2Date2m = p2Date2m;
    }

    public Date getP2Date4m() {
        return this.p2Date4m;
    }

    public void setP2Date4m(Date p2Date4m) {
        this.p2Date4m = p2Date4m;
    }

    public Date getP2Date6m() {
        return this.p2Date6m;
    }

    public void setP2Date6m(Date p2Date6m) {
        this.p2Date6m = p2Date6m;
    }

    public String getP2Development2m() {
        return this.p2Development2m;
    }

    public void setP2Development2m(String p2Development2m) {
        this.p2Development2m = p2Development2m;
    }

    public String getP2Development4m() {
        return this.p2Development4m;
    }

    public void setP2Development4m(String p2Development4m) {
        this.p2Development4m = p2Development4m;
    }

    public String getP2Development6m() {
        return this.p2Development6m;
    }

    public void setP2Development6m(String p2Development6m) {
        this.p2Development6m = p2Development6m;
    }


    public String getP2Education2m() {
        return p2Education2m;
    }

    public void setP2Education2m(String p2Education2m) {
        this.p2Education2m = p2Education2m;
    }

    public String getP2Education4m() {
        return p2Education4m;
    }

    public void setP2Education4m(String p2Education4m) {
        this.p2Education4m = p2Education4m;
    }

    public String getP2Education6m() {
        return p2Education6m;
    }

    public void setP2Education6m(String p2Education6m) {
        this.p2Education6m = p2Education6m;
    }

    public String getP2Hc2m() {
        return this.p2Hc2m;
    }

    public void setP2Hc2m(String p2Hc2m) {
        this.p2Hc2m = p2Hc2m;
    }

    public String getP2Hc4m() {
        return this.p2Hc4m;
    }

    public void setP2Hc4m(String p2Hc4m) {
        this.p2Hc4m = p2Hc4m;
    }

    public String getP2Hc6m() {
        return this.p2Hc6m;
    }

    public void setP2Hc6m(String p2Hc6m) {
        this.p2Hc6m = p2Hc6m;
    }

    public String getP2Ht2m() {
        return this.p2Ht2m;
    }

    public void setP2Ht2m(String p2Ht2m) {
        this.p2Ht2m = p2Ht2m;
    }

    public String getP2Ht4m() {
        return this.p2Ht4m;
    }

    public void setP2Ht4m(String p2Ht4m) {
        this.p2Ht4m = p2Ht4m;
    }

    public String getP2Ht6m() {
        return this.p2Ht6m;
    }

    public void setP2Ht6m(String p2Ht6m) {
        this.p2Ht6m = p2Ht6m;
    }

    public String getP2Immunization6m() {
        return this.p2Immunization6m;
    }

    public void setP2Immunization6m(String p2Immunization6m) {
        this.p2Immunization6m = p2Immunization6m;
    }

    public String getP2Nutrition2m() {
        return this.p2Nutrition2m;
    }

    public void setP2Nutrition2m(String p2Nutrition2m) {
        this.p2Nutrition2m = p2Nutrition2m;
    }

    public String getP2Nutrition4m() {
        return this.p2Nutrition4m;
    }

    public void setP2Nutrition4m(String p2Nutrition4m) {
        this.p2Nutrition4m = p2Nutrition4m;
    }

    public String getP2Nutrition6m() {
        return this.p2Nutrition6m;
    }

    public void setP2Nutrition6m(String p2Nutrition6m) {
        this.p2Nutrition6m = p2Nutrition6m;
    }

    public String getP2_pConcern2m() {
        return this.p2_pConcern2m;
    }

    public void setP2_pConcern2m(String p2_pConcern2m) {
        this.p2_pConcern2m = p2_pConcern2m;
    }

    public String getP2_pConcern4m() {
        return this.p2_pConcern4m;
    }

    public void setP2_pConcern4m(String p2_pConcern4m) {
        this.p2_pConcern4m = p2_pConcern4m;
    }

    public String getP2_pConcern6m() {
        return this.p2_pConcern6m;
    }

    public void setP2_pConcern6m(String p2_pConcern6m) {
        this.p2_pConcern6m = p2_pConcern6m;
    }

    public String getP2Physical2m() {
        return this.p2Physical2m;
    }

    public void setP2Physical2m(String p2Physical2m) {
        this.p2Physical2m = p2Physical2m;
    }

    public String getP2Physical4m() {
        return this.p2Physical4m;
    }

    public void setP2Physical4m(String p2Physical4m) {
        this.p2Physical4m = p2Physical4m;
    }

    public String getP2Physical6m() {
        return this.p2Physical6m;
    }

    public void setP2Physical6m(String p2Physical6m) {
        this.p2Physical6m = p2Physical6m;
    }

    public String getP2Problems2m() {
        return this.p2Problems2m;
    }

    public void setP2Problems2m(String p2Problems2m) {
        this.p2Problems2m = p2Problems2m;
    }

    public String getP2Problems4m() {
        return this.p2Problems4m;
    }

    public void setP2Problems4m(String p2Problems4m) {
        this.p2Problems4m = p2Problems4m;
    }

    public String getP2Problems6m() {
        return this.p2Problems6m;
    }

    public void setP2Problems6m(String p2Problems6m) {
        this.p2Problems6m = p2Problems6m;
    }

    public String getP2Signature2m() {
        return this.p2Signature2m;
    }

    public void setP2Signature2m(String p2Signature2m) {
        this.p2Signature2m = p2Signature2m;
    }

    public String getP2Signature4m() {
        return this.p2Signature4m;
    }

    public void setP2Signature4m(String p2Signature4m) {
        this.p2Signature4m = p2Signature4m;
    }

    public String getP2Signature6m() {
        return this.p2Signature6m;
    }

    public void setP2Signature6m(String p2Signature6m) {
        this.p2Signature6m = p2Signature6m;
    }

    public String getP2Wt2m() {
        return this.p2Wt2m;
    }

    public void setP2Wt2m(String p2Wt2m) {
        this.p2Wt2m = p2Wt2m;
    }

    public String getP2Wt4m() {
        return this.p2Wt4m;
    }

    public void setP2Wt4m(String p2Wt4m) {
        this.p2Wt4m = p2Wt4m;
    }

    public String getP2Wt6m() {
        return this.p2Wt6m;
    }

    public void setP2Wt6m(String p2Wt6m) {
        this.p2Wt6m = p2Wt6m;
    }

    public Date getP3Date12m() {
        return this.p3Date12m;
    }

    public void setP3Date12m(Date p3Date12m) {
        this.p3Date12m = p3Date12m;
    }

    public Date getP3Date15m() {
        return this.p3Date15m;
    }

    public void setP3Date15m(Date p3Date15m) {
        this.p3Date15m = p3Date15m;
    }

    public Date getP3Date9m() {
        return this.p3Date9m;
    }

    public void setP3Date9m(Date p3Date9m) {
        this.p3Date9m = p3Date9m;
    }

    public String getP3Development12m() {
        return this.p3Development12m;
    }

    public void setP3Development12m(String p3Development12m) {
        this.p3Development12m = p3Development12m;
    }

    public String getP3Development15m() {
        return this.p3Development15m;
    }

    public void setP3Development15m(String p3Development15m) {
        this.p3Development15m = p3Development15m;
    }

    public String getP3Development9m() {
        return this.p3Development9m;
    }

    public void setP3Development9m(String p3Development9m) {
        this.p3Development9m = p3Development9m;
    }

    public String getP3Education9m() {
        return p3Education9m;
    }

    public void setP3Education9m(String p3Education9m) {
        this.p3Education9m = p3Education9m;
    }

    public String getP3Education12m() {
        return p3Education12m;
    }

    public void setP3Education12m(String p3Education12m) {
        this.p3Education12m = p3Education12m;
    }

    public String getP3Education15m() {
        return p3Education15m;
    }

    public void setP3Education15m(String p3Education15m) {
        this.p3Education15m = p3Education15m;
    }

    public String getP3Hc12m() {
        return this.p3Hc12m;
    }

    public void setP3Hc12m(String p3Hc12m) {
        this.p3Hc12m = p3Hc12m;
    }

    public String getP3Hc15m() {
        return this.p3Hc15m;
    }

    public void setP3Hc15m(String p3Hc15m) {
        this.p3Hc15m = p3Hc15m;
    }

    public String getP3Hc9m() {
        return this.p3Hc9m;
    }

    public void setP3Hc9m(String p3Hc9m) {
        this.p3Hc9m = p3Hc9m;
    }

    public String getP3Ht12m() {
        return this.p3Ht12m;
    }

    public void setP3Ht12m(String p3Ht12m) {
        this.p3Ht12m = p3Ht12m;
    }

    public String getP3Ht15m() {
        return this.p3Ht15m;
    }

    public void setP3Ht15m(String p3Ht15m) {
        this.p3Ht15m = p3Ht15m;
    }

    public String getP3Ht9m() {
        return this.p3Ht9m;
    }

    public void setP3Ht9m(String p3Ht9m) {
        this.p3Ht9m = p3Ht9m;
    }

    public String getP3Nutrition12m() {
        return this.p3Nutrition12m;
    }

    public void setP3Nutrition12m(String p3Nutrition12m) {
        this.p3Nutrition12m = p3Nutrition12m;
    }

    public String getP3Nutrition15m() {
        return this.p3Nutrition15m;
    }

    public void setP3Nutrition15m(String p3Nutrition15m) {
        this.p3Nutrition15m = p3Nutrition15m;
    }

    public String getP3Nutrition9m() {
        return this.p3Nutrition9m;
    }

    public void setP3Nutrition9m(String p3Nutrition9m) {
        this.p3Nutrition9m = p3Nutrition9m;
    }

    public String getP3_pConcern12m() {
        return this.p3_pConcern12m;
    }

    public void setP3_pConcern12m(String p3_pConcern12m) {
        this.p3_pConcern12m = p3_pConcern12m;
    }

    public String getP3_pConcern15m() {
        return this.p3_pConcern15m;
    }

    public void setP3_pConcern15m(String p3_pConcern15m) {
        this.p3_pConcern15m = p3_pConcern15m;
    }

    public String getP3_pConcern9m() {
        return this.p3_pConcern9m;
    }

    public void setP3_pConcern9m(String p3_pConcern9m) {
        this.p3_pConcern9m = p3_pConcern9m;
    }

    public String getP3Physical12m() {
        return this.p3Physical12m;
    }

    public void setP3Physical12m(String p3Physical12m) {
        this.p3Physical12m = p3Physical12m;
    }

    public String getP3Physical15m() {
        return this.p3Physical15m;
    }

    public void setP3Physical15m(String p3Physical15m) {
        this.p3Physical15m = p3Physical15m;
    }

    public String getP3Physical9m() {
        return this.p3Physical9m;
    }

    public void setP3Physical9m(String p3Physical9m) {
        this.p3Physical9m = p3Physical9m;
    }

    public String getP3Problems12m() {
        return this.p3Problems12m;
    }

    public void setP3Problems12m(String p3Problems12m) {
        this.p3Problems12m = p3Problems12m;
    }

    public String getP3Problems15m() {
        return this.p3Problems15m;
    }

    public void setP3Problems15m(String p3Problems15m) {
        this.p3Problems15m = p3Problems15m;
    }

    public String getP3Problems9m() {
        return this.p3Problems9m;
    }

    public void setP3Problems9m(String p3Problems9m) {
        this.p3Problems9m = p3Problems9m;
    }

    public String getP3Signature12m() {
        return this.p3Signature12m;
    }

    public void setP3Signature12m(String p3Signature12m) {
        this.p3Signature12m = p3Signature12m;
    }

    public String getP3Signature15m() {
        return this.p3Signature15m;
    }

    public void setP3Signature15m(String p3Signature15m) {
        this.p3Signature15m = p3Signature15m;
    }

    public String getP3Signature9m() {
        return this.p3Signature9m;
    }

    public void setP3Signature9m(String p3Signature9m) {
        this.p3Signature9m = p3Signature9m;
    }

    public String getP3Wt12m() {
        return this.p3Wt12m;
    }

    public void setP3Wt12m(String p3Wt12m) {
        this.p3Wt12m = p3Wt12m;
    }

    public String getP3Wt15m() {
        return this.p3Wt15m;
    }

    public void setP3Wt15m(String p3Wt15m) {
        this.p3Wt15m = p3Wt15m;
    }

    public String getP3Wt9m() {
        return this.p3Wt9m;
    }

    public void setP3Wt9m(String p3Wt9m) {
        this.p3Wt9m = p3Wt9m;
    }

    public Date getP4Date18m() {
        return this.p4Date18m;
    }

    public void setP4Date18m(Date p4Date18m) {
        this.p4Date18m = p4Date18m;
    }

    public Date getP4Date24m() {
        return this.p4Date24m;
    }

    public void setP4Date24m(Date p4Date24m) {
        this.p4Date24m = p4Date24m;
    }

    public Date getP4Date48m() {
        return this.p4Date48m;
    }

    public void setP4Date48m(Date p4Date48m) {
        this.p4Date48m = p4Date48m;
    }

    public String getP4Development18m() {
        return this.p4Development18m;
    }

    public void setP4Development18m(String p4Development18m) {
        this.p4Development18m = p4Development18m;
    }

    public String getP4Development24m() {
        return this.p4Development24m;
    }

    public void setP4Development24m(String p4Development24m) {
        this.p4Development24m = p4Development24m;
    }

    public String getP4Development36m() {
        return this.p4Development36m;
    }

    public void setP4Development36m(String p4Development36m) {
        this.p4Development36m = p4Development36m;
    }

    public String getP4Development48m() {
        return this.p4Development48m;
    }

    public void setP4Development48m(String p4Development48m) {
        this.p4Development48m = p4Development48m;
    }

    public String getP4Development60m() {
        return this.p4Development60m;
    }

    public void setP4Development60m(String p4Development60m) {
        this.p4Development60m = p4Development60m;
    }

    public String getP4Education18m() {
        return this.p4Education18m;
    }

    public void setP4Education18m(String p4Education18m) {
        this.p4Education18m = p4Education18m;
    }

    public String getP4Education24m() {
        return p4Education24m;
    }

    public void setP4Education24m(String p4Education24m) {
        this.p4Education24m = p4Education24m;
    }

    public String getP4Education48m() {
        return this.p4Education48m;
    }

    public void setP4Education48m(String p4Education48m) {
        this.p4Education48m = p4Education48m;
    }

    public String getP4Hc18m() {
        return this.p4Hc18m;
    }

    public void setP4Hc18m(String p4Hc18m) {
        this.p4Hc18m = p4Hc18m;
    }

    public String getP4Hc24m() {
        return this.p4Hc24m;
    }

    public void setP4Hc24m(String p4Hc24m) {
        this.p4Hc24m = p4Hc24m;
    }

    public String getP4Ht18m() {
        return this.p4Ht18m;
    }

    public void setP4Ht18m(String p4Ht18m) {
        this.p4Ht18m = p4Ht18m;
    }

    public String getP4Ht24m() {
        return this.p4Ht24m;
    }

    public void setP4Ht24m(String p4Ht24m) {
        this.p4Ht24m = p4Ht24m;
    }

    public String getP4Ht48m() {
        return this.p4Ht48m;
    }

    public void setP4Ht48m(String p4Ht48m) {
        this.p4Ht48m = p4Ht48m;
    }

    public String getP4Bmi24m() {
        return p4Bmi24m;
    }

    public void setP4Bmi24m(String p4Bmi24m) {
        this.p4Bmi24m = p4Bmi24m;
    }

    public String getP4Bmi48m() {
        return p4Bmi48m;
    }

    public void setP4Bmi48m(String p4Bmi48m) {
        this.p4Bmi48m = p4Bmi48m;
    }

    public String getP4Nippisingattained() {
        return this.p4Nippisingattained;
    }

    public void setP4Nippisingattained(String p4Nippisingattained) {
        this.p4Nippisingattained = p4Nippisingattained;
    }

    public String getP4Nutrition18m() {
        return this.p4Nutrition18m;
    }

    public void setP4Nutrition18m(String p4Nutrition18m) {
        this.p4Nutrition18m = p4Nutrition18m;
    }

    public String getP4Nutrition24m() {
        return this.p4Nutrition24m;
    }

    public void setP4Nutrition24m(String p4Nutrition24m) {
        this.p4Nutrition24m = p4Nutrition24m;
    }

    public String getP4Nutrition48m() {
        return this.p4Nutrition48m;
    }

    public void setP4Nutrition48m(String p4Nutrition48m) {
        this.p4Nutrition48m = p4Nutrition48m;
    }

    public String getP4_pConcern18m() {
        return this.p4_pConcern18m;
    }

    public void setP4_pConcern18m(String p4_pConcern18m) {
        this.p4_pConcern18m = p4_pConcern18m;
    }

    public String getP4_pConcern24m() {
        return this.p4_pConcern24m;
    }

    public void setP4_pConcern24m(String p4_pConcern24m) {
        this.p4_pConcern24m = p4_pConcern24m;
    }

    public String getP4_pConcern48m() {
        return this.p4_pConcern48m;
    }

    public void setP4_pConcern48m(String p4_pConcern48m) {
        this.p4_pConcern48m = p4_pConcern48m;
    }

    public String getP4Physical18m() {
        return this.p4Physical18m;
    }

    public void setP4Physical18m(String p4Physical18m) {
        this.p4Physical18m = p4Physical18m;
    }

    public String getP4Physical24m() {
        return this.p4Physical24m;
    }

    public void setP4Physical24m(String p4Physical24m) {
        this.p4Physical24m = p4Physical24m;
    }

    public String getP4Physical48m() {
        return this.p4Physical48m;
    }

    public void setP4Physical48m(String p4Physical48m) {
        this.p4Physical48m = p4Physical48m;
    }

    public String getP4Problems18m() {
        return this.p4Problems18m;
    }

    public void setP4Problems18m(String p4Problems18m) {
        this.p4Problems18m = p4Problems18m;
    }

    public String getP4Problems24m() {
        return this.p4Problems24m;
    }

    public void setP4Problems24m(String p4Problems24m) {
        this.p4Problems24m = p4Problems24m;
    }

    public String getP4Problems48m() {
        return this.p4Problems48m;
    }

    public void setP4Problems48m(String p4Problems48m) {
        this.p4Problems48m = p4Problems48m;
    }

    public String getP4Signature18m() {
        return this.p4Signature18m;
    }

    public void setP4Signature18m(String p4Signature18m) {
        this.p4Signature18m = p4Signature18m;
    }

    public String getP4Signature24m() {
        return this.p4Signature24m;
    }

    public void setP4Signature24m(String p4Signature24m) {
        this.p4Signature24m = p4Signature24m;
    }

    public String getP4Signature48m() {
        return this.p4Signature48m;
    }

    public void setP4Signature48m(String p4Signature48m) {
        this.p4Signature48m = p4Signature48m;
    }

    public String getP4Wt18m() {
        return this.p4Wt18m;
    }

    public void setP4Wt18m(String p4Wt18m) {
        this.p4Wt18m = p4Wt18m;
    }

    public String getP4Wt24m() {
        return this.p4Wt24m;
    }

    public void setP4Wt24m(String p4Wt24m) {
        this.p4Wt24m = p4Wt24m;
    }

    public String getP4Wt48m() {
        return this.p4Wt48m;
    }

    public void setP4Wt48m(String p4Wt48m) {
        this.p4Wt48m = p4Wt48m;
    }

    public String getFormTable() {
        return FORM_TABLE;
    }

    /**
     * Sets the booleanValueMap using values taken from the provided Properties object
     *
     * @param properties
     */
    public void createBooleanValueMapFromFormProperties(Properties properties) {
        HashMap<String, FormBooleanValue> booleanMap = new HashMap<String, FormBooleanValue>();
        // Look over every known radiobutton/checkbox field with each permutation of a suffix
        for (String fieldName : FormRourke2017Constants.BOOLEAN_FIELD_NAMES) {
            for (String suffix : FormRourke2017Constants.BOOLEAN_FIELD_SUFFIXES) {
                String fullFieldName = fieldName + suffix;
                String value = properties.getProperty(fullFieldName, null);
                if (value != null) {
                    FormBooleanValue valueObject = new FormBooleanValue(FORM_TABLE, id, fullFieldName, value.equalsIgnoreCase("on") || value.equalsIgnoreCase("checked='checked'"));
                    booleanMap.put(fullFieldName, valueObject);
                }

            }
        }
        setBooleanValueMap(booleanMap);
    }

    public Properties toProperties() {
        FrmRecordHelp frmRecordHelp = new FrmRecordHelp();
        frmRecordHelp.setDateFormat("dd/MM/yyyy");

        Properties props = new Properties();
        props.setProperty("provider_no", getProviderNo());
        props.setProperty("demographic_no", String.valueOf(getDemographicNo()));
        props.setProperty("c_male", getcMale());
        props.setProperty("c_female", getcFemale());
        props.setProperty("formCreated", frmRecordHelp.parseDateFieldOrNull(getFormCreated()));
        props.setProperty("formEdited", new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(getFormEdited()));
        props.setProperty("c_APGAR1min", String.valueOf(getC_APGAR1min()));
        props.setProperty("c_APGAR5min", String.valueOf(getC_APGAR5min()));
        if (getC_birthDate() != null) {
            props.setProperty("c_birthDate", frmRecordHelp.parseDateFieldOrNull(getC_birthDate()));
        }
        props.setProperty("c_birthRemarks", getC_birthRemarks());
        props.setProperty("c_birthWeight", getC_birthWeight());
        props.setProperty("c_dischargeWeight", getC_dischargeWeight());
        props.setProperty("c_famHistory", getC_famHistory());
        props.setProperty("c_fsa", getCFsa());
        if (getStartOfGestation() != null) {
            props.setProperty("c_startOfGestation", frmRecordHelp.parseDateFieldOrNull(getStartOfGestation()));
        }
        props.setProperty("c_headCirc", getC_headCirc());
        props.setProperty("c_headCirc", getC_headCirc());
        props.setProperty("c_lastVisited", getC_lastVisited());
        props.setProperty("c_length", getCLength());
        props.setProperty("c_pName", getC_pName());
        props.setProperty("c_riskFactors", getC_riskFactors());
        if (getP1Date1m() != null) {
            props.setProperty("p1_date1m", frmRecordHelp.parseDateFieldOrNull(getP1Date1m()));
        }
        if (getP1Date1w() != null) {
            props.setProperty("p1_date1w", frmRecordHelp.parseDateFieldOrNull(getP1Date1w()));
        }
        if (getP1Date2w() != null) {
            props.setProperty("p1_date2w", frmRecordHelp.parseDateFieldOrNull(getP1Date2w()));
        }
        props.setProperty("p1_development1m", getP1Development1m());
        props.setProperty("p1_development1w", getP1Development1w());
        props.setProperty("p1_development2w", getP1Development2w());
        props.setProperty("p1_hc1m", getP1Hc1m());
        props.setProperty("p1_hc1w", getP1Hc1w());
        props.setProperty("p1_hc2w", getP1Hc2w());
        props.setProperty("p1_ht1m", getP1Ht1m());
        props.setProperty("p1_ht1w", getP1Ht1w());
        props.setProperty("p1_ht2w", getP1Ht2w());
        props.setProperty("p1_immunization1m", getP1Immunization1m());
        props.setProperty("p1_immunization1w", getP1Immunization1w());
        props.setProperty("p1_immunization2w", getP1Immunization2w());
        props.setProperty("p1_pConcern1m", getP1_pConcern1m());
        props.setProperty("p1_pConcern1w", getP1_pConcern1w());
        props.setProperty("p1_pConcern2w", getP1_pConcern2w());
        props.setProperty("p1_education1w", getP1Education1w());
        props.setProperty("p1_education2w", getP1Education2w());
        props.setProperty("p1_education1m", getP1Education1m());
        props.setProperty("p1_pNutrition1m", getP1_pNutrition1m());
        props.setProperty("p1_pNutrition1w", getP1_pNutrition1w());
        props.setProperty("p1_pNutrition2w", getP1_pNutrition2w());
        props.setProperty("p1_pPhysical1m", getP1_pPhysical1m());
        props.setProperty("p1_pPhysical1w", getP1_pPhysical1w());
        props.setProperty("p1_pPhysical2w", getP1_pPhysical2w());
        props.setProperty("p1_problems1m", getP1Problems1m());
        props.setProperty("p1_problems1w", getP1Problems1w());
        props.setProperty("p1_problems2w", getP1Problems2w());
        props.setProperty("p1_signature1m", getP1Signature1m());
        props.setProperty("p1_signature1w", getP1Signature1w());
        props.setProperty("p1_signature2w", getP1Signature2w());
        props.setProperty("p1_wt1m", getP1Wt1m());
        props.setProperty("p1_wt1w", getP1Wt1w());
        props.setProperty("p1_wt2w", getP1Wt2w());
        if (getP2Date2m() != null) {
            props.setProperty("p2_date2m", frmRecordHelp.parseDateFieldOrNull(getP2Date2m()));
        }
        if (getP2Date4m() != null) {
            props.setProperty("p2_date4m", frmRecordHelp.parseDateFieldOrNull(getP2Date4m()));
        }
        if (getP2Date6m() != null) {
            props.setProperty("p2_date6m", frmRecordHelp.parseDateFieldOrNull(getP2Date6m()));
        }
        props.setProperty("p2_development2m", getP2Development2m());
        props.setProperty("p2_development4m", getP2Development4m());
        props.setProperty("p2_development6m", getP2Development6m());
        props.setProperty("p2_education2m", getP2Education2m());
        props.setProperty("p2_education4m", getP2Education4m());
        props.setProperty("p2_education6m", getP2Education6m());
        props.setProperty("p2_hc2m", getP2Hc2m());
        props.setProperty("p2_hc4m", getP2Hc4m());
        props.setProperty("p2_hc6m", getP2Hc6m());
        props.setProperty("p2_ht2m", getP2Ht2m());
        props.setProperty("p2_ht4m", getP2Ht4m());
        props.setProperty("p2_ht6m", getP2Ht6m());
        props.setProperty("p2_immunization6m", getP2Immunization6m());
        props.setProperty("p2_nutrition2m", getP2Nutrition2m());
        props.setProperty("p2_nutrition4m", getP2Nutrition4m());
        props.setProperty("p2_nutrition6m", getP2Nutrition6m());
        props.setProperty("p2_pConcern2m", getP2_pConcern2m());
        props.setProperty("p2_pConcern4m", getP2_pConcern4m());
        props.setProperty("p2_pConcern6m", getP2_pConcern6m());
        props.setProperty("p2_physical2m", getP2Physical2m());
        props.setProperty("p2_physical4m", getP2Physical4m());
        props.setProperty("p2_physical6m", getP2Physical6m());
        props.setProperty("p2_problems2m", getP2Problems2m());
        props.setProperty("p2_problems4m", getP2Problems4m());
        props.setProperty("p2_problems6m", getP2Problems6m());
        props.setProperty("p2_signature2m", getP2Signature2m());
        props.setProperty("p2_signature4m", getP2Signature4m());
        props.setProperty("p2_signature6m", getP2Signature6m());
        props.setProperty("p2_wt2m", getP2Wt2m());
        props.setProperty("p2_wt4m", getP2Wt4m());
        props.setProperty("p2_wt6m", getP2Wt6m());
        if (getP3Date12m() != null) {
            props.setProperty("p3_date12m", frmRecordHelp.parseDateFieldOrNull(getP3Date12m()));
        }
        if (getP3Date15m() != null) {
            props.setProperty("p3_date15m", frmRecordHelp.parseDateFieldOrNull(getP3Date15m()));
        }
        if (getP3Date9m() != null) {
            props.setProperty("p3_date9m", frmRecordHelp.parseDateFieldOrNull(getP3Date9m()));
        }
        props.setProperty("p3_development12m", getP3Development12m());
        props.setProperty("p3_development15m", getP3Development15m());
        props.setProperty("p3_development9m", getP3Development9m());
        props.setProperty("p3_education9m", getP3Education9m());
        props.setProperty("p3_education12m", getP3Education12m());
        props.setProperty("p3_education15m", getP3Education15m());
        props.setProperty("p3_hc12m", getP3Hc12m());
        props.setProperty("p3_hc15m", getP3Hc15m());
        props.setProperty("p3_hc9m", getP3Hc9m());
        props.setProperty("p3_ht12m", getP3Ht12m());
        props.setProperty("p3_ht15m", getP3Ht15m());
        props.setProperty("p3_ht9m", getP3Ht9m());
        props.setProperty("p3_nutrition12m", getP3Nutrition12m());
        props.setProperty("p3_nutrition15m", getP3Nutrition15m());
        props.setProperty("p3_nutrition9m", getP3Nutrition9m());
        props.setProperty("p3_pConcern12m", getP3_pConcern12m());
        props.setProperty("p3_pConcern15m", getP3_pConcern15m());
        props.setProperty("p3_pConcern9m", getP3_pConcern9m());
        props.setProperty("p3_physical12m", getP3Physical12m());
        props.setProperty("p3_physical15m", getP3Physical15m());
        props.setProperty("p3_physical9m", getP3Physical9m());
        props.setProperty("p3_problems12m", getP3Problems12m());
        props.setProperty("p3_problems15m", getP3Problems15m());
        props.setProperty("p3_problems9m", getP3Problems9m());
        props.setProperty("p3_signature12m", getP3Signature12m());
        props.setProperty("p3_signature15m", getP3Signature15m());
        props.setProperty("p3_signature9m", getP3Signature9m());
        props.setProperty("p3_wt12m", getP3Wt12m());
        props.setProperty("p3_wt15m", getP3Wt15m());
        props.setProperty("p3_wt9m", getP3Wt9m());
        if (getP4Date18m() != null) {
            props.setProperty("p4_date18m", frmRecordHelp.parseDateFieldOrNull(getP4Date18m()));
        }
        if (getP4Date24m() != null) {
            props.setProperty("p4_date24m", frmRecordHelp.parseDateFieldOrNull(getP4Date24m()));
        }
        if (getP4Date48m() != null) {
            props.setProperty("p4_date48m", frmRecordHelp.parseDateFieldOrNull(getP4Date48m()));
        }
        props.setProperty("p4_development18m", getP4Development18m());
        props.setProperty("p4_development24m", getP4Development24m());
        props.setProperty("p4_development36m", getP4Development36m());
        props.setProperty("p4_development48m", getP4Development48m());
        props.setProperty("p4_development60m", getP4Development60m());
        props.setProperty("p4_education18m", getP4Education18m());
        props.setProperty("p4_education24m", getP4Education24m());
        props.setProperty("p4_education48m", getP4Education48m());
        props.setProperty("p4_hc18m", getP4Hc18m());
        props.setProperty("p4_hc24m", getP4Hc24m());
        props.setProperty("p4_ht18m", getP4Ht18m());
        props.setProperty("p4_ht24m", getP4Ht24m());
        props.setProperty("p4_ht48m", getP4Ht48m());
        props.setProperty("p4_bmi24m", getP4Bmi24m());
        props.setProperty("p4_bmi48m", getP4Bmi48m());
        props.setProperty("p4_nippisingattained", getP4Nippisingattained());
        props.setProperty("p4_nutrition18m", getP4Nutrition18m());
        props.setProperty("p4_nutrition24m", getP4Nutrition24m());
        props.setProperty("p4_nutrition48m", getP4Nutrition48m());
        props.setProperty("p4_pConcern18m", getP4_pConcern18m());
        props.setProperty("p4_pConcern24m", getP4_pConcern24m());
        props.setProperty("p4_pConcern48m", getP4_pConcern48m());
        props.setProperty("p4_physical18m", getP4Physical18m());
        props.setProperty("p4_physical24m", getP4Physical24m());
        props.setProperty("p4_physical48m", getP4Physical48m());
        props.setProperty("p4_problems18m", getP4Problems18m());
        props.setProperty("p4_problems24m", getP4Problems24m());
        props.setProperty("p4_problems48m", getP4Problems48m());
        props.setProperty("p4_signature18m", getP4Signature18m());
        props.setProperty("p4_signature24m", getP4Signature24m());
        props.setProperty("p4_signature48m", getP4Signature48m());
        props.setProperty("p4_wt18m", getP4Wt18m());
        props.setProperty("p4_wt24m", getP4Wt24m());
        props.setProperty("p4_wt48m", getP4Wt48m());

        for (FormBooleanValue booleanValue : getBooleanValueMap().values()) {
            props.setProperty(booleanValue.getId().getFieldName(), booleanValue.getValue() ? "checked='checked'" : "");
        }
        return props;
    }
}
