-- Appointment Location List (issue #3982, ported from Open-O PR #2518).
--
-- Clinics without a site setup (multisite sites, scheduleSiteID, or CAISI program locations) book
-- a free-text location today. This lets an administrator keep a list of bookable locations instead:
-- a Look-Up List named appointmentLocationCode whose items carry a colour and an icon. Once the
-- list has an active item, the booking screens offer it as a dropdown and save the item's id in
-- appointment.locationCode next to a snapshot of its label in appointment.location.
--
-- appointment.location, appointmentArchive.location and appointmentType.location stay
-- varchar(255) (V1.0.8). The fork narrows them to varchar(80); CARLOS must not.
--
-- Idempotent: every column is added IF NOT EXISTS and the list row only when missing, so a
-- re-run (the devcontainer re-applies migrations over demo data) changes nothing.
--
-- Renumber before merge: take the next free version above the highest in BOTH develop and
-- release/2026.08 at merge time.

-- Item style: an icon (a Font Awesome class from the server's whitelist) and a #rrggbb colour.
ALTER TABLE LookupListItem
    ADD COLUMN IF NOT EXISTS icon varchar(64) NULL AFTER label,
    ADD COLUMN IF NOT EXISTS colour varchar(7) NULL AFTER icon;

-- The Location List item chosen at booking: a LookupListItem id, no foreign key, because the
-- item may be deactivated (never deleted) and older bookings keep their snapshot text.
ALTER TABLE appointment
    ADD COLUMN IF NOT EXISTS locationCode int(11) NULL AFTER location;

ALTER TABLE appointmentArchive
    ADD COLUMN IF NOT EXISTS locationCode int(11) NULL AFTER location;

-- The list ships empty, so every booking screen keeps its free-text location until an
-- administrator adds and enables a location.
INSERT INTO LookupList (name, listTitle, description, categoryId, active, createdBy, dateCreated)
SELECT 'appointmentLocationCode', 'Appointment Locations',
       'Places an appointment can be booked (Administration > Schedule > Appointment Location Setting)',
       NULL, 1, 'oscar', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM LookupList WHERE name = 'appointmentLocationCode');
