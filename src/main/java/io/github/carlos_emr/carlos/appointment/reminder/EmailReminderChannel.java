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
package io.github.carlos_emr.carlos.appointment.reminder;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDao;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailFieldLengthException;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * Emails an appointment reminder to the patient (#5887), through {@link EmailManager} like every patient email,
 * as the system provider {@code -1} (#5887). Each send is therefore archived to the chart, checked against
 * the patient's email consent (explicit opt-in only), logged in Manage Emails as an
 * {@link EmailLog.TransactionType#APPOINTMENT_REMINDER}, and written without a chart note.
 *
 * <p>The message has fixed wording (#5887): the clinic's name, the date and time, and the clinic's phone number,
 * in the patient's language. It never carries the provider, the reason, notes or any clinical detail: the
 * channel does not read them. A refusal (no sender, no footer, no address, no consent, no access) is
 * returned without sending and without an email log row.
 *
 * <p>What this class returns is what it knows: a refusal or a failure before {@link EmailManager} could hand
 * the email to the mail server is recorded as not sent, with the exception's class as the reason. Only an
 * exception {@code EmailManager} raises once it may have started the send is left to the service, which
 * records it as unconfirmed and never sends that start again.
 *
 * @since 2026-11-12
 */
@Service
public class EmailReminderChannel implements AppointmentReminderChannel {

    /** The active sender address reminders go from (#5887). */
    static final String SENDER_PROPERTY = "appointment_reminder.email.sender";

    static final String SUBJECT_KEY = "appointment.reminder.email.subject";
    static final String BODY_KEY = "appointment.reminder.email.body";
    /** For a clinic with no phone number on file: "contact the clinic" in place of "call {phone}". */
    static final String BODY_WITHOUT_PHONE_KEY = "appointment.reminder.email.bodyWithoutPhone";

    private final EmailManager emails;
    private final EmailComposeManager compose;
    private final EmailConfigDao senders;
    private final DemographicManager demographics;
    private final ClinicDAO clinics;
    private final ProviderDao providers;
    private final SecurityInfoManager security;
    private final Supplier<String> senderAddress;

    @Autowired
    public EmailReminderChannel(EmailManager emails, EmailComposeManager compose, EmailConfigDao senders,
                                DemographicManager demographics, ClinicDAO clinics, ProviderDao providers,
                                SecurityInfoManager security) {
        this(emails, compose, senders, demographics, clinics, providers, security,
                () -> CarlosProperties.getInstance().getProperty(SENDER_PROPERTY, null));
    }

    EmailReminderChannel(EmailManager emails, EmailComposeManager compose, EmailConfigDao senders,
                         DemographicManager demographics, ClinicDAO clinics, ProviderDao providers,
                         SecurityInfoManager security, Supplier<String> senderAddress) {
        this.emails = Objects.requireNonNull(emails, "emails");
        this.compose = Objects.requireNonNull(compose, "compose");
        this.senders = Objects.requireNonNull(senders, "senders");
        this.demographics = Objects.requireNonNull(demographics, "demographics");
        this.clinics = Objects.requireNonNull(clinics, "clinics");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.security = Objects.requireNonNull(security, "security");
        this.senderAddress = Objects.requireNonNull(senderAddress, "senderAddress");
    }

    @Override
    public ReminderChannel channel() {
        return ReminderChannel.EMAIL;
    }

    /** Email reminders run while the clinic has them on, the switch that also shows the box (#884: per channel). */
    @Override
    public boolean enabled() {
        return CarlosProperties.getInstance().isPropertyActive(AppointmentReminderScheduler.ENABLED_PROPERTY);
    }

    @Override
    public ReminderOutcome send(ClaimedReminder reminder) {
        LoggedInInfo system;
        EmailData email;
        try {
            system = systemUser();
            // EmailManager's own first check, made here so that a system account without it is a refusal.
            if (system == null || !security.hasPrivilege(system, "_email", SecurityInfoManager.WRITE, null)) {
                return ReminderOutcome.of(AppointmentReminderStatus.NOT_PERMITTED);
            }
            EmailConfig sender = activeSender();
            if (sender == null) {
                return ReminderOutcome.of(AppointmentReminderStatus.NO_SENDER);
            }
            if (emails.clinicFooterMissing()) {
                return ReminderOutcome.of(AppointmentReminderStatus.NO_FOOTER);
            }
            Demographic patient = demographics.getDemographic(system, reminder.demographicNo());
            if (patient == null) {
                return ReminderOutcome.of(AppointmentReminderStatus.NO_ADDRESS);
            }
            List<?>[] recipients = compose.getRecipients(system, reminder.demographicNo());
            if (recipients[0].isEmpty()) {
                return ReminderOutcome.of(recipients[1].isEmpty()
                        ? AppointmentReminderStatus.NO_ADDRESS : AppointmentReminderStatus.INVALID_ADDRESS);
            }
            email = message(reminder, patient, sender, (String) recipients[0].get(0));
            if (emails.consentBlockMessage(system, email) != null) {
                return ReminderOutcome.of(AppointmentReminderStatus.BLOCKED_CONSENT);
            }
        } catch (SecurityException | PatientDirectiveException e) {
            // The system provider may not read this patient (a restricted program, a patient's directive, or a
            // clinic that removed its rights). Nothing was sent.
            return ReminderOutcome.because(AppointmentReminderStatus.NOT_PERMITTED, e);
        } catch (RuntimeException e) {
            // Before the email reached EmailManager's send: nothing was sent, so it is a definite failure.
            return ReminderOutcome.because(AppointmentReminderStatus.FAILED, e);
        }
        try {
            return outcome(emails.sendEmailWithResult(system, email));
        } catch (SecurityException | PatientDirectiveException e) {
            // EmailManager's checks of the sender and the patient, before its outbox row and the transport.
            return ReminderOutcome.because(AppointmentReminderStatus.NOT_PERMITTED, e);
        } catch (EmailFieldLengthException e) {
            // Thrown by EmailManager's field check, before its outbox row and the transport.
            return ReminderOutcome.because(AppointmentReminderStatus.FAILED, e);
        }
    }

    /** The system provider, as the BC alert timer builds it; {@code null} if the clinic has no such row. */
    private LoggedInInfo systemUser() {
        return systemUser(providers);
    }

    /** The system provider any reminder channel reads the patient as (#884 shares it). */
    static LoggedInInfo systemUser(ProviderDao providers) {
        Provider provider = providers.getProvider(Provider.SYSTEM_PROVIDER_NO);
        if (provider == null) {
            return null;
        }
        Security security = new Security();
        security.setSecurityNo(0);
        LoggedInInfo system = new LoggedInInfo();
        system.setLoggedInSecurity(security);
        system.setLoggedInProvider(provider);
        return system;
    }

    private EmailConfig activeSender() {
        String address = senderAddress.get();
        return address == null || address.isBlank() ? null : senders.findActiveEmailConfig(address.trim());
    }

    private EmailData message(ClaimedReminder reminder, Demographic patient, EmailConfig sender, String address) {
        Locale locale = ReminderLanguage.of(patient.getOfficialLanguage(), patient.getSpokenLanguage());
        Clinic clinic = clinics.getClinic();
        String clinicName = clinic == null || clinic.getClinicName() == null ? "" : clinic.getClinicName().trim();
        String phone = clinic == null || clinic.getClinicPhone() == null ? "" : clinic.getClinicPhone().trim();
        String[] text = text(locale, clinicName, phone, reminder.start());

        EmailData email = new EmailData();
        email.setSenderConfigId(sender.getId());
        email.setRecipients(new String[]{address});
        email.setSubject(text[0]);
        email.setBody(text[1]);
        email.setIsEncrypted(false);
        email.setChartDisplayOption(EmailLog.ChartDisplayOption.WITHOUT_NOTE);
        email.setTransactionType(EmailLog.TransactionType.APPOINTMENT_REMINDER);
        email.setDemographicNo(reminder.demographicNo());
        email.setProviderNo(Provider.SYSTEM_PROVIDER_NO);
        return email;
    }

    /** The subject and body in {@code locale}, from the bundle. Only these four values ever reach the text. */
    static String[] text(Locale locale, String clinicName, String phone, LocalDateTime start) {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale);
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(start);
        String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(start);
        String subject = new MessageFormat(bundle.getString(SUBJECT_KEY), locale).format(new Object[]{clinicName});
        String body = phone.isEmpty()
                ? new MessageFormat(bundle.getString(BODY_WITHOUT_PHONE_KEY), locale).format(new Object[]{clinicName, date, time})
                : new MessageFormat(bundle.getString(BODY_KEY), locale).format(new Object[]{clinicName, date, time, phone});
        return new String[]{subject, body};
    }

    private static ReminderOutcome outcome(EmailSendResult result) {
        EmailLog log = result == null ? null : result.getEmailLog();
        Integer logId = log == null ? null : log.getId();
        if (result == null) {
            return ReminderOutcome.of(AppointmentReminderStatus.UNCONFIRMED);
        }
        if (result.isTransportAccepted()) {
            return new ReminderOutcome(AppointmentReminderStatus.SENT, logId);
        }
        if (result.isDeliveryUnconfirmed()) {
            return new ReminderOutcome(AppointmentReminderStatus.UNCONFIRMED, logId);
        }
        if (log != null && log.getStatus() == EmailLog.EmailStatus.BLOCKED) {
            return new ReminderOutcome(AppointmentReminderStatus.BLOCKED_CONSENT, logId);
        }
        return new ReminderOutcome(AppointmentReminderStatus.FAILED, logId);
    }
}
