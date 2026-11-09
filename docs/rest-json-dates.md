# REST JSON date contracts

The two published REST surfaces retain their established JSON types. The
session `/ws/rs` mapper writes unannotated dates as epoch milliseconds. The
OAuth `/ws/services` mapper writes declared calendar dates as `yyyy-MM-dd`,
clock times as `HH:mm:ss`, and explicitly marked instants as epoch milliseconds.
DOB already has an explicit date-only serializer on both surfaces. Clients
sharing a parser must select it by surface and property contract; a JSON number
and a date string do not become interchangeable implicitly.

OAuth no longer infers a declared property's kind from whether its value falls
on 1970-01-01 or midnight. For example, `DemographicTo1.effDate` on the epoch day
is `"1970-01-01"`, rather than `"00:00:00"`. A calendar property remains a date
when imported data has a non-midnight time component. Instants retain their
full numeric precision, including at midnight. Calendar and clock fields use
the server's configured local calendar, as before; this does not change stored
values, XML, or the session mapper.

For OAuth clients requiring the historical value-dependent representation,
select `X-Carlos-JSON-Date-Profile: legacy-value-inference` explicitly on every
request. The default, also selectable explicitly, is `declared`. The response
identifies the selection and adds the header to `Vary`. Blank, unknown, duplicate
or combined selections return HTTP 400 before the resource runs. The choice
is scoped to the request and never changes another caller's mapper. This
header is defined on the OAuth data server, not SOAP or OAuth token endpoints.

The legacy profile retains timestamp annotations and DOB's existing explicit
serializer. It is a compatibility exception, not a recommendation to infer a
field's meaning from a particular value. JSON readers continue to accept their
existing numeric/date/time inputs; this change does not add a new input grammar.
Do not route profiled requests to an older instance that cannot acknowledge
the header. Qualify clients against the selected profile before cutover.
