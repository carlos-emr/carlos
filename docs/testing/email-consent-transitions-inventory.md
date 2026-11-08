# Email consent transitions (#5807)

Run `CARLOS_DISPOSABLE_HOST=true EXCLUSIVE=1 npm run test:email-consent-transitions-playwright`
on the installed application host. Email consent tracking must name one active
native consent type and the patient consent module must be enabled. An owned
SMTP sender connects only to an ephemeral loopback sink, which accepts only the
owned `example.com` recipient. No real SMTP service is contacted.

The current release implements an entry warning and a consent snapshot, rather
than enforcing opt-in when sending. Unknown and Explicit Opt-Out cause the
native eForm Email control to prompt for `Yes`; `No` leaves the form draft intact
and creates no save, delivery or success log. Explicit acknowledgment permits
composition and sending. Explicit Opt-In enters without that warning. Each fresh
composer must display the configured consent name and correct status.

For each of Opt-Out and cleared/Unknown, a composer is first opened under Opt-In.
A second independently authenticated context changes consent through native
Master Record controls and saves it. The original composer retains its Opt-In
snapshot. Missing-subject validation preserves its pending body without sending.
The native corrected submission sends that original pending body unchanged; the
current send path does not recheck consent. A newly opened composer must reflect
the changed state and apply its warning. Native restored Opt-In sends once more.
These explicit expectations pin the existing contract requested by the ticket;
they do not introduce a new consent enforcement or retention policy.

All six actual SMTP messages must match distinct subjects/bodies and the owned
recipient. Exact success-log patient/sender/recipient/content attribution, decoded
Base64 log bodies and delivery counts reconcile after each send. No clinical
note may be created under the selected Do not add as note option. Owned consent,
eForm revisions, sender, log and supporting demographic rows are removed.

Qualification caught a real fixture hazard: unused sender IDs can already have
references in legacy seed email logs. The allocator now inventories logical and
foreign references and chooses an ID above both parent IDs and every log
reference while respecting the existing auto increment. It changes none of those
rows and refuses unknown references, malformed counters, exhaustion or a newly
occupied ID. Ten independent controls pass. The existing email-compose workflow
uses the same allocator and waits for Administration initialization before
opening its status view.

The failed collision run retained its fixtures rather than deleting unowned
logs. Exact private recovery restored the original absent sender and removed only
the owned message/patient/form rows; all 184 unowned log rows retained their full
row digests in each province. This exceptional restoration used a connection-
local foreign-key suspension only to undo the newly created parent of preexisting
orphan references. Normal test execution never disables constraints. Final allocator retakes pass all six delivery cases in both provinces, alongside
the existing email-compose and patient-consent workflows. ON additionally passes
both single-item and multi-item payment workflows in the same sequential run.
Independent audits are recorded in the consolidated acceptance register.
