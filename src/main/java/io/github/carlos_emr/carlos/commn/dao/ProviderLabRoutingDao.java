/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;

/**
 * DAO interface for healthcare provider operations.
 *
 * @since 2001
 */

public interface ProviderLabRoutingDao extends AbstractDao<ProviderLabRoutingModel> {
    public static final String UNCLAIMED_PROVIDER = "0";

    /**
     * LAB_TYPE for data access operations.
     *
     * @since 2001
     */

    public enum LAB_TYPE {
        DOC, HL7
    }

    /**
     * STATUS for data access operations.
     *
     * @since 2001
     */

    public enum STATUS {
        X, N, A, D
    }

    public List<ProviderLabRoutingModel> findByLabNoAndLabTypeAndProviderNo(int labNo, String labType,
                                                                            String providerNo);

    /**
     * Get Provider Lab Routing Documents.
     *
     * @param labNo Integer the labNo
     * @return List<ProviderLabRoutingModel>
     */
    public List<ProviderLabRoutingModel> getProviderLabRoutingDocuments(Integer labNo);

    public List<ProviderLabRoutingModel> getProviderLabRoutingForLabProviderType(Integer labNo, String providerNo,
                                                                                 String labType);

    /**
     * Get Provider Lab Routing For Lab And Type.
     *
     * @param labNo Integer the labNo
     * @param labType String the labType
     * @return List<ProviderLabRoutingModel>
     */
    public List<ProviderLabRoutingModel> getProviderLabRoutingForLabAndType(Integer labNo, String labType);

    /**
     * Find All Lab Routing By Idand Type.
     *
     * @param labNo Integer the labNo
     * @param labType String the labType
     * @return List<ProviderLabRoutingModel>
     */
    public List<ProviderLabRoutingModel> findAllLabRoutingByIdandType(Integer labNo, String labType);

    /**
     * Update Status.
     *
     * @param labNo Integer the labNo
     * @param labType String the labType
     */
    public void updateStatus(Integer labNo, String labType);

    /**
     * Find By Lab No.
     *
     * @param labNo int the labNo
     * @return ProviderLabRoutingModel
     */
    public ProviderLabRoutingModel findByLabNo(int labNo);

    /**
     * Find By Lab No Including Potential Duplicates.
     *
     * @param labNo int the labNo
     * @return List<ProviderLabRoutingModel>
     */
    public List<ProviderLabRoutingModel> findByLabNoIncludingPotentialDuplicates(int labNo);

    /**
     * Find By Lab No And Lab Type.
     *
     * @param labNo int the labNo
     * @param labType String the labType
     * @return ProviderLabRoutingModel
     */
    public ProviderLabRoutingModel findByLabNoAndLabType(int labNo, String labType);

    /**
     * Get Provider Lab Routings.
     *
     * @param labNo Integer the labNo
     * @param labType String the labType
     * @return List<Object[]>
     */
    public List<Object[]> getProviderLabRoutings(Integer labNo, String labType);

    /**
     * Find By Status A N D Lab No Type.
     *
     * @param labNo Integer the labNo
     * @param labType String the labType
     * @param status String the status
     * @return List<ProviderLabRoutingModel>
     */
    public List<ProviderLabRoutingModel> findByStatusANDLabNoType(Integer labNo, String labType, String status);

    /**
     * Find By Provider No.
     *
     * @param providerNo String the providerNo
     * @param status String the status
     * @return List<ProviderLabRoutingModel>
     */
    public List<ProviderLabRoutingModel> findByProviderNo(String providerNo, String status);

    /**
     * Find By Lab No Type And Status.
     *
     * @param labId int the labId
     * @param labType String the labType
     * @param status String the status
     * @return List<ProviderLabRoutingModel>
     */
    public List<ProviderLabRoutingModel> findByLabNoTypeAndStatus(int labId, String labType, String status);

    /**
     * Find Last Routing Id Grouped By Provider And Created By Doc Creator.
     *
     * @param docCreator String the docCreator
     * @return List<Integer>
     */
    public List<Integer> findLastRoutingIdGroupedByProviderAndCreatedByDocCreator(String docCreator);

    /**
     * Find Provider And Lab Routing By Id.
     *
     * @param id Integer the id
     * @return List<Object[]>
     */
    public List<Object[]> findProviderAndLabRoutingById(Integer id);

    public List<Object[]> findMdsResultResultDataByManyThings(String status, String providerNo, String patientLastName,
                                                              String patientFirstName, String patientHealthNumber);

    /**
     * Find Mds Result Result Data By Demographic No And Lab No.
     *
     * @param demographicNo Integer the demographicNo
     * @param labNo Integer the labNo
     * @return List<Object[]>
     */
    public List<Object[]> findMdsResultResultDataByDemographicNoAndLabNo(Integer demographicNo, Integer labNo);

    /**
     * Find Mds Result Result Data By Demo Id.
     *
     * @param demographicNo String the demographicNo
     * @return List<Object[]>
     */
    public List<Object[]> findMdsResultResultDataByDemoId(String demographicNo);

    /**
     * Find Provider And Lab Routing By Id And Lab Type.
     *
     * @param id Integer the id
     * @param labType String the labType
     * @return List<Object[]>
     */
    public List<Object[]> findProviderAndLabRoutingByIdAndLabType(Integer id, String labType);
}
