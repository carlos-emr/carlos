# Text Appointment Reminders

CARLOS can text a patient a reminder before an appointment (issue #884). Staff ask for it per appointment with the
**Text Reminder** box on Add and Edit Appointment. The reminder job that sends
[email reminders](../email/appointment-email-reminders.md) hands each text to the SMS queue, so every rule of the
[SMS backend](../architecture/sms-backend.md) applies: consent, the provider, the rate limit, retries and the SMS
Queue page.

The switch name, lead time, quiet hours, wording, clinic name setting and time limit are provisional and may change
before release.

## Turning it on

| Setting | Where | Meaning |
|---|---|---|
| `smsApptReminder` | `carlos.properties` | `yes`, `true` or `on` shows the Text Reminder box on Add/Edit Appointment and runs the text channel of the reminder job every 5 minutes. Off by default. It is separate from `emailApptReminder`: either, both or neither may be on. |
| `appointment_reminder.sms.hours_before` | `carlos.properties` | How many hours before the start the text goes. Default 24; kept between 3 and 168. A missing or invalid value uses 24, and the log says so. |
| `appointment_reminder.sms.clinic_name` | `carlos.properties` | The clinic's name as texts show it. Optional; without it, the clinic's own name is used. Either way it is cut at a word to 30 characters (see **What the patient receives**). |

Restart Tomcat after changing `smsApptReminder` or `hours_before`: the job and its window are set at startup. The
clinic name is read at every send.

SMS itself must be ready, or every text is refused and the reminder records why:
- **Administration > SMS:** a provider with its credentials and sender number, and sending switched on.
- **`sms.queue.scheduler.enabled=true`**, so the queue sends what the reminder hands it.
- **The SMS consent type is active**, and the patient has an explicit opt-in recorded on it (see the SMS backend's
  "Patient consent").

The `appointment_reminder` table and `sms_transaction.expires_at` come from the release's database migrations.

## Who gets a text, and when
- **The appointment:** it has a patient, the box was ticked, and it is not cancelled (`C`) or a no-show (`N`).
- **Consent:** the patient has an **explicit** opt-in to the clinic's SMS consent type. Opted out, implied or no
  consent: no text. The queue checks when the text is handed over and again just before it is sent, so an opt-out
  recorded while a text waits still stops it.
- **Number:** the patient's cell number only (`demo_cell`; OSCAR's preferred-number `*` is removed). It must be a
  valid North American number. Home and work numbers are never texted.
- **Access:** the patient is read as the system account (`-1`), as for email. A patient that account may not read (a
  restricted program or a patient directive) gets no text.
- **Timing:** the text is handed to the queue from `hours_before` before the start until 2 hours before. A booking
  made inside that window is reminded at the next run, as long as more than 2 hours remain.
- **Quiet hours:** no text is handed over between 21:00 and 08:00, server time. A reminder that falls in those hours
  goes at 08:00 if more than 2 hours remain before the start; otherwise no text goes.
- **Once per start:** each appointment start gets one text. Moving the appointment by changing its date or time makes
  it due again for the new start. Unticking the box stops a text not yet handed over.
- **A time limit:** each text must be sent by 2 hours before the start. A text still waiting then (sending switched
  off, an outage, the rate limit) is failed as `QUEUE_EXPIRED`, never sent late. A text already handed over is not
  withdrawn when the appointment is cancelled or moved; the time limit still applies to it.
- **The other buttons on the form keep it,** as for email: Repeat Booking, a repeat-series Group Update, Group Appt
  (one booking gets the text) and Group Action then Group Update. Cut and Paste does not copy the choice.
- **Retries:** the SMS queue retries a text itself, so the reminder hands it over only once. A refusal (consent,
  number, setup) is never retried.
- **A restart while handing over:** if the server stops between marking a reminder and recording what the queue said,
  that reminder shows as unconfirmed after 15 minutes and is never handed over again.

## What the patient receives
One SMS (160 characters), in French when the patient's reminder language is French (spoken language where CARLOS has
a translation, otherwise official language), otherwise in English:

> Reminder: appointment at {clinic} on {Tue Nov 18} at {12:30 PM}. To change or cancel, call {phone}. Reply STOP
> to stop texts.

> Rappel : RDV à {clinic} le {mer. 18/11} à {12 h 30}. Modifier ou annuler : {phone}. Répondez STOP pour ne plus
> recevoir de textos.

- **Never included:** the provider, the reason, notes or any clinical detail.
- **Clinic with no phone on file:** "contact the clinic" / "joignez la clinique" in place of the number.
- **Other languages get English.** Their accents need the 70-character SMS encoding, which can't hold the details.
- **The clinic's name:** letters the SMS alphabet lacks lose their accents, other such characters are dropped, and
  the name is cut at a word to 30 characters.
- **A text that would not fit one SMS** (an unusual clinic name or phone) is recorded as failed. It is never cut or
  split.

## Where staff see the outcome
- **Edit Appointment:** a line under the Text Reminder box says, for the appointment's current start, when the text
  will go, that it was handed to the SMS queue or sent (and when), that it was not sent in time, that it failed
  ("See SMS Queue"), or why it was not sent:
  - no consent, or opted out;
  - no valid cell number;
  - SMS not fully set up;
  - no access;
  - too close to the appointment.

  The line follows the text through the queue. It is shown to staff who may open the patient's record and read their
  texts (`_sms` read), as the patient's SMS History asks.
- **Administration > SMS Queue** and the patient's **SMS History:** the text is an ordinary SMS row with purpose
  `APPOINTMENT_REMINDER`, the appointment number, and the staff member who ticked the box as requester. Failures and
  uncertain sends are handled there like any other text.
- **Audit log:**
  - ticking and unticking the box: `AppointmentReminder.requested` / `.withdrawn` with `channel=SMS`, under the staff
    user;
  - each handoff: `AppointmentReminder.SMS.<outcome>`, under the system account (`-1`), with the reminder id and the
    SMS row id;
  - the number and the message text are never logged.

## Replies
Texts are one-way: CARLOS does not read replies. Patients change or cancel by phone, as the text says.

The text says "Reply STOP to stop texts". CARLOS does not yet act on a STOP reply; that part is still to be agreed.
Until it is, if a provider or carrier blocks texts to a patient after a STOP, the text fails and shows in the SMS
Queue and on the status line. Record the opt-out on the patient's SMS consent.

## Several servers
Reminders are claimed with `SELECT … FOR UPDATE SKIP LOCKED`, as for email, and the SMS queue claims its own rows
the same way, so every server may run the job without sending a text twice.
