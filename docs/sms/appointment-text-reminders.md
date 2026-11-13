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

None of these keys is in the shipped `carlos.properties`; add the ones you need. Restart Tomcat after changing
`smsApptReminder` or `hours_before`: the job and its window are set at startup. The clinic name is read at every send.

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
- **Quiet hours:** no text goes out between 21:00 and 08:00, server time, and none is handed over in the 10
  minutes before 21:00, where it would barely have time to go. A reminder that falls in those hours is handed over
  at 08:00 if more than 2 hours remain before the start; otherwise no text goes.
- **One text per appointment start:** moving an appointment by changing its date or time sends a reminder for the
  new time, so the patient's latest text always has the right time (the owner's decision). Unticking the box stops
  a text not yet handed over.
- **A time limit:** each text must be sent by 2 hours before the start, or by 21:00 on the day it is handed over if
  that comes first. A text still waiting then (a retry, the rate limit, an outage, sending switched off) is failed as
  `QUEUE_EXPIRED`: it is never sent late, and never at night. Nor is it handed over again the next morning, even when
  time remains before the appointment: the patient gets no text for that start. A text already handed over is not
  withdrawn when the appointment is cancelled or moved; the time limit still applies to it.
- **The other buttons on the form keep it,** as for email: Repeat Booking, a repeat-series Group Update, Group Appt
  (one booking gets the text) and Group Action then Group Update. Cut and Paste does not copy the choice. As for
  email, the patient gets one text per visit: a box ticked later on another booking at the same time is not texted
  again, and its line says so. A booking covered this way stays covered if the covering booking's text later fails,
  expires or is refused in the SMS queue. The line records the covering booking's handover, not confirmed delivery:
  check that booking's SMS Queue outcome. A covered booking is not retried automatically.
- **Retries:** the SMS queue retries a text itself, so a text it took is never handed over again. A text that never
  reached the queue (a refusal, or an error before anything was stored) is tried once more 30 minutes later, while
  there is time. A refusal for consent, number or setup is never retried, and neither is a text too long for one SMS.
- **A restart while handing over:** if the server stops between marking a reminder and recording what the queue said,
  that reminder shows as unconfirmed after 15 minutes and is never handed over again.

## What the patient receives
One SMS (160 characters), in French when the patient's reminder language is French (spoken language where CARLOS has
a translation, otherwise official language), otherwise in English:

> Reminder: appointment at {clinic} on {Tue Nov 18} at {12:30 PM}. To change or cancel, call {phone}.

> Rappel : RDV à {clinic} le {mer. 18/11} à {12 h 30}. Modifier ou annuler : {phone}.

Once CARLOS handles STOP replies, the text will end with "Reply STOP to stop texts." / "Répondez STOP pour ne plus
recevoir de textos." (see **Replies**). It fits one SMS with that sentence too.

- **Never included:** the provider, the reason, notes or any clinical detail.
- **Clinic with no phone on file:** "contact the clinic" / "joignez la clinique" in place of the number.
- **Other languages get English.** Their accents need the 70-character SMS encoding, which can't hold the details.
- **The clinic's name:** letters the SMS alphabet lacks lose their accents, other such characters are dropped, and
  the name is cut at a word to 30 characters.
- **A text that would not fit one SMS** (an unusual clinic name or phone) is not sent, and the line says so. It is
  never cut, split or retried.

## Where staff see the outcome
- **Edit Appointment:** a line under the Text Reminder box says, for the appointment's current start:
  - when the text will go (08:00 when the box is ticked at night), or that it goes within a few minutes;
  - that it was handed to the SMS queue or sent (and when);
  - that the queue took it but could not send it ("See SMS Queue"), or could not send it in time;
  - that it never reached the queue, so nothing was sent, and whether it is tried once more;
  - or why it was not sent: no consent or opted out; no valid cell number; SMS not fully set up; no access; the
    clinic's name or phone made it longer than one SMS; too close to the appointment; the time left falls in the
    quiet hours; or the patient's text for this time comes from another booking.

  The line follows the text through the queue. Anyone who may open this patient's appointment sees it, front desk
  included: it needs the Edit Appointment page's own right (`_appointment` write) for this patient, with the
  patient's record locks, and no right on their texts (`_sms`). It holds only a time or one of the reasons above,
  never the number, the text or an error.
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

CARLOS does not yet act on a STOP reply; that part is still to be agreed. Until it is, the text does not invite one.
If a patient replies STOP anyway and their provider or carrier then blocks texts to them, the next text fails and
shows in the SMS Queue and on the status line. Record the opt-out on the patient's SMS consent.

## Several servers
Reminders are claimed with `SELECT … FOR UPDATE SKIP LOCKED`, as for email, and the SMS queue claims its own rows
the same way, so every server may run the job without sending a text twice.
