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
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentReminder;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.service.SmsQueueService;
import io.github.carlos_emr.carlos.sms.service.SmsSendService;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import io.github.carlos_emr.carlos.sms.support.SmsSegments;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Texts an appointment reminder to the patient's cell phone (#884), the second channel of the reminder system
 * #5887 built for email.
 *
 * <p>The reminder job claims a due reminder and marks it as being sent for its start, exactly as for email. This
 * channel then hands one text to the SMS queue, with purpose {@code APPOINTMENT_REMINDER}, the appointment's
 * number, and a time limit ({@link ReminderWindow#sendBy}): the start minus the cutoff, or the next start of the
 * quiet hours when that comes first. The queue sends, retries and recovers it, and checks the patient's SMS consent
 * again before sending; a text it could not send in time is failed, never sent late or at night. So a text the
 * queue took is never handed over again; only one that never reached the queue is tried once more
 * ({@link #maxAttempts}). The status line reads where the text got to ({@link #currentStatus}).</p>
 *
 * <p>The text carries the clinic's short name, the date and time and the clinic's phone (to change or cancel), and
 * nothing else: no provider, reason, notes or clinical detail. "Reply STOP to stop texts" is added only once STOP
 * replies are handled ({@link #STOP_REPLIES_HANDLED}). It must fit one GSM-7 segment
 * (160 characters), so it is written in English or French only, and the clinic's name is at most
 * {@value #CLINIC_NAME_MAX} characters. Wording, clinic name, quiet hours and the switch are provisional, as
 * recommended to the owner.</p>
 *
 * @since 2026-11-12
 */
@Service
public class SmsReminderChannel implements AppointmentReminderChannel {

    /** The clinic switch: shows the Text Reminder box and runs this channel (provisional, #884). */
    public static final String ENABLED_PROPERTY = "smsApptReminder";
    /** The clinic's name as texts show it, up to {@value #CLINIC_NAME_MAX} GSM-7 characters (provisional, #884). */
    public static final String CLINIC_NAME_PROPERTY = "appointment_reminder.sms.clinic_name";
    /** What a text leaves for the clinic's name, measured against the longest wording and date (#884 plan). */
    static final int CLINIC_NAME_MAX = 30;
    static final String BODY_KEY = "appointment.reminder.sms.body";
    static final String BODY_WITHOUT_PHONE_KEY = "appointment.reminder.sms.bodyWithoutPhone";
    static final String STOP_KEY = "appointment.reminder.sms.stop";
    /**
     * Whether CARLOS acts on a STOP reply. Not yet (#884: the STOP opt-out waits on the maintainers' agreement), so
     * the texts do not invite one; the change that handles STOP turns this on.
     */
    static final boolean STOP_REPLIES_HANDLED = false;
    /** The demographicExt key of the patient's cell phone number. */
    static final String CELL_PHONE_KEY = "demo_cell";

    private final SmsQueueService queue;
    private final SmsTransactionDao texts;
    private final DemographicManager demographics;
    private final DemographicExtDao extras;
    private final ClinicDAO clinics;
    private final ProviderDao providers;
    private final SecurityInfoManager security;
    private final BooleanSupplier switchedOn;
    private final Supplier<String> clinicNameSetting;
    private final ReminderWindow window;
    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public SmsReminderChannel(SmsQueueService queue, SmsTransactionDao texts, DemographicManager demographics,
                              DemographicExtDao extras, ClinicDAO clinics, ProviderDao providers,
                              SecurityInfoManager security) {
        this(queue, texts, demographics, extras, clinics, providers, security,
                () -> CarlosProperties.getInstance().isPropertyActive(ENABLED_PROPERTY),
                () -> CarlosProperties.getInstance().getProperty(CLINIC_NAME_PROPERTY, null),
                ReminderWindow.forSmsHoursBefore(
                        CarlosProperties.getInstance().getProperty(ReminderWindow.SMS_HOURS_BEFORE_PROPERTY, null)),
                Clock.systemDefaultZone());
    }

    SmsReminderChannel(SmsQueueService queue, SmsTransactionDao texts, DemographicManager demographics,
                       DemographicExtDao extras, ClinicDAO clinics, ProviderDao providers, SecurityInfoManager security,
                       BooleanSupplier switchedOn, Supplier<String> clinicNameSetting, ReminderWindow window,
                       Clock clock) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.demographics = Objects.requireNonNull(demographics, "demographics");
        this.extras = Objects.requireNonNull(extras, "extras");
        this.clinics = Objects.requireNonNull(clinics, "clinics");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.security = Objects.requireNonNull(security, "security");
        this.switchedOn = Objects.requireNonNull(switchedOn, "switchedOn");
        this.clinicNameSetting = Objects.requireNonNull(clinicNameSetting, "clinicNameSetting");
        this.window = Objects.requireNonNull(window, "window");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = clock.getZone();
    }

    @Override
    public ReminderChannel channel() {
        return ReminderChannel.SMS;
    }

    @Override
    public boolean enabled() {
        return switchedOn.getAsBoolean();
    }

    @Override
    public Optional<ReminderWindow> window() {
        return Optional.of(window);
    }

    /**
     * Two: a text that never reached the SMS queue (a refusal, or an error before anything was stored) is tried
     * once more, as an email is. A text the queue took is never handed over again (its status is not
     * {@link AppointmentReminderStatus#FAILED}), since the queue retries it itself and a second handover could send
     * it twice.
     */
    @Override
    public int maxAttempts() {
        return 2;
    }

    @Override
    public ReminderOutcome send(ClaimedReminder reminder) {
        SmsSendCommand command;
        try {
            LoggedInInfo system = EmailReminderChannel.systemUser(providers);
            if (system == null) {
                return ReminderOutcome.of(AppointmentReminderStatus.NOT_PERMITTED);
            }
            // getDemographic asks the same question but refuses with a plain RuntimeException, which would be
            // recorded as a failure and retried. Asked here, a refusal (a patient's record lock, or a clinic that
            // removed the right) is recorded as no access.
            if (!security.hasPrivilege(system, "_demographic", SecurityInfoManager.READ, reminder.demographicNo())) {
                return ReminderOutcome.because(AppointmentReminderStatus.NOT_PERMITTED, "noPatientAccess");
            }
            Demographic patient = demographics.getDemographic(system, reminder.demographicNo());
            if (patient == null) {
                return ReminderOutcome.of(AppointmentReminderStatus.NO_ADDRESS);
            }
            String cell = cellNumber(extras.getValueForDemoKey(reminder.demographicNo(), CELL_PHONE_KEY));
            if (cell.isEmpty()) {
                return ReminderOutcome.of(AppointmentReminderStatus.NO_ADDRESS);
            }
            if (SmsPhoneNumbers.normalizeToE164(cell).isEmpty()) {
                return ReminderOutcome.of(AppointmentReminderStatus.INVALID_ADDRESS);
            }
            String body = text(patient, reminder.start());
            if (SmsSegments.count(body).segments() > 1) {
                // A clinic name or phone the wording did not plan for: never cut, never split, never retried.
                return ReminderOutcome.because(AppointmentReminderStatus.TOO_LONG, "tooLong");
            }
            command = SmsSendCommand.appointmentReminder(reminder.demographicNo(), cell, body,
                    reminder.requestedBy(), reminder.appointmentNo(),
                    window.sendBy(reminder.start(), LocalDateTime.now(clock)).atZone(zone).toInstant());
        } catch (SecurityException | PatientDirectiveException e) {
            // The system provider may not read this patient (a restricted program or a patient's directive).
            return ReminderOutcome.because(AppointmentReminderStatus.NOT_PERMITTED, e);
        } catch (RuntimeException e) {
            // A database or lookup error before anything reached the queue: nothing was sent. A failure, so its one
            // retry runs; never unconfirmed, which would say it may have gone and cover other bookings.
            return ReminderOutcome.because(AppointmentReminderStatus.FAILED, e);
        }
        try {
            return outcome(queue.enqueue(command));
        } catch (RuntimeException e) {
            // Admission throws only before anything is recorded (a consent check that failed): nothing was queued.
            return ReminderOutcome.because(AppointmentReminderStatus.FAILED, e);
        }
    }

    /**
     * Where the text got to: the queue sends it after this channel hands it over, so a reminder recorded as
     * queued reads its newest text (a provider swap moves a waiting text to a new row). The text is found by the
     * appointment number on the text the reminder handed over, so a reminder Group Update moved to a new number still
     * finds the text it handed over under the old one.
     */
    @Override
    public AppointmentReminderStatus currentStatus(AppointmentReminder reminder) {
        if (reminder.getLastStatus() != AppointmentReminderStatus.QUEUED || reminder.getAppointmentNo() == null) {
            return reminder.getLastStatus();
        }
        int appointmentNo = reminder.getAppointmentNo();
        if (reminder.getLastSmsTransactionId() != null) {
            SmsTransaction handedOver = texts.find(reminder.getLastSmsTransactionId());
            if (handedOver != null && handedOver.getAppointmentNo() != null) {
                appointmentNo = handedOver.getAppointmentNo();
            }
        }
        Optional<SmsTransaction> text = texts.findLatestOutboundByAppointment(appointmentNo,
                SmsMessagePurpose.APPOINTMENT_REMINDER);
        if (text.isEmpty()) {
            return AppointmentReminderStatus.QUEUED;
        }
        return switch (text.get().getStatus()) {
            case SENT, DELIVERED -> AppointmentReminderStatus.SENT;
            case CONSENT_BLOCKED, OPTOUT_BLOCKED -> AppointmentReminderStatus.BLOCKED_CONSENT;
            case FAILED -> "QUEUE_EXPIRED".equals(text.get().getErrorCode())
                    ? AppointmentReminderStatus.EXPIRED : AppointmentReminderStatus.FAILED;
            default -> AppointmentReminderStatus.QUEUED;
        };
    }

    /** The cell number as stored, without OSCAR's trailing "preferred" {@code *} (the normalizer refuses it). */
    static String cellNumber(String stored) {
        if (stored == null) {
            return "";
        }
        String number = stored.trim();
        while (number.endsWith("*")) {
            number = number.substring(0, number.length() - 1).trim();
        }
        return number;
    }

    private String text(Demographic patient, LocalDateTime start) {
        Clinic clinic = clinics.getClinic();
        String phone = clinic == null || clinic.getClinicPhone() == null ? "" : clinic.getClinicPhone().trim();
        String clinicName = clinicName(clinicNameSetting.get(), clinic == null ? null : clinic.getClinicName());
        Locale locale = Locale.FRENCH.equals(ReminderLanguage.of(patient.getOfficialLanguage(), patient.getSpokenLanguage()))
                ? Locale.FRENCH : Locale.ENGLISH;
        return text(locale, clinicName, phone, start, STOP_REPLIES_HANDLED);
    }

    /**
     * The text in English or French, from the bundle. Only these four values ever reach it. Other languages would
     * need UCS-2, whose 70 characters cannot carry the details, so they get English.
     */
    static String text(Locale locale, String clinicName, String phone, LocalDateTime start, boolean stopLine) {
        boolean french = Locale.FRENCH.getLanguage().equals(locale.getLanguage());
        Locale shown = french ? Locale.FRENCH : Locale.ENGLISH;
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", shown);
        // Numeric French dates: some French month names ("août") are not GSM-7.
        String date = DateTimeFormatter.ofPattern(french ? "EEE dd/MM" : "EEE MMM d", shown).format(start);
        String time = DateTimeFormatter.ofPattern(french ? "H 'h' mm" : "h:mm a", shown).format(start);
        String body = phone.isEmpty()
                ? new MessageFormat(bundle.getString(BODY_WITHOUT_PHONE_KEY), shown).format(new Object[]{clinicName, date, time})
                : new MessageFormat(bundle.getString(BODY_KEY), shown).format(new Object[]{clinicName, date, time, phone});
        return stopLine ? body + " " + bundle.getString(STOP_KEY) : body;
    }

    /**
     * The clinic's name as a text shows it: the setting, else the clinic's name. Either way, letters outside GSM-7
     * lose their accents, anything else outside it is dropped, and it is cut at a word to
     * {@value #CLINIC_NAME_MAX} characters, so the text stays one segment.
     */
    static String clinicName(String setting, String clinicName) {
        String name = setting != null && !setting.isBlank() ? setting : clinicName == null ? "" : clinicName;
        StringBuilder folded = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            String one = String.valueOf(c);
            if (SmsSegments.count(one).encoding() == SmsSegments.Encoding.GSM_7) {
                folded.append(c);
            } else {
                String plain = Normalizer.normalize(one, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                if (!plain.isEmpty() && SmsSegments.count(plain).encoding() == SmsSegments.Encoding.GSM_7) {
                    folded.append(plain);
                }
            }
        }
        String result = folded.toString().replaceAll("\\s+", " ").trim();
        if (result.length() <= CLINIC_NAME_MAX) {
            return result;
        }
        int cut = result.lastIndexOf(' ', CLINIC_NAME_MAX);
        return (cut > 0 ? result.substring(0, cut) : result.substring(0, CLINIC_NAME_MAX)).trim();
    }

    private static ReminderOutcome outcome(SmsSendResultDto result) {
        if (result == null) {
            return ReminderOutcome.of(AppointmentReminderStatus.UNCONFIRMED);
        }
        Long textId = result.transactionId();
        if (result.status() == SmsStatus.CONSENT_BLOCKED || result.status() == SmsStatus.OPTOUT_BLOCKED) {
            return ReminderOutcome.sms(AppointmentReminderStatus.BLOCKED_CONSENT, textId, result.status().name());
        }
        if (result.accepted() && result.status() == SmsStatus.QUEUED) {
            return ReminderOutcome.sms(AppointmentReminderStatus.QUEUED, textId, null);
        }
        if (result.messages().contains(SmsSendService.SMS_TURNED_OFF_MESSAGE)
                || result.messages().contains(SmsSendService.SMS_PROVIDER_NOT_READY_MESSAGE)) {
            return ReminderOutcome.sms(AppointmentReminderStatus.NO_SENDER, null, "smsNotSetUp");
        }
        if (result.messages().contains(SmsSendService.SMS_EXPIRED_MESSAGE)) {
            return ReminderOutcome.sms(AppointmentReminderStatus.EXPIRED, null, null);
        }
        // Refused before anything was recorded (the validator's fixed reasons): nothing was queued.
        return ReminderOutcome.sms(AppointmentReminderStatus.FAILED, textId, "refused");
    }
}
