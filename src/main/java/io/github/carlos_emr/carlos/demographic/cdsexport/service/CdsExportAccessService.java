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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobOwnerDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconstructs current account/provider/facility and permissions at each disclosure boundary.
 * A saved job actor is identity, not a saved grant; logout alone does not cancel accepted work.
 * Each call owns a fresh read-committed transaction, so an enclosing captured source snapshot
 * cannot keep a revoked permission alive. Never invoke while holding a maintenance capability
 * that would block these ordinary clinical security reads.
 */
@Service
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class CdsExportAccessService {
    private final SecurityInfoManager security;
    private final CdsExportConfigurationService configuration;
    private final ProviderDao providers;
    private final SecurityDao accounts;
    private final ProviderFacilityDao memberships;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    public CdsExportAccessService(SecurityInfoManager security, CdsExportConfigurationService configuration,
            ProviderDao providers, SecurityDao accounts, ProviderFacilityDao memberships) {
        this.security = Objects.requireNonNull(security); this.configuration = Objects.requireNonNull(configuration);
        this.providers = Objects.requireNonNull(providers); this.accounts = Objects.requireNonNull(accounts);
        this.memberships = Objects.requireNonNull(memberships);
    }
    public CdsExportActorDto webActor(LoggedInInfo supplied, CdsExportCommand command) {
        Security account = sessionAccount(supplied);
        CdsExportActorDto actor = new CdsExportActorDto("web-" + account.getSecurityNo(), command.providerNo(),
                account.getSecurityNo(), command.facilityId());
        context(actor, command, null, null, false); return actor;
    }
    /** Caller must first verify the immutable root-issued exact-argv delegation ticket. */
    public CdsExportActorDto delegatedActor(String verifiedOperator, CdsExportCommand command) {
        CdsExportActorDto actor = new CdsExportActorDto(verifiedOperator, command.providerNo(), null, command.facilityId());
        context(actor, command, null, null, false); return actor;
    }
    /** Worker entry rechecks the recorded actor's current global scope before any patient discovery. */
    public void requireScope(CdsExportActorDto actor, CdsExportCommand command) { context(actor, command, null, null, false); }
    /** Current delegated metadata identity; the provider establishes live administration, not a replacement disclosure scope. */
    public CdsExportActorDto delegatedSession(String verifiedOperator, String provider, Integer historicalFacilityHint) {
        Integer facility = configuration.metadataFacility(verifiedOperator, historicalFacilityHint);
        var actor = new CdsExportActorDto(verifiedOperator, provider, null, facility); metadataContext(actor); return actor;
    }
    /** Admission/capture/publication/retrieval callers recheck every selected category and patient. */
    public LoggedInInfo requirePatient(CdsExportActorDto actor, CdsExportCommand command, int patient) {
        requireSelectedPatient(command, patient); return context(actor, command, patient, null, false);
    }
    /** Source adapters must additionally flag actual sensitive laboratory occurrences; absence of that flag is not proof. */
    public LoggedInInfo requireCategory(CdsExportActorDto actor, CdsExportCommand command,
            int patient, CdsCategory category, boolean sensitiveLaboratory) {
        requireSelectedPatient(command, patient);
        if (category == null || !command.categories().contains(category)
                || sensitiveLaboratory && category != CdsCategory.LABORATORY_RESULTS) deny();
        return context(actor, command, patient, category, sensitiveLaboratory);
    }
    /** Native pharmacy rights apply even when MedicationsAndTreatments is not selected for the wire. */
    public LoggedInInfo requirePharmacyRead(CdsExportActorDto actor, CdsExportCommand command, int patient) {
        requireSelectedPatient(command, patient);
        if (!command.categories().contains(CdsCategory.DEMOGRAPHICS)) deny();
        return context(actor, command, patient, CdsCategory.DEMOGRAPHICS, false, "_rx");
    }
    private static void requireSelectedPatient(CdsExportCommand command, int patient) {
        if (patient < 1 || command == null || command.selection() == CdsExportCommand.Selection.SELECTED_PATIENTS
                && java.util.Collections.binarySearch(command.patientNos(), patient) < 0) deny();
    }
    /** Owner or current administrator may observe/control metadata; this never substitutes for clinical publication/download authority. */
    public void requireObserve(CdsExportActorDto observer, CdsExportJobOwnerDto owner) {
        LoggedInInfo current = metadataContext(observer);
        if (!Objects.equals(observer.operatorKey(), owner.operatorKey())
                && !security.hasPrivilegeReadOnly(current, "_admin", "w", null)) deny();
    }
    /** Compatibility overload for trusted callers already holding a validated immutable intent. */
    public void requireObserve(CdsExportActorDto observer, CdsExportJobDocumentDto document) {
        requireObserve(observer, new CdsExportJobOwnerDto(document.actor().operatorKey(), document.command().providerNo(), document.command().facilityId()));
    }
    private LoggedInInfo metadataContext(CdsExportActorDto actor) {
        if (actor == null) deny();
        Security account = actor.securityNo() == null ? null : activeAccount(actor.securityNo());
        if (account == null) configuration.requireDelegation(actor.operator(), actor.providerNo(), actor.facilityId());
        else if (!actor.operator().equals("web-" + account.getSecurityNo()) || !Objects.equals(actor.providerNo(), account.getProviderNo())) deny();
        LoggedInInfo current = providerContext(actor.providerNo(), actor.facilityId(), account);
        requireAdministration(current); requireMetadataRead(current); return current;
    }
    /** Metadata/history authorization without import admission, clinical writes, or a fabricated export command. */
    public CdsExportActorDto operatorSession(LoggedInInfo supplied) {
        Security account = sessionAccount(supplied);
        Integer facility = supplied.getCurrentFacility() == null ? null : supplied.getCurrentFacility().getId();
        LoggedInInfo current = providerContext(account.getProviderNo(), facility, account); requireAdministration(current);
        if (!security.hasPrivilegeReadOnly(current, "_demographic", "r", null)) deny();
        return new CdsExportActorDto("web-" + account.getSecurityNo(), account.getProviderNo(), account.getSecurityNo(), facility);
    }
    private LoggedInInfo context(CdsExportActorDto actor, CdsExportCommand command, Integer patient,
            CdsCategory only, boolean sensitive) {
        return context(actor, command, patient, only, sensitive, null);
    }
    private LoggedInInfo context(CdsExportActorDto actor, CdsExportCommand command, Integer patient,
            CdsCategory only, boolean sensitive, String additionalReadObject) {
        CurrentIdentity current = identity(actor, command);
        requireReads(current.effective(), command, patient, only, sensitive);
        if (additionalReadObject != null && (!security.hasPrivilegeReadOnly(current.effective(), additionalReadObject, "r", null)
                || !security.hasPrivilegeReadOnly(current.effective(), additionalReadObject, "r", patient.toString()))) deny();
        if (current.operator() != null) {
            requireReads(current.operator(), command, patient, only, sensitive);
            if (additionalReadObject != null && (!security.hasPrivilegeReadOnly(current.operator(), additionalReadObject, "r", null)
                || !security.hasPrivilegeReadOnly(current.operator(), additionalReadObject, "r", patient.toString()))) deny();
        }
        return current.effective();
    }
    private CurrentIdentity identity(CdsExportActorDto actor, CdsExportCommand command) {
        if (actor == null || !Objects.equals(actor.providerNo(), command.providerNo())
                || !Objects.equals(actor.facilityId(), command.facilityId())) deny();
        Security account = actor.securityNo() == null ? null : activeAccount(actor.securityNo());
        LoggedInInfo operator = null;
        if (account != null) {
            if (!actor.operator().equals("web-" + account.getSecurityNo())) deny();
            operator = providerContext(account.getProviderNo(), actor.facilityId(), account);
            requireAdministration(operator);
            if (!Objects.equals(actor.providerNo(), account.getProviderNo())) {
                configuration.requireDelegation(actor.operator(), actor.providerNo(), actor.facilityId());
            }
        } else configuration.requireDelegation(actor.operator(), actor.providerNo(), actor.facilityId());
        LoggedInInfo effective = providerContext(actor.providerNo(), actor.facilityId(), account);
        requireAdministration(effective);
        if (operator != null && Objects.equals(actor.providerNo(), account.getProviderNo())) operator = null;
        effective.setInitiatingCode("CdsExport/" + actor.operatorKey());
        effective.setIp(actor.securityNo() == null ? "cli/delegated" : "web/export-worker");
        return new CurrentIdentity(effective, operator);
    }
    private void requireReads(LoggedInInfo context, CdsExportCommand command, Integer patient, CdsCategory only, boolean sensitive) {
        String target = patient == null ? null : patient.toString();
        if (!security.hasPrivilegeReadOnly(context, "_demographic", "r", target)
                || patient != null && !security.isAllowedAccessToPatientRecord(context, patient)) deny();
        for (CdsCategory category : command.categories()) {
            if (only != null && category != only) continue;
            if (!security.hasPrivilegeReadOnly(context, io.github.carlos_emr.carlos.demographic.cds.security.CdsCategoryReadPermission.object(category), "r", target)) deny();
        }
        if (sensitive && !security.hasPrivilegeReadOnly(context, "_lab_sensitive", "r", target)) deny();
    }
    private void requireAdministration(LoggedInInfo actor) {
        if (!security.hasPrivilegeReadOnly(actor, "_admin", "w", null)
                && !security.hasPrivilegeReadOnly(actor, "_cds_export", "w", null)) deny();
    }
    private Security sessionAccount(LoggedInInfo supplied) {
        if (supplied == null || supplied.getLoggedInSecurity() == null || supplied.getLoggedInProviderNo() == null) deny();
        Security account = activeAccount(supplied.getLoggedInSecurity().getSecurityNo());
        if (!Objects.equals(account.getProviderNo(), supplied.getLoggedInProviderNo())) deny(); return account;
    }
    private Security activeAccount(Integer number) {
        Security account = number == null ? null : accounts.find(number.intValue());
        if (account == null || Integer.valueOf(1).equals(account.getBExpireset())
                && account.getDateExpiredate() != null && account.getDateExpiredate().before(new Date())) deny(); return account;
    }
    private LoggedInInfo providerContext(String number, Integer facilityId, Security account) {
        Provider provider = providers.getProvider(number);
        if (provider == null || !Objects.equals(number, provider.getProviderNo()) || !"1".equals(provider.getStatus())) deny();
        LoggedInInfo context = new LoggedInInfo(); context.setLoggedInProvider(provider); context.setLoggedInSecurity(account);
        if (facilityId != null) {
            Facility facility = entityManager.find(Facility.class, facilityId);
            if (facility == null || memberships.countByProviderAndFacility(number, facilityId) == 0) deny(); context.setCurrentFacility(facility);
        }
        return context;
    }
    private void requireMetadataRead(LoggedInInfo context) {
        if (!security.hasPrivilegeReadOnly(context, "_demographic", "r", null)) deny();
    }
    private record CurrentIdentity(LoggedInInfo effective, LoggedInInfo operator) {
        @Override public String toString() { return "CurrentIdentity[protected]"; }
    }
    private static void deny() { throw new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED"); }
}
