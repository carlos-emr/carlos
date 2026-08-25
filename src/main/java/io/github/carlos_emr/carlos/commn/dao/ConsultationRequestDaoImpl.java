/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.hibernate.query.criteria.JpaEntityJoin;
import org.hibernate.query.criteria.JpaRoot;

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationRequestListItemDTO;

@SuppressWarnings("unchecked")
public class ConsultationRequestDaoImpl extends AbstractDaoImpl<ConsultationRequest> implements ConsultationRequestDao {

    public ConsultationRequestDaoImpl() {
        super(ConsultationRequest.class);
    }

    public int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff) {
        Query query = entityManager.createNativeQuery("select count(*) from consultationRequests where referalDate < ?1 and status != 4");
        query.setParameter(1, referralDateCutoff);

        return ((Number) query.getSingleResult()).intValue();
    }

    public int getCountReferralsAfterCutOffDateAndNotCompleted(Date referralDateCutoff, String sendto) {
        Query query = entityManager.createNativeQuery("select count(*) from consultationRequests where referalDate < ?1 and status != 4 and sendto = ?2");
        query.setParameter(1, referralDateCutoff);
        query.setParameter(2, sendto);

        return ((Number) query.getSingleResult()).intValue();
    }

    public List<ConsultationRequest> getConsults(Integer demoNo) {
        StringBuilder sql = new StringBuilder("select cr from ConsultationRequest cr, Demographic d, Provider p where d.demographicNo = cr.demographicId and p.providerNo = cr.providerNo and cr.demographicId = ?1");
        Query query = entityManager.createQuery(sql.toString());
        query.setParameter(1, demoNo);

        List<ConsultationRequest> results = query.getResultList();
        return results;
    }


    /**
     * Queries consultation requests for the consult list view, applying the optional team, status
     * and date filters and the legacy order-by / pagination contract. The query LEFT-joins the
     * specialist association plus the demographic, provider and service rows so the optional
     * ORDER BY columns are available, and binds every filter value as a Criteria parameter.
     *
     * @param team          {@code sendTo} value to match exactly; {@code null} or empty skips the team filter
     * @param showCompleted when {@code false}, rows with status {@code "4"} (and NULL status) are excluded
     * @param startDate     inclusive lower bound on the searched date column (see {@code searchDate}), or {@code null} for none
     * @param endDate       inclusive upper bound on the searched date column (see {@code searchDate}), or {@code null} for none
     * @param orderby       legacy sort token {@code "1"}-{@code "9"}; {@code null} or an unknown token falls back to referral date descending
     * @param desc          primary sort direction; {@code "1"} sorts descending, any other value ascending
     * @param searchDate    {@code "1"} filters on {@code appointmentDate}, any other value filters on {@code referralDate}
     * @param offset        zero-based index of the first row to return; {@code null} starts at {@code 0}
     * @param limit         maximum rows to return; {@code null} uses {@link ConsultationRequestDao#DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT}, capped at the maximum list return size
     * @return the matching consultation requests in the requested order, never {@code null}
     * @since 2026-06-18
     */
    public List<ConsultationRequest> getConsults(String team, boolean showCompleted, Date startDate, Date endDate, String orderby, String desc, String searchDate, Integer offset, Integer limit) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ConsultationRequest> cq = cb.createQuery(ConsultationRequest.class);
        JpaRoot<ConsultationRequest> cr = (JpaRoot<ConsultationRequest>) cq.from(ConsultationRequest.class);

        // professionalSpecialist is a mapped @ManyToOne association: clean LEFT association join.
        Join<ConsultationRequest, ProfessionalSpecialist> specialist =
                cr.join("professionalSpecialist", JoinType.LEFT);

        // ConsultationServices, Demographic and Provider are NOT mapped associations on
        // ConsultationRequest (the columns are raw FK scalars). They are reproduced as Hibernate
        // LEFT entity joins so the optional ORDER BY columns stay available; the correlation
        // predicates live in the ON clause to preserve LEFT (not INNER) semantics. The legacy
        // 'ereferral_service' ConsultationRequestExt join was a dead join (never selected,
        // filtered or ordered on) whose only effect was to multiply result rows; it is dropped.
        JpaEntityJoin<ConsultationRequest, ConsultationServices> service =
                cr.join(ConsultationServices.class, JoinType.LEFT);
        service.on(cb.equal(cr.get("serviceId"), service.get("serviceId")));

        JpaEntityJoin<ConsultationRequest, Demographic> demographic =
                cr.join(Demographic.class, JoinType.LEFT);
        demographic.on(cb.equal(cr.get("demographicId"), demographic.get("demographicNo")));

        JpaEntityJoin<ConsultationRequest, Provider> provider =
                cr.join(Provider.class, JoinType.LEFT);
        provider.on(cb.equal(demographic.get("providerNo"), provider.get("providerNo")));

        List<Predicate> predicates = new ArrayList<>();

        if (!showCompleted) {
            // NULL-excluding, exactly like the legacy "cr.status != '4'": rows with NULL status
            // are dropped (NULL != '4' is UNKNOWN). status is a String literal, not promoted.
            predicates.add(cb.notEqual(cr.get("status"), "4"));
        }

        if (team != null && !team.isEmpty()) {
            // sendTo is bound as a parameter. The original code concatenated team into the HQL
            // string; #2898 moved it to a named :team parameter, and this keeps it bound in the
            // type-safe Criteria form. A null or empty team skips the filter (returns all teams),
            // consistent with how the empty-string case is already handled and avoiding a latent NPE.
            predicates.add(cb.equal(cr.get("sendTo"), team));
        }

        // Date bounds target appointmentDate when searchDate=="1", else referralDate; values are
        // bound as real Date parameters (the legacy code formatted them to datetime strings).
        String dateAttr = "1".equals(searchDate) ? "appointmentDate" : "referralDate";
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(cr.<Date>get(dateAttr), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(cr.<Date>get(dateAttr), endDate));
        }

        cq.select(cr).where(predicates.toArray(new Predicate[0]));
        cq.orderBy(buildConsultsOrder(cb, cr, specialist, service, demographic, provider, orderby, desc));

        TypedQuery<ConsultationRequest> query = entityManager.createQuery(cq);
        query.setFirstResult(offset != null ? offset : 0);

        //need to never send more than MAX_LIST_RETURN_SIZE
        int myLimit = limit != null ? limit : DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT;
        query.setMaxResults(Math.min(myLimit, MAX_LIST_RETURN_SIZE));

        return query.getResultList();
    }

    /**
     * Builds the ORDER BY for {@link #getConsults(String, boolean, Date, Date, String, String, String, Integer, Integer)},
     * reproducing the legacy hand-rolled token whitelist exactly.
     *
     * <p>Tokens {@code "1"}-{@code "9"} map to fixed columns; the primary direction is
     * {@code DESC} when {@code desc} equals {@code "1"} and ascending otherwise. Tokens
     * {@code "1","2","3","4","6"} append the legacy secondary sort {@code service.serviceDesc},
     * which was always ascending (no direction token). A {@code null} or unrecognized
     * {@code orderby} falls back to {@code referralDate} descending, regardless of {@code desc},
     * matching the legacy default branches.</p>
     */
    private List<Order> buildConsultsOrder(CriteriaBuilder cb, Root<ConsultationRequest> cr,
            Join<?, ?> specialist, Join<?, ?> service, Join<?, ?> demographic, Join<?, ?> provider,
            String orderby, String desc) {

        boolean descending = "1".equals(desc);
        Order secondary = cb.asc(service.get("serviceDesc"));

        if (orderby == null) {
            return List.of(cb.desc(cr.get("referralDate")));
        }

        switch (orderby) {
            case "1": // msgStatus
                return List.of(consultsDirection(cb, cr.get("status"), descending), secondary);
            case "2": // msgTeam
                return List.of(consultsDirection(cb, cr.get("sendTo"), descending), secondary);
            case "3": // msgPatient
                return List.of(consultsDirection(cb, demographic.get("lastName"), descending), secondary);
            case "4": // msgProvider
                return List.of(consultsDirection(cb, provider.get("lastName"), descending), secondary);
            case "5": // msgServiceDesc
                return List.of(consultsDirection(cb, service.get("serviceDesc"), descending));
            case "6": // msgSpecialistName
                return List.of(consultsDirection(cb, specialist.get("lastName"), descending), secondary);
            case "7": // msgRefDate
                return List.of(consultsDirection(cb, cr.get("referralDate"), descending));
            case "8": // appointmentDate
                return List.of(consultsDirection(cb, cr.get("appointmentDate"), descending));
            case "9": // followUpDate
                return List.of(consultsDirection(cb, cr.get("followUpDate"), descending));
            default:
                return List.of(cb.desc(cr.get("referralDate")));
        }
    }

    /** Wraps the given expression as an ascending or descending {@link Order} per the {@code descending} flag. */
    private Order consultsDirection(CriteriaBuilder cb, Expression<?> expression, boolean descending) {
        return descending ? cb.desc(expression) : cb.asc(expression);
    }


    public List<ConsultationRequest> getConsultationsByStatus(Integer demographicNo, String status) {
        Query query = entityManager.createQuery("SELECT c FROM ConsultationRequest c where c.demographicId = ?1 and c.status = ?2");
        query.setParameter(1, demographicNo);
        query.setParameter(2, status);


        List<ConsultationRequest> results = query.getResultList();
        return results;
    }

    public ConsultationRequest getConsultation(Integer requestId) {
        return this.find(requestId);
    }


    public List<ConsultationRequest> getReferrals(String providerId, Date cutoffDate) {
        Query query = createQuery("cr", "cr.referralDate <= ?1 AND cr.status = '1' and cr.providerNo = ?2");
        query.setParameter(1, cutoffDate);
        query.setParameter(2, providerId);
        return query.getResultList();
    }

    public List<Object[]> findRequests(Date timeLimit, String providerNo) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT d.lastName, c.demographicId FROM ConsultationRequest c, Demographic d " +
                "WHERE c.referralDate >= ?1" +
                " AND c.demographicId = d.demographicNo");
        if (providerNo != null) {
            sql.append(" AND d.providerNo = ?2");
        }
        sql.append(" ORDER BY d.lastName");

        Query query = entityManager.createQuery(sql.toString());
        query.setParameter(1, timeLimit);
        if (providerNo != null) {
            query.setParameter(2, providerNo);
        }
        return query.getResultList();
    }

    public List<ConsultationRequest> findRequestsByDemoNo(Integer demoId, Date cutoffDate) {
        Query query = createQuery("cr", "cr.referralDate <= ?1 AND cr.demographicId = ?2");
        query.setParameter(1, cutoffDate);
        query.setParameter(2, demoId);
        return query.getResultList();
    }

    public List<ConsultationRequest> findByDemographicAndService(Integer demographicNo, String serviceName) {
        String sql = "SELECT cr FROM ConsultationRequest cr, ConsultationServices cs WHERE cr.serviceId = cs.serviceId and cr.demographicId = ?1 and cs.serviceDesc = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, serviceName);

        return query.getResultList();
    }

    public List<ConsultationRequest> findByDemographicAndServices(Integer demographicNo, List<String> serviceNameList) {
        String sql = "SELECT cr FROM ConsultationRequest cr, ConsultationServices cs WHERE cr.serviceId = cs.serviceId and cr.demographicId = ?1 and cs.serviceDesc IN (?2)";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, serviceNameList);

        return query.getResultList();
    }

    @NativeSql("consultationRequests")
    public List<Integer> findNewConsultationsSinceDemoKey(String keyName) {

        String sql = "select distinct dr.demographicNo from consultationRequests dr,demographic d,demographicExt e where dr.demographicNo = d.demographic_no and d.demographic_no = e.demographic_no and e.key_val=?1 and dr.lastUpdateDate > e.value";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, keyName);
        return query.getResultList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Executes a JPQL {@code SELECT NEW} projection with a LEFT JOIN on
     * {@code professionalSpecialist} to pre-populate the specialist's name,
     * ordered by {@code referralDate} descending.</p>
     */
    @Override
    public List<ConsultationRequestListItemDTO> findConsultationDTOsByDemographicId(Integer demographicId) {
        Query query = entityManager.createQuery("""
                SELECT NEW io.github.carlos_emr.carlos.consultation.dto.ConsultationRequestListItemDTO(
                    cr.id, cr.referralDate, cr.serviceId, cr.demographicId,
                    cr.providerNo, cr.status, cr.statusText, cr.urgency,
                    cr.reasonForReferral, cr.appointmentDate, cr.followUpDate,
                    cr.sendTo, cr.siteName, cr.letterheadName, cr.source, cr.lastUpdateDate,
                    ps.lastName, ps.firstName)
                FROM ConsultationRequest cr
                LEFT JOIN cr.professionalSpecialist ps
                WHERE cr.demographicId = :demoId
                ORDER BY cr.referralDate DESC
                """);
        query.setParameter("demoId", demographicId);
        return query.getResultList();
    }
}
