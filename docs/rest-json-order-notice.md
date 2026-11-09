# REST JSON property order notice

For #5749, both production JSON mappers were exercised directly, using their
Spring-configured introspectors. `DrugTransfer` emits its properties in
alphabetical order, including `eTreatmentType` between `duration` and `endDate`.
This is observable on both `/ws/rs` and `/ws/services`. Clients comparing JSON
text must account for this ordering in 2026.08; ordinary JSON object readers
should select members by name.

`AppointmentExtTo` uses the explicit sequence restored by #5722:

```text
appointmentDate, appointmentNo, demoCell, demographicNo, email, firstName,
lastName, location, notes, phone, phone2, providerNo, reminderPreference,
resources, startTime, status, hPhoneExt, wPhoneExt
```

The historical JSON change had been inferred from annotations, rather than
reproduced across deployed versions. The current mapper probe confirms the
candidate behavior; it does not establish a historical byte-for-byte baseline.
This release announces the drug ordering rather than changing it again. No
clinical field, value or JSON type is changed by this disposition. XML profile
selection does not change JSON property order.
