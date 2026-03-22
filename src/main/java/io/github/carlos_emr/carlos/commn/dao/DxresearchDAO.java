/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.DxRegistedPTInfo;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.dxresearch.bean.dxCodeSearchBean;

/**
 * DAO interface for diagnosis operations.
 *
 * @since 2001
 */

public interface DxresearchDAO extends AbstractDao<Dxresearch> {
    /**
     * Get Patient Registed.
     *
     * @param dList List<Dxresearch> the dList
     * @param doctorList List<String> the doctorList
     * @return List<DxRegistedPTInfo>
     */
    List<DxRegistedPTInfo> getPatientRegisted(List<Dxresearch> dList, List<String> doctorList);

    /**
     * Patient Registed Distincted.
     *
     * @param searchItems List<dxCodeSearchBean> the searchItems
     * @param doctorList List<String> the doctorList
     * @return List<DxRegistedPTInfo>
     */
    List<DxRegistedPTInfo> patientRegistedDistincted(List<dxCodeSearchBean> searchItems, List<String> doctorList);

    /**
     * Patient Registed All.
     *
     * @param searchItems List<dxCodeSearchBean> the searchItems
     * @param doctorList List<String> the doctorList
     * @return List<DxRegistedPTInfo>
     */
    List<DxRegistedPTInfo> patientRegistedAll(List<dxCodeSearchBean> searchItems, List<String> doctorList);

    /**
     * Patient Registed Active.
     *
     * @param searchItems List<dxCodeSearchBean> the searchItems
     * @param doctorList List<String> the doctorList
     * @return List<DxRegistedPTInfo>
     */
    List<DxRegistedPTInfo> patientRegistedActive(List<dxCodeSearchBean> searchItems, List<String> doctorList);

    /**
     * Patient Registed Resolve.
     *
     * @param searchItems List<dxCodeSearchBean> the searchItems
     * @param doctorList List<String> the doctorList
     * @return List<DxRegistedPTInfo>
     */
    List<DxRegistedPTInfo> patientRegistedResolve(List<dxCodeSearchBean> searchItems, List<String> doctorList);

    /**
     * Patient Registed Deleted.
     *
     * @param searchItems List<dxCodeSearchBean> the searchItems
     * @param doctorList List<String> the doctorList
     * @return List<DxRegistedPTInfo>
     */
    List<DxRegistedPTInfo> patientRegistedDeleted(List<dxCodeSearchBean> searchItems, List<String> doctorList);

    /**
     * Get Dx Research Items By Patient.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Dxresearch>
     */
    List<Dxresearch> getDxResearchItemsByPatient(Integer demographicNo);

    /**
     * Save.
     *
     * @param d Dxresearch the d
     */
    void save(Dxresearch d);

    /**
     * Get By Demographic No.
     *
     * @param demographicNo int the demographicNo
     * @return List<Dxresearch>
     */
    List<Dxresearch> getByDemographicNo(int demographicNo);

    /**
     * Find.
     *
     * @param demographicNo int the demographicNo
     * @param codeType String the codeType
     * @param code String the code
     * @return List<Dxresearch>
     */
    List<Dxresearch> find(int demographicNo, String codeType, String code);

    /**
     * Find Active.
     *
     * @param codeType String the codeType
     * @param code String the code
     * @return List<Dxresearch>
     */
    List<Dxresearch> findActive(String codeType, String code);

    /**
     * Entry Exists.
     *
     * @param demographicNo int the demographicNo
     * @param codeType String the codeType
     * @param code String the code
     * @return boolean
     */
    boolean entryExists(int demographicNo, String codeType, String code);

    /**
     * Active Entry Exists.
     *
     * @param demographicNo int the demographicNo
     * @param codeType String the codeType
     * @param code String the code
     * @return boolean
     */
    boolean activeEntryExists(int demographicNo, String codeType, String code);

    /**
     * Remove All Association Entries.
     */
    void removeAllAssociationEntries();

    /**
     * Find Research And Coding System By Demographic And Conding System.
     *
     * @param codingSystem String the codingSystem
     * @param demographicNo String the demographicNo
     * @return List<Object[]>
     */
    List<Object[]> findResearchAndCodingSystemByDemographicAndCondingSystem(String codingSystem, String demographicNo);

    /**
     * Find Current By Code Type And Code.
     *
     * @param codeType String the codeType
     * @param code String the code
     * @return List<Dxresearch>
     */
    List<Dxresearch> findCurrentByCodeTypeAndCode(String codeType, String code);

    /**
     * Get By Demographic No Since.
     *
     * @param demographicNo int the demographicNo
     * @param lastUpdateDate Date the lastUpdateDate
     * @return List<Dxresearch>
     */
    List<Dxresearch> getByDemographicNoSince(int demographicNo, Date lastUpdateDate);

    /**
     * Get By Demographic No Since.
     *
     * @param lastUpdateDate Date the lastUpdateDate
     * @return List<Integer>
     */
    List<Integer> getByDemographicNoSince(Date lastUpdateDate);

    /**
     * Find Non Deleted By Demographic No.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Dxresearch>
     */
    List<Dxresearch> findNonDeletedByDemographicNo(Integer demographicNo);

    /**
     * Find New Problems Since Demokey.
     *
     * @param keyName String the keyName
     * @return List<Integer>
     */
    List<Integer> findNewProblemsSinceDemokey(String keyName);

    /**
     * Get Description.
     *
     * @param codingSystem String the codingSystem
     * @param code String the code
     * @return String
     */
    String getDescription(String codingSystem, String code);

    /**
     * Find By Demographic No Research Code And Coding System.
     *
     * @param demographicNo Integer the demographicNo
     * @param dxresearchCode String the dxresearchCode
     * @param codingSystem String the codingSystem
     * @return List<Dxresearch>
     */
    public List<Dxresearch> findByDemographicNoResearchCodeAndCodingSystem(Integer demographicNo, String dxresearchCode, String codingSystem);

    /**
     * Get Quick List Items.
     *
     * @param quickListName String the quickListName
     * @return List<dxCodeSearchBean>
     */
    public List<dxCodeSearchBean> getQuickListItems(String quickListName);

    /**
     * Get Data For Inr Report.
     *
     * @param fromDate Date the fromDate
     * @param toDate Date the toDate
     * @return List<Object[]>
     */
    public List<Object[]> getDataForInrReport(Date fromDate, Date toDate);

    /**
     * Count Researches.
     *
     * @param researchCode String the researchCode
     * @param sdate Date the sdate
     * @param edate Date the edate
     * @return Integer
     */
    Integer countResearches(String researchCode, Date sdate, Date edate);

    /**
     * Count Billing Researches.
     *
     * @param researchCode String the researchCode
     * @param diagCode String the diagCode
     * @param creator String the creator
     * @param sdate Date the sdate
     * @param edate Date the edate
     * @return Integer
     */
    Integer countBillingResearches(String researchCode, String diagCode, String creator, Date sdate, Date edate);
}
