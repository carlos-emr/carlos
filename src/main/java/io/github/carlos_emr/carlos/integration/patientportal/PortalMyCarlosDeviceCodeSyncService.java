/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.managers.DeviceCodeEmailScope;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs the device-code job (clinic code v1, carlos-emr/mycarlos#141) every {@value #INTERVAL_SECONDS}
 * seconds while myCarlos device approval is switched on: it emails codes for new first-device
 * requests and settles the codes devices send back (see
 * {@link PortalMyCarlosStaffApprovalService#syncCodes}). It acts as the built-in system provider,
 * with no staff login, and never logs a code.
 *
 * <p>Mail goes out through the normal CARLOS email path from the portal invitation sender
 * ({@value PortalInviteSettings#SENDER_EMAIL_KEY}), with the usual consent gate. When that sender or
 * the patient's email is not usable, the request is left for staff to approve.
 */
@Service
public class PortalMyCarlosDeviceCodeSyncService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    static final long INTERVAL_SECONDS = 5;

    private final PortalMyCarlosStaffApprovalService approvals;
    private final BooleanSupplier enabled;
    private final Supplier<PatientPortalService> portal;
    private final Supplier<PortalMyCarlosApprovalPolicy> policy;
    private final PortalMyCarlosStaffApprovalService.CodeMail mail;
    private ScheduledExecutorService executor;

    @Autowired
    public PortalMyCarlosDeviceCodeSyncService(PortalMyCarlosStaffApprovalService approvals, EmailManager emails,
            EmailConfigDao emailConfigs, DemographicDao demographics, ClinicDAO clinics, ProviderDao providers,
            SecurityInfoManager security) {
        this(approvals, PortalMyCarlosDeviceCodeSyncService::switchedOn,
                // Until Spring has finished starting, SpringUtils has no beans: the run waits.
                () -> SpringUtils.getBeanFactory() == null ? null : SpringUtils.getBean(PatientPortalService.class),
                PortalMyCarlosApprovalPolicy::fromCarlosProperties,
                new ChartMail(emails, emailConfigs, demographics, clinics, providers, security,
                        () -> PortalInviteSettings.fromCarlosProperties().senderEmail()));
    }

    PortalMyCarlosDeviceCodeSyncService(PortalMyCarlosStaffApprovalService approvals, BooleanSupplier enabled,
            Supplier<PatientPortalService> portal, Supplier<PortalMyCarlosApprovalPolicy> policy,
            PortalMyCarlosStaffApprovalService.CodeMail mail) {
        this.approvals = Objects.requireNonNull(approvals);
        this.enabled = Objects.requireNonNull(enabled);
        this.portal = Objects.requireNonNull(portal);
        this.policy = Objects.requireNonNull(policy);
        this.mail = Objects.requireNonNull(mail);
    }

    /** On only with device approval on (which needs the portal link) and the approval job's key set. */
    static boolean switchedOn() {
        return PortalMyCarlosStaffApprovalService.isEnabled()
                && PortalJobKey.fromCarlosProperties(PortalJob.DEVICE_APPROVAL_SYNC).isPresent();
    }

    @PostConstruct
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        try {
            if (!enabled.getAsBoolean()) {
                return;
            }
            executor = Executors.newSingleThreadScheduledExecutor(
                    new DeamonThreadFactory(getClass().getSimpleName(), Thread.NORM_PRIORITY));
            executor.scheduleWithFixedDelay(this::runSafely, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
        } catch (RuntimeException failure) {
            LOGGER.warn("myCarlos device codes are off; check the approval settings; exceptionClass={}",
                    failure.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void runSafely() {
        try {
            runOnce();
        } catch (RuntimeException failure) {
            // The class only: a message could carry portal or patient detail.
            LOGGER.warn("myCarlos device code run failed; exceptionClass={}", failure.getClass().getSimpleName());
        }
    }

    /**
     * One bounded run; does nothing while device approval is off or before CARLOS has finished
     * starting. Never inside a transaction.
     */
    void runOnce() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("the device code job must run outside a database transaction");
        }
        // D141: no device code email scope may be open on the job's thread between sends.
        if (DeviceCodeEmailScope.isOpen()) {
            throw new IllegalStateException("device code email scope is open before a run");
        }
        var current = portal.get();
        if (current == null) {
            return;
        }
        var result = approvals.syncCodes(current, policy.get(), mail);
        if (result.challenged() + result.emailed() + result.matched() + result.wrong() + result.accepted()
                + result.refused() + result.ended() > 0) {
            LOGGER.info("myCarlos device codes: challenged={} emailed={} matched={} wrong={} accepted={} refused={} ended={}",
                    result.challenged(), result.emailed(), result.matched(), result.wrong(), result.accepted(),
                    result.refused(), result.ended());
        }
    }

    /** CARLOS email for the job: the chart address, the consent gate, and the send as the system provider. */
    static final class ChartMail implements PortalMyCarlosStaffApprovalService.CodeMail {
        private final EmailManager emails;
        private final EmailConfigDao emailConfigs;
        private final DemographicDao demographics;
        private final ClinicDAO clinics;
        private final ProviderDao providers;
        private final SecurityInfoManager security;
        private final Supplier<String> senderEmail;

        ChartMail(EmailManager emails, EmailConfigDao emailConfigs, DemographicDao demographics, ClinicDAO clinics,
                ProviderDao providers, SecurityInfoManager security, Supplier<String> senderEmail) {
            this.emails = Objects.requireNonNull(emails);
            this.emailConfigs = Objects.requireNonNull(emailConfigs);
            this.demographics = Objects.requireNonNull(demographics);
            this.clinics = Objects.requireNonNull(clinics);
            this.providers = Objects.requireNonNull(providers);
            this.security = Objects.requireNonNull(security);
            this.senderEmail = Objects.requireNonNull(senderEmail);
        }

        /** The built-in system provider, which the seed data gives email rights. */
        private LoggedInInfo system() {
            Provider provider = providers.getProvider(Provider.SYSTEM_PROVIDER_NO);
            if (provider == null) throw new IllegalStateException("the system provider is missing");
            LoggedInInfo login = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
            login.setLoggedInProvider(provider);
            return login;
        }

        private Optional<EmailConfig> sender() {
            String address = senderEmail.get();
            return address == null ? Optional.empty() : Optional.ofNullable(emailConfigs.findActiveEmailConfig(address));
        }

        private static String chartEmail(Demographic patient) {
            String email = patient == null || patient.getEmail() == null ? "" : patient.getEmail().strip();
            return EmailValidator.getInstance().isValid(email) ? email : null;
        }

        @Override
        public PortalMyCarlosStaffApprovalService.CodeReach reach(int patient) {
            if (sender().isEmpty()) {
                return PortalMyCarlosStaffApprovalService.CodeReach.staff(PortalMyCarlosStaffApprovalService.SENDER_INACTIVE);
            }
            // A send would be refused for a missing required clinic footer: no code is made that
            // can't go out (as ClinicEmailFooterService asks of senders that prepare something first).
            if (emails.clinicFooterMissing()) {
                return PortalMyCarlosStaffApprovalService.CodeReach.staff(PortalMyCarlosStaffApprovalService.FOOTER_MISSING);
            }
            // The archive checks the sender's access to the patient's record; a privacy-locked
            // record would refuse every send, so that request goes to staff now.
            if (!security.isAllowedAccessToPatientRecord(system(), patient)) {
                return PortalMyCarlosStaffApprovalService.CodeReach.staff(PortalMyCarlosStaffApprovalService.RECORD_RESTRICTED);
            }
            String address = chartEmail(demographics.getDemographicById(patient));
            if (address == null) {
                return PortalMyCarlosStaffApprovalService.CodeReach.staff(PortalMyCarlosStaffApprovalService.NO_USABLE_EMAIL);
            }
            EmailData probe = new EmailData();
            probe.setDemographicNo(patient);
            probe.setRecipients(new String[] {address});
            return emails.consentBlockMessage(system(), probe) == null
                    ? PortalMyCarlosStaffApprovalService.CodeReach.to(address)
                    : PortalMyCarlosStaffApprovalService.CodeReach.staff(PortalMyCarlosStaffApprovalService.NO_CONSENT);
        }

        @Override
        public boolean send(int patient, String address, String displayCode, long minutes) {
            try {
                var sender = sender().orElse(null);
                Demographic chart = demographics.getDemographicById(patient);
                if (sender == null || !address.equals(chartEmail(chart))) return false;
                Locale locale = PortalMyCarlosDeviceCodeEmailComposer.locale(chart.getSpokenLanguage(),
                        chart.getOfficialLanguage());
                Clinic clinic = clinics.getClinic();
                String clinicName = clinic == null || clinic.getClinicName() == null || clinic.getClinicName().isBlank()
                        ? "CARLOS" : clinic.getClinicName().strip();
                EmailData email = PortalMyCarlosDeviceCodeEmailComposer.request(patient, address, sender.getId(),
                        Provider.SYSTEM_PROVIDER_NO, PortalMyCarlosDeviceCodeEmailComposer.subject(locale),
                        PortalMyCarlosDeviceCodeEmailComposer.body(locale, clinicName, displayCode, minutes), displayCode);
                try {
                    EmailSendResult result;
                    // D141: the archive takes this one email as the system provider only inside
                    // this scope, opened on this thread around this send.
                    try (var scope = DeviceCodeEmailScope.open(patient)) {
                        result = emails.sendEmailWithResult(system(), email);
                    }
                    if (DeviceCodeEmailScope.isOpen()) {
                        throw new IllegalStateException("device code email scope is open after a send");
                    }
                    return result.isTransportAccepted();
                } finally {
                    email.setBody("");
                    email.setArchiveRedactions(java.util.List.of());
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("myCarlos device code email was not sent; exceptionClass={}", failure.getClass().getSimpleName());
                return false;
            }
        }
    }
}
