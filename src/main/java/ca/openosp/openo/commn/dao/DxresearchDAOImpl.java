//CHECKSTYLE:OFF
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
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.openosp.openo.commn.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.persistence.Query;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.NativeSql;
import ca.openosp.openo.commn.model.AbstractCodeSystemModel;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.DxRegistedPTInfo;
import ca.openosp.openo.commn.model.Dxresearch;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.stereotype.Repository;

import ca.openosp.openo.dxresearch.bean.dxCodeSearchBean;
import ca.openosp.openo.dxresearch.bean.dxQuickListItemsHandler;

/**
 * @author toby
 */
@Repository
@SuppressWarnings("unchecked")
public class DxresearchDAOImpl extends AbstractDaoImpl<Dxresearch> implements DxresearchDAO {
    private static final Logger logger = MiscUtils.getLogger();

    public DxresearchDAOImpl() {
        super(Dxresearch.class);
    }

    public List<DxRegistedPTInfo> getPatientRegisted(List<Dxresearch> dList, List<String> doctorList) {

        List<DxRegistedPTInfo> rList = new ArrayList<DxRegistedPTInfo>();


        Iterator<Dxresearch> i = dList.listIterator();
        while (i.hasNext()) {

            Dxresearch dxres = i.next();
            Integer demographicNo = dxres.getDemographicNo();

            DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
            Demographic demo = demographicDao.getClientByDemographicNo(demographicNo);

            if (demo != null) {
                String demoprovider = demo.getProviderNo();
                boolean isDoctorPatient = false;
                // if patient's doctor is not in the doctor's list then skip, "*" means all doctor
                Iterator<String> j = doctorList.listIterator();
                while (j.hasNext()) {
                    String providerNo = j.next();
                    if (providerNo.equalsIgnoreCase("*") || providerNo.equalsIgnoreCase(demoprovider)) {
                        isDoctorPatient = true;
                        break;
                    }
                }


                if (isDoctorPatient && demo.getPatientStatus().equalsIgnoreCase("AC"))
                    rList.add(new DxRegistedPTInfo(demo.getFirstName(), demo.getLastName(), demo.getSex(),
                            demo.getYearOfBirth() + "-" + demo.getMonthOfBirth() + "-" + demo.getDateOfBirth(),
                            demo.getPhone(), demo.getHin(), dxres.getCodingSystem(), dxres.getDxresearchCode(),
                            dxres.getStartDate().toString(), dxres.getUpdateDate().toString(),
                            dxres.getStatus().toString()));
            }
        }

        if (rList != null && rList.size() > 0) {
            return rList;
        } else {
            return null;
        }
    }


    public List<DxRegistedPTInfo> patientRegistedDistincted(List<dxCodeSearchBean> searchItems, List<String> doctorList) {
        List<Dxresearch> dList = null;
        List<dxCodeSearchBean> listItems = searchItems;
        String HQL = "";

        Query query = null;
        if (listItems != null && listItems.size() > 0) {
            Iterator<dxCodeSearchBean> ite = listItems.listIterator();
            HQL = "SELECT dxres FROM Dxresearch dxres";

            if (ite.hasNext()) {
                HQL += " WHERE ";
            }

            while (ite.hasNext()) {
                dxCodeSearchBean bean = ite.next();
                String codeSys = bean.getType();
                String code = bean.getDxSearchCode();
                HQL += "dxres.codingSystem= '" + codeSys + "' AND dxres.dxresearchCode='" + code + "'";
                if (ite.hasNext()) {
                    HQL += " OR ";
                }
            }
            HQL += " GROUP BY dxres.demographicNo ORDER BY dxres.updateDate asc";
            query = entityManager.createQuery(HQL);
        } else {
            HQL = "SELECT dxres FROM Dxresearch dxres GROUP BY dxres.demographicNo ORDER BY dxres.updateDate asc";
            query = entityManager.createQuery(HQL);
        }

        dList = query.getResultList();

        if (dList != null && dList.size() > 0) {
            return getPatientRegisted(dList, doctorList);
        } else {
            return null;
        }
    }


    public List<DxRegistedPTInfo> patientRegistedAll(List<dxCodeSearchBean> searchItems, List<String> doctorList) {
        List<Dxresearch> dList = null;
        List<dxCodeSearchBean> listItems = searchItems;
        String HQL = "";
        Query query = null;
        if (listItems != null && listItems.size() > 0) {
            Iterator<dxCodeSearchBean> ite = listItems.listIterator();
            HQL = "SELECT dxres FROM Dxresearch dxres";

            if (ite.hasNext()) {
                HQL += " WHERE ";
            }

            while (ite.hasNext()) {
                dxCodeSearchBean bean = ite.next();
                String codeSys = bean.getType();
                String code = bean.getDxSearchCode().trim();
                HQL += "dxres.codingSystem= '" + codeSys + "' AND dxres.dxresearchCode='" + code + "'";
                if (ite.hasNext()) {
                    HQL += " OR ";
                }
            }
            HQL += " ORDER BY dxres.demographicNo asc, dxres.updateDate asc";
            query = entityManager.createQuery(HQL);
        } else {
            HQL = "SELECT dxres FROM Dxresearch dxres ORDER BY dxres.demographicNo asc, dxres.updateDate asc";
            query = entityManager.createQuery(HQL);
        }

        dList = query.getResultList();

        if (dList != null && dList.size() > 0) {
            //Iterator i = dList.listIterator();
            //while (i.hasNext())

            return getPatientRegisted(dList, doctorList);
        } else {
            return null;
        }
    }

    public List<DxRegistedPTInfo> patientRegistedActive(List<dxCodeSearchBean> searchItems, List<String> doctorList) {
        return patientRegistedStatus("A", searchItems, doctorList);
    }

    public List<DxRegistedPTInfo> patientRegistedResolve(List<dxCodeSearchBean> searchItems, List<String> doctorList) {
        return patientRegistedStatus("C", searchItems, doctorList);
    }

    public List<DxRegistedPTInfo> patientRegistedDeleted(List<dxCodeSearchBean> searchItems, List<String> doctorList) {
        return patientRegistedStatus("D", searchItems, doctorList);
    }


    public List<DxRegistedPTInfo> patientRegistedStatus(String status, List<dxCodeSearchBean> searchItems, List<String> doctorList) {
        List<Dxresearch> dList = null;

        List<dxCodeSearchBean> listItems = searchItems;
        String HQL = "";

        Query query = null;
        if (listItems != null && listItems.size() > 0) {
            Iterator<dxCodeSearchBean> ite = listItems.listIterator();
            HQL = "SELECT dxres FROM Dxresearch dxres WHERE (";

            while (ite.hasNext()) {
                dxCodeSearchBean bean = ite.next();
                String codeSys = bean.getType();
                String code = bean.getDxSearchCode();
                HQL += "dxres.codingSystem= '" + codeSys + "' AND dxres.dxresearchCode='" + code + "'";
                if (ite.hasNext()) {
                    HQL += " OR ";
                }
            }
            if (listItems.size() > 0)
                HQL += ") AND ";

            HQL += "dxres.status= '" + status + "' ORDER BY dxres.demographicNo asc, dxres.updateDate asc";
            query = entityManager.createQuery(HQL);
        } else {
            HQL = "SELECT dxres FROM Dxresearch dxres WHERE dxres.status= '" + status + "' ORDER BY dxres.demographicNo asc, dxres.updateDate asc";
            query = entityManager.createQuery(HQL);
        }
        dList = query.getResultList();


        if (dList != null && dList.size() > 0) {
            //Iterator i = dList.listIterator();
            //while (i.hasNext())

            return getPatientRegisted(dList, doctorList);
        } else {
            return null;
        }
    }

    public List<dxCodeSearchBean> getQuickListItems(String quickListName) {

        List<dxCodeSearchBean> listItems = new ArrayList<dxCodeSearchBean>();
        listItems.addAll(new dxQuickListItemsHandler(quickListName).getDxQuickListItemsVector());
        return listItems;
    }

    public List<Dxresearch> getDxResearchItemsByPatient(Integer demographicNo) {
        String hql = "select d from Dxresearch d where d.demographicNo=?1";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, demographicNo);


        List<Dxresearch> items = query.getResultList();

        return items;
    }

    public void save(Dxresearch d) {
        if (d.getId() != null && d.getId().intValue() > 0) {
            merge(d);
        } else {
            persist(d);
        }

    }

    public List<Dxresearch> getByDemographicNo(int demographicNo) {
        String hql = "select d from Dxresearch d where d.demographicNo=?1 and d.status='A'";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, demographicNo);


        List<Dxresearch> items = query.getResultList();

        return items;
    }

    public List<Dxresearch> find(int demographicNo, String codeType, String code) {
        String hql = "select d from Dxresearch d where d.demographicNo=?1 and d.codingSystem=?2 and d.dxresearchCode=?3 order by d.updateDate desc";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, codeType);
        query.setParameter(3, code);

        @SuppressWarnings("unchecked")
        List<Dxresearch> items = query.getResultList();

        return items;
    }

    public List<Dxresearch> findActive(String codeType, String code) {
        String hql = "select d from Dxresearch d where d.status='A' and d.codingSystem=?1 and d.dxresearchCode=?2 order by d.updateDate desc";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, codeType);
        query.setParameter(2, code);

        @SuppressWarnings("unchecked")
        List<Dxresearch> items = query.getResultList();

        return items;
    }


    public boolean entryExists(int demographicNo, String codeType, String code) {
        String hql = "select d from Dxresearch d where d.demographicNo=?1 and d.codingSystem=?2 and d.dxresearchCode=?3";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, codeType);
        query.setParameter(3, code);

        @SuppressWarnings("unchecked")
        List<Dxresearch> items = query.getResultList();

        return !items.isEmpty();
    }

    public boolean activeEntryExists(int demographicNo, String codeType, String code) {
        String hql = "select d from Dxresearch d where d.status='A' and d.demographicNo=?1 and d.codingSystem=?2 and d.dxresearchCode=?3";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, codeType);
        query.setParameter(3, code);


        List<Dxresearch> items = query.getResultList();

        return !items.isEmpty();
    }

    public void removeAllAssociationEntries() {
        String hql = "DELETE from Dxresearch dx WHERE dx.association='1'";
        Query query = entityManager.createQuery(hql);

        query.executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<Dxresearch> findByDemographicNoResearchCodeAndCodingSystem(Integer demographicNo, String dxresearchCode, String codingSystem) {
        Query query = entityManager.createQuery("FROM Dxresearch d WHERE d.demographicNo = ?1 AND d.dxresearchCode = ?2 and (d.status = 'A' or d.status = 'C') and d.codingSystem = ?3");
        query.setParameter(1, demographicNo);
        query.setParameter(2, dxresearchCode);
        query.setParameter(3, codingSystem);
        return query.getResultList();
    }

    public List<Object[]> getDataForInrReport(Date fromDate, Date toDate) {
        String sql = "FROM Demographic d, Measurement m, Dxresearch dx " +
                "wHERE m.demographicId = dx.demographicNo " +
                "AND dx.status != 'D' " +
                "AND dx.codingSystem = 'icd9' " +
                "AND (" +
                "	dx.dxresearchCode = '42731' " +
                "	OR dx.dxresearchCode = 'V5861' " +
                "	OR dx.dxresearchCode = 'V1251'" +
                ") AND m.demographicId = d.DemographicNo " +
                "AND m.type = 'INR' " +
                "AND m.dateObserved >= ?1" +
                "AND m.dateObserved <= ?2" +
                "ORDER BY d.LastName, d.FirstName, m.dateObserved";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, fromDate);
        q.setParameter(2, toDate);
        return q.getResultList();
    }

    public Integer countResearches(String researchCode, Date sdate, Date edate) {
        String sql = "SELECT DISTINCT x.demographicNo FROM Dxresearch x, Demographic d " +
                "WHERE x.dxresearchCode = ?1" +
                "AND x.demographicNo = d.id " +
                "AND x.updateDate >= ?2" +
                "AND x.updateDate <= ?3" +
                "AND x.status <> 'D'";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, researchCode);
        query.setParameter(2, sdate);
        query.setParameter(3, edate);

        List<Integer> ids = query.getResultList();
        return ids.size();
    }

    public Integer countBillingResearches(String researchCode, String diagCode, String creator, Date sdate, Date edate) {
        String sql = "SELECT DISTINCT x.demographicNo FROM Dxresearch x, Billing b, BillingDetail bd " +
                "WHERE x.status <> 'D' " +
                "AND x.dxresearchCode= ?1" +
                "AND x.demographicNo = b.demographicNo " +
                "AND b.id = bd.billingNo " +
                "AND bd.diagnosticCode = ?2" +
                "AND b.creator = ?3" +
                "AND b.billingDate >= ?4" +
                "AND b.billingDate <= ?5" +
                "AND b.status != 'D' " +
                "AND bd.status != 'D'";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, researchCode);
        query.setParameter(2, diagCode);
        query.setParameter(3, creator);
        query.setParameter(4, sdate);
        query.setParameter(5, edate);

        List<Integer> ids = query.getResultList();
        return ids.size();
    }

    @NativeSql
    public List<Object[]> findResearchAndCodingSystemByDemographicAndCondingSystem(String codingSystem, String demographicNo) {
        // Validate codingSystem against allowed values to prevent SQL injection
        // Using the same enum validation as in getDescription() method
        try {
            // This will throw IllegalArgumentException if codingSystem is not valid
            AbstractCodeSystemDaoImpl.codingSystem.valueOf(codingSystem);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid coding system provided: " + codingSystem);
            return new ArrayList<>();
        }
        
        // Now that codingSystem is validated, we can safely use it for table/column names
        // Use parameterized query for demographicNo to prevent SQL injection
        String sql = "select d.start_date, d.update_date, c.description, c." + codingSystem + ", d.dxresearch_no, d.status,d.providerNo from dxresearch d, " + codingSystem + " c " +
                "where d.dxresearch_code=c." + codingSystem + " and d.status<>'D' and d.demographic_no = ?1 and d.coding_system = ?2"
                + " order by d.start_date desc, d.update_date desc";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, codingSystem);
        return query.getResultList();
    }


    public List<Dxresearch> findCurrentByCodeTypeAndCode(String codeType, String code) {
        String hql = "select d from Dxresearch d where d.codingSystem=?1 and d.dxresearchCode=?2 and d.status='A'";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, codeType);
        query.setParameter(2, code);

        @SuppressWarnings("unchecked")
        List<Dxresearch> items = query.getResultList();

        return items;
    }

    public List<Dxresearch> getByDemographicNoSince(int demographicNo, Date lastUpdateDate) {
        String hql = "select d from Dxresearch d where d.demographicNo=?1 and d.updateDate > ?2";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, lastUpdateDate);

        @SuppressWarnings("unchecked")
        List<Dxresearch> items = query.getResultList();

        return items;
    }

    //for integrator
    public List<Integer> getByDemographicNoSince(Date lastUpdateDate) {
        String hql = "select d.demographicNo from Dxresearch d where d.updateDate > ?1";
        Query query = entityManager.createQuery(hql);
        query.setParameter(1, lastUpdateDate);

        @SuppressWarnings("unchecked")
        List<Integer> items = query.getResultList();

        return items;
    }

    @SuppressWarnings("unchecked")
    public List<Dxresearch> findNonDeletedByDemographicNo(Integer demographicNo) {
        Query query = entityManager.createQuery("FROM Dxresearch d WHERE d.demographicNo = ?1 and (d.status = 'A' or d.status = 'C')");
        query.setParameter(1, demographicNo);

        return query.getResultList();
    }

    @NativeSql("dxresearch")
    public List<Integer> findNewProblemsSinceDemokey(String keyName) {

        String sql = "select distinct dx.demographic_no from dxresearch dx,demographic d,demographicExt e where dx.demographic_no = d.demographic_no and d.demographic_no = e.demographic_no and e.key_val=?1 and dx.status != 'D' and dx.update_date > e.value";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, keyName);

        return query.getResultList();
    }

    public String getDescription(String codingSystem, String code) {
        String description = null;
        try {
            Class<?> daoClass = AbstractCodeSystemDao.getDaoName(AbstractCodeSystemDaoImpl.codingSystem.valueOf(codingSystem));
            @SuppressWarnings("unchecked")
            AbstractCodeSystemDao<AbstractCodeSystemModel<?>> csDao = (AbstractCodeSystemDao<AbstractCodeSystemModel<?>>) SpringUtils.getBean(daoClass);

            if (code != null && !code.isEmpty()) {
                AbstractCodeSystemModel<?> codingSystemEntity = csDao.findByCode(code);
                description = codingSystemEntity.getDescription();
            }
        } catch (Exception e) {
            logger.error("error getting description", e);
        }
        return description;
    }
}
