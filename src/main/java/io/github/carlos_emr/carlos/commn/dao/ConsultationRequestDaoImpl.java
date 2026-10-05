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

import jakarta.persistence.LockModeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.consultation.dto.ConsultantOptionDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationListFilterDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationRequestListItemDTO;

@SuppressWarnings("unchecked")
public class ConsultationRequestDaoImpl extends AbstractDaoImpl<ConsultationRequest> implements ConsultationRequestDao {

    /**
     * Name tokens honoured by {@link #searchDistinctConsultants(String, int)}. A person's name has
     * at most a handful of parts; the cap keeps a pasted paragraph from producing an unbounded
     * number of LIKE predicates.
     */
    static final int MAX_CONSULTANT_SEARCH_TOKENS = 4;

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
        // A consultation request belongs to the PATIENT and must appear in the
        // patient's chart regardless of the state of the ordering provider's record.
        // The previous query cross-joined Demographic AND Provider purely as existence
        // filters (neither table is projected), so a consult whose providerNo was null
        // or referenced a provider row that no longer exists was silently dropped from
        // the Consultations tab. The demographic constraint (cr.demographicId = ?1)
        // already scopes the result to this patient, and the caller
        // (EctViewConsultationRequestsUtil) null-tolerantly re-resolves the provider
        // and demographic per row, so select on the demographic alone.
        //
        // That null-tolerance is a CONTRACT this query depends on, not an incidental
        // detail: dropping the joins is what lets rows with a dangling demographic or
        // provider reach the caller, and the caller shares one try/catch across its
        // whole loop, so a single unguarded dereference there blanks the entire
        // Consultations tab rather than degrading one row.
        Query query = entityManager.createQuery(
                "select cr from ConsultationRequest cr where cr.demographicId = ?1");
        query.setParameter(1, demoNo);

        List<ConsultationRequest> results = query.getResultList();
        return results;
    }


    public List<ConsultationRequest> getConsults(String team, boolean showCompleted, Date startDate, Date endDate, String orderby, String desc, String searchDate, Integer offset, Integer limit) {
        return getConsults(new ConsultationListFilterDto(team, showCompleted, startDate, endDate, orderby, desc,
                searchDate, offset, limit, null, null));
    }

    @Override
    public List<ConsultationRequest> getConsults(ConsultationListFilterDto filter) {
        Objects.requireNonNull(filter, "filter");
        if ((filter.visibleProviderNos() != null && filter.visibleProviderNos().isEmpty())
                || (filter.visibleSiteNames() != null && filter.visibleSiteNames().isEmpty())) {
            return List.of();
        }
        String team = filter.team();
        boolean showCompleted = filter.showCompleted();
        Date startDate = filter.startDate();
        Date endDate = filter.endDate();
        String orderby = filter.orderby();
        String desc = filter.desc();
        String searchDate = filter.searchDate();
        Integer offset = filter.offset();
        Integer limit = filter.limit();
        Integer consultantId = filter.consultantId();
        String mrpProviderNo = filter.mrpProviderNo() == null ? null : filter.mrpProviderNo().trim();
        boolean filterByMrp = mrpProviderNo != null && !mrpProviderNo.isEmpty();

        	StringBuilder sql = new StringBuilder("SELECT cr " +
					"FROM ConsultationRequest cr " +
                    "LEFT JOIN cr.professionalSpecialist specialist " +
                    "LEFT JOIN ConsultationServices service ON cr.serviceId = service.serviceId " +
                    "LEFT JOIN ConsultationRequestExt ext ON cr.id = ext.requestId AND ext.key = 'ereferral_service' " +
					"LEFT JOIN Demographic d on cr.demographicId = d.demographicNo " +
					"LEFT JOIN Provider p on d.providerNo = p.providerNo WHERE 1=1 ");

        // Apply visibility before offsets and the lookahead row, matching the list's privacy checks.
        if (filter.visibleProviderNos() != null) sql.append("and p.providerNo in (:visibleProviders) ");
        if (filter.visibleSiteNames() != null) sql.append("and cr.siteName in (:visibleSites) ");

        if (!showCompleted) {
            sql.append("and cr.status != '4' ");
        }

        if (team != null && !team.isEmpty()) {
            sql.append("and cr.sendTo = :team ");
        }

        boolean searchByAppt = searchDate != null && searchDate.equals("1");

        if (startDate != null) {
            sql.append(searchByAppt
                    ? "and cr.appointmentDate >= :startDate "
                    : "and cr.referralDate >= :startDate ");
        }

        if (endDate != null) {
            sql.append(searchByAppt
                    ? "and cr.appointmentDate <= :endDate "
                    : "and cr.referralDate <= :endDate ");
        }

        // Issue #3976: "every request sent to Dr X" and "every request for my own patients".
        // The MRP is the patient's demographic.provider_no (the same column the list shows in its
        // Provider column), not the requesting provider on the consult row.
        if (consultantId != null) {
            sql.append("and specialist.id = :consultantId ");
        }

        if (filterByMrp) {
            sql.append("and d.providerNo = :mrpProviderNo ");
        }

        String orderDesc = desc != null && desc.equals("1") ? "DESC" : "";
        String service = ", service.serviceDesc";
        if (orderby == null) {
            sql.append("order by cr.referralDate desc ");
        } else if (orderby.equals("1")) {               //1 = msgStatus
            sql.append("order by cr.status " + orderDesc + service);
        } else if (orderby.equals("2")) {               //2 = msgTeam
            sql.append("order by cr.sendTo " + orderDesc + service);
        } else if (orderby.equals("3")) {               //3 = msgPatient
            sql.append("order by d.lastName " + orderDesc + service);
        } else if (orderby.equals("4")) {               //4 = msgProvider
            sql.append("order by p.lastName " + orderDesc + service);
        } else if (orderby.equals("5")) {               //5 = msgService Desc
            sql.append("order by service.serviceDesc " + orderDesc);
        } else if (orderby.equals("6")) {               //6 = msgSpecialist Name
            sql.append("order by specialist.lastName " + orderDesc + service);
        } else if (orderby.equals("7")) {               //7 = msgRefDate
            sql.append("order by cr.referralDate " + orderDesc);
        } else if (orderby.equals("8")) {               //8 = Appointment Date
            sql.append("order by cr.appointmentDate " + orderDesc);
        } else if (orderby.equals("9")) {               //9 = FollowUp Date
            sql.append("order by cr.followUpDate " + orderDesc);
        } else {
            sql.append("order by cr.referralDate desc");
        }


        // Equal dates/names must not reshuffle between pages.
        sql.append(", cr.id");

        Query query = entityManager.createQuery(sql.toString());
        if (team != null && !team.isEmpty()) {
            query.setParameter("team", team);
        }
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }
        if (consultantId != null) {
            query.setParameter("consultantId", consultantId);
        }
        if (filterByMrp) {
            query.setParameter("mrpProviderNo", mrpProviderNo);
        }
        if (filter.visibleProviderNos() != null) query.setParameter("visibleProviders", filter.visibleProviderNos());
        if (filter.visibleSiteNames() != null) query.setParameter("visibleSites", filter.visibleSiteNames());
        query.setFirstResult(offset != null ? offset : 0);

        //need to never send more than MAX_LIST_RETURN_SIZE
        int myLimit = limit != null ? limit : DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT;
        query.setMaxResults(Math.min(myLimit, MAX_LIST_RETURN_SIZE));

        return query.getResultList();
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
    /**
     * {@inheritDoc}
     *
     * <p>Only specialists referenced by a consultation request are returned (an {@code EXISTS}
     * on {@code consultationRequests.specId}), so a large specialist directory does not flood the
     * suggestions with people nobody has referred to. Every token is bound as a parameter with
     * {@code !} as the LIKE escape character; the number of predicates depends only on the token
     * count, never on the token text.</p>
     */
    @Override
    public List<ConsultantOptionDto> searchDistinctConsultants(String keyword, int maxResults) {
        List<String> tokens = tokenizeConsultantKeyword(keyword);
        if (tokens.isEmpty() || maxResults < 1) {
            return Collections.emptyList();
        }

        StringBuilder jpql = new StringBuilder("""
                SELECT NEW io.github.carlos_emr.carlos.consultation.dto.ConsultantOptionDto(
                    s.id, s.lastName, s.firstName)
                FROM ProfessionalSpecialist s
                WHERE EXISTS (SELECT 1 FROM ConsultationRequest cr WHERE cr.professionalSpecialist.id = s.id)
                """);
        for (int i = 0; i < tokens.size(); i++) {
            jpql.append(" AND LOWER(CONCAT(COALESCE(s.lastName, ''), ', ', COALESCE(s.firstName, ''))) LIKE :term")
                    .append(i)
                    .append(" ESCAPE '!'");
        }
        jpql.append(" ORDER BY s.lastName, s.firstName, s.id");

        Query query = entityManager.createQuery(jpql.toString());
        for (int i = 0; i < tokens.size(); i++) {
            query.setParameter("term" + i, "%" + escapeLikeLiteral(tokens.get(i)) + "%");
        }
        query.setMaxResults(Math.min(maxResults, MAX_LIST_RETURN_SIZE));
        return query.getResultList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Projects only the provider number and name; the dropdown never needs the full
     * {@code Provider} entity. A provider row that no longer exists is simply absent.</p>
     */
    @Override
    public List<ConsultationMrpOptionDto> findDistinctConsultMrps() {
        Query query = entityManager.createQuery("""
                SELECT NEW io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto(
                    p.providerNo, p.lastName, p.firstName)
                FROM Provider p
                WHERE EXISTS (
                    SELECT 1 FROM ConsultationRequest cr, Demographic d
                    WHERE d.demographicNo = cr.demographicId AND d.providerNo = p.providerNo)
                ORDER BY p.lastName, p.firstName, p.providerNo
                """);
        return query.getResultList();
    }

    /**
     * Splits a consultant keyword on whitespace and commas into lower-case tokens
     * ({@link Locale#ROOT}, so the result does not depend on the server locale).
     *
     * @param keyword String the raw keyword; may be null
     * @return at most {@link #MAX_CONSULTANT_SEARCH_TOKENS} non-empty tokens
     */
    static List<String> tokenizeConsultantKeyword(String keyword) {
        List<String> tokens = new ArrayList<>();
        if (keyword == null) {
            return tokens;
        }
        for (String part : keyword.toLowerCase(Locale.ROOT).split("[\\s,]+")) {
            if (!part.isEmpty()) {
                tokens.add(part);
                if (tokens.size() == MAX_CONSULTANT_SEARCH_TOKENS) {
                    break;
                }
            }
        }
        return tokens;
    }

    /**
     * Escapes the LIKE metacharacters so user text is matched literally under {@code ESCAPE '!'}.
     * The escape character itself is escaped first.
     *
     * @param value String the token to escape; not null
     * @return the escaped token
     */
    static String escapeLikeLiteral(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Override
    public ConsultationRequest lockForAttachmentSync(Integer id) {
        return entityManager.find(ConsultationRequest.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

}
