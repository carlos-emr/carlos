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
This needs the booking prompt permission (with its booking eligibility check) plus schedule write
(`_appointment`), because the patient's pick becomes a booking. Times stay within 365 days (the portal refuses more than
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
| `patient_portal.booking.sync.interval_seconds` | `60` | How often the job polls (15 to 3600). |
| `patient_portal.job.booking-sync.private_key` | none | The job's own Ed25519 private key (unpadded base64url PKCS#8). Required. |
| `patient_portal.job.booking-sync.key_id` | none | Its key ID (`[A-Za-z0-9._-]{1,64}`), never the staff key ID. Required. |

CARLOS reads these settings, and `patient_portal.enabled`, when it starts: restart CARLOS after
changing any of them.

`patient_portal.booking.sync.provider_no` is no longer used: the job signs with its own key
(below), not as a provider. Remove it from `carlos.properties`. If a non-login provider was made
for the old sync, deactivate it rather than delete it, so its past log rows still show its name.

## The booking-sync job and its key

The polling job is not a person and not a CARLOS provider. It calls the portal as the
`booking-sync` job: each call carries the service token and an `X-CARLOS-Job-Assertion` signed
with the job's own key (audience `carlos-patient-portal-job-api`, exactly one permission,
`portal.booking_prompt.sync`, a 60-second life, bound to the exact request). It never sends the
staff assertion and never uses the staff key. The portal lists these calls under the job ("Clinic
booking"), and refuses `portal.booking_prompt.sync` on any staff assertion.

In CARLOS, every row the job writes names the built-in system provider `-1`: the appointment's
creator and last update user, the replacement times it offers, and its log rows. The log row names
the job instead: action `PortalJob.booking-sync.booked`, `PortalJob.booking-sync.undone` or
`PortalJob.booking-sync.undoFailed`, the appointment number as the content, and data
`kid=<key ID> choice=<choice ID>`, never the patient.

The key is read once, at startup. With it missing or invalid the job is off: CARLOS logs one WARN
naming the job and the setting and still starts, and nothing falls back to the staff key. Every start
logs one INFO line for the job, `configured; kid=<key ID>` or `off`. The `.next` variants of both
settings, which a rotation may stage, are ignored.

### Making the key by hand

A carlos-ctl command for this is planned. Until then, on a trusted machine:

1. Make the key pair, in a file only you can read:

   ```bash
   umask 077
   openssl genpkey -algorithm ed25519 -out booking-sync.pem
   ```

2. Choose its key ID. It must differ from `patient_portal.staff_assertion.key_id`:

   ```bash
   kid="booking-sync-$(date -u +%Y%m%d)-$(openssl rand -hex 3)"
   ```

3. CARLOS side: the private value, the unpadded base64url of the PKCS#8 DER key, goes in
   `/etc/carlos-emr/carlos.properties` (installed `0640 root:carlos`) with the key ID:

   ```bash
   openssl pkey -in booking-sync.pem -outform DER | basenc --base64url | tr -d '=\n'
   ```

   ```properties
   patient_portal.job.booking-sync.private_key=<the output above>
   patient_portal.job.booking-sync.key_id=<the kid>
   ```

4. Portal side: the public value, the raw 32-byte key as unpadded base64url, goes in the portal's
   `PATIENT_PORTAL_INTERNAL_JOB_ASSERTION_PUBLIC_KEYRING` as
   `{"booking-sync": {"<kid>": "<public>"}}`:

   ```bash
   openssl pkey -in booking-sync.pem -pubout -outform DER | tail -c 32 | basenc --base64url | tr -d '=\n'
   ```

5. Once both values are set, shred the key file (`shred -u booking-sync.pem`); the private key now
   lives only in the CARLOS properties file.

**Rotation:** make a new pair and key ID as above. Add the new key ID to the portal's keyring first
and restart the portal (it then accepts both). Then put the new private key and key ID in CARLOS
and restart **every** CARLOS node. After 2 minutes, the longest a signed call lives, remove the old
key ID from the portal. For a leaked key, remove its key ID from the portal at once: the job stops
until CARLOS has the new key.

## What the portal sees

Per offered time: an opaque `slot_id` (32 random characters, nothing readable), the start with its
UTC offset, the duration, the visit mode and optionally the location code. Never the provider, the
reason, or free text. The table `portal_booking_offer` (migration `V1.0.61`) is the only place that
says which provider and time a `slot_id` stands for, and what became of it.

## Booking a pick

The job lists pending picks, then for each:

- **Still open:** in one transaction, lock the provider's row first, check the time again (the lead
  time applies only when offering), insert the appointment (booking source `PORTAL`, status `t`,
  reason "Booked by the patient through the portal", creator and last update user `-1`) and mark
  the offer `booked`; then
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
  `PortalJob.booking-sync.undoFailed` with the appointment number to the audit log for staff.
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
`PortalBookingSettingsUnitTest`, `PatientPortalBookingSyncCallsUnitTest`,
`PortalJobAssertionSignerUnitTest`, `PortalJobKeyUnitTest` and
`PortalBookingPrompt2ActionOfferedTimesUnitTest` run in the normal suite.
`src/test/resources/patientportal/job-assertion-contract.json` is a byte-for-byte copy of the
portal's `tests/fixtures/job-assertion-contract.json`: CARLOS must compute each request hash and
signed assertion in it exactly. Copy it again, unchanged, whenever the portal's copy changes.
`PortalBookingLockMariaDbIntegrationTest` needs real MariaDB (H2 cannot show the race): point
`CARLOS_TEST_MARIADB_URL` at a throwaway schema whose name contains `carlos_race_`, with
`CARLOS_TEST_MARIADB_USER` and `CARLOS_TEST_MARIADB_PASSWORD`. Eight bookers pick the same time at
once; exactly one is booked. Without the lock all eight are.
