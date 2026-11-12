# Email Appointment Reminders

CARLOS can email a patient a reminder before an appointment (issue #5887). Staff ask for it per appointment with
the **Email Reminder** box on Add and Edit Appointment. A background job sends it through the normal patient email
path (`EmailManager`), so every email rule that applies to staff-sent patient email applies here too.

## Turning it on

| Setting | Where | Meaning |
|---|---|---|
| `emailApptReminder` | `carlos.properties` | `yes`, `true` or `on` shows the box on Add/Edit Appointment and sends email reminders. Off by default. The reminder job runs every 5 minutes while this or the text reminder switch (`smsApptReminder`, see [Text Appointment Reminders](../sms/appointment-text-reminders.md)) is on. |
| `appointment_reminder.email.sender` | `carlos.properties` | The sender address reminders go from. It must be an **active** sender in Administration > Email configuration. Without it, nothing is sent and each reminder records "not set up". |
| `appointment_reminder.email.hours_before` | `carlos.properties` | How many hours before the start the reminder goes. Default 24; kept between 3 and 168. A missing or invalid value uses 24, and the log says so. |

`emailApptReminder` and `hours_before` are read at startup, so restart Tomcat after changing them: the job starts
at startup when either reminder switch is on. The sender address is read at every send.

The clinic email footer must be configured, as for every patient email. The `appointment_reminder` table and the
`_edoc` write right for the system account come from the release's database migration.

## Who gets a reminder, and when
- **The appointment:** it has a patient, the box was ticked, and it is not cancelled (`C`) or a no-show (`N`).
- **Consent:** the patient has given **explicit** consent to email under the clinic's electronic-communication
  consent type. Implied or unknown consent is not enough, and there is no override for the job.
- **Address:** the patient has a valid email address on their demographic record. The first valid address is used.
- **Timing:** the reminder goes from `hours_before` before the start until 2 hours before. A booking made inside
  that window is reminded at the next run, as long as more than 2 hours remain.
- **Once per start:** each appointment start is reminded once. Moving the appointment by changing its date or time
  (Edit Appointment, dragging it on the schedule, REST, HL7, or a change to a whole repeat series) makes it due
  again for the new start. Unticking the box stops it. A reminder already sent cannot be recalled.
- **The other buttons on the form keep it:**
  - **Repeat Booking** books every repeat with the box as it was on the form, and a repeat-series **Group Update**
    applies it to every appointment in the series.
  - **Group Appt** books the patient with several providers at the same time, so the box goes on one booking only:
    the one for the provider the form was opened for, or else the first booking with the patient. The patient
    gets one reminder. If the box is later ticked on another of those bookings, the patient still gets one: a
    reminder whose patient already has a reminder for that visit is not sent, and its line says so. A booking whose
    reminder went for that time keeps covering the visit even if staff untick its box, cancel it or move it after,
    for the bookings already at that time when it went; a booking moved or booked into that time later gets its own
    reminder. Since when a booking has been at its time comes from its archived versions: every path that changes an
    appointment's date or time archives the version it replaces, except the old add-record card page
    (`appointmentaddrecordcard.jsp`), which nothing posts to with an existing appointment. Without a creation time,
    a booking counts as already there.
    While another booking's reminder for that time is being sent, it waits for the next run; if the check itself
    fails, nothing is sent and it is tried once more 30 minutes later.
  - **Group Action, then Group Update**, books each appointment again under a new number. Its reminder, and the
    start already reminded for, go with it, so the same start is not reminded twice. The Edit form's box applies to
    the appointment Edit was opened for.
- **Cut and Paste does not keep it:** the pasted appointment is new, and the choice is not copied, so tick the box
  on it if it should be reminded.
- **Retry:** one retry, 30 minutes after a definite failure (the mail server refused it, or it failed before
  being handed over). A refusal (consent, address, setup) or an unconfirmed send is never retried, so a patient is
  not emailed twice.
- **A restart while sending:** the job marks each reminder just before sending it. If the server stops between that
  mark and the outcome, that one reminder shows "sent, but the mail server did not confirm it" after 15 minutes,
  and is never sent again. Reminders the job had not reached go at a later run.

## What the patient receives
The subject is "Appointment reminder: {clinic}". The body:

> This is a reminder of your appointment at {clinic} on {date} at {time}. If you need to change or cancel it,
> please call {phone}. Please do not reply to this email.

- **Language:** the patient's spoken language where CARLOS has a translation (English, French, Spanish, Polish,
  Brazilian Portuguese), otherwise their official language, otherwise English.
- **Never included:** the provider, the reason, notes or any clinical detail.
- **Clinic with no phone on file:** "please contact the clinic" in place of "please call {phone}".

## Known limits of one reminder per visit
These need several bookings for the same patient at the same time, and a change in a particular order:
- **After Group Action, then Group Update.** It books each appointment again under a new number, so a rebooked
  booking's history starts at the rebook. If another booking's reminder already went for that time and that booking
  then left it (unticked, cancelled or moved), the rebooked booking counts as arrived after it, and may get a second
  reminder.
- **A covered booking stays covered for that time.** A booking is covered, and recorded as `DUPLICATE`, when another
  booking's reminder already went for that time, or when another booking still at that time has an earlier reminder
  (its box was first ticked before this one's) that has not already ended without one going. `DUPLICATE` is final for
  that time, so the patient gets no reminder for the covered booking if the other booking then moves away (it may
  already have been reminded for the old time, and is reminded again for its new one), is unticked or cancelled
  before its reminder goes, or fails its retry. There is no manual re-send: unticking and ticking the covered
  booking's box again does not send it, and its line still says the reminder comes from another booking.
- **A deleted booking stops covering.** Once a booking is deleted (Cut, Delete or Group Delete), its reminder no
  longer counts for the visit: another booking at the same time whose box is ticked later sends a second reminder
  for that time.
- **A booking reminded again for a new time.** A reminder keeps only the last time it went for. If a booking was
  reminded for 14:00, then moved to 15:00 and reminded again, it no longer covers 14:00: another booking ticked later
  at 14:00 sends a second 14:00 reminder.

## Where staff see the outcome
- **Edit Appointment:** a line under the box says when the reminder will go, that it went (and when), or why it was
  not sent:
  - not opted in;
  - no valid address;
  - not set up;
  - no access;
  - too close to the appointment;
  - the patient's reminder for this time comes from another booking.

  Anyone who may open this patient's appointment sees the line, front desk included: it needs the Edit
  Appointment page's own right (`_appointment` write) for this patient, with the patient's record locks, and no
  right on their email. It holds only a time or one of the reasons above, never an address or message text.
- **Administration > Manage Emails:** each sent reminder is an email log row, marked "Type: Appointment reminder".
  The Type filter lists reminders on their own. Failures and unconfirmed sends are handled there like any other
  email.
- **Chart:** each reminder is archived to the chart like every patient email, without a chart note.
- **Audit log:**
  - ticking and unticking the box: `AppointmentReminder.requested` / `.withdrawn`, under the staff user;
  - each attempt: `AppointmentReminder.EMAIL.<outcome>`, under the system account (`-1`);
  - no address or message text is logged.

## Several servers
Each reminder is claimed with `SELECT … FOR UPDATE SKIP LOCKED` before it is sent, as the SMS queue does, so every
server may run the job. An attempt is recorded before the email is sent: a server stopping mid-send can leave a
reminder "not confirmed", but never sends it twice.

## Text reminders
Each reminder row names its channel. Text reminders are the second channel, with their own box, switch and consent
gate: see [Text Appointment Reminders](../sms/appointment-text-reminders.md) (#884).
