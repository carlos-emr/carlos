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
@Table(name = "formRourke2020")
public class FormRourke2020 extends AbstractModel<Integer> implements Serializable, BooleanValueForm {
    public static final String FORM_TABLE = "formRourke2020";

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
//=====================================================================

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtRota")
    private Date p5_1GiveDtRota;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtRota")
    private Date p5_2GiveDtRota;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3GiveDtRota")
    private Date p5_3GiveDtRota;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtHib")
    private Date p5_1GiveDtHib;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtHib")
    private Date p5_2GiveDtHib;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3GiveDtHib")
    private Date p5_3GiveDtHib;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_4GiveDtHib")
    private Date p5_4GiveDtHib;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtPneu")
    private Date p5_1GiveDtPneu;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtPneu")
    private Date p5_2GiveDtPneu;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3GiveDtPneu")
    private Date p5_3GiveDtPneu;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_4GiveDtPneu")
    private Date p5_4GiveDtPneu;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtMenCon")
    private Date p5_1GiveDtMenCon;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtMenCon")
    private Date p5_2GiveDtMenCon;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3GiveDtMenCon")
    private Date p5_3GiveDtMenCon;

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtRota")
    private Date p5_1ExpDtRota;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtRota")
    private Date p5_2ExpDtRota;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3ExpDtRota")
    private Date p5_3ExpDtRota;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtHib")
    private Date p5_1ExpDtHib;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtHib")
    private Date p5_2ExpDtHib;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3ExpDtHib")
    private Date p5_3ExpDtHib;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_4ExpDtHib")
    private Date p5_4ExpDtHib;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtPneu")
    private Date p5_1ExpDtPneu;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtPneu")
    private Date p5_2ExpDtPneu;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3ExpDtPneu")
    private Date p5_3ExpDtPneu;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_4ExpDtPneu")
    private Date p5_4ExpDtPneu;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtMenCon")
    private Date p5_1ExpDtMenCon;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtMenCon")
    private Date p5_2ExpDtMenCon;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3ExpDtMenCon")
    private Date p5_3ExpDtMenCon;

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtHepa")
    private Date p5_1GiveDtHepa;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtHepa")
    private Date p5_2GiveDtHepa;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3GiveDtHepa")
    private Date p5_3GiveDtHepa;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtHepa")
    private Date p5_1ExpDtHepa;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtHepa")
    private Date p5_2ExpDtHepa;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3ExpDtHepa")
    private Date p5_3ExpDtHepa;

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtMMR")
    private Date p5_1GiveDtMMR;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtMMR")
    private Date p5_2GiveDtMMR;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtMMR")
    private Date p5_1ExpDtMMR;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtMMR")
    private Date p5_2ExpDtMMR;

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtVaricella")
    private Date p5_1GiveDtVaricella;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtVaricella")
    private Date p5_2GiveDtVaricella;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtVaricella")
    private Date p5_1ExpDtVaricella;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtVaricella")
    private Date p5_2ExpDtVaricella;

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtdTapIpv")
    private Date p5_1GiveDtdTapIpv;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtdTapIpv")
    private Date p5_1ExpDtdTapIpv;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtdTap")
    private Date p5_1GiveDtdTap;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtdTap")
    private Date p5_1ExpDtdTap;

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtInfluenza")
    private Date p5_1GiveDtInfluenza;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtInfluenza")
    private Date p5_2GiveDtInfluenza;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3GiveDtInfluenza")
    private Date p5_3GiveDtInfluenza;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtInfluenza")
    private Date p5_1ExpDtInfluenza;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtInfluenza")
    private Date p5_2ExpDtInfluenza;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3ExpDtInfluenza")
    private Date p5_3ExpDtInfluenza;

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtHpv")
    private Date p5_1GiveDtHpv;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2GiveDtHpv")
    private Date p5_2GiveDtHpv;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3GiveDtHpv")
    private Date p5_3GiveDtHpv;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtHpv")
    private Date p5_1ExpDtHpv;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_2ExpDtHpv")
    private Date p5_2ExpDtHpv;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_3ExpDtHpv")
    private Date p5_3ExpDtHpv;

    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1GiveDtOther")
    private Date p5_1GiveDtOther;
    @Temporal(TemporalType.DATE)
    @Column(name = "p5_1ExpDtOther")
    private Date p5_1ExpDtOther;

//	RotaVirus

    @Column(name = "p5_1InjeRota")
    private String p5_1InjeRota;
    @Column(name = "p5_2InjeRota")
    private String p5_2InjeRota;
    @Column(name = "p5_3InjeRota")
    private String p5_3InjeRota;
    @Column(name = "p5_1LotNRota")
    private String p5_1LotNRota;
    @Column(name = "p5_2LotNRota")
    private String p5_2LotNRota;
    @Column(name = "p5_3LotNRota")
    private String p5_3LotNRota;
    @Column(name = "p5_1InitialRota")
    private String p5_1InitialRota;
    @Column(name = "p5_2InitialRota")
    private String p5_2InitialRota;
    @Column(name = "p5_3InitialRota")
    private String p5_3InitialRota;
    @Column(name = "p5_1CommentsRota")
    private String p5_1CommentsRota;
    @Column(name = "p5_2CommentsRota")
    private String p5_2CommentsRota;
    @Column(name = "p5_3CommentsRota")
    private String p5_3CommentsRota;

//	Hib

    @Column(name = "p5_1InjeHib")
    private String p5_1InjeHib;
    @Column(name = "p5_2InjeHib")
    private String p5_2InjeHib;
    @Column(name = "p5_3InjeHib")
    private String p5_3InjeHib;
    @Column(name = "p5_4InjeHib")
    private String p5_4InjeHib;

    @Column(name = "p5_1LotNHib")
    private String p5_1LotNHib;
    @Column(name = "p5_2LotNHib")
    private String p5_2LotNHib;
    @Column(name = "p5_3LotNHib")
    private String p5_3LotNHib;
    @Column(name = "p5_4LotNHib")
    private String p5_4LotNHib;

    @Column(name = "p5_1InitialHib")
    private String p5_1InitialHib;
    @Column(name = "p5_2InitialHib")
    private String p5_2InitialHib;
    @Column(name = "p5_3InitialHib")
    private String p5_3InitialHib;
    @Column(name = "p5_4InitialHib")
    private String p5_4InitialHib;

    @Column(name = "p5_1CommentsHib")
    private String p5_1CommentsHib;
    @Column(name = "p5_2CommentsHib")
    private String p5_2CommentsHib;
    @Column(name = "p5_3CommentsHib")
    private String p5_3CommentsHib;
    @Column(name = "p5_4CommentsHib")
    private String p5_4CommentsHib;

//	Pneu

    @Column(name = "p5_1InjePneu")
    private String p5_1InjePneu;
    @Column(name = "p5_2InjePneu")
    private String p5_2InjePneu;
    @Column(name = "p5_3InjePneu")
    private String p5_3InjePneu;
    @Column(name = "p5_4InjePneu")
    private String p5_4InjePneu;

    @Column(name = "p5_1LotNPneu")
    private String p5_1LotNPneu;
    @Column(name = "p5_2LotNPneu")
    private String p5_2LotNPneu;
    @Column(name = "p5_3LotNPneu")
    private String p5_3LotNPneu;
    @Column(name = "p5_4LotNPneu")
    private String p5_4LotNPneu;

    @Column(name = "p5_1InitialPneu")
    private String p5_1InitialPneu;
    @Column(name = "p5_2InitialPneu")
    private String p5_2InitialPneu;
    @Column(name = "p5_3InitialPneu")
    private String p5_3InitialPneu;
    @Column(name = "p5_4InitialPneu")
    private String p5_4InitialPneu;

    @Column(name = "p5_1CommentsPneu")
    private String p5_1CommentsPneu;
    @Column(name = "p5_2CommentsPneu")
    private String p5_2CommentsPneu;
    @Column(name = "p5_3CommentsPneu")
    private String p5_3CommentsPneu;
    @Column(name = "p5_4CommentsPneu")
    private String p5_4CommentsPneu;

//	Men-Conjugate

    @Column(name = "p5_1InjeMenCon")
    private String p5_1InjeMenCon;
    @Column(name = "p5_2InjeMenCon")
    private String p5_2InjeMenCon;
    @Column(name = "p5_3InjeMenCon")
    private String p5_3InjeMenCon;

    @Column(name = "p5_1LotNMenCon")
    private String p5_1LotNMenCon;
    @Column(name = "p5_2LotNMenCon")
    private String p5_2LotNMenCon;
    @Column(name = "p5_3LotNMenCon")
    private String p5_3LotNMenCon;

    @Column(name = "p5_1InitialMenCon")
    private String p5_1InitialMenCon;
    @Column(name = "p5_2InitialMenCon")
    private String p5_2InitialMenCon;
    @Column(name = "p5_3InitialMenCon")
    private String p5_3InitialMenCon;

    @Column(name = "p5_1CommentsMenCon")
    private String p5_1CommentsMenCon;
    @Column(name = "p5_2CommentsMenCon")
    private String p5_2CommentsMenCon;
    @Column(name = "p5_3CommentsMenCon")
    private String p5_3CommentsMenCon;

//	Hepatitis

    @Column(name = "p5_1InjeHepa")
    private String p5_1InjeHepa;
    @Column(name = "p5_2InjeHepa")
    private String p5_2InjeHepa;
    @Column(name = "p5_3InjeHepa")
    private String p5_3InjeHepa;

    @Column(name = "p5_1LotNHepa")
    private String p5_1LotNHepa;
    @Column(name = "p5_2LotNHepa")
    private String p5_2LotNHepa;
    @Column(name = "p5_3LotNHepa")
    private String p5_3LotNHepa;

    @Column(name = "p5_1InitialHepa")
    private String p5_1InitialHepa;
    @Column(name = "p5_2InitialHepa")
    private String p5_2InitialHepa;
    @Column(name = "p5_3InitialHepa")
    private String p5_3InitialHepa;

    @Column(name = "p5_1CommentsHepa")
    private String p5_1CommentsHepa;
    @Column(name = "p5_2CommentsHepa")
    private String p5_2CommentsHepa;
    @Column(name = "p5_3CommentsHepa")
    private String p5_3CommentsHepa;

//	MMR

    @Column(name = "p5_1InjeMMR")
    private String p5_1InjeMMR;
    @Column(name = "p5_2InjeMMR")
    private String p5_2InjeMMR;

    @Column(name = "p5_1LotNMMR")
    private String p5_1LotNMMR;
    @Column(name = "p5_2LotNMMR")
    private String p5_2LotNMMR;

    @Column(name = "p5_1InitialMMR")
    private String p5_1InitialMMR;
    @Column(name = "p5_2InitialMMR")
    private String p5_2InitialMMR;

    @Column(name = "p5_1CommentsMMR")
    private String p5_1CommentsMMR;
    @Column(name = "p5_2CommentsMMR")
    private String p5_2CommentsMMR;

    // varicella
    @Column(name = "p5_1InjeVaricella")
    private String p5_1InjeVaricella;
    @Column(name = "p5_2InjeVaricella")
    private String p5_2InjeVaricella;

    @Column(name = "p5_1LotNVaricella")
    private String p5_1LotNVaricella;
    @Column(name = "p5_2LotNVaricella")
    private String p5_2LotNVaricella;

    @Column(name = "p5_1InitialVaricella")
    private String p5_1InitialVaricella;
    @Column(name = "p5_2InitialVaricella")
    private String p5_2InitialVaricella;

    @Column(name = "p5_1CommentsVaricella")
    private String p5_1CommentsVaricella;
    @Column(name = "p5_2CommentsVaricella")
    private String p5_2CommentsVaricella;

    // dTapIpv

    @Column(name = "p5_1InjeDTaPIpv")
    private String p5_1InjeDTaPIpv;
    @Column(name = "p5_1LotNDTaPIpv")
    private String p5_1LotNDTaPIpv;

    @Column(name = "p5_1InitialDTaPIpv")
    private String p5_1InitialDTaPIpv;
    @Column(name = "p5_1CommentsDTaPIpv")
    private String p5_1CommentsDTaPIpv;

    // dTap

    @Column(name = "p5_1InjeDTap")
    private String p5_1InjeDTap;
    @Column(name = "p5_1LotNDTap")
    private String p5_1LotNDTap;

    @Column(name = "p5_1InitialDTap")
    private String p5_1InitialDTap;
    @Column(name = "p5_1CommentsDTap")
    private String p5_1CommentsDTap;

    @Column(name = "p5_1NaciInfluenza")
    private String p5_1NaciInfluenza;
    @Column(name = "p5_1InjeInfluenza")
    private String p5_1InjeInfluenza;
    @Column(name = "p5_2InjeInfluenza")
    private String p5_2InjeInfluenza;
    @Column(name = "p5_3InjeInfluenza")
    private String p5_3InjeInfluenza;

    @Column(name = "p5_2NaciInfluenza")
    private String p5_2NaciInfluenza;
    @Column(name = "p5_1LotNInfluenza")
    private String p5_1LotNInfluenza;
    @Column(name = "p5_2LotNInfluenza")
    private String p5_2LotNInfluenza;
    @Column(name = "p5_3LotNInfluenza")
    private String p5_3LotNInfluenza;

    @Column(name = "p5_3NaciInfluenza")
    private String p5_3NaciInfluenza;
    @Column(name = "p5_1InitialInfluenza")
    private String p5_1InitialInfluenza;
    @Column(name = "p5_2InitialInfluenza")
    private String p5_2InitialInfluenza;
    @Column(name = "p5_3InitialInfluenza")
    private String p5_3InitialInfluenza;

    @Column(name = "p5_1CommentsInfluenza")
    private String p5_1CommentsInfluenza;
    @Column(name = "p5_2CommentsInfluenza")
    private String p5_2CommentsInfluenza;
    @Column(name = "p5_3CommentsInfluenza")
    private String p5_3CommentsInfluenza;

    // HPV

    @Column(name = "p5_1InjeHPV")
    private String p5_1InjeHPV;
    @Column(name = "p5_2InjeHPV")
    private String p5_2InjeHPV;
    @Column(name = "p5_3InjeHPV")
    private String p5_3InjeHPV;

    @Column(name = "p5_1LotNHPV")
    private String p5_1LotNHPV;
    @Column(name = "p5_2LotNHPV")
    private String p5_2LotNHPV;
    @Column(name = "p5_3LotNHPV")
    private String p5_3LotNHPV;

    @Column(name = "p5_1InitialHPV")
    private String p5_1InitialHPV;
    @Column(name = "p5_2InitialHPV")
    private String p5_2InitialHPV;
    @Column(name = "p5_3InitialHPV")
    private String p5_3InitialHPV;

    @Column(name = "p5_1CommentsHPV")
    private String p5_1CommentsHPV;
    @Column(name = "p5_2CommentsHPV")
    private String p5_2CommentsHPV;
    @Column(name = "p5_3CommentsHPV")
    private String p5_3CommentsHPV;

    @Column(name = "p5_1NaciOther")
    private String p5_1NaciOther;

    @Column(name = "p5_1InjeOther")
    private String p5_1InjeOther;
    @Column(name = "p5_1LotNOther")
    private String p5_1LotNOther;
    @Column(name = "p5_1InitialOther")
    private String p5_1InitialOther;
    @Column(name = "p5_1CommentsOther")
    private String p5_1CommentsOther;

    @Transient
    private Map<String, FormBooleanValue> booleanValueMap = new HashMap<String, FormBooleanValue>();

    public FormRourke2020(Properties props) {
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

        // p5_________

        this.p5_1GiveDtRota = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtRota");
        this.p5_1ExpDtRota = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtRota");
        this.p5_1InjeRota = props.getProperty("p5_1InjeRota", "");
        this.p5_1LotNRota = props.getProperty("p5_1LotNRota", "");
        this.p5_1InitialRota = props.getProperty("p5_1InitialRota", "");
        this.p5_1CommentsRota = props.getProperty("p5_1CommentsRota", "");
        this.p5_1GiveDtHib = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtHib");
        this.p5_1ExpDtHib = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtHib");
        this.p5_1InjeHib = props.getProperty("p5_1InjeHib", "");
        this.p5_1LotNHib = props.getProperty("p5_1LotNHib", "");
        this.p5_1InitialHib = props.getProperty("p5_1InitialHib", "");
        this.p5_1CommentsHib = props.getProperty("p5_1CommentsHib", "");
        this.p5_1GiveDtPneu = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtPneu");
        this.p5_1ExpDtPneu = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtPneu");
        this.p5_1InjePneu = props.getProperty("p5_1InjePneu", "");
        this.p5_1LotNPneu = props.getProperty("p5_1LotNPneu", "");
        this.p5_1InitialPneu = props.getProperty("p5_1InitialPneu", "");
        this.p5_1CommentsPneu = props.getProperty("p5_1CommentsPneu", "");
        this.p5_1GiveDtMenCon = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtMenCon");
        this.p5_1ExpDtMenCon = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtMenCon");
        this.p5_1InjeMenCon = props.getProperty("p5_1InjeMenCon", "");
        this.p5_1LotNMenCon = props.getProperty("p5_1LotNMenCon", "");
        this.p5_1InitialMenCon = props.getProperty("p5_1InitialMenCon", "");
        this.p5_1CommentsMenCon = props.getProperty("p5_1CommentsMenCon", "");
        this.p5_1GiveDtHepa = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtHepa");
        this.p5_1ExpDtHepa = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtHepa");
        this.p5_1InjeHepa = props.getProperty("p5_1InjeHepa", "");
        this.p5_1LotNHepa = props.getProperty("p5_1LotNHepa", "");
        this.p5_1InitialHepa = props.getProperty("p5_1InitialHepa", "");
        this.p5_1CommentsHepa = props.getProperty("p5_1CommentsHepa", "");
        this.p5_1GiveDtMMR = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtMMR");
        this.p5_1ExpDtMMR = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtMMR");
        this.p5_1InjeMMR = props.getProperty("p5_1InjeMMR", "");
        this.p5_1LotNMMR = props.getProperty("p5_1LotNMMR", "");
        this.p5_1InitialMMR = props.getProperty("p5_1InitialMMR", "");
        this.p5_1CommentsMMR = props.getProperty("p5_1CommentsMMR", "");
        this.p5_1GiveDtVaricella = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtVaricella");
        this.p5_1ExpDtVaricella = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtVaricella");
        this.p5_1InjeVaricella = props.getProperty("p5_1InjeVaricella", "");
        this.p5_1LotNVaricella = props.getProperty("p5_1LotNVaricella", "");
        this.p5_1InitialVaricella = props.getProperty("p5_1InitialVaricella", "");
        this.p5_1CommentsVaricella = props.getProperty("p5_1CommentsVaricella", "");
        this.p5_1GiveDtdTapIpv = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtdTapIpv");
        this.p5_1ExpDtdTapIpv = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtdTapIpv");
        this.p5_1InjeDTaPIpv = props.getProperty("p5_1InjeDTaPIpv", "");
        this.p5_1LotNDTaPIpv = props.getProperty("p5_1LotNDTaPIpv", "");
        this.p5_1InitialDTaPIpv = props.getProperty("p5_1InitialDTaPIpv", "");
        this.p5_1CommentsDTaPIpv = props.getProperty("p5_1CommentsDTaPIpv", "");
        this.p5_1GiveDtdTap = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtdTap");
        this.p5_1ExpDtdTap = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtdTap");
        this.p5_1InjeDTap = props.getProperty("p5_1InjeDTap", "");
        this.p5_1LotNDTap = props.getProperty("p5_1LotNDTap", "");
        this.p5_1InitialDTap = props.getProperty("p5_1InitialDTap", "");
        this.p5_1CommentsDTap = props.getProperty("p5_1CommentsDTap", "");
        this.p5_1NaciInfluenza = props.getProperty("p5_1NaciInfluenza", "");
        this.p5_1GiveDtInfluenza = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtInfluenza");
        this.p5_1ExpDtInfluenza = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtInfluenza");
        this.p5_1InjeInfluenza = props.getProperty("p5_1InjeInfluenza", "");
        this.p5_1LotNInfluenza = props.getProperty("p5_1LotNInfluenza", "");
        this.p5_1InitialInfluenza = props.getProperty("p5_1InitialInfluenza", "");
        this.p5_1CommentsInfluenza = props.getProperty("p5_1CommentsInfluenza", "");
        this.p5_1GiveDtHpv = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtHpv");
        this.p5_1ExpDtHpv = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtHpv");
        this.p5_1InjeHPV = props.getProperty("p5_1InjeHPV", "");
        this.p5_1LotNHPV = props.getProperty("p5_1LotNHPV", "");
        this.p5_1InitialHPV = props.getProperty("p5_1InitialHPV", "");
        this.p5_1CommentsHPV = props.getProperty("p5_1CommentsHPV", "");
        this.p5_1GiveDtOther = frmRecordHelp.getDateFieldOrNull(props, "p5_1GiveDtOther");
        this.p5_1ExpDtOther = frmRecordHelp.getDateFieldOrNull(props, "p5_1ExpDtOther");
        this.p5_1InjeOther = props.getProperty("p5_1InjeOther", "");
        this.p5_1LotNOther = props.getProperty("p5_1LotNOther", "");
        this.p5_1InitialOther = props.getProperty("p5_1InitialOther", "");
        this.p5_1CommentsOther = props.getProperty("p5_1CommentsOther", "");
        this.p5_1NaciOther = props.getProperty("p5_1NaciOther", "");

        this.p5_2GiveDtRota = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtRota");
        this.p5_2ExpDtRota = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtRota");
        this.p5_2InjeRota = props.getProperty("p5_2InjeRota", "");
        this.p5_2LotNRota = props.getProperty("p5_2LotNRota", "");
        this.p5_2InitialRota = props.getProperty("p5_2InitialRota", "");
        this.p5_2CommentsRota = props.getProperty("p5_2CommentsRota", "");
        this.p5_2GiveDtHib = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtHib");
        this.p5_2ExpDtHib = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtHib");
        this.p5_2InjeHib = props.getProperty("p5_2InjeHib", "");
        this.p5_2LotNHib = props.getProperty("p5_2LotNHib", "");
        this.p5_2InitialHib = props.getProperty("p5_2InitialHib", "");
        this.p5_2CommentsHib = props.getProperty("p5_2CommentsHib", "");
        this.p5_2GiveDtPneu = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtPneu");
        this.p5_2ExpDtPneu = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtPneu");
        this.p5_2InjePneu = props.getProperty("p5_2InjePneu", "");
        this.p5_2LotNPneu = props.getProperty("p5_2LotNPneu", "");
        this.p5_2InitialPneu = props.getProperty("p5_2InitialPneu", "");
        this.p5_2CommentsPneu = props.getProperty("p5_2CommentsPneu", "");
        this.p5_2GiveDtMenCon = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtMenCon");
        this.p5_2ExpDtMenCon = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtMenCon");
        this.p5_2InjeMenCon = props.getProperty("p5_2InjeMenCon", "");
        this.p5_2LotNMenCon = props.getProperty("p5_2LotNMenCon", "");
        this.p5_2InitialMenCon = props.getProperty("p5_2InitialMenCon", "");
        this.p5_2CommentsMenCon = props.getProperty("p5_2CommentsMenCon", "");
        this.p5_2GiveDtHepa = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtHepa");
        this.p5_2ExpDtHepa = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtHepa");
        this.p5_2InjeHepa = props.getProperty("p5_2InjeHepa", "");
        this.p5_2LotNHepa = props.getProperty("p5_2LotNHepa", "");
        this.p5_2InitialHepa = props.getProperty("p5_2InitialHepa", "");
        this.p5_2CommentsHepa = props.getProperty("p5_2CommentsHepa", "");
        this.p5_2GiveDtMMR = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtMMR");
        this.p5_2ExpDtMMR = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtMMR");
        this.p5_2InjeMMR = props.getProperty("p5_2InjeMMR", "");
        this.p5_2LotNMMR = props.getProperty("p5_2LotNMMR", "");
        this.p5_2InitialMMR = props.getProperty("p5_2InitialMMR", "");
        this.p5_2CommentsMMR = props.getProperty("p5_2CommentsMMR", "");
        this.p5_2GiveDtVaricella = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtVaricella");
        this.p5_2ExpDtVaricella = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtVaricella");
        this.p5_2InjeVaricella = props.getProperty("p5_2InjeVaricella", "");
        this.p5_2LotNVaricella = props.getProperty("p5_2LotNVaricella", "");
        this.p5_2InitialVaricella = props.getProperty("p5_2InitialVaricella", "");
        this.p5_2CommentsVaricella = props.getProperty("p5_2CommentsVaricella", "");
        this.p5_2NaciInfluenza = props.getProperty("p5_2NaciInfluenza", "");
        this.p5_2GiveDtInfluenza = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtInfluenza");
        this.p5_2ExpDtInfluenza = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtInfluenza");
        this.p5_2InjeInfluenza = props.getProperty("p5_2InjeInfluenza", "");
        this.p5_2LotNInfluenza = props.getProperty("p5_2LotNInfluenza", "");
        this.p5_2InitialInfluenza = props.getProperty("p5_2InitialInfluenza", "");
        this.p5_2CommentsInfluenza = props.getProperty("p5_2CommentsInfluenza", "");
        this.p5_2GiveDtHpv = frmRecordHelp.getDateFieldOrNull(props, "p5_2GiveDtHpv");
        this.p5_2ExpDtHpv = frmRecordHelp.getDateFieldOrNull(props, "p5_2ExpDtHpv");
        this.p5_2InjeHPV = props.getProperty("p5_2InjeHPV", "");
        this.p5_2LotNHPV = props.getProperty("p5_2LotNHPV", "");
        this.p5_2InitialHPV = props.getProperty("p5_2InitialHPV", "");
        this.p5_2CommentsHPV = props.getProperty("p5_2CommentsHPV", "");

        this.p5_3GiveDtRota = frmRecordHelp.getDateFieldOrNull(props, "p5_3GiveDtRota");
        this.p5_3ExpDtRota = frmRecordHelp.getDateFieldOrNull(props, "p5_3ExpDtRota");
        this.p5_3InjeRota = props.getProperty("p5_3InjeRota", "");
        this.p5_3LotNRota = props.getProperty("p5_3LotNRota", "");
        this.p5_3InitialRota = props.getProperty("p5_3InitialRota", "");
        this.p5_3CommentsRota = props.getProperty("p5_3CommentsRota", "");
        this.p5_3GiveDtHib = frmRecordHelp.getDateFieldOrNull(props, "p5_3GiveDtHib");
        this.p5_3ExpDtHib = frmRecordHelp.getDateFieldOrNull(props, "p5_3ExpDtHib");
        this.p5_3InjeHib = props.getProperty("p5_3InjeHib", "");
        this.p5_3LotNHib = props.getProperty("p5_3LotNHib", "");
        this.p5_3InitialHib = props.getProperty("p5_3InitialHib", "");
        this.p5_3CommentsHib = props.getProperty("p5_3CommentsHib", "");
        this.p5_3GiveDtPneu = frmRecordHelp.getDateFieldOrNull(props, "p5_3GiveDtPneu");
        this.p5_3ExpDtPneu = frmRecordHelp.getDateFieldOrNull(props, "p5_3ExpDtPneu");
        this.p5_3InjePneu = props.getProperty("p5_3InjePneu", "");
        this.p5_3LotNPneu = props.getProperty("p5_3LotNPneu", "");
        this.p5_3InitialPneu = props.getProperty("p5_3InitialPneu", "");
        this.p5_3CommentsPneu = props.getProperty("p5_3CommentsPneu", "");
        this.p5_3GiveDtMenCon = frmRecordHelp.getDateFieldOrNull(props, "p5_3GiveDtMenCon");
        this.p5_3ExpDtMenCon = frmRecordHelp.getDateFieldOrNull(props, "p5_3ExpDtMenCon");
        this.p5_3InjeMenCon = props.getProperty("p5_3InjeMenCon", "");
        this.p5_3LotNMenCon = props.getProperty("p5_3LotNMenCon", "");
        this.p5_3InitialMenCon = props.getProperty("p5_3InitialMenCon", "");
        this.p5_3CommentsMenCon = props.getProperty("p5_3CommentsMenCon", "");
        this.p5_3GiveDtHepa = frmRecordHelp.getDateFieldOrNull(props, "p5_3GiveDtHepa");
        this.p5_3ExpDtHepa = frmRecordHelp.getDateFieldOrNull(props, "p5_3ExpDtHepa");
        this.p5_3InjeHepa = props.getProperty("p5_3InjeHepa", "");
        this.p5_3LotNHepa = props.getProperty("p5_3LotNHepa", "");
        this.p5_3InitialHepa = props.getProperty("p5_3InitialHepa", "");
        this.p5_3CommentsHepa = props.getProperty("p5_3CommentsHepa", "");
        this.p5_3NaciInfluenza = props.getProperty("p5_3NaciInfluenza", "");
        this.p5_3GiveDtInfluenza = frmRecordHelp.getDateFieldOrNull(props, "p5_3GiveDtInfluenza");
        this.p5_3ExpDtInfluenza = frmRecordHelp.getDateFieldOrNull(props, "p5_3ExpDtInfluenza");
        this.p5_3InjeInfluenza = props.getProperty("p5_3InjeInfluenza", "");
        this.p5_3LotNInfluenza = props.getProperty("p5_3LotNInfluenza", "");
        this.p5_3InitialInfluenza = props.getProperty("p5_3InitialInfluenza", "");
        this.p5_3CommentsInfluenza = props.getProperty("p5_3CommentsInfluenza", "");
        this.p5_3GiveDtHpv = frmRecordHelp.getDateFieldOrNull(props, "p5_3GiveDtHpv");
        this.p5_3ExpDtHpv = frmRecordHelp.getDateFieldOrNull(props, "p5_3ExpDtHpv");
        this.p5_3InjeHPV = props.getProperty("p5_3InjeHPV", "");
        this.p5_3LotNHPV = props.getProperty("p5_3LotNHPV", "");
        this.p5_3InitialHPV = props.getProperty("p5_3InitialHPV", "");
        this.p5_3CommentsHPV = props.getProperty("p5_3CommentsHPV", "");

        this.p5_4GiveDtHib = frmRecordHelp.getDateFieldOrNull(props, "p5_4GiveDtHib");
        this.p5_4ExpDtHib = frmRecordHelp.getDateFieldOrNull(props, "p5_4ExpDtHib");
        this.p5_4InjeHib = props.getProperty("p5_4InjeHib", "");
        this.p5_4LotNHib = props.getProperty("p5_4LotNHib", "");
        this.p5_4InitialHib = props.getProperty("p5_4InitialHib", "");
        this.p5_4CommentsHib = props.getProperty("p5_4CommentsHib", "");
        this.p5_4GiveDtPneu = frmRecordHelp.getDateFieldOrNull(props, "p5_4GiveDtPneu");
        this.p5_4ExpDtPneu = frmRecordHelp.getDateFieldOrNull(props, "p5_4ExpDtPneu");
        this.p5_4InjePneu = props.getProperty("p5_4InjePneu", "");
        this.p5_4LotNPneu = props.getProperty("p5_4LotNPneu", "");
        this.p5_4InitialPneu = props.getProperty("p5_4InitialPneu", "");
        this.p5_4CommentsPneu = props.getProperty("p5_4CommentsPneu", "");

    }

    public FormRourke2020() {
        // default
    }

    public Properties toProperties() {
        FrmRecordHelp frmRecordHelp = new FrmRecordHelp();
        frmRecordHelp.setDateFormat("dd/MM/yyyy");

        Properties props = new Properties();
        props.setProperty("provider_no", getProviderNo());
        props.setProperty("demographic_no", String.valueOf(getDemographicNo()));
        props.setProperty("c_male", getCFemale());
        props.setProperty("c_female", getCFemale());
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

        props.setProperty("p5_1InjeRota", getP5_1InjeRota());
        props.setProperty("p5_2InjeRota", getP5_2InjeRota());
        props.setProperty("p5_3InjeRota", getP5_2InjeRota());
        props.setProperty("p5_1LotNRota", getP5_1LotNRota());
        props.setProperty("p5_2LotNRota", getP5_2LotNRota());
        props.setProperty("p5_3LotNRota", getP5_3LotNRota());
        props.setProperty("p5_1InitialRota", getP5_1InitialRota());
        props.setProperty("p5_2InitialRota", getP5_2InitialRota());
        props.setProperty("p5_3InitialRota", getP5_3InitialRota());
        props.setProperty("p5_1CommentsRota", getP5_1CommentsRota());
        props.setProperty("p5_2CommentsRota", getP5_2CommentsRota());
        props.setProperty("p5_3CommentsRota", getP5_3CommentsRota());
        if (getP5_1GiveDtRota() != null) {
            props.setProperty("p5_1GiveDtRota", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtRota()));
        }
        if (getP5_2GiveDtRota() != null) {
            props.setProperty("p5_2GiveDtRota", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtRota()));
        }
        if (getP5_3GiveDtRota() != null) {
            props.setProperty("p5_3GiveDtRota", frmRecordHelp.parseDateFieldOrNull(getP5_3GiveDtRota()));
        }
        if (getP5_1ExpDtRota() != null) {
            props.setProperty("p5_1ExpDtRota", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtRota()));
        }
        if (getP5_2ExpDtRota() != null) {
            props.setProperty("p5_2ExpDtRota", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtRota()));
        }
        if (getP5_3ExpDtRota() != null) {
            props.setProperty("p5_3ExpDtRota", frmRecordHelp.parseDateFieldOrNull(getP5_3ExpDtRota()));
        }
        if (getP5_1GiveDtHib() != null) {
            props.setProperty("p5_1GiveDtHib", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtHib()));
        }
        if (getP5_2GiveDtHib() != null) {
            props.setProperty("p5_2GiveDtHib", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtHib()));
        }
        if (getP5_3GiveDtHib() != null) {
            props.setProperty("p5_3GiveDtHib", frmRecordHelp.parseDateFieldOrNull(getP5_3GiveDtHib()));
        }
        if (getP5_4GiveDtHib() != null) {
            props.setProperty("p5_4GiveDtHib", frmRecordHelp.parseDateFieldOrNull(getP5_4GiveDtHib()));
        }
        if (getP5_1ExpDtHib() != null) {
            props.setProperty("p5_1ExpDtHib", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtHib()));
        }
        if (getP5_2ExpDtHib() != null) {
            props.setProperty("p5_2ExpDtHib", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtHib()));
        }
        if (getP5_3ExpDtHib() != null) {
            props.setProperty("p5_3ExpDtHib", frmRecordHelp.parseDateFieldOrNull(getP5_3ExpDtHib()));
        }
        if (getP5_4ExpDtHib() != null) {
            props.setProperty("p5_4ExpDtHib", frmRecordHelp.parseDateFieldOrNull(getP5_4ExpDtHib()));
        }
        props.setProperty("p5_1InjeHib", getP5_1InjeHib());
        props.setProperty("p5_1LotNHib", getP5_1LotNHib());
        props.setProperty("p5_1InitialHib", getP5_1InitialHib());
        props.setProperty("p5_1CommentsHib", getP5_1CommentsHib());
        props.setProperty("p5_2InjeHib", getP5_2InjeHib());
        props.setProperty("p5_2LotNHib", getP5_2LotNHib());
        props.setProperty("p5_2InitialHib", getP5_2InitialHib());
        props.setProperty("p5_2CommentsHib", getP5_2CommentsHib());
        props.setProperty("p5_3InjeHib", getP5_3InjeHib());
        props.setProperty("p5_3LotNHib", getP5_3LotNHib());
        props.setProperty("p5_3InitialHib", getP5_3InitialHib());
        props.setProperty("p5_3CommentsHib", getP5_3CommentsHib());
        props.setProperty("p5_4InjeHib", getP5_4InjeHib());
        props.setProperty("p5_4LotNHib", getP5_4LotNHib());
        props.setProperty("p5_4InitialHib", getP5_4InitialHib());
        props.setProperty("p5_4CommentsHib", getP5_4CommentsHib());

        if (getP5_1GiveDtPneu() != null) {
            props.setProperty("p5_1GiveDtPneu", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtPneu()));
        }
        if (getP5_1ExpDtPneu() != null) {
            props.setProperty("p5_1ExpDtPneu", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtPneu()));
        }
        props.setProperty("p5_1InjePneu", getP5_1InjePneu());
        props.setProperty("p5_1LotNPneu", getP5_1LotNPneu());
        props.setProperty("p5_1InitialPneu", getP5_1InitialPneu());
        props.setProperty("p5_1CommentsPneu", getP5_1CommentsPneu());
        if (getP5_2GiveDtPneu() != null) {
            props.setProperty("p5_2GiveDtPneu", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtPneu()));
        }
        if (getP5_2ExpDtPneu() != null) {
            props.setProperty("p5_2ExpDtPneu", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtPneu()));
        }
        props.setProperty("p5_2InjePneu", getP5_2InjePneu());
        props.setProperty("p5_2LotNPneu", getP5_2LotNPneu());
        props.setProperty("p5_2InitialPneu", getP5_2InitialPneu());
        props.setProperty("p5_2CommentsPneu", getP5_2CommentsPneu());
        if (getP5_3GiveDtPneu() != null) {
            props.setProperty("p5_3GiveDtPneu", frmRecordHelp.parseDateFieldOrNull(getP5_3GiveDtPneu()));
        }
        if (getP5_3ExpDtPneu() != null) {
            props.setProperty("p5_3ExpDtPneu", frmRecordHelp.parseDateFieldOrNull(getP5_3ExpDtPneu()));
        }
        props.setProperty("p5_3InjePneu", getP5_3InjePneu());
        props.setProperty("p5_3LotNPneu", getP5_3LotNPneu());
        props.setProperty("p5_3InitialPneu", getP5_3InitialPneu());
        props.setProperty("p5_3CommentsPneu", getP5_3CommentsPneu());
        if (getP5_4GiveDtPneu() != null) {
            props.setProperty("p5_4GiveDtPneu", frmRecordHelp.parseDateFieldOrNull(getP5_4GiveDtPneu()));
        }
        if (getP5_4ExpDtPneu() != null) {
            props.setProperty("p5_4ExpDtPneu", frmRecordHelp.parseDateFieldOrNull(getP5_4ExpDtPneu()));
        }
        props.setProperty("p5_4InjePneu", getP5_4InjePneu());
        props.setProperty("p5_4LotNPneu", getP5_4LotNPneu());
        props.setProperty("p5_4InitialPneu", getP5_4InitialPneu());
        props.setProperty("p5_4CommentsPneu", getP5_4CommentsPneu());

        if (getP5_1GiveDtMenCon() != null) {
            props.setProperty("p5_1GiveDtMenCon", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtMenCon()));
        }
        if (getP5_1ExpDtMenCon() != null) {
            props.setProperty("p5_1ExpDtMenCon", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtMenCon()));
        }
        props.setProperty("p5_1InjeMenCon", getP5_1InjeMenCon());
        props.setProperty("p5_1LotNMenCon", getP5_1LotNMenCon());
        props.setProperty("p5_1InitialMenCon", getP5_1InitialMenCon());
        props.setProperty("p5_1CommentsMenCon", getP5_1CommentsMenCon());
        if (getP5_2GiveDtMenCon() != null) {
            props.setProperty("p5_2GiveDtMenCon", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtMenCon()));
        }
        if (getP5_2ExpDtMenCon() != null) {
            props.setProperty("p5_2ExpDtMenCon", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtMenCon()));
        }
        props.setProperty("p5_2InjeMenCon", getP5_2InjeMenCon());
        props.setProperty("p5_2LotNMenCon", getP5_2LotNMenCon());
        props.setProperty("p5_2InitialMenCon", getP5_2InitialMenCon());
        props.setProperty("p5_2CommentsMenCon", getP5_2CommentsMenCon());
        if (getP5_3GiveDtMenCon() != null) {
            props.setProperty("p5_3GiveDtMenCon", frmRecordHelp.parseDateFieldOrNull(getP5_3GiveDtMenCon()));
        }
        if (getP5_3ExpDtMenCon() != null) {
            props.setProperty("p5_3ExpDtMenCon", frmRecordHelp.parseDateFieldOrNull(getP5_3ExpDtMenCon()));
        }
        props.setProperty("p5_3InjeMenCon", getP5_3InjeMenCon());
        props.setProperty("p5_3LotNMenCon", getP5_3LotNMenCon());
        props.setProperty("p5_3InitialMenCon", getP5_3InitialMenCon());
        props.setProperty("p5_3CommentsMenCon", getP5_3CommentsMenCon());

        if (getP5_1GiveDtHepa() != null) {
            props.setProperty("p5_1GiveDtHepa", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtHepa()));
        }
        if (getP5_1ExpDtHepa() != null) {
            props.setProperty("p5_1ExpDtHepa", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtHepa()));
        }
        props.setProperty("p5_1InjeHepa", getP5_1InjeHepa());
        props.setProperty("p5_1LotNHepa", getP5_1LotNHepa());
        props.setProperty("p5_1InitialHepa", getP5_1InitialHepa());
        props.setProperty("p5_1CommentsHepa", getP5_1CommentsHepa());
        if (getP5_2GiveDtHepa() != null) {
            props.setProperty("p5_2GiveDtHepa", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtHepa()));
        }
        if (getP5_2ExpDtHepa() != null) {
            props.setProperty("p5_2ExpDtHepa", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtHepa()));
        }
        props.setProperty("p5_2InjeHepa", getP5_2InjeHepa());
        props.setProperty("p5_2LotNHepa", getP5_2LotNHepa());
        props.setProperty("p5_2InitialHepa", getP5_2InitialHepa());
        props.setProperty("p5_2CommentsHepa", getP5_2CommentsHepa());
        if (getP5_3GiveDtHepa() != null) {
            props.setProperty("p5_3GiveDtHepa", frmRecordHelp.parseDateFieldOrNull(getP5_3GiveDtHepa()));
        }
        if (getP5_3ExpDtHepa() != null) {
            props.setProperty("p5_3ExpDtHepa", frmRecordHelp.parseDateFieldOrNull(getP5_3ExpDtHepa()));
        }
        props.setProperty("p5_3InjeHepa", getP5_3InjeHepa());
        props.setProperty("p5_3LotNHepa", getP5_3LotNHepa());
        props.setProperty("p5_3InitialHepa", getP5_3InitialHepa());
        props.setProperty("p5_3CommentsHepa", getP5_3CommentsHepa());

        if (getP5_1GiveDtMMR() != null) {
            props.setProperty("p5_1GiveDtMMR", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtMMR()));
        }
        if (getP5_1ExpDtMMR() != null) {
            props.setProperty("p5_1ExpDtMMR", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtMMR()));
        }
        props.setProperty("p5_1InjeMMR", getP5_1InjeMMR());
        props.setProperty("p5_1LotNMMR", getP5_1LotNMMR());
        props.setProperty("p5_1InitialMMR", getP5_1InitialMMR());
        props.setProperty("p5_1CommentsMMR", getP5_1CommentsMMR());
        if (getP5_2GiveDtMMR() != null) {
            props.setProperty("p5_2GiveDtMMR", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtMMR()));
        }
        if (getP5_2ExpDtMMR() != null) {
            props.setProperty("p5_2ExpDtMMR", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtMMR()));
        }
        props.setProperty("p5_2InjeMMR", getP5_2InjeMMR());
        props.setProperty("p5_2LotNMMR", getP5_2LotNMMR());
        props.setProperty("p5_2InitialMMR", getP5_2InitialMMR());
        props.setProperty("p5_2CommentsMMR", getP5_2CommentsMMR());

        if (getP5_1GiveDtVaricella() != null) {
            props.setProperty("p5_1GiveDtVaricella", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtVaricella()));
        }
        if (getP5_1ExpDtVaricella() != null) {
            props.setProperty("p5_1ExpDtVaricella", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtVaricella()));
        }
        props.setProperty("p5_1InjeVaricella", getP5_1InjeVaricella());
        props.setProperty("p5_1LotNVaricella", getP5_1LotNVaricella());
        props.setProperty("p5_1InitialVaricella", getP5_1InitialVaricella());
        props.setProperty("p5_1CommentsVaricella", getP5_1CommentsVaricella());
        if (getP5_2GiveDtVaricella() != null) {
            props.setProperty("p5_2GiveDtVaricella", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtVaricella()));
        }
        if (getP5_2ExpDtVaricella() != null) {
            props.setProperty("p5_2ExpDtVaricella", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtVaricella()));
        }
        props.setProperty("p5_2InjeVaricella", getP5_2InjeVaricella());
        props.setProperty("p5_2LotNVaricella", getP5_2LotNVaricella());
        props.setProperty("p5_2InitialVaricella", getP5_2InitialVaricella());
        props.setProperty("p5_2CommentsVaricella", getP5_2CommentsVaricella());

        if (getP5_1GiveDtdTapIpv() != null) {
            props.setProperty("p5_1GiveDtdTapIpv", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtdTapIpv()));
        }
        if (getP5_1ExpDtdTapIpv() != null) {
            props.setProperty("p5_1ExpDtdTapIpv", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtdTapIpv()));
        }
        props.setProperty("p5_1InjeDTaPIpv", getP5_1InjeDTaPIpv());
        props.setProperty("p5_1LotNDTaPIpv", getP5_1LotNDTaPIpv());
        props.setProperty("p5_1InitialDTaPIpv", getP5_1InitialDTaPIpv());
        props.setProperty("p5_1CommentsDTaPIpv", getP5_1CommentsDTaPIpv());

        if (getP5_1GiveDtdTap() != null) {
            props.setProperty("p5_1GiveDtdTap", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtdTap()));
        }
        if (getP5_1ExpDtdTap() != null) {
            props.setProperty("p5_1ExpDtdTap", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtdTap()));
        }
        props.setProperty("p5_1InjeDTap", getP5_1InjeDTap());
        props.setProperty("p5_1LotNDTap", getP5_1LotNDTap());
        props.setProperty("p5_1InitialDTap", getP5_1InitialDTap());
        props.setProperty("p5_1CommentsDTap", getP5_1CommentsDTap());

        props.setProperty("p5_1NaciInfluenza", getP5_1NaciInfluenza());
        if (getP5_1GiveDtInfluenza() != null) {
            props.setProperty("p5_1GiveDtInfluenza", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtInfluenza()));
        }
        if (getP5_1ExpDtInfluenza() != null) {
            props.setProperty("p5_1ExpDtInfluenza", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtInfluenza()));
        }
        props.setProperty("p5_1InjeInfluenza", getP5_1InjeInfluenza());
        props.setProperty("p5_1LotNInfluenza", getP5_1LotNInfluenza());
        props.setProperty("p5_1InitialInfluenza", getP5_1InitialInfluenza());
        props.setProperty("p5_1CommentsInfluenza", getP5_1CommentsInfluenza());
        props.setProperty("p5_2NaciInfluenza", getP5_2NaciInfluenza());
        if (getP5_2GiveDtInfluenza() != null) {
            props.setProperty("p5_2GiveDtInfluenza", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtInfluenza()));
        }
        if (getP5_2ExpDtInfluenza() != null) {
            props.setProperty("p5_2ExpDtInfluenza", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtInfluenza()));
        }
        props.setProperty("p5_2InjeInfluenza", getP5_2InjeInfluenza());
        props.setProperty("p5_2LotNInfluenza", getP5_2LotNInfluenza());
        props.setProperty("p5_2InitialInfluenza", getP5_2InitialInfluenza());
        props.setProperty("p5_2CommentsInfluenza", getP5_2CommentsInfluenza());
        props.setProperty("p5_3NaciInfluenza", getP5_3NaciInfluenza());
        if (getP5_3GiveDtInfluenza() != null) {
            props.setProperty("p5_3GiveDtInfluenza", frmRecordHelp.parseDateFieldOrNull(getP5_3GiveDtInfluenza()));
        }
        if (getP5_3ExpDtInfluenza() != null) {
            props.setProperty("p5_3ExpDtInfluenza", frmRecordHelp.parseDateFieldOrNull(getP5_3ExpDtInfluenza()));
        }
        props.setProperty("p5_3InjeInfluenza", getP5_3InjeInfluenza());
        props.setProperty("p5_3LotNInfluenza", getP5_3LotNInfluenza());
        props.setProperty("p5_3InitialInfluenza", getP5_3InitialInfluenza());
        props.setProperty("p5_3CommentsInfluenza", getP5_3CommentsInfluenza());

        if (getP5_1GiveDtHpv() != null) {
            props.setProperty("p5_1GiveDtHpv", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtHpv()));
        }
        if (getP5_1ExpDtHpv() != null) {
            props.setProperty("p5_1ExpDtHpv", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtHpv()));
        }
        props.setProperty("p5_1InjeHPV", getP5_1InjeHPV());
        props.setProperty("p5_1LotNHPV", getP5_1LotNHPV());
        props.setProperty("p5_1InitialHPV", getP5_1InitialHPV());
        props.setProperty("p5_1CommentsHPV", getP5_1CommentsHPV());
        if (getP5_2GiveDtHpv() != null) {
            props.setProperty("p5_2GiveDtHpv", frmRecordHelp.parseDateFieldOrNull(getP5_2GiveDtHpv()));
        }
        if (getP5_2ExpDtHpv() != null) {
            props.setProperty("p5_2ExpDtHpv", frmRecordHelp.parseDateFieldOrNull(getP5_2ExpDtHpv()));
        }
        props.setProperty("p5_2InjeHPV", getP5_2InjeHPV());
        props.setProperty("p5_2LotNHPV", getP5_2LotNHPV());
        props.setProperty("p5_2InitialHPV", getP5_2InitialHPV());
        props.setProperty("p5_2CommentsHPV", getP5_2CommentsHPV());
        if (getP5_3GiveDtHpv() != null) {
            props.setProperty("p5_3GiveDtHpv", frmRecordHelp.parseDateFieldOrNull(getP5_3GiveDtHpv()));
        }
        if (getP5_3ExpDtHpv() != null) {
            props.setProperty("p5_3ExpDtHpv", frmRecordHelp.parseDateFieldOrNull(getP5_3ExpDtHpv()));
        }
        props.setProperty("p5_3InjeHPV", getP5_3InjeHPV());
        props.setProperty("p5_3LotNHPV", getP5_3LotNHPV());
        props.setProperty("p5_3InitialHPV", getP5_3InitialHPV());
        props.setProperty("p5_3CommentsHPV", getP5_3CommentsHPV());

        if (getP5_1GiveDtOther() != null) {
            props.setProperty("p5_1GiveDtOther", frmRecordHelp.parseDateFieldOrNull(getP5_1GiveDtOther()));
        }
        if (getP5_1ExpDtOther() != null) {
            props.setProperty("p5_1ExpDtOther", frmRecordHelp.parseDateFieldOrNull(getP5_1ExpDtOther()));
        }
        props.setProperty("p5_1InjeOther", getP5_1InjeOther());
        props.setProperty("p5_1LotNOther", getP5_1LotNOther());
        props.setProperty("p5_1InitialOther", getP5_1InitialOther());
        props.setProperty("p5_1CommentsOther", getP5_1CommentsOther());
        props.setProperty("p5_1NaciOther", getP5_1NaciOther());

        for (FormBooleanValue booleanValue : getBooleanValueMap().values()) {
            props.setProperty(booleanValue.getId().getFieldName(), booleanValue.getValue() ? "checked='checked'" : "");
        }

        return props;

    }

    /**
     * Sets the booleanValueMap using values taken from the provided Properties
     * object
     *
     * @param properties
     */
    public void createBooleanValueMapFromFormProperties(Properties properties) {
        HashMap<String, FormBooleanValue> booleanMap = new HashMap<String, FormBooleanValue>();
        // Look over every known radiobutton/checkbox field with each permutation of a
        // suffix
        for (String fieldName : FormRourke2020Constants.BOOLEAN_FIELD_NAMES) {
            for (String suffix : FormRourke2020Constants.BOOLEAN_FIELD_SUFFIXES) {
                String fullFieldName = fieldName + suffix;
                String value = properties.getProperty(fullFieldName, null);
                if (value != null) {
                    FormBooleanValue valueObject = new FormBooleanValue(FORM_TABLE, id, fullFieldName,
                            value.equalsIgnoreCase("on") || value.equalsIgnoreCase("checked='checked'"));
                    booleanMap.put(fullFieldName, valueObject);
                }

            }
        }
        setBooleanValueMap(booleanMap);
    }


    public String getFormTable() {
        return FORM_TABLE;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getCMale() {
        return cMale;
    }

    public void setCMale(String cMale) {
        this.cMale = cMale;
    }

    public String getCFemale() {
        return cFemale;
    }

    public void setCFemale(String cFemale) {
        this.cFemale = cFemale;
    }

    public Date getFormCreated() {
        return formCreated;
    }

    public void setFormCreated(Date formCreated) {
        this.formCreated = formCreated;
    }

    public Timestamp getFormEdited() {
        return formEdited;
    }

    public void setFormEdited(Timestamp formEdited) {
        this.formEdited = formEdited;
    }

    public Integer getC_APGAR1min() {
        return c_APGAR1min;
    }

    public void setC_APGAR1min(Integer c_APGAR1min) {
        this.c_APGAR1min = c_APGAR1min;
    }

    public Integer getC_APGAR5min() {
        return c_APGAR5min;
    }

    public void setC_APGAR5min(Integer c_APGAR5min) {
        this.c_APGAR5min = c_APGAR5min;
    }

    public Date getC_birthDate() {
        return c_birthDate;
    }

    public void setC_birthDate(Date c_birthDate) {
        this.c_birthDate = c_birthDate;
    }

    public String getC_birthRemarks() {
        return c_birthRemarks;
    }

    public void setC_birthRemarks(String c_birthRemarks) {
        this.c_birthRemarks = c_birthRemarks;
    }

    public String getC_birthWeight() {
        return c_birthWeight;
    }

    public void setC_birthWeight(String c_birthWeight) {
        this.c_birthWeight = c_birthWeight;
    }

    public String getC_dischargeWeight() {
        return c_dischargeWeight;
    }

    public void setC_dischargeWeight(String c_dischargeWeight) {
        this.c_dischargeWeight = c_dischargeWeight;
    }

    public String getC_famHistory() {
        return c_famHistory;
    }

    public void setC_famHistory(String c_famHistory) {
        this.c_famHistory = c_famHistory;
    }

    public String getCFsa() {
        return cFsa;
    }

    public void setcFsa(String cFsa) {
        this.cFsa = cFsa;
    }

    public Date getStartOfGestation() {
        return startOfGestation;
    }

    public void setStartOfGestation(Date startOfGestation) {
        this.startOfGestation = startOfGestation;
    }

    public String getC_headCirc() {
        return c_headCirc;
    }

    public void setC_headCirc(String c_headCirc) {
        this.c_headCirc = c_headCirc;
    }

    public String getC_lastVisited() {
        return c_lastVisited;
    }

    public void setC_lastVisited(String c_lastVisited) {
        this.c_lastVisited = c_lastVisited;
    }

    public String getCLength() {
        return cLength;
    }

    public void setcLength(String cLength) {
        this.cLength = cLength;
    }

    public String getC_pName() {
        return c_pName;
    }

    public void setC_pName(String c_pName) {
        this.c_pName = c_pName;
    }

    public String getC_riskFactors() {
        return c_riskFactors;
    }

    public void setC_riskFactors(String c_riskFactors) {
        this.c_riskFactors = c_riskFactors;
    }

    public Date getP1Date1m() {
        return p1Date1m;
    }

    public void setP1Date1m(Date p1Date1m) {
        this.p1Date1m = p1Date1m;
    }

    public Date getP1Date1w() {
        return p1Date1w;
    }

    public void setP1Date1w(Date p1Date1w) {
        this.p1Date1w = p1Date1w;
    }

    public Date getP1Date2w() {
        return p1Date2w;
    }

    public void setP1Date2w(Date p1Date2w) {
        this.p1Date2w = p1Date2w;
    }

    public String getP1Development1m() {
        return p1Development1m;
    }

    public void setP1Development1m(String p1Development1m) {
        this.p1Development1m = p1Development1m;
    }

    public String getP1Development1w() {
        return p1Development1w;
    }

    public void setP1Development1w(String p1Development1w) {
        this.p1Development1w = p1Development1w;
    }

    public String getP1Development2w() {
        return p1Development2w;
    }

    public void setP1Development2w(String p1Development2w) {
        this.p1Development2w = p1Development2w;
    }

    public String getP1Hc1m() {
        return p1Hc1m;
    }

    public void setP1Hc1m(String p1Hc1m) {
        this.p1Hc1m = p1Hc1m;
    }

    public String getP1Hc1w() {
        return p1Hc1w;
    }

    public void setP1Hc1w(String p1Hc1w) {
        this.p1Hc1w = p1Hc1w;
    }

    public String getP1Hc2w() {
        return p1Hc2w;
    }

    public void setP1Hc2w(String p1Hc2w) {
        this.p1Hc2w = p1Hc2w;
    }

    public String getP1Ht1m() {
        return p1Ht1m;
    }

    public void setP1Ht1m(String p1Ht1m) {
        this.p1Ht1m = p1Ht1m;
    }

    public String getP1Ht1w() {
        return p1Ht1w;
    }

    public void setP1Ht1w(String p1Ht1w) {
        this.p1Ht1w = p1Ht1w;
    }

    public String getP1Ht2w() {
        return p1Ht2w;
    }

    public void setP1Ht2w(String p1Ht2w) {
        this.p1Ht2w = p1Ht2w;
    }

    public String getP1Immunization1m() {
        return p1Immunization1m;
    }

    public void setP1Immunization1m(String p1Immunization1m) {
        this.p1Immunization1m = p1Immunization1m;
    }

    public String getP1Immunization1w() {
        return p1Immunization1w;
    }

    public void setP1Immunization1w(String p1Immunization1w) {
        this.p1Immunization1w = p1Immunization1w;
    }

    public String getP1Immunization2w() {
        return p1Immunization2w;
    }

    public void setP1Immunization2w(String p1Immunization2w) {
        this.p1Immunization2w = p1Immunization2w;
    }

    public String getP1_pConcern1m() {
        return p1_pConcern1m;
    }

    public void setP1_pConcern1m(String p1_pConcern1m) {
        this.p1_pConcern1m = p1_pConcern1m;
    }

    public String getP1_pConcern1w() {
        return p1_pConcern1w;
    }

    public void setP1_pConcern1w(String p1_pConcern1w) {
        this.p1_pConcern1w = p1_pConcern1w;
    }

    public String getP1_pConcern2w() {
        return p1_pConcern2w;
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
        return p1_pNutrition1m;
    }

    public void setP1_pNutrition1m(String p1_pNutrition1m) {
        this.p1_pNutrition1m = p1_pNutrition1m;
    }

    public String getP1_pNutrition1w() {
        return p1_pNutrition1w;
    }

    public void setP1_pNutrition1w(String p1_pNutrition1w) {
        this.p1_pNutrition1w = p1_pNutrition1w;
    }

    public String getP1_pNutrition2w() {
        return p1_pNutrition2w;
    }

    public void setP1_pNutrition2w(String p1_pNutrition2w) {
        this.p1_pNutrition2w = p1_pNutrition2w;
    }

    public String getP1_pPhysical1m() {
        return p1_pPhysical1m;
    }

    public void setP1_pPhysical1m(String p1_pPhysical1m) {
        this.p1_pPhysical1m = p1_pPhysical1m;
    }

    public String getP1_pPhysical1w() {
        return p1_pPhysical1w;
    }

    public void setP1_pPhysical1w(String p1_pPhysical1w) {
        this.p1_pPhysical1w = p1_pPhysical1w;
    }

    public String getP1_pPhysical2w() {
        return p1_pPhysical2w;
    }

    public void setP1_pPhysical2w(String p1_pPhysical2w) {
        this.p1_pPhysical2w = p1_pPhysical2w;
    }

    public String getP1Problems1m() {
        return p1Problems1m;
    }

    public void setP1Problems1m(String p1Problems1m) {
        this.p1Problems1m = p1Problems1m;
    }

    public String getP1Problems1w() {
        return p1Problems1w;
    }

    public void setP1Problems1w(String p1Problems1w) {
        this.p1Problems1w = p1Problems1w;
    }

    public String getP1Problems2w() {
        return p1Problems2w;
    }

    public void setP1Problems2w(String p1Problems2w) {
        this.p1Problems2w = p1Problems2w;
    }

    public String getP1Signature1m() {
        return p1Signature1m;
    }

    public void setP1Signature1m(String p1Signature1m) {
        this.p1Signature1m = p1Signature1m;
    }

    public String getP1Signature1w() {
        return p1Signature1w;
    }

    public void setP1Signature1w(String p1Signature1w) {
        this.p1Signature1w = p1Signature1w;
    }

    public String getP1Signature2w() {
        return p1Signature2w;
    }

    public void setP1Signature2w(String p1Signature2w) {
        this.p1Signature2w = p1Signature2w;
    }

    public String getP1Wt1m() {
        return p1Wt1m;
    }

    public void setP1Wt1m(String p1Wt1m) {
        this.p1Wt1m = p1Wt1m;
    }

    public String getP1Wt1w() {
        return p1Wt1w;
    }

    public void setP1Wt1w(String p1Wt1w) {
        this.p1Wt1w = p1Wt1w;
    }

    public String getP1Wt2w() {
        return p1Wt2w;
    }

    public void setP1Wt2w(String p1Wt2w) {
        this.p1Wt2w = p1Wt2w;
    }

    public Date getP2Date2m() {
        return p2Date2m;
    }

    public void setP2Date2m(Date p2Date2m) {
        this.p2Date2m = p2Date2m;
    }

    public Date getP2Date4m() {
        return p2Date4m;
    }

    public void setP2Date4m(Date p2Date4m) {
        this.p2Date4m = p2Date4m;
    }

    public Date getP2Date6m() {
        return p2Date6m;
    }

    public void setP2Date6m(Date p2Date6m) {
        this.p2Date6m = p2Date6m;
    }

    public String getP2Development2m() {
        return p2Development2m;
    }

    public void setP2Development2m(String p2Development2m) {
        this.p2Development2m = p2Development2m;
    }

    public String getP2Development4m() {
        return p2Development4m;
    }

    public void setP2Development4m(String p2Development4m) {
        this.p2Development4m = p2Development4m;
    }

    public String getP2Development6m() {
        return p2Development6m;
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
        return p2Hc2m;
    }

    public void setP2Hc2m(String p2Hc2m) {
        this.p2Hc2m = p2Hc2m;
    }

    public String getP2Hc4m() {
        return p2Hc4m;
    }

    public void setP2Hc4m(String p2Hc4m) {
        this.p2Hc4m = p2Hc4m;
    }

    public String getP2Hc6m() {
        return p2Hc6m;
    }

    public void setP2Hc6m(String p2Hc6m) {
        this.p2Hc6m = p2Hc6m;
    }

    public String getP2Ht2m() {
        return p2Ht2m;
    }

    public void setP2Ht2m(String p2Ht2m) {
        this.p2Ht2m = p2Ht2m;
    }

    public String getP2Ht4m() {
        return p2Ht4m;
    }

    public void setP2Ht4m(String p2Ht4m) {
        this.p2Ht4m = p2Ht4m;
    }

    public String getP2Ht6m() {
        return p2Ht6m;
    }

    public void setP2Ht6m(String p2Ht6m) {
        this.p2Ht6m = p2Ht6m;
    }

    public String getP2Immunization6m() {
        return p2Immunization6m;
    }

    public void setP2Immunization6m(String p2Immunization6m) {
        this.p2Immunization6m = p2Immunization6m;
    }

    public String getP2Nutrition2m() {
        return p2Nutrition2m;
    }

    public void setP2Nutrition2m(String p2Nutrition2m) {
        this.p2Nutrition2m = p2Nutrition2m;
    }

    public String getP2Nutrition4m() {
        return p2Nutrition4m;
    }

    public void setP2Nutrition4m(String p2Nutrition4m) {
        this.p2Nutrition4m = p2Nutrition4m;
    }

    public String getP2Nutrition6m() {
        return p2Nutrition6m;
    }

    public void setP2Nutrition6m(String p2Nutrition6m) {
        this.p2Nutrition6m = p2Nutrition6m;
    }

    public String getP2_pConcern2m() {
        return p2_pConcern2m;
    }

    public void setP2_pConcern2m(String p2_pConcern2m) {
        this.p2_pConcern2m = p2_pConcern2m;
    }

    public String getP2_pConcern4m() {
        return p2_pConcern4m;
    }

    public void setP2_pConcern4m(String p2_pConcern4m) {
        this.p2_pConcern4m = p2_pConcern4m;
    }

    public String getP2_pConcern6m() {
        return p2_pConcern6m;
    }

    public void setP2_pConcern6m(String p2_pConcern6m) {
        this.p2_pConcern6m = p2_pConcern6m;
    }

    public String getP2Physical2m() {
        return p2Physical2m;
    }

    public void setP2Physical2m(String p2Physical2m) {
        this.p2Physical2m = p2Physical2m;
    }

    public String getP2Physical4m() {
        return p2Physical4m;
    }

    public void setP2Physical4m(String p2Physical4m) {
        this.p2Physical4m = p2Physical4m;
    }

    public String getP2Physical6m() {
        return p2Physical6m;
    }

    public void setP2Physical6m(String p2Physical6m) {
        this.p2Physical6m = p2Physical6m;
    }

    public String getP2Problems2m() {
        return p2Problems2m;
    }

    public void setP2Problems2m(String p2Problems2m) {
        this.p2Problems2m = p2Problems2m;
    }

    public String getP2Problems4m() {
        return p2Problems4m;
    }

    public void setP2Problems4m(String p2Problems4m) {
        this.p2Problems4m = p2Problems4m;
    }

    public String getP2Problems6m() {
        return p2Problems6m;
    }

    public void setP2Problems6m(String p2Problems6m) {
        this.p2Problems6m = p2Problems6m;
    }

    public String getP2Signature2m() {
        return p2Signature2m;
    }

    public void setP2Signature2m(String p2Signature2m) {
        this.p2Signature2m = p2Signature2m;
    }

    public String getP2Signature4m() {
        return p2Signature4m;
    }

    public void setP2Signature4m(String p2Signature4m) {
        this.p2Signature4m = p2Signature4m;
    }

    public String getP2Signature6m() {
        return p2Signature6m;
    }

    public void setP2Signature6m(String p2Signature6m) {
        this.p2Signature6m = p2Signature6m;
    }

    public String getP2Wt2m() {
        return p2Wt2m;
    }

    public void setP2Wt2m(String p2Wt2m) {
        this.p2Wt2m = p2Wt2m;
    }

    public String getP2Wt4m() {
        return p2Wt4m;
    }

    public void setP2Wt4m(String p2Wt4m) {
        this.p2Wt4m = p2Wt4m;
    }

    public String getP2Wt6m() {
        return p2Wt6m;
    }

    public void setP2Wt6m(String p2Wt6m) {
        this.p2Wt6m = p2Wt6m;
    }

    public Date getP3Date12m() {
        return p3Date12m;
    }

    public void setP3Date12m(Date p3Date12m) {
        this.p3Date12m = p3Date12m;
    }

    public Date getP3Date15m() {
        return p3Date15m;
    }

    public void setP3Date15m(Date p3Date15m) {
        this.p3Date15m = p3Date15m;
    }

    public Date getP3Date9m() {
        return p3Date9m;
    }

    public void setP3Date9m(Date p3Date9m) {
        this.p3Date9m = p3Date9m;
    }

    public String getP3Development12m() {
        return p3Development12m;
    }

    public void setP3Development12m(String p3Development12m) {
        this.p3Development12m = p3Development12m;
    }

    public String getP3Development15m() {
        return p3Development15m;
    }

    public void setP3Development15m(String p3Development15m) {
        this.p3Development15m = p3Development15m;
    }

    public String getP3Development9m() {
        return p3Development9m;
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
        return p3Hc12m;
    }

    public void setP3Hc12m(String p3Hc12m) {
        this.p3Hc12m = p3Hc12m;
    }

    public String getP3Hc15m() {
        return p3Hc15m;
    }

    public void setP3Hc15m(String p3Hc15m) {
        this.p3Hc15m = p3Hc15m;
    }

    public String getP3Hc9m() {
        return p3Hc9m;
    }

    public void setP3Hc9m(String p3Hc9m) {
        this.p3Hc9m = p3Hc9m;
    }

    public String getP3Ht12m() {
        return p3Ht12m;
    }

    public void setP3Ht12m(String p3Ht12m) {
        this.p3Ht12m = p3Ht12m;
    }

    public String getP3Ht15m() {
        return p3Ht15m;
    }

    public void setP3Ht15m(String p3Ht15m) {
        this.p3Ht15m = p3Ht15m;
    }

    public String getP3Ht9m() {
        return p3Ht9m;
    }

    public void setP3Ht9m(String p3Ht9m) {
        this.p3Ht9m = p3Ht9m;
    }

    public String getP3Nutrition12m() {
        return p3Nutrition12m;
    }

    public void setP3Nutrition12m(String p3Nutrition12m) {
        this.p3Nutrition12m = p3Nutrition12m;
    }

    public String getP3Nutrition15m() {
        return p3Nutrition15m;
    }

    public void setP3Nutrition15m(String p3Nutrition15m) {
        this.p3Nutrition15m = p3Nutrition15m;
    }

    public String getP3Nutrition9m() {
        return p3Nutrition9m;
    }

    public void setP3Nutrition9m(String p3Nutrition9m) {
        this.p3Nutrition9m = p3Nutrition9m;
    }

    public String getP3_pConcern12m() {
        return p3_pConcern12m;
    }

    public void setP3_pConcern12m(String p3_pConcern12m) {
        this.p3_pConcern12m = p3_pConcern12m;
    }

    public String getP3_pConcern15m() {
        return p3_pConcern15m;
    }

    public void setP3_pConcern15m(String p3_pConcern15m) {
        this.p3_pConcern15m = p3_pConcern15m;
    }

    public String getP3_pConcern9m() {
        return p3_pConcern9m;
    }

    public void setP3_pConcern9m(String p3_pConcern9m) {
        this.p3_pConcern9m = p3_pConcern9m;
    }

    public String getP3Physical12m() {
        return p3Physical12m;
    }

    public void setP3Physical12m(String p3Physical12m) {
        this.p3Physical12m = p3Physical12m;
    }

    public String getP3Physical15m() {
        return p3Physical15m;
    }

    public void setP3Physical15m(String p3Physical15m) {
        this.p3Physical15m = p3Physical15m;
    }

    public String getP3Physical9m() {
        return p3Physical9m;
    }

    public void setP3Physical9m(String p3Physical9m) {
        this.p3Physical9m = p3Physical9m;
    }

    public String getP3Problems12m() {
        return p3Problems12m;
    }

    public void setP3Problems12m(String p3Problems12m) {
        this.p3Problems12m = p3Problems12m;
    }

    public String getP3Problems15m() {
        return p3Problems15m;
    }

    public void setP3Problems15m(String p3Problems15m) {
        this.p3Problems15m = p3Problems15m;
    }

    public String getP3Problems9m() {
        return p3Problems9m;
    }

    public void setP3Problems9m(String p3Problems9m) {
        this.p3Problems9m = p3Problems9m;
    }

    public String getP3Signature12m() {
        return p3Signature12m;
    }

    public void setP3Signature12m(String p3Signature12m) {
        this.p3Signature12m = p3Signature12m;
    }

    public String getP3Signature15m() {
        return p3Signature15m;
    }

    public void setP3Signature15m(String p3Signature15m) {
        this.p3Signature15m = p3Signature15m;
    }

    public String getP3Signature9m() {
        return p3Signature9m;
    }

    public void setP3Signature9m(String p3Signature9m) {
        this.p3Signature9m = p3Signature9m;
    }

    public String getP3Wt12m() {
        return p3Wt12m;
    }

    public void setP3Wt12m(String p3Wt12m) {
        this.p3Wt12m = p3Wt12m;
    }

    public String getP3Wt15m() {
        return p3Wt15m;
    }

    public void setP3Wt15m(String p3Wt15m) {
        this.p3Wt15m = p3Wt15m;
    }

    public String getP3Wt9m() {
        return p3Wt9m;
    }

    public void setP3Wt9m(String p3Wt9m) {
        this.p3Wt9m = p3Wt9m;
    }

    public Date getP4Date18m() {
        return p4Date18m;
    }

    public void setP4Date18m(Date p4Date18m) {
        this.p4Date18m = p4Date18m;
    }

    public Date getP4Date24m() {
        return p4Date24m;
    }

    public void setP4Date24m(Date p4Date24m) {
        this.p4Date24m = p4Date24m;
    }

    public Date getP4Date48m() {
        return p4Date48m;
    }

    public void setP4Date48m(Date p4Date48m) {
        this.p4Date48m = p4Date48m;
    }

    public String getP4Development18m() {
        return p4Development18m;
    }

    public void setP4Development18m(String p4Development18m) {
        this.p4Development18m = p4Development18m;
    }

    public String getP4Development24m() {
        return p4Development24m;
    }

    public void setP4Development24m(String p4Development24m) {
        this.p4Development24m = p4Development24m;
    }

    public String getP4Development36m() {
        return p4Development36m;
    }

    public void setP4Development36m(String p4Development36m) {
        this.p4Development36m = p4Development36m;
    }

    public String getP4Development48m() {
        return p4Development48m;
    }

    public void setP4Development48m(String p4Development48m) {
        this.p4Development48m = p4Development48m;
    }

    public String getP4Development60m() {
        return p4Development60m;
    }

    public void setP4Development60m(String p4Development60m) {
        this.p4Development60m = p4Development60m;
    }

    public String getP4Education18m() {
        return p4Education18m;
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
        return p4Education48m;
    }

    public void setP4Education48m(String p4Education48m) {
        this.p4Education48m = p4Education48m;
    }

    public String getP4Hc18m() {
        return p4Hc18m;
    }

    public void setP4Hc18m(String p4Hc18m) {
        this.p4Hc18m = p4Hc18m;
    }

    public String getP4Hc24m() {
        return p4Hc24m;
    }

    public void setP4Hc24m(String p4Hc24m) {
        this.p4Hc24m = p4Hc24m;
    }

    public String getP4Ht18m() {
        return p4Ht18m;
    }

    public void setP4Ht18m(String p4Ht18m) {
        this.p4Ht18m = p4Ht18m;
    }

    public String getP4Ht24m() {
        return p4Ht24m;
    }

    public void setP4Ht24m(String p4Ht24m) {
        this.p4Ht24m = p4Ht24m;
    }

    public String getP4Ht48m() {
        return p4Ht48m;
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
        return p4Nippisingattained;
    }

    public void setP4Nippisingattained(String p4Nippisingattained) {
        this.p4Nippisingattained = p4Nippisingattained;
    }

    public String getP4Nutrition18m() {
        return p4Nutrition18m;
    }

    public void setP4Nutrition18m(String p4Nutrition18m) {
        this.p4Nutrition18m = p4Nutrition18m;
    }

    public String getP4Nutrition24m() {
        return p4Nutrition24m;
    }

    public void setP4Nutrition24m(String p4Nutrition24m) {
        this.p4Nutrition24m = p4Nutrition24m;
    }

    public String getP4Nutrition48m() {
        return p4Nutrition48m;
    }

    public void setP4Nutrition48m(String p4Nutrition48m) {
        this.p4Nutrition48m = p4Nutrition48m;
    }

    public String getP4_pConcern18m() {
        return p4_pConcern18m;
    }

    public void setP4_pConcern18m(String p4_pConcern18m) {
        this.p4_pConcern18m = p4_pConcern18m;
    }

    public String getP4_pConcern24m() {
        return p4_pConcern24m;
    }

    public void setP4_pConcern24m(String p4_pConcern24m) {
        this.p4_pConcern24m = p4_pConcern24m;
    }

    public String getP4_pConcern48m() {
        return p4_pConcern48m;
    }

    public void setP4_pConcern48m(String p4_pConcern48m) {
        this.p4_pConcern48m = p4_pConcern48m;
    }

    public String getP4Physical18m() {
        return p4Physical18m;
    }

    public void setP4Physical18m(String p4Physical18m) {
        this.p4Physical18m = p4Physical18m;
    }

    public String getP4Physical24m() {
        return p4Physical24m;
    }

    public void setP4Physical24m(String p4Physical24m) {
        this.p4Physical24m = p4Physical24m;
    }

    public String getP4Physical48m() {
        return p4Physical48m;
    }

    public void setP4Physical48m(String p4Physical48m) {
        this.p4Physical48m = p4Physical48m;
    }

    public String getP4Problems18m() {
        return p4Problems18m;
    }

    public void setP4Problems18m(String p4Problems18m) {
        this.p4Problems18m = p4Problems18m;
    }

    public String getP4Problems24m() {
        return p4Problems24m;
    }

    public void setP4Problems24m(String p4Problems24m) {
        this.p4Problems24m = p4Problems24m;
    }

    public String getP4Problems48m() {
        return p4Problems48m;
    }

    public void setP4Problems48m(String p4Problems48m) {
        this.p4Problems48m = p4Problems48m;
    }

    public String getP4Signature18m() {
        return p4Signature18m;
    }

    public void setP4Signature18m(String p4Signature18m) {
        this.p4Signature18m = p4Signature18m;
    }

    public String getP4Signature24m() {
        return p4Signature24m;
    }

    public void setP4Signature24m(String p4Signature24m) {
        this.p4Signature24m = p4Signature24m;
    }

    public String getP4Signature48m() {
        return p4Signature48m;
    }

    public void setP4Signature48m(String p4Signature48m) {
        this.p4Signature48m = p4Signature48m;
    }

    public String getP4Wt18m() {
        return p4Wt18m;
    }

    public void setP4Wt18m(String p4Wt18m) {
        this.p4Wt18m = p4Wt18m;
    }

    public String getP4Wt24m() {
        return p4Wt24m;
    }

    public void setP4Wt24m(String p4Wt24m) {
        this.p4Wt24m = p4Wt24m;
    }

    public String getP4Wt48m() {
        return p4Wt48m;
    }

    public void setP4Wt48m(String p4Wt48m) {
        this.p4Wt48m = p4Wt48m;
    }

    public Date getP5_1GiveDtRota() {
        return p5_1GiveDtRota;
    }

    public void setP5_1GiveDtRota(Date p5_1GiveDtRota) {
        this.p5_1GiveDtRota = p5_1GiveDtRota;
    }

    public Date getP5_2GiveDtRota() {
        return p5_2GiveDtRota;
    }

    public void setP5_2GiveDtRota(Date p5_2GiveDtRota) {
        this.p5_2GiveDtRota = p5_2GiveDtRota;
    }

    public Date getP5_3GiveDtRota() {
        return p5_3GiveDtRota;
    }

    public void setP5_3GiveDtRota(Date p5_3GiveDtRota) {
        this.p5_3GiveDtRota = p5_3GiveDtRota;
    }

    public Date getP5_1GiveDtHib() {
        return p5_1GiveDtHib;
    }

    public void setP5_1GiveDtHib(Date p5_1GiveDtHib) {
        this.p5_1GiveDtHib = p5_1GiveDtHib;
    }

    public Date getP5_2GiveDtHib() {
        return p5_2GiveDtHib;
    }

    public void setP5_2GiveDtHib(Date p5_2GiveDtHib) {
        this.p5_2GiveDtHib = p5_2GiveDtHib;
    }

    public Date getP5_3GiveDtHib() {
        return p5_3GiveDtHib;
    }

    public void setP5_3GiveDtHib(Date p5_3GiveDtHib) {
        this.p5_3GiveDtHib = p5_3GiveDtHib;
    }

    public Date getP5_4GiveDtHib() {
        return p5_4GiveDtHib;
    }

    public void setP5_4GiveDtHib(Date p5_4GiveDtHib) {
        this.p5_4GiveDtHib = p5_4GiveDtHib;
    }

    public Date getP5_1GiveDtPneu() {
        return p5_1GiveDtPneu;
    }

    public void setP5_1GiveDtPneu(Date p5_1GiveDtPneu) {
        this.p5_1GiveDtPneu = p5_1GiveDtPneu;
    }

    public Date getP5_2GiveDtPneu() {
        return p5_2GiveDtPneu;
    }

    public void setP5_2GiveDtPneu(Date p5_2GiveDtPneu) {
        this.p5_2GiveDtPneu = p5_2GiveDtPneu;
    }

    public Date getP5_3GiveDtPneu() {
        return p5_3GiveDtPneu;
    }

    public void setP5_3GiveDtPneu(Date p5_3GiveDtPneu) {
        this.p5_3GiveDtPneu = p5_3GiveDtPneu;
    }

    public Date getP5_4GiveDtPneu() {
        return p5_4GiveDtPneu;
    }

    public void setP5_4GiveDtPneu(Date p5_4GiveDtPneu) {
        this.p5_4GiveDtPneu = p5_4GiveDtPneu;
    }

    public Date getP5_1GiveDtMenCon() {
        return p5_1GiveDtMenCon;
    }

    public void setP5_1GiveDtMenCon(Date p5_1GiveDtMenCon) {
        this.p5_1GiveDtMenCon = p5_1GiveDtMenCon;
    }

    public Date getP5_2GiveDtMenCon() {
        return p5_2GiveDtMenCon;
    }

    public void setP5_2GiveDtMenCon(Date p5_2GiveDtMenCon) {
        this.p5_2GiveDtMenCon = p5_2GiveDtMenCon;
    }

    public Date getP5_3GiveDtMenCon() {
        return p5_3GiveDtMenCon;
    }

    public void setP5_3GiveDtMenCon(Date p5_3GiveDtMenCon) {
        this.p5_3GiveDtMenCon = p5_3GiveDtMenCon;
    }

    public Date getP5_1ExpDtRota() {
        return p5_1ExpDtRota;
    }

    public void setP5_1ExpDtRota(Date p5_1ExpDtRota) {
        this.p5_1ExpDtRota = p5_1ExpDtRota;
    }

    public Date getP5_2ExpDtRota() {
        return p5_2ExpDtRota;
    }

    public void setP5_2ExpDtRota(Date p5_2ExpDtRota) {
        this.p5_2ExpDtRota = p5_2ExpDtRota;
    }

    public Date getP5_3ExpDtRota() {
        return p5_3ExpDtRota;
    }

    public void setP5_3ExpDtRota(Date p5_3ExpDtRota) {
        this.p5_3ExpDtRota = p5_3ExpDtRota;
    }

    public Date getP5_1ExpDtHib() {
        return p5_1ExpDtHib;
    }

    public void setP5_1ExpDtHib(Date p5_1ExpDtHib) {
        this.p5_1ExpDtHib = p5_1ExpDtHib;
    }

    public Date getP5_2ExpDtHib() {
        return p5_2ExpDtHib;
    }

    public void setP5_2ExpDtHib(Date p5_2ExpDtHib) {
        this.p5_2ExpDtHib = p5_2ExpDtHib;
    }

    public Date getP5_3ExpDtHib() {
        return p5_3ExpDtHib;
    }

    public void setP5_3ExpDtHib(Date p5_3ExpDtHib) {
        this.p5_3ExpDtHib = p5_3ExpDtHib;
    }

    public Date getP5_4ExpDtHib() {
        return p5_4ExpDtHib;
    }

    public void setP5_4ExpDtHib(Date p5_4ExpDtHib) {
        this.p5_4ExpDtHib = p5_4ExpDtHib;
    }

    public Date getP5_1ExpDtPneu() {
        return p5_1ExpDtPneu;
    }

    public void setP5_1ExpDtPneu(Date p5_1ExpDtPneu) {
        this.p5_1ExpDtPneu = p5_1ExpDtPneu;
    }

    public Date getP5_2ExpDtPneu() {
        return p5_2ExpDtPneu;
    }

    public void setP5_2ExpDtPneu(Date p5_2ExpDtPneu) {
        this.p5_2ExpDtPneu = p5_2ExpDtPneu;
    }

    public Date getP5_3ExpDtPneu() {
        return p5_3ExpDtPneu;
    }

    public void setP5_3ExpDtPneu(Date p5_3ExpDtPneu) {
        this.p5_3ExpDtPneu = p5_3ExpDtPneu;
    }

    public Date getP5_4ExpDtPneu() {
        return p5_4ExpDtPneu;
    }

    public void setP5_4ExpDtPneu(Date p5_4ExpDtPneu) {
        this.p5_4ExpDtPneu = p5_4ExpDtPneu;
    }

    public Date getP5_1ExpDtMenCon() {
        return p5_1ExpDtMenCon;
    }

    public void setP5_1ExpDtMenCon(Date p5_1ExpDtMenCon) {
        this.p5_1ExpDtMenCon = p5_1ExpDtMenCon;
    }

    public Date getP5_2ExpDtMenCon() {
        return p5_2ExpDtMenCon;
    }

    public void setP5_2ExpDtMenCon(Date p5_2ExpDtMenCon) {
        this.p5_2ExpDtMenCon = p5_2ExpDtMenCon;
    }

    public Date getP5_3ExpDtMenCon() {
        return p5_3ExpDtMenCon;
    }

    public void setP5_3ExpDtMenCon(Date p5_3ExpDtMenCon) {
        this.p5_3ExpDtMenCon = p5_3ExpDtMenCon;
    }

    public Date getP5_1GiveDtHepa() {
        return p5_1GiveDtHepa;
    }

    public void setP5_1GiveDtHepa(Date p5_1GiveDtHepa) {
        this.p5_1GiveDtHepa = p5_1GiveDtHepa;
    }

    public Date getP5_2GiveDtHepa() {
        return p5_2GiveDtHepa;
    }

    public void setP5_2GiveDtHepa(Date p5_2GiveDtHepa) {
        this.p5_2GiveDtHepa = p5_2GiveDtHepa;
    }

    public Date getP5_3GiveDtHepa() {
        return p5_3GiveDtHepa;
    }

    public void setP5_3GiveDtHepa(Date p5_3GiveDtHepa) {
        this.p5_3GiveDtHepa = p5_3GiveDtHepa;
    }

    public Date getP5_1ExpDtHepa() {
        return p5_1ExpDtHepa;
    }

    public void setP5_1ExpDtHepa(Date p5_1ExpDtHepa) {
        this.p5_1ExpDtHepa = p5_1ExpDtHepa;
    }

    public Date getP5_2ExpDtHepa() {
        return p5_2ExpDtHepa;
    }

    public void setP5_2ExpDtHepa(Date p5_2ExpDtHepa) {
        this.p5_2ExpDtHepa = p5_2ExpDtHepa;
    }

    public Date getP5_3ExpDtHepa() {
        return p5_3ExpDtHepa;
    }

    public void setP5_3ExpDtHepa(Date p5_3ExpDtHepa) {
        this.p5_3ExpDtHepa = p5_3ExpDtHepa;
    }

    public Date getP5_1GiveDtMMR() {
        return p5_1GiveDtMMR;
    }

    public void setP5_1GiveDtMMR(Date p5_1GiveDtMMR) {
        this.p5_1GiveDtMMR = p5_1GiveDtMMR;
    }

    public Date getP5_2GiveDtMMR() {
        return p5_2GiveDtMMR;
    }

    public void setP5_2GiveDtMMR(Date p5_2GiveDtMMR) {
        this.p5_2GiveDtMMR = p5_2GiveDtMMR;
    }

    public Date getP5_1ExpDtMMR() {
        return p5_1ExpDtMMR;
    }

    public void setP5_1ExpDtMMR(Date p5_1ExpDtMMR) {
        this.p5_1ExpDtMMR = p5_1ExpDtMMR;
    }

    public Date getP5_2ExpDtMMR() {
        return p5_2ExpDtMMR;
    }

    public void setP5_2ExpDtMMR(Date p5_2ExpDtMMR) {
        this.p5_2ExpDtMMR = p5_2ExpDtMMR;
    }

    public Date getP5_1GiveDtVaricella() {
        return p5_1GiveDtVaricella;
    }

    public void setP5_1GiveDtVaricella(Date p5_1GiveDtVaricella) {
        this.p5_1GiveDtVaricella = p5_1GiveDtVaricella;
    }

    public Date getP5_2GiveDtVaricella() {
        return p5_2GiveDtVaricella;
    }

    public void setP5_2GiveDtVaricella(Date p5_2GiveDtVaricella) {
        this.p5_2GiveDtVaricella = p5_2GiveDtVaricella;
    }

    public Date getP5_1ExpDtVaricella() {
        return p5_1ExpDtVaricella;
    }

    public void setP5_1ExpDtVaricella(Date p5_1ExpDtVaricella) {
        this.p5_1ExpDtVaricella = p5_1ExpDtVaricella;
    }

    public Date getP5_2ExpDtVaricella() {
        return p5_2ExpDtVaricella;
    }

    public void setP5_2ExpDtVaricella(Date p5_2ExpDtVaricella) {
        this.p5_2ExpDtVaricella = p5_2ExpDtVaricella;
    }

    public Date getP5_1GiveDtdTapIpv() {
        return p5_1GiveDtdTapIpv;
    }

    public void setP5_1GiveDtdTapIpv(Date p5_1GiveDtdTapIpv) {
        this.p5_1GiveDtdTapIpv = p5_1GiveDtdTapIpv;
    }

    public Date getP5_1ExpDtdTapIpv() {
        return p5_1ExpDtdTapIpv;
    }

    public void setP5_1ExpDtdTapIpv(Date p5_1ExpDtdTapIpv) {
        this.p5_1ExpDtdTapIpv = p5_1ExpDtdTapIpv;
    }

    public Date getP5_1GiveDtdTap() {
        return p5_1GiveDtdTap;
    }

    public void setP5_1GiveDtdTap(Date p5_1GiveDtdTap) {
        this.p5_1GiveDtdTap = p5_1GiveDtdTap;
    }

    public Date getP5_1ExpDtdTap() {
        return p5_1ExpDtdTap;
    }

    public void setP5_1ExpDtdTap(Date p5_1ExpDtdTap) {
        this.p5_1ExpDtdTap = p5_1ExpDtdTap;
    }

    public Date getP5_1GiveDtInfluenza() {
        return p5_1GiveDtInfluenza;
    }

    public void setP5_1GiveDtInfluenza(Date p5_1GiveDtInfluenza) {
        this.p5_1GiveDtInfluenza = p5_1GiveDtInfluenza;
    }

    public Date getP5_2GiveDtInfluenza() {
        return p5_2GiveDtInfluenza;
    }

    public void setP5_2GiveDtInfluenza(Date p5_2GiveDtInfluenza) {
        this.p5_2GiveDtInfluenza = p5_2GiveDtInfluenza;
    }

    public Date getP5_3GiveDtInfluenza() {
        return p5_3GiveDtInfluenza;
    }

    public void setP5_3GiveDtInfluenza(Date p5_3GiveDtInfluenza) {
        this.p5_3GiveDtInfluenza = p5_3GiveDtInfluenza;
    }

    public Date getP5_1ExpDtInfluenza() {
        return p5_1ExpDtInfluenza;
    }

    public void setP5_1ExpDtInfluenza(Date p5_1ExpDtInfluenza) {
        this.p5_1ExpDtInfluenza = p5_1ExpDtInfluenza;
    }

    public Date getP5_2ExpDtInfluenza() {
        return p5_2ExpDtInfluenza;
    }

    public void setP5_2ExpDtInfluenza(Date p5_2ExpDtInfluenza) {
        this.p5_2ExpDtInfluenza = p5_2ExpDtInfluenza;
    }

    public Date getP5_3ExpDtInfluenza() {
        return p5_3ExpDtInfluenza;
    }

    public void setP5_3ExpDtInfluenza(Date p5_3ExpDtInfluenza) {
        this.p5_3ExpDtInfluenza = p5_3ExpDtInfluenza;
    }

    public Date getP5_1GiveDtHpv() {
        return p5_1GiveDtHpv;
    }

    public void setP5_1GiveDtHpv(Date p5_1GiveDtHpv) {
        this.p5_1GiveDtHpv = p5_1GiveDtHpv;
    }

    public Date getP5_2GiveDtHpv() {
        return p5_2GiveDtHpv;
    }

    public void setP5_2GiveDtHpv(Date p5_2GiveDtHpv) {
        this.p5_2GiveDtHpv = p5_2GiveDtHpv;
    }

    public Date getP5_3GiveDtHpv() {
        return p5_3GiveDtHpv;
    }

    public void setP5_3GiveDtHpv(Date p5_3GiveDtHpv) {
        this.p5_3GiveDtHpv = p5_3GiveDtHpv;
    }

    public Date getP5_1ExpDtHpv() {
        return p5_1ExpDtHpv;
    }

    public void setP5_1ExpDtHpv(Date p5_1ExpDtHpv) {
        this.p5_1ExpDtHpv = p5_1ExpDtHpv;
    }

    public Date getP5_2ExpDtHpv() {
        return p5_2ExpDtHpv;
    }

    public void setP5_2ExpDtHpv(Date p5_2ExpDtHpv) {
        this.p5_2ExpDtHpv = p5_2ExpDtHpv;
    }

    public Date getP5_3ExpDtHpv() {
        return p5_3ExpDtHpv;
    }

    public void setP5_3ExpDtHpv(Date p5_3ExpDtHpv) {
        this.p5_3ExpDtHpv = p5_3ExpDtHpv;
    }

    public Date getP5_1GiveDtOther() {
        return p5_1GiveDtOther;
    }

    public void setP5_1GiveDtOther(Date p5_1GiveDtOther) {
        this.p5_1GiveDtOther = p5_1GiveDtOther;
    }

    public Date getP5_1ExpDtOther() {
        return p5_1ExpDtOther;
    }

    public void setP5_1ExpDtOther(Date p5_1ExpDtOther) {
        this.p5_1ExpDtOther = p5_1ExpDtOther;
    }

    public String getP5_1InjeRota() {
        return p5_1InjeRota;
    }

    public void setP5_1InjeRota(String p5_1InjeRota) {
        this.p5_1InjeRota = p5_1InjeRota;
    }

    public String getP5_2InjeRota() {
        return p5_2InjeRota;
    }

    public void setP5_2InjeRota(String p5_2InjeRota) {
        this.p5_2InjeRota = p5_2InjeRota;
    }

    public String getP5_3InjeRota() {
        return p5_3InjeRota;
    }

    public void setP5_3InjeRota(String p5_3InjeRota) {
        this.p5_3InjeRota = p5_3InjeRota;
    }

    public String getP5_1LotNRota() {
        return p5_1LotNRota;
    }

    public void setP5_1LotNRota(String p5_1LotNRota) {
        this.p5_1LotNRota = p5_1LotNRota;
    }

    public String getP5_2LotNRota() {
        return p5_2LotNRota;
    }

    public void setP5_2LotNRota(String p5_2LotNRota) {
        this.p5_2LotNRota = p5_2LotNRota;
    }

    public String getP5_3LotNRota() {
        return p5_3LotNRota;
    }

    public void setP5_3LotNRota(String p5_3LotNRota) {
        this.p5_3LotNRota = p5_3LotNRota;
    }

    public String getP5_1InitialRota() {
        return p5_1InitialRota;
    }

    public void setP5_1InitialRota(String p5_1InitialRota) {
        this.p5_1InitialRota = p5_1InitialRota;
    }

    public String getP5_2InitialRota() {
        return p5_2InitialRota;
    }

    public void setP5_2InitialRota(String p5_2InitialRota) {
        this.p5_2InitialRota = p5_2InitialRota;
    }

    public String getP5_3InitialRota() {
        return p5_3InitialRota;
    }

    public void setP5_3InitialRota(String p5_3InitialRota) {
        this.p5_3InitialRota = p5_3InitialRota;
    }

    public String getP5_1CommentsRota() {
        return p5_1CommentsRota;
    }

    public void setP5_1CommentsRota(String p5_1CommentsRota) {
        this.p5_1CommentsRota = p5_1CommentsRota;
    }

    public String getP5_2CommentsRota() {
        return p5_2CommentsRota;
    }

    public void setP5_2CommentsRota(String p5_2CommentsRota) {
        this.p5_2CommentsRota = p5_2CommentsRota;
    }

    public String getP5_3CommentsRota() {
        return p5_3CommentsRota;
    }

    public void setP5_3CommentsRota(String p5_3CommentsRota) {
        this.p5_3CommentsRota = p5_3CommentsRota;
    }

    public String getP5_1InjeHib() {
        return p5_1InjeHib;
    }

    public void setP5_1InjeHib(String p5_1InjeHib) {
        this.p5_1InjeHib = p5_1InjeHib;
    }

    public String getP5_2InjeHib() {
        return p5_2InjeHib;
    }

    public void setP5_2InjeHib(String p5_2InjeHib) {
        this.p5_2InjeHib = p5_2InjeHib;
    }

    public String getP5_3InjeHib() {
        return p5_3InjeHib;
    }

    public void setP5_3InjeHib(String p5_3InjeHib) {
        this.p5_3InjeHib = p5_3InjeHib;
    }

    public String getP5_4InjeHib() {
        return p5_4InjeHib;
    }

    public void setP5_4InjeHib(String p5_4InjeHib) {
        this.p5_4InjeHib = p5_4InjeHib;
    }

    public String getP5_1LotNHib() {
        return p5_1LotNHib;
    }

    public void setP5_1LotNHib(String p5_1LotNHib) {
        this.p5_1LotNHib = p5_1LotNHib;
    }

    public String getP5_2LotNHib() {
        return p5_2LotNHib;
    }

    public void setP5_2LotNHib(String p5_2LotNHib) {
        this.p5_2LotNHib = p5_2LotNHib;
    }

    public String getP5_3LotNHib() {
        return p5_3LotNHib;
    }

    public void setP5_3LotNHib(String p5_3LotNHib) {
        this.p5_3LotNHib = p5_3LotNHib;
    }

    public String getP5_4LotNHib() {
        return p5_4LotNHib;
    }

    public void setP5_4LotNHib(String p5_4LotNHib) {
        this.p5_4LotNHib = p5_4LotNHib;
    }

    public String getP5_1InitialHib() {
        return p5_1InitialHib;
    }

    public void setP5_1InitialHib(String p5_1InitialHib) {
        this.p5_1InitialHib = p5_1InitialHib;
    }

    public String getP5_2InitialHib() {
        return p5_2InitialHib;
    }

    public void setP5_2InitialHib(String p5_2InitialHib) {
        this.p5_2InitialHib = p5_2InitialHib;
    }

    public String getP5_3InitialHib() {
        return p5_3InitialHib;
    }

    public void setP5_3InitialHib(String p5_3InitialHib) {
        this.p5_3InitialHib = p5_3InitialHib;
    }

    public String getP5_4InitialHib() {
        return p5_4InitialHib;
    }

    public void setP5_4InitialHib(String p5_4InitialHib) {
        this.p5_4InitialHib = p5_4InitialHib;
    }

    public String getP5_1CommentsHib() {
        return p5_1CommentsHib;
    }

    public void setP5_1CommentsHib(String p5_1CommentsHib) {
        this.p5_1CommentsHib = p5_1CommentsHib;
    }

    public String getP5_2CommentsHib() {
        return p5_2CommentsHib;
    }

    public void setP5_2CommentsHib(String p5_2CommentsHib) {
        this.p5_2CommentsHib = p5_2CommentsHib;
    }

    public String getP5_3CommentsHib() {
        return p5_3CommentsHib;
    }

    public void setP5_3CommentsHib(String p5_3CommentsHib) {
        this.p5_3CommentsHib = p5_3CommentsHib;
    }

    public String getP5_4CommentsHib() {
        return p5_4CommentsHib;
    }

    public void setP5_4CommentsHib(String p5_4CommentsHib) {
        this.p5_4CommentsHib = p5_4CommentsHib;
    }

    public String getP5_1InjePneu() {
        return p5_1InjePneu;
    }

    public void setP5_1InjePneu(String p5_1InjePneu) {
        this.p5_1InjePneu = p5_1InjePneu;
    }

    public String getP5_2InjePneu() {
        return p5_2InjePneu;
    }

    public void setP5_2InjePneu(String p5_2InjePneu) {
        this.p5_2InjePneu = p5_2InjePneu;
    }

    public String getP5_3InjePneu() {
        return p5_3InjePneu;
    }

    public void setP5_3InjePneu(String p5_3InjePneu) {
        this.p5_3InjePneu = p5_3InjePneu;
    }

    public String getP5_4InjePneu() {
        return p5_4InjePneu;
    }

    public void setP5_4InjePneu(String p5_4InjePneu) {
        this.p5_4InjePneu = p5_4InjePneu;
    }

    public String getP5_1LotNPneu() {
        return p5_1LotNPneu;
    }

    public void setP5_1LotNPneu(String p5_1LotNPneu) {
        this.p5_1LotNPneu = p5_1LotNPneu;
    }

    public String getP5_2LotNPneu() {
        return p5_2LotNPneu;
    }

    public void setP5_2LotNPneu(String p5_2LotNPneu) {
        this.p5_2LotNPneu = p5_2LotNPneu;
    }

    public String getP5_3LotNPneu() {
        return p5_3LotNPneu;
    }

    public void setP5_3LotNPneu(String p5_3LotNPneu) {
        this.p5_3LotNPneu = p5_3LotNPneu;
    }

    public String getP5_4LotNPneu() {
        return p5_4LotNPneu;
    }

    public void setP5_4LotNPneu(String p5_4LotNPneu) {
        this.p5_4LotNPneu = p5_4LotNPneu;
    }

    public String getP5_1InitialPneu() {
        return p5_1InitialPneu;
    }

    public void setP5_1InitialPneu(String p5_1InitialPneu) {
        this.p5_1InitialPneu = p5_1InitialPneu;
    }

    public String getP5_2InitialPneu() {
        return p5_2InitialPneu;
    }

    public void setP5_2InitialPneu(String p5_2InitialPneu) {
        this.p5_2InitialPneu = p5_2InitialPneu;
    }

    public String getP5_3InitialPneu() {
        return p5_3InitialPneu;
    }

    public void setP5_3InitialPneu(String p5_3InitialPneu) {
        this.p5_3InitialPneu = p5_3InitialPneu;
    }

    public String getP5_4InitialPneu() {
        return p5_4InitialPneu;
    }

    public void setP5_4InitialPneu(String p5_4InitialPneu) {
        this.p5_4InitialPneu = p5_4InitialPneu;
    }

    public String getP5_1CommentsPneu() {
        return p5_1CommentsPneu;
    }

    public void setP5_1CommentsPneu(String p5_1CommentsPneu) {
        this.p5_1CommentsPneu = p5_1CommentsPneu;
    }

    public String getP5_2CommentsPneu() {
        return p5_2CommentsPneu;
    }

    public void setP5_2CommentsPneu(String p5_2CommentsPneu) {
        this.p5_2CommentsPneu = p5_2CommentsPneu;
    }

    public String getP5_3CommentsPneu() {
        return p5_3CommentsPneu;
    }

    public void setP5_3CommentsPneu(String p5_3CommentsPneu) {
        this.p5_3CommentsPneu = p5_3CommentsPneu;
    }

    public String getP5_4CommentsPneu() {
        return p5_4CommentsPneu;
    }

    public void setP5_4CommentsPneu(String p5_4CommentsPneu) {
        this.p5_4CommentsPneu = p5_4CommentsPneu;
    }

    public String getP5_1InjeMenCon() {
        return p5_1InjeMenCon;
    }

    public void setP5_1InjeMenCon(String p5_1InjeMenCon) {
        this.p5_1InjeMenCon = p5_1InjeMenCon;
    }

    public String getP5_2InjeMenCon() {
        return p5_2InjeMenCon;
    }

    public void setP5_2InjeMenCon(String p5_2InjeMenCon) {
        this.p5_2InjeMenCon = p5_2InjeMenCon;
    }

    public String getP5_3InjeMenCon() {
        return p5_3InjeMenCon;
    }

    public void setP5_3InjeMenCon(String p5_3InjeMenCon) {
        this.p5_3InjeMenCon = p5_3InjeMenCon;
    }

    public String getP5_1LotNMenCon() {
        return p5_1LotNMenCon;
    }

    public void setP5_1LotNMenCon(String p5_1LotNMenCon) {
        this.p5_1LotNMenCon = p5_1LotNMenCon;
    }

    public String getP5_2LotNMenCon() {
        return p5_2LotNMenCon;
    }

    public void setP5_2LotNMenCon(String p5_2LotNMenCon) {
        this.p5_2LotNMenCon = p5_2LotNMenCon;
    }

    public String getP5_3LotNMenCon() {
        return p5_3LotNMenCon;
    }

    public void setP5_3LotNMenCon(String p5_3LotNMenCon) {
        this.p5_3LotNMenCon = p5_3LotNMenCon;
    }

    public String getP5_1InitialMenCon() {
        return p5_1InitialMenCon;
    }

    public void setP5_1InitialMenCon(String p5_1InitialMenCon) {
        this.p5_1InitialMenCon = p5_1InitialMenCon;
    }

    public String getP5_2InitialMenCon() {
        return p5_2InitialMenCon;
    }

    public void setP5_2InitialMenCon(String p5_2InitialMenCon) {
        this.p5_2InitialMenCon = p5_2InitialMenCon;
    }

    public String getP5_3InitialMenCon() {
        return p5_3InitialMenCon;
    }

    public void setP5_3InitialMenCon(String p5_3InitialMenCon) {
        this.p5_3InitialMenCon = p5_3InitialMenCon;
    }

    public String getP5_1CommentsMenCon() {
        return p5_1CommentsMenCon;
    }

    public void setP5_1CommentsMenCon(String p5_1CommentsMenCon) {
        this.p5_1CommentsMenCon = p5_1CommentsMenCon;
    }

    public String getP5_2CommentsMenCon() {
        return p5_2CommentsMenCon;
    }

    public void setP5_2CommentsMenCon(String p5_2CommentsMenCon) {
        this.p5_2CommentsMenCon = p5_2CommentsMenCon;
    }

    public String getP5_3CommentsMenCon() {
        return p5_3CommentsMenCon;
    }

    public void setP5_3CommentsMenCon(String p5_3CommentsMenCon) {
        this.p5_3CommentsMenCon = p5_3CommentsMenCon;
    }

    public String getP5_1InjeHepa() {
        return p5_1InjeHepa;
    }

    public void setP5_1InjeHepa(String p5_1InjeHepa) {
        this.p5_1InjeHepa = p5_1InjeHepa;
    }

    public String getP5_2InjeHepa() {
        return p5_2InjeHepa;
    }

    public void setP5_2InjeHepa(String p5_2InjeHepa) {
        this.p5_2InjeHepa = p5_2InjeHepa;
    }

    public String getP5_3InjeHepa() {
        return p5_3InjeHepa;
    }

    public void setP5_3InjeHepa(String p5_3InjeHepa) {
        this.p5_3InjeHepa = p5_3InjeHepa;
    }

    public String getP5_1LotNHepa() {
        return p5_1LotNHepa;
    }

    public void setP5_1LotNHepa(String p5_1LotNHepa) {
        this.p5_1LotNHepa = p5_1LotNHepa;
    }

    public String getP5_2LotNHepa() {
        return p5_2LotNHepa;
    }

    public void setP5_2LotNHepa(String p5_2LotNHepa) {
        this.p5_2LotNHepa = p5_2LotNHepa;
    }

    public String getP5_3LotNHepa() {
        return p5_3LotNHepa;
    }

    public void setP5_3LotNHepa(String p5_3LotNHepa) {
        this.p5_3LotNHepa = p5_3LotNHepa;
    }

    public String getP5_1InitialHepa() {
        return p5_1InitialHepa;
    }

    public void setP5_1InitialHepa(String p5_1InitialHepa) {
        this.p5_1InitialHepa = p5_1InitialHepa;
    }

    public String getP5_2InitialHepa() {
        return p5_2InitialHepa;
    }

    public void setP5_2InitialHepa(String p5_2InitialHepa) {
        this.p5_2InitialHepa = p5_2InitialHepa;
    }

    public String getP5_3InitialHepa() {
        return p5_3InitialHepa;
    }

    public void setP5_3InitialHepa(String p5_3InitialHepa) {
        this.p5_3InitialHepa = p5_3InitialHepa;
    }

    public String getP5_1CommentsHepa() {
        return p5_1CommentsHepa;
    }

    public void setP5_1CommentsHepa(String p5_1CommentsHepa) {
        this.p5_1CommentsHepa = p5_1CommentsHepa;
    }

    public String getP5_2CommentsHepa() {
        return p5_2CommentsHepa;
    }

    public void setP5_2CommentsHepa(String p5_2CommentsHepa) {
        this.p5_2CommentsHepa = p5_2CommentsHepa;
    }

    public String getP5_3CommentsHepa() {
        return p5_3CommentsHepa;
    }

    public void setP5_3CommentsHepa(String p5_3CommentsHepa) {
        this.p5_3CommentsHepa = p5_3CommentsHepa;
    }

    public String getP5_1InjeMMR() {
        return p5_1InjeMMR;
    }

    public void setP5_1InjeMMR(String p5_1InjeMMR) {
        this.p5_1InjeMMR = p5_1InjeMMR;
    }

    public String getP5_2InjeMMR() {
        return p5_2InjeMMR;
    }

    public void setP5_2InjeMMR(String p5_2InjeMMR) {
        this.p5_2InjeMMR = p5_2InjeMMR;
    }

    public String getP5_1LotNMMR() {
        return p5_1LotNMMR;
    }

    public void setP5_1LotNMMR(String p5_1LotNMMR) {
        this.p5_1LotNMMR = p5_1LotNMMR;
    }

    public String getP5_2LotNMMR() {
        return p5_2LotNMMR;
    }

    public void setP5_2LotNMMR(String p5_2LotNMMR) {
        this.p5_2LotNMMR = p5_2LotNMMR;
    }

    public String getP5_1InitialMMR() {
        return p5_1InitialMMR;
    }

    public void setP5_1InitialMMR(String p5_1InitialMMR) {
        this.p5_1InitialMMR = p5_1InitialMMR;
    }

    public String getP5_2InitialMMR() {
        return p5_2InitialMMR;
    }

    public void setP5_2InitialMMR(String p5_2InitialMMR) {
        this.p5_2InitialMMR = p5_2InitialMMR;
    }

    public String getP5_1CommentsMMR() {
        return p5_1CommentsMMR;
    }

    public void setP5_1CommentsMMR(String p5_1CommentsMMR) {
        this.p5_1CommentsMMR = p5_1CommentsMMR;
    }

    public String getP5_2CommentsMMR() {
        return p5_2CommentsMMR;
    }

    public void setP5_2CommentsMMR(String p5_2CommentsMMR) {
        this.p5_2CommentsMMR = p5_2CommentsMMR;
    }

    public String getP5_1InjeVaricella() {
        return p5_1InjeVaricella;
    }

    public void setP5_1InjeVaricella(String p5_1InjeVaricella) {
        this.p5_1InjeVaricella = p5_1InjeVaricella;
    }

    public String getP5_2InjeVaricella() {
        return p5_2InjeVaricella;
    }

    public void setP5_2InjeVaricella(String p5_2InjeVaricella) {
        this.p5_2InjeVaricella = p5_2InjeVaricella;
    }

    public String getP5_1LotNVaricella() {
        return p5_1LotNVaricella;
    }

    public void setP5_1LotNVaricella(String p5_1LotNVaricella) {
        this.p5_1LotNVaricella = p5_1LotNVaricella;
    }

    public String getP5_2LotNVaricella() {
        return p5_2LotNVaricella;
    }

    public void setP5_2LotNVaricella(String p5_2LotNVaricella) {
        this.p5_2LotNVaricella = p5_2LotNVaricella;
    }

    public String getP5_1InitialVaricella() {
        return p5_1InitialVaricella;
    }

    public void setP5_1InitialVaricella(String p5_1InitialVaricella) {
        this.p5_1InitialVaricella = p5_1InitialVaricella;
    }

    public String getP5_2InitialVaricella() {
        return p5_2InitialVaricella;
    }

    public void setP5_2InitialVaricella(String p5_2InitialVaricella) {
        this.p5_2InitialVaricella = p5_2InitialVaricella;
    }

    public String getP5_1CommentsVaricella() {
        return p5_1CommentsVaricella;
    }

    public void setP5_1CommentsVaricella(String p5_1CommentsVaricella) {
        this.p5_1CommentsVaricella = p5_1CommentsVaricella;
    }

    public String getP5_2CommentsVaricella() {
        return p5_2CommentsVaricella;
    }

    public void setP5_2CommentsVaricella(String p5_2CommentsVaricella) {
        this.p5_2CommentsVaricella = p5_2CommentsVaricella;
    }

    public String getP5_1InjeDTaPIpv() {
        return p5_1InjeDTaPIpv;
    }

    public void setP5_1InjeDTaPIpv(String p5_1InjeDTaPIpv) {
        this.p5_1InjeDTaPIpv = p5_1InjeDTaPIpv;
    }

    public String getP5_1LotNDTaPIpv() {
        return p5_1LotNDTaPIpv;
    }

    public void setP5_1LotNDTaPIpv(String p5_1LotNDTaPIpv) {
        this.p5_1LotNDTaPIpv = p5_1LotNDTaPIpv;
    }

    public String getP5_1InitialDTaPIpv() {
        return p5_1InitialDTaPIpv;
    }

    public void setP5_1InitialDTaPIpv(String p5_1InitialDTaPIpv) {
        this.p5_1InitialDTaPIpv = p5_1InitialDTaPIpv;
    }

    public String getP5_1CommentsDTaPIpv() {
        return p5_1CommentsDTaPIpv;
    }

    public void setP5_1CommentsDTaPIpv(String p5_1CommentsDTaPIpv) {
        this.p5_1CommentsDTaPIpv = p5_1CommentsDTaPIpv;
    }

    public String getP5_1InjeDTap() {
        return p5_1InjeDTap;
    }

    public void setP5_1InjeDTap(String p5_1InjeDTap) {
        this.p5_1InjeDTap = p5_1InjeDTap;
    }

    public String getP5_1LotNDTap() {
        return p5_1LotNDTap;
    }

    public void setP5_1LotNDTap(String p5_1LotNDTap) {
        this.p5_1LotNDTap = p5_1LotNDTap;
    }

    public String getP5_1InitialDTap() {
        return p5_1InitialDTap;
    }

    public void setP5_1InitialDTap(String p5_1InitialDTap) {
        this.p5_1InitialDTap = p5_1InitialDTap;
    }

    public String getP5_1CommentsDTap() {
        return p5_1CommentsDTap;
    }

    public void setP5_1CommentsDTap(String p5_1CommentsDTap) {
        this.p5_1CommentsDTap = p5_1CommentsDTap;
    }

    public String getP5_1NaciInfluenza() {
        return p5_1NaciInfluenza;
    }

    public void setP5_1NaciInfluenza(String p5_1NaciInfluenza) {
        this.p5_1NaciInfluenza = p5_1NaciInfluenza;
    }

    public String getP5_1InjeInfluenza() {
        return p5_1InjeInfluenza;
    }

    public void setP5_1InjeInfluenza(String p5_1InjeInfluenza) {
        this.p5_1InjeInfluenza = p5_1InjeInfluenza;
    }

    public String getP5_2InjeInfluenza() {
        return p5_2InjeInfluenza;
    }

    public void setP5_2InjeInfluenza(String p5_2InjeInfluenza) {
        this.p5_2InjeInfluenza = p5_2InjeInfluenza;
    }

    public String getP5_3InjeInfluenza() {
        return p5_3InjeInfluenza;
    }

    public void setP5_3InjeInfluenza(String p5_3InjeInfluenza) {
        this.p5_3InjeInfluenza = p5_3InjeInfluenza;
    }

    public String getP5_2NaciInfluenza() {
        return p5_2NaciInfluenza;
    }

    public void setP5_2NaciInfluenza(String p5_2NaciInfluenza) {
        this.p5_2NaciInfluenza = p5_2NaciInfluenza;
    }

    public String getP5_1LotNInfluenza() {
        return p5_1LotNInfluenza;
    }

    public void setP5_1LotNInfluenza(String p5_1LotNInfluenza) {
        this.p5_1LotNInfluenza = p5_1LotNInfluenza;
    }

    public String getP5_2LotNInfluenza() {
        return p5_2LotNInfluenza;
    }

    public void setP5_2LotNInfluenza(String p5_2LotNInfluenza) {
        this.p5_2LotNInfluenza = p5_2LotNInfluenza;
    }

    public String getP5_3LotNInfluenza() {
        return p5_3LotNInfluenza;
    }

    public void setP5_3LotNInfluenza(String p5_3LotNInfluenza) {
        this.p5_3LotNInfluenza = p5_3LotNInfluenza;
    }

    public String getP5_3NaciInfluenza() {
        return p5_3NaciInfluenza;
    }

    public void setP5_3NaciInfluenza(String p5_3NaciInfluenza) {
        this.p5_3NaciInfluenza = p5_3NaciInfluenza;
    }

    public String getP5_1InitialInfluenza() {
        return p5_1InitialInfluenza;
    }

    public void setP5_1InitialInfluenza(String p5_1InitialInfluenza) {
        this.p5_1InitialInfluenza = p5_1InitialInfluenza;
    }

    public String getP5_2InitialInfluenza() {
        return p5_2InitialInfluenza;
    }

    public void setP5_2InitialInfluenza(String p5_2InitialInfluenza) {
        this.p5_2InitialInfluenza = p5_2InitialInfluenza;
    }

    public String getP5_3InitialInfluenza() {
        return p5_3InitialInfluenza;
    }

    public void setP5_3InitialInfluenza(String p5_3InitialInfluenza) {
        this.p5_3InitialInfluenza = p5_3InitialInfluenza;
    }

    public String getP5_1CommentsInfluenza() {
        return p5_1CommentsInfluenza;
    }

    public void setP5_1CommentsInfluenza(String p5_1CommentsInfluenza) {
        this.p5_1CommentsInfluenza = p5_1CommentsInfluenza;
    }

    public String getP5_2CommentsInfluenza() {
        return p5_2CommentsInfluenza;
    }

    public void setP5_2CommentsInfluenza(String p5_2CommentsInfluenza) {
        this.p5_2CommentsInfluenza = p5_2CommentsInfluenza;
    }

    public String getP5_3CommentsInfluenza() {
        return p5_3CommentsInfluenza;
    }

    public void setP5_3CommentsInfluenza(String p5_3CommentsInfluenza) {
        this.p5_3CommentsInfluenza = p5_3CommentsInfluenza;
    }

    public String getP5_1InjeHPV() {
        return p5_1InjeHPV;
    }

    public void setP5_1InjeHPV(String p5_1InjeHPV) {
        this.p5_1InjeHPV = p5_1InjeHPV;
    }

    public String getP5_2InjeHPV() {
        return p5_2InjeHPV;
    }

    public void setP5_2InjeHPV(String p5_2InjeHPV) {
        this.p5_2InjeHPV = p5_2InjeHPV;
    }

    public String getP5_3InjeHPV() {
        return p5_3InjeHPV;
    }

    public void setP5_3InjeHPV(String p5_3InjeHPV) {
        this.p5_3InjeHPV = p5_3InjeHPV;
    }

    public String getP5_1LotNHPV() {
        return p5_1LotNHPV;
    }

    public void setP5_1LotNHPV(String p5_1LotNHPV) {
        this.p5_1LotNHPV = p5_1LotNHPV;
    }

    public String getP5_2LotNHPV() {
        return p5_2LotNHPV;
    }

    public void setP5_2LotNHPV(String p5_2LotNHPV) {
        this.p5_2LotNHPV = p5_2LotNHPV;
    }

    public String getP5_3LotNHPV() {
        return p5_3LotNHPV;
    }

    public void setP5_3LotNHPV(String p5_3LotNHPV) {
        this.p5_3LotNHPV = p5_3LotNHPV;
    }

    public String getP5_1InitialHPV() {
        return p5_1InitialHPV;
    }

    public void setP5_1InitialHPV(String p5_1InitialHPV) {
        this.p5_1InitialHPV = p5_1InitialHPV;
    }

    public String getP5_2InitialHPV() {
        return p5_2InitialHPV;
    }

    public void setP5_2InitialHPV(String p5_2InitialHPV) {
        this.p5_2InitialHPV = p5_2InitialHPV;
    }

    public String getP5_3InitialHPV() {
        return p5_3InitialHPV;
    }

    public void setP5_3InitialHPV(String p5_3InitialHPV) {
        this.p5_3InitialHPV = p5_3InitialHPV;
    }

    public String getP5_1CommentsHPV() {
        return p5_1CommentsHPV;
    }

    public void setP5_1CommentsHPV(String p5_1CommentsHPV) {
        this.p5_1CommentsHPV = p5_1CommentsHPV;
    }

    public String getP5_2CommentsHPV() {
        return p5_2CommentsHPV;
    }

    public void setP5_2CommentsHPV(String p5_2CommentsHPV) {
        this.p5_2CommentsHPV = p5_2CommentsHPV;
    }

    public String getP5_3CommentsHPV() {
        return p5_3CommentsHPV;
    }

    public void setP5_3CommentsHPV(String p5_3CommentsHPV) {
        this.p5_3CommentsHPV = p5_3CommentsHPV;
    }

    public String getP5_1NaciOther() {
        return p5_1NaciOther;
    }

    public void setP5_1NaciOther(String p5_1NaciOther) {
        this.p5_1NaciOther = p5_1NaciOther;
    }

    public String getP5_1InjeOther() {
        return p5_1InjeOther;
    }

    public void setP5_1InjeOther(String p5_1InjeOther) {
        this.p5_1InjeOther = p5_1InjeOther;
    }

    public String getP5_1LotNOther() {
        return p5_1LotNOther;
    }

    public void setP5_1LotNOther(String p5_1LotNOther) {
        this.p5_1LotNOther = p5_1LotNOther;
    }

    public String getP5_1InitialOther() {
        return p5_1InitialOther;
    }

    public void setP5_1InitialOther(String p5_1InitialOther) {
        this.p5_1InitialOther = p5_1InitialOther;
    }

    public String getP5_1CommentsOther() {
        return p5_1CommentsOther;
    }

    public void setP5_1CommentsOther(String p5_1CommentsOther) {
        this.p5_1CommentsOther = p5_1CommentsOther;
    }

    public Map<String, FormBooleanValue> getBooleanValueMap() {
        return booleanValueMap;
    }

    public void setBooleanValueMap(Map<String, FormBooleanValue> booleanValueMap) {
        this.booleanValueMap = booleanValueMap;
    }
}
