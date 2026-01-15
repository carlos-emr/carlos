//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.commn.dao;

import java.util.List;

import javax.persistence.Query;

import ca.openosp.openo.commn.NativeSql;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.ProviderLabRoutingModel;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@SuppressWarnings("unchecked")
public class ProviderLabRoutingDaoImpl extends AbstractDaoImpl<ProviderLabRoutingModel>
        implements ProviderLabRoutingDao {

    public enum LAB_TYPE {
        DOC, HL7
    }

    public enum STATUS {
        X, N, A, D
    }


    public ProviderLabRoutingDaoImpl() {
        super(ProviderLabRoutingModel.class);
    }

    private List<ProviderLabRoutingModel> getProviderLabRoutings(Integer labNo, String labType, String providerNo,
                                                                 String status) {
        Query q = entityManager.createQuery("select x from " + modelClass.getName() + " x "
                    + "where x.labNo LIKE ?1 and x.labType LIKE ?2 and x.providerNo LIKE ?3 and x.status LIKE ?4");
        q.setParameter(1, labNo != null ? labNo : "%");
        q.setParameter(2, labType != null ? labType : "%");
        q.setParameter(3, providerNo != null ? providerNo : "%");
        q.setParameter(4, status != null ? status : "%");

        return q.getResultList();
    }

    @Override
    public List<ProviderLabRoutingModel> findByLabNoAndLabTypeAndProviderNo(int labNo, String labType,
                                                                            String providerNo) {
        Query q = entityManager.createQuery(
                "select x from " + modelClass.getName() + " x where x.labNo=?1 and x.labType=?2 and x.providerNo=?3");
        q.setParameter(1, labNo);
        q.setParameter(2, labType);
        q.setParameter(3, providerNo);

        return q.getResultList();
    }

    @Override
    public List<ProviderLabRoutingModel> getProviderLabRoutingDocuments(Integer labNo) {
        return getProviderLabRoutings(labNo, "DOC", null, null);
    }

    @Override
    public List<ProviderLabRoutingModel> getProviderLabRoutingForLabProviderType(Integer labNo, String providerNo,
                                                                                 String labType) {
        return getProviderLabRoutings(labNo, labType, providerNo, null);
    }

    @Override
    public List<ProviderLabRoutingModel> getProviderLabRoutingForLabAndType(Integer labNo, String labType) {
        return getProviderLabRoutings(labNo, labType, null, "N");
    }

    @Override
    public List<ProviderLabRoutingModel> findAllLabRoutingByIdandType(Integer labNo, String labType) {
        return getProviderLabRoutings(labNo, labType, null, null);
    }

    @Override
    public void updateStatus(Integer labNo, String labType) {
        String updateString = "UPDATE " + modelClass.getName() + " x set x.status='N' WHERE x.labNo=?1 AND x.labType=?2";
        Query query = entityManager.createQuery(updateString);
        query.setParameter(1, labNo);
        query.setParameter(2, labType);

        query.executeUpdate();
    }

    @Override
    public ProviderLabRoutingModel findByLabNo(int labNo) {
        Query query = entityManager.createQuery("select x from " + modelClass.getName() + " x where x.labNo=?1");
        query.setParameter(1, labNo);

        return this.getSingleResultOrNull(query);
    }

    // this is written for the clean()method to fix OSCAREMR-6161.
    @Override
    public List<ProviderLabRoutingModel> findByLabNoIncludingPotentialDuplicates(int labNo) {
        Query query = entityManager.createQuery("select x from " + modelClass.getName() + " x where x.labNo=?1");
        query.setParameter(1, labNo);

        return query.getResultList();
    }

    @Override
    public ProviderLabRoutingModel findByLabNoAndLabType(int labNo, String labType) {
        Query query = entityManager
                .createQuery("select x from " + modelClass.getName() + " x where x.labNo=?1 and x.labType=?2");
        query.setParameter(1, labNo);
        query.setParameter(2, labType);

        return this.getSingleResultOrNull(query);
    }

    /**
     * Finds all providers and lab routing models for the specified lab
     *
     * @param labNo   Lab number to find data for
     * @param labType Lab type to find data for
     * @return Returns an array of objects containing {@link Provider},
     * {@link ProviderLabRoutingModel} pairs.
     */
    @Override
    public List<Object[]> getProviderLabRoutings(Integer labNo, String labType) {
        Query query = entityManager.createQuery("FROM " + Provider.class.getSimpleName() + " p, " + modelClass.getName() 
                    + " r WHERE p.id = r.providerNo AND r.labNo = ?1 AND r.labType = ?2");
        query.setParameter(1, labNo);
        query.setParameter(2, labType);
        return query.getResultList();
    }

    @Override
    public List<ProviderLabRoutingModel> findByStatusANDLabNoType(Integer labNo, String labType, String status) {
        Query query = createQuery("r", "r.labNo = ?1 and r.labType = ?2 and r.status = ?3");
        query.setParameter(1, labNo);
        query.setParameter(2, labType);
        query.setParameter(3, status);
        return query.getResultList();
    }

    @Override
    public List<ProviderLabRoutingModel> findByProviderNo(String providerNo, String status) {
        Query query = createQuery("p", "p.providerNo = ?1 AND p.status = ?2");
        query.setParameter(1, providerNo);
        query.setParameter(2, status);
        return query.getResultList();
    }

    @Override
    public List<ProviderLabRoutingModel> findByLabNoTypeAndStatus(int labId, String labType, String status) {
        Query query = createQuery("p", "p.labNo = ?1 AND p.status = ?2 AND p.labType = ?3");
        query.setParameter(1, labId);
        query.setParameter(2, status);
        query.setParameter(3, labType);
        return query.getResultList();
    }

    @Override
    public List<Integer> findLastRoutingIdGroupedByProviderAndCreatedByDocCreator(String docCreator) {
        Query query = entityManager.createQuery("SELECT max(r.id) FROM ProviderLabRoutingModel r, Document d "+
        "WHERE d.documentNo=r.labNo AND d.doccreator= ?1 AND r.providerNo!=0 AND r.providerNo!=-1 " +
                "AND r.providerNo IS NOT NULL group by r.providerNo");
        query.setParameter(1, docCreator);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findProviderAndLabRoutingById(Integer id) {
        String sql = "FROM Provider provider, ProviderLabRoutingModel providerLabRouting "
                + "WHERE provider.ProviderNo = providerLabRouting.providerNo "
                + "AND providerLabRouting.id = ?1 ";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        return query.getResultList();
    }

    @NativeSql({"providerLabRouting", "mdsMSH", "mdsPID", "mdsPV1", "mdsZFR", "mdsOBR", "mdsZRG"})
    @Override
    public List<Object[]> findMdsResultResultDataByManyThings(String status, String providerNo, String patientLastName,
                                                              String patientFirstName, String patientHealthNumber) {
        // note to self: lab reports not found in the providerLabRouting table will not
        // show up - need to ensure every lab is entered in providerLabRouting, with '0'
        // for the providers number if unable to find correct providers
        String sql;
        sql = "SELECT mdsMSH.segmentID, mdsMSH.messageConID AS accessionNum, providerLabRouting.status, mdsPID.patientName, mdsPID.healthNumber, "
                + "mdsPID.sex, max(mdsZFR.abnormalFlag) as abnormalFlag, mdsMSH.dateTime, mdsOBR.quantityTiming, mdsPV1.refDoctor, "
                + "min(mdsZFR.reportFormStatus) as reportFormStatus, mdsZRG.reportGroupDesc " +
                "FROM " +
                "providerLabRouting " +
                "LEFT JOIN mdsMSH on providerLabRouting.lab_no = mdsMSH.segmentID " +
                "LEFT JOIN mdsPID on providerLabRouting.lab_no = mdsPID.segmentID " +
                "LEFT JOIN mdsPV1 on providerLabRouting.lab_no = mdsPV1.segmentID " +
                "LEFT JOIN mdsZFR on providerLabRouting.lab_no = mdsZFR.segmentID " +
                "LEFT JOIN mdsOBR on providerLabRouting.lab_no = mdsOBR.segmentID " +
                "LEFT JOIN mdsZRG on providerLabRouting.lab_no = mdsZRG.segmentID " +
                "WHERE " +
                "providerLabRouting.lab_type = 'MDS' " +
                "AND providerLabRouting.status LIKE ?1 " +
                "AND providerLabRouting.provider_no LIKE ?2 " +
                "AND mdsPID.patientName LIKE ?3 " +
                "AND mdsPID.healthNumber LIKE ?4 " +
                "GROUP BY mdsMSH.segmentID";

        // Prepare parameters
        String statusParam = "%" + status + "%";
        String providerNoParam = providerNo.equals("") ? "%" : providerNo;
        String patientNameParam = patientLastName + "%^" + patientFirstName + "%^%";
        String healthNumberParam = "%" + patientHealthNumber + "%";

        // Create query and set parameters
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, statusParam);
        query.setParameter(2, providerNoParam);
        query.setParameter(3, patientNameParam);
        query.setParameter(4, healthNumberParam);

        return query.getResultList();
    }

    @NativeSql({"providerLabRouting", "mdsMSH", "mdsPID", "mdsPV1", "mdsZFR", "mdsOBR", "mdsZRG"})
    @Override
    public List<Object[]> findMdsResultResultDataByDemographicNoAndLabNo(Integer demographicNo, Integer labNo) {

        String sql;
        sql = "SELECT mdsMSH.segmentID, mdsMSH.messageConID AS accessionNum, providerLabRouting.status, mdsPID.patientName, mdsPID.healthNumber, "
                +
                "mdsPID.sex, max(mdsZFR.abnormalFlag) as abnormalFlag, mdsMSH.dateTime, mdsOBR.quantityTiming, mdsPV1.refDoctor, "
                +
                "min(mdsZFR.reportFormStatus) as reportFormStatus, mdsZRG.reportGroupDesc " +
                "FROM " +
                "patientLabRouting " +
                "LEFT JOIN providerLabRouting on patientLabRouting.lab_no = providerLabRouting.lab_no " +
                "LEFT JOIN mdsMSH on patientLabRouting.lab_no = mdsMSH.segmentID " +
                "LEFT JOIN mdsPID on patientLabRouting.lab_no = mdsPID.segmentID " +
                "LEFT JOIN mdsPV1 on patientLabRouting.lab_no = mdsPV1.segmentID " +
                "LEFT JOIN mdsZFR on patientLabRouting.lab_no = mdsZFR.segmentID " +
                "LEFT JOIN mdsOBR on patientLabRouting.lab_no = mdsOBR.segmentID " +
                "LEFT JOIN mdsZRG on patientLabRouting.lab_no = mdsZRG.segmentID " +
                "WHERE " +
                "patientLabRouting.lab_type = 'MDS' " +
                "AND patientLabRouting.demographic_no = ?1 and mdsMSH.segmentID = ?2 group by mdsMSH.segmentID";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, labNo);
        return query.getResultList();

    }

    @NativeSql({"providerLabRouting", "mdsMSH", "mdsPID", "mdsPV1", "mdsZFR", "mdsOBR", "mdsZRG"})
    @Override
    public List<Object[]> findMdsResultResultDataByDemoId(String demographicNo) {
        String sql = "SELECT mdsMSH.segmentID, mdsMSH.messageConID AS accessionNum, mdsPID.patientName, mdsPID.healthNumber, "
                +
                "mdsPID.sex, max(mdsZFR.abnormalFlag) as abnormalFlag, mdsMSH.dateTime, mdsOBR.quantityTiming, mdsPV1.refDoctor, "
                +
                "min(mdsZFR.reportFormStatus) as reportFormStatus, mdsZRG.reportGroupDesc " +
                "FROM patientLabRouting " +
                "LEFT JOIN mdsMSH on patientLabRouting.lab_no = mdsMSH.segmentID " +
                "LEFT JOIN mdsPID on patientLabRouting.lab_no = mdsPID.segmentID " +
                "LEFT JOIN mdsPV1 on patientLabRouting.lab_no = mdsPV1.segmentID " +
                "LEFT JOIN mdsZFR on patientLabRouting.lab_no = mdsZFR.segmentID " +
                "LEFT JOIN mdsOBR on patientLabRouting.lab_no = mdsOBR.segmentID " +
                "LEFT JOIN mdsZRG on patientLabRouting.lab_no = mdsZRG.segmentID " +
                "WHERE " +
                "patientLabRouting.lab_type = 'MDS' " +
                "AND patientLabRouting.demographic_no=?1 group by mdsMSH.segmentID";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, demographicNo);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findProviderAndLabRoutingByIdAndLabType(Integer id, String labType) {
        String sql = "FROM Provider provider, ProviderLabRoutingModel providerLabRouting " +
                "WHERE provider.ProviderNo = providerLabRouting.providerNo " +
                "AND providerLabRouting.labNo = ?1 " +
                "AND providerLabRouting.labType = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        query.setParameter(2, labType);
        return query.getResultList();
    }

}