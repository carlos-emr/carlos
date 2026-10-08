# Patient portal: offered appointment times (#3850)

Staff can send a booking prompt with a few open times; the patient picks one in the portal and
CARLOS books it, so the patient does not have to phone. This is the CARLOS side of
carlos-portal#11 (portal: offered times, picks, and results). CARLOS still never accepts calls from
the portal: it pushes the times with the prompt and collects the pick by polling.

## Which times are offered

Only free time inside the schedule template codes the clinic chose, on the doctor's own day
template, is ever offered (Cortico style). Nothing is offered off the template, and nothing is held
while a prompt is open: when a patient picks a time, CARLOS checks it again under a lock and books
it only if it is still free and still on a bookable code.

A time is offered when:

- every template slot the visit covers has a bookable code (the visit lasts the code's own
  duration, else one template slot);
- no appointment that is not cancelled overlaps it;
- it starts after the lead time (default 24 hours).

Times are spread across days and across mornings and afternoons. Staff choose the provider whose
schedule to offer from and a window (`offerAfterDays`, `offerWithinDays`, default the next 14 days)
and how many times (`offerCount`, 1 to 8, default 4) when they create the prompt
(`PortalBookingPrompt2Action`, `method=create`, `offerFrom=<provider number>`, an active provider).
This needs the booking prompt and portal account permissions plus schedule write (`_appointment`),
because the patient's pick becomes a booking. Times stay within 365 days (the portal refuses more than
366). With no open time in the window the prompt is not sent and staff are told to choose another
window. A visit longer than one template slot can be offered at each slot it may start at.

## Settings (CARLOS properties)

| Property | Default | Meaning |
|---|---|---|
| `patient_portal.booking.offerable_codes` | empty | Template code letters patients may book, for example `B,F`. Empty: nothing is offered. |
| `patient_portal.booking.offer_lead_hours` | `24` | Earliest offered time, in hours from now (0 to 720). |
| `patient_portal.booking.visit_mode` | `in_person` | `in_person`, `phone`, or `video`, shown to the patient. |
| `patient_portal.booking.location_code` | none | One of the portal's `PATIENT_PORTAL_BOOKING_LOCATIONS` codes. |
| `patient_portal.booking.sync.enabled` | `false` | Turns the polling job on. |
| `patient_portal.booking.sync.provider_no` | none | The job's provider: an existing provider with **no login**. Required. |
| `patient_portal.booking.sync.interval_seconds` | `60` | How often the job polls (15 to 3600). |

The sync provider signs the job's portal requests holding only `portal.booking_prompt.sync`; the
portal refuses that permission combined with any other, and records these calls as `system`.
CARLOS refuses to run the job as a provider that can log in.

## What the portal sees

Per offered time: an opaque `slot_id` (32 random characters, nothing readable), the start with its
UTC offset, the duration, the visit mode and optionally the location code. Never the provider, the
reason, or free text. The table `portal_booking_offer` (migration `V1.0.61`) is the only place that
says which provider and time a `slot_id` stands for, and what became of it.

## Booking a pick

The job lists pending picks, then for each:

- **Still open:** in one transaction, lock the provider's row first, check the time again (the lead
  time applies only when offering), insert the appointment (booking source `PORTAL`, status `t`,
  reason "Booked by patient via portal", creator "patient portal") and mark the offer `booked`; then
  report `booked`. Once the portal records it the offer is `confirmed` and the prompt's other times
  are closed (the portal drops them too); until then they stay open, in case the portal refuses it.
- **Gone:** report `slot_unavailable` with up to 3 fresh times near the refused one (never more than 8
  on offer in all).
- **Not a time CARLOS offered this patient:** report `slot_unavailable` and book nothing.

Every step is safe to repeat. If the job stops between booking and reporting, the pick is listed again
and the same booking is reported again, never made twice. A pick that keeps failing for another reason
is left for a later run without holding up the others; a portal outage (no answer, 5xx, or throttling)
stops the run before more times are booked, and paging stops when a page answers nothing. If the portal
answers `409` "booking choice expired" or "booking choice was withdrawn", the portal will never show the
pick as booked, so CARLOS cancels the appointment it made. Bookings are never made first and cancelled
on a clash (`removeIfDoubleBooked` is not used).

Known limits:

- If that cancellation fails, the portal will not list the pick again: CARLOS writes
  `PortalBooking.undoFailed` with the appointment number to the audit log for staff.
- If reporting keeps failing until the time starts, or the portal records the booking but its answer
  is lost, the portal does not list the pick again and the offer stays `booked` (not `confirmed`) in
  `portal_booking_offer`, with the prompt's other times still `offered` there (the portal has dropped
  them; nothing else can pick them). Finding these needs the
  portal's prompt states, which the sync permission cannot read yet (#4480).
- On a daylight-saving change day the schedule's slot times follow `ScheduleManager`, which counts
  minutes from midnight.

Cancelling or moving a portal-booked appointment stays a phone call (#4471). Ticklers for prompts the
patient declined or let expire are a follow-up (#4480): they need a new portal sync endpoint.

## Tests

`PortalOfferedSlotLoaderUnitTest`, `PortalBookingOfferServiceUnitTest`,
`PortalBookingChoiceServiceUnitTest`, `PortalBookingSyncServiceUnitTest`,
`PortalBookingSettingsUnitTest`, `PatientPortalBookingSyncCallsUnitTest` and
`PortalBookingPrompt2ActionOfferedTimesUnitTest` run in the normal suite.
`PortalBookingLockMariaDbRaceTest` needs real MariaDB (H2 cannot show the race): point
`CARLOS_TEST_MARIADB_URL` at a throwaway schema whose name contains `carlos_race_`, with
`CARLOS_TEST_MARIADB_USER` and `CARLOS_TEST_MARIADB_PASSWORD`. Eight bookers pick the same time at
once; exactly one is booked. Without the lock all eight are.
