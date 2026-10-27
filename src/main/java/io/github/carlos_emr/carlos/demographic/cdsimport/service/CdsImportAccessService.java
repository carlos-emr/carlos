/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconstructs and reauthorizes explicit operator/provider scopes at each durable write boundary. */
@Service
public class CdsImportAccessService {
    private final SecurityInfoManager security;
    private final CdsImportConfigurationService configuration;
    private final CdsCourseService courses;
    private final CdsProgramContextService programs;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    public CdsImportAccessService(SecurityInfoManager security, CdsImportConfigurationService configuration, CdsCourseService courses, CdsProgramContextService programs) {
        this.security = security;
        this.configuration = configuration;
        this.courses = courses;
        this.programs = programs;
    }

    @Transactional(readOnly = true)
    public CdsImportActorDto webActor(LoggedInInfo supplied, CdsImportCommand command) {
        if (supplied == null || supplied.getLoggedInSecurity() == null
                || supplied.getLoggedInProviderNo() == null) deny();
        Security account = activeAccount(supplied.getLoggedInSecurity().getSecurityNo());
        if (!Objects.equals(account.getProviderNo(), supplied.getLoggedInProviderNo())) deny();
        String operator = "web-" + account.getSecurityNo();
        if (!Objects.equals(command.providerNo(), account.getProviderNo())) {
            configuration.requireDelegation(operator, command.providerNo(), command.facilityId(), command.programId());
        }
        CdsImportActorDto actor = new CdsImportActorDto(operator, command.providerNo(), account.getProviderNo(),
                account.getSecurityNo(), command.facilityId(), command.programId());
        context(actor, command, null);
        return actor;
    }

    /** Caller supplies an independently verified root-owned delegation ticket, never an arbitrary username. */
    @Transactional(readOnly = true)
    public CdsImportActorDto delegatedActor(String verifiedOperator, CdsImportCommand command) {
        configuration.requireDelegation(verifiedOperator, command.providerNo(), command.facilityId(), command.programId());
        CdsImportActorDto actor = new CdsImportActorDto(verifiedOperator, command.providerNo(), null, null,
                command.facilityId(), command.programId());
        context(actor, command, null);
        return actor;
    }

    @Transactional(readOnly = true)
    public LoggedInInfo context(CdsImportActorDto actor, CdsImportCommand command, Integer target) {
        if (actor == null || !Objects.equals(actor.providerNo(), command.providerNo())
                || !Objects.equals(actor.facilityId(), command.facilityId())
                || !Objects.equals(actor.programId(), command.programId())) deny();
        configuration.requireSource(command.sourceNamespace(), command.sourceProfile());
        Security account = actor.securityNo() == null ? null : activeAccount(actor.securityNo());
        if (account != null) {
            if (!Objects.equals(account.getProviderNo(), actor.operatorProviderNo())
                    || !Objects.equals(actor.operator(), "web-" + account.getSecurityNo())) deny();
            if (!Objects.equals(actor.providerNo(), actor.operatorProviderNo())) {
                configuration.requireDelegation(actor.operator(), actor.providerNo(), actor.facilityId(), actor.programId());
            }
        } else configuration.requireDelegation(actor.operator(), actor.providerNo(), actor.facilityId(), actor.programId());

        // A historical clinical migration is an explicit administrative capability. Ordinary
        // demographic editing alone cannot authorize writes to prescriptions, labs and notes.
        if (account != null) requireImportPrivilege(providerContext(actor.operatorProviderNo(), actor.facilityId(), account));

        LoggedInInfo effective = providerContext(actor.providerNo(), actor.facilityId(), account);
        requirePrivileges(effective, command.mergeExisting(), target);
        if (account != null && !Objects.equals(actor.operatorProviderNo(), actor.providerNo())) {
            requirePrivileges(providerContext(actor.operatorProviderNo(), actor.facilityId(), account), command.mergeExisting(), target);
        }
        if (command.synthetic()) courses.requireCopy(actor, command);
        if (actor.programId() != null && !command.synthetic()) {
            var program = programs.metadata(actor.programId());
            if (program == null || !"active".equalsIgnoreCase(program.status())
                    || (actor.facilityId() != null && !Objects.equals(program.facilityId(), actor.facilityId()))) deny();
            if (!programs.member(actor.providerNo(), actor.programId())) deny();
        }
        effective.setInitiatingCode("CdsImport/" + actor.operator());
        effective.setIp(actor.securityNo() == null ? "cli/delegated" : "web/import-worker");
        return effective;
    }

    @Transactional(readOnly = true)
    public void requireView(LoggedInInfo supplied, CdsImportJobDto job) {
        if (supplied == null || supplied.getLoggedInSecurity() == null) deny();
        Security account = activeAccount(supplied.getLoggedInSecurity().getSecurityNo());
        if (!Objects.equals(account.getProviderNo(), supplied.getLoggedInProviderNo())) deny();
        LoggedInInfo current = providerContext(account.getProviderNo(),
                supplied.getCurrentFacility() == null ? null : supplied.getCurrentFacility().getId(), account);
        requireImportPrivilege(current);
        if (!Objects.equals(job.operator(), "web-" + account.getSecurityNo())
                && !security.hasPrivilegeReadOnly(current, "_admin", "w", null)) deny();
        requirePrivileges(current, false, null);
    }

    /** Administrative transfer/status requests recheck the current account and provider independently of session age. */
    @Transactional(readOnly = true)
    public void requireOperatorSession(LoggedInInfo supplied) {
        if (supplied == null || supplied.getLoggedInSecurity() == null) deny();
        Security account = activeAccount(supplied.getLoggedInSecurity().getSecurityNo());
        if (!Objects.equals(account.getProviderNo(), supplied.getLoggedInProviderNo())) deny();
        LoggedInInfo current = providerContext(account.getProviderNo(),
                supplied.getCurrentFacility() == null ? null : supplied.getCurrentFacility().getId(), account);
        requirePrivileges(current, false, null);
        requireImportPrivilege(current);
    }

    /** Recheck the live account, requested patient and category read right for every clinical source-field read. */
    @Transactional(readOnly = true)
    public void requireClinicalRead(LoggedInInfo supplied, int demographicNo, String category) {
        if (supplied == null || supplied.getLoggedInSecurity() == null || demographicNo < 1) deny();
        String object = switch (category == null ? "" : category) {
            case "Demographics" -> "_demographic";
            case "MedicationsAndTreatments" -> "_rx";
            case "AllergiesAndAdverseReactions" -> "_allergy";
            case "Immunizations" -> "_prevention";
            case "LaboratoryResults" -> "_lab";
            case "Reports" -> "_edoc";
            case "Appointments" -> "_appointment";
            case "CareElements" -> "_measurement";
            case "PersonalHistory", "FamilyHistory", "PastHealth", "ProblemList", "RiskFactors",
                    "AlertsAndSpecialNeeds", "ClinicalNotes", "NewCategory" -> "_casemgmt.notes";
            default -> throw new IllegalArgumentException("IMPORT_CATEGORY_INVALID");
        };
        Security account = activeAccount(supplied.getLoggedInSecurity().getSecurityNo());
        if (!Objects.equals(account.getProviderNo(), supplied.getLoggedInProviderNo())) deny();
        LoggedInInfo current = providerContext(account.getProviderNo(),
                supplied.getCurrentFacility() == null ? null : supplied.getCurrentFacility().getId(), account);
        String patient = Integer.toString(demographicNo);
        if (!security.hasPrivilegeReadOnly(current, "_demographic", "r", patient)
                || !security.hasPrivilegeReadOnly(current, object, "r", patient)
                || !security.isAllowedAccessToPatientRecord(current, demographicNo)) deny();
    }

    private LoggedInInfo providerContext(String providerNo, Integer facilityId, Security account) {
        Provider provider = entityManager.find(Provider.class, providerNo);
        if (provider == null || !"1".equals(provider.getStatus())) deny();
        LoggedInInfo context = new LoggedInInfo();
        context.setLoggedInProvider(provider);
        context.setLoggedInSecurity(account);
        if (facilityId != null) {
            Facility facility = entityManager.find(Facility.class, facilityId);
            if (facility == null) deny();
            Number count = (Number) entityManager.createNativeQuery(
                    "select count(*) from provider_facility where provider_no=:provider and facility_id=:facility")
                    .setParameter("provider", providerNo).setParameter("facility", facilityId).getSingleResult();
            if (count.longValue() == 0) deny();
            context.setCurrentFacility(facility);
        }
        return context;
    }

    private Security activeAccount(Integer securityNo) {
        Security account = securityNo == null ? null : entityManager.find(Security.class, securityNo);
        // Local/remote lock flags configure login PIN checks; they are not account-disable flags.
        if (account == null || (Integer.valueOf(1).equals(account.getBExpireset())
                && account.getDateExpiredate() != null && account.getDateExpiredate().before(new Date()))) deny();
        return account;
    }

    private void requirePrivileges(LoggedInInfo actor, boolean merge, Integer target) {
        String patient = target == null ? null : target.toString();
        if (!security.hasPrivilegeReadOnly(actor, "_demographic", "w", patient)
                || (merge && !security.hasPrivilegeReadOnly(actor, "_demographic", "r", patient))
                || (target != null && !security.isAllowedAccessToPatientRecord(actor, target))) deny();
    }

    private void requireImportPrivilege(LoggedInInfo actor) {
        if (!security.hasPrivilegeReadOnly(actor, "_admin", "w", null)
                && !security.hasPrivilegeReadOnly(actor, "_cds_import", "w", null)) {
            throw new SecurityException("CDS import administration privilege required");
        }
    }

    private static void deny() { throw new SecurityException("missing required sec object (_demographic)"); }
}
