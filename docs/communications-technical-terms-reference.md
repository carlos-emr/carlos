# CARLOS Communications Technical Terms Reference

This reference explains common telecom, messaging, fax, cloud, and healthcare-compliance terms used in the vendor review.

# Core Telecom Terms

## DID

**Stands for:** Direct Inward Dialing.

**Simple meaning:** A real phone number that can receive calls or messages.

**Why it matters for CARLOS:**  
If CARLOS sends texts, receives replies, handles faxes, or routes calls, it usually needs one or more DIDs. A clinic might have its own DID so patients recognize the sender.

## VoIP

**Stands for:** Voice over Internet Protocol.

**Simple meaning:** Phone calls sent over the internet instead of traditional phone lines.

**Why it matters for CARLOS:**  
VoIP can support clinic calling, call routing, voicemail, and sometimes fax/SMS depending on the provider.

## SIP

**Stands for:** Session Initiation Protocol.

**Simple meaning:** A standard way for internet phone systems to start and manage phone calls.

**Why it matters for CARLOS:**  
SIP is common when connecting a phone system, PBX, or call-routing platform to a telecom provider.

## SIP Trunk

**Simple meaning:** A virtual phone line between a phone system and a telecom provider.

**Why it matters for CARLOS:**  
A clinic phone system may use SIP trunks to place and receive calls through a provider like VoIP.ms, Telnyx, Bandwidth, or Cloudli.

## PBX

**Stands for:** Private Branch Exchange.

**Simple meaning:** A business phone system that manages extensions, call routing, voicemail, menus, and queues.

**Why it matters for CARLOS:**  
FreePBX is PBX software. It is useful for clinic phone systems, but it is not the same thing as an SMS/fax API provider.

## FreePBX

**Simple meaning:** Open-source software for managing a PBX phone system.

**Why it matters for CARLOS:**  
FreePBX may help with clinic calling, extensions, IVRs, and SIP trunks, but it would require hosting, maintenance, telecom configuration, and a separate carrier/provider.

## IVR

**Stands for:** Interactive Voice Response.

**Simple meaning:** A phone menu like “Press 1 for appointments, press 2 for billing.”

**Why it matters for CARLOS:**  
Useful if CARLOS later needs phone workflows, but not central to SMS or fax delivery.

## PSTN

**Stands for:** Public Switched Telephone Network.

**Simple meaning:** The traditional global phone network.

**Why it matters for CARLOS:**  
Even if CARLOS uses internet APIs, messages and calls often end up crossing the regular phone network.

# SMS and Messaging Terms

## SMS

**Stands for:** Short Message Service.

**Simple meaning:** Standard text messaging.

**Why it matters for CARLOS:**  
SMS would be used for appointment reminders, notifications, patient follow-ups, or links to secure messages.

## MMS

**Stands for:** Multimedia Messaging Service.

**Simple meaning:** Text messaging with media attachments like images, PDFs, or longer rich content.

**Why it matters for CARLOS:**  
MMS may be useful for simple attachments, but healthcare content should usually be handled carefully because patient information may be exposed on a phone lock screen or carrier system.

## A2P

**Stands for:** Application-to-Person.

**Simple meaning:** Messages sent by software to people.

**Why it matters for CARLOS:**  
CARLOS sending appointment reminders or patient notifications is A2P messaging. A2P traffic often requires registration and compliance checks.

## P2P

**Stands for:** Person-to-Person.

**Simple meaning:** Normal texting between two individuals.

**Why it matters for CARLOS:**  
CARLOS messages are not usually P2P, because they are sent by an application on behalf of clinics.

## 10DLC

**Stands for:** 10-Digit Long Code.

**Simple meaning:** A regular-looking 10-digit phone number used for business texting.

**Why it matters for CARLOS:**  
In the US, business texting over normal phone numbers usually requires 10DLC brand and campaign registration.

## Toll-Free SMS

**Simple meaning:** Texting from a toll-free number, such as a number starting with 800, 888, 877, etc.

**Why it matters for CARLOS:**  
Toll-free numbers can be used for business SMS, but they still require verification and have carrier rules.

## Short Code

**Simple meaning:** A short 5- or 6-digit texting number used by large organizations.

**Why it matters for CARLOS:**  
Short codes can support high-volume messaging but are expensive and require carrier approval.

## Campaign Registration

**Simple meaning:** Registering who is sending messages, what the messages are for, and how recipients gave consent.

**Why it matters for CARLOS:**  
At scale, CARLOS may need campaign registration for each clinic, organization, or message type depending on the provider and carrier rules.

## TCR

**Stands for:** The Campaign Registry.

**Simple meaning:** The organization used in the US to register brands and campaigns for 10DLC business messaging.

**Why it matters for CARLOS:**  
If CARLOS sends US 10DLC messages, TCR registration may be required.

## Carrier Fees

**Simple meaning:** Extra fees charged by mobile carriers on top of the vendor’s messaging price.

**Why it matters for CARLOS:**  
The listed SMS price is often not the full cost. Carrier fees can materially change total cost at millions of messages.

## Throughput

**Simple meaning:** How many messages can be sent in a given time period.

**Why it matters for CARLOS:**  
Millions of patient messages require high throughput. A provider that is fine for 100 texts/day may not be suitable for national-scale reminders.

## Deliverability

**Simple meaning:** How reliably messages actually reach recipients.

**Why it matters for CARLOS:**  
Low price does not help if messages are filtered, delayed, blocked, or marked as spam.

## Webhook

**Simple meaning:** A callback from a provider to CARLOS when something happens.

**Why it matters for CARLOS:**  
Webhooks can tell CARLOS when a message was delivered, failed, received a reply, or got an opt-out like STOP.

## Delivery Receipt

**Simple meaning:** A status update saying whether a message was delivered, failed, queued, or rejected.

**Why it matters for CARLOS:**  
CARLOS should track message outcomes for support, auditability, and retry logic.

## Opt-In

**Simple meaning:** A patient agrees to receive messages.

**Why it matters for CARLOS:**  
Consent must be tracked before sending many types of patient communication.

## Opt-Out

**Simple meaning:** A patient says they no longer want messages, often by replying STOP.

**Why it matters for CARLOS:**  
CARLOS must stop sending messages when a patient opts out, unless a legally valid exception applies.

## Consent

**Simple meaning:** Permission from the patient to communicate in a certain way.

**Why it matters for CARLOS:**  
Consent should be tracked by channel, such as SMS, email, phone, fax, portal, and possibly by message type.

# Fax Terms

## Virtual Fax

**Simple meaning:** Sending and receiving faxes through a web portal, email, or API instead of a physical fax machine.

**Why it matters for CARLOS:**  
Virtual fax could support clinics that still need to send documents to other healthcare organizations.

## Fax DID

**Simple meaning:** A phone number dedicated to sending or receiving faxes.

**Why it matters for CARLOS:**  
Some vendors require fax-specific numbers instead of reusing normal voice numbers.

## T.38

**Simple meaning:** A standard for sending fax over IP networks.

**Why it matters for CARLOS:**  
T.38 is useful for connecting traditional fax machines or fax servers, but status tracking may be weaker than API-based fax.

## Fax API

**Simple meaning:** A software interface for sending, receiving, and tracking faxes.

**Why it matters for CARLOS:**  
A fax API is better than manual faxing if CARLOS needs audit logs, delivery status, and automated workflows.

## Fax Delivery Confirmation

**Simple meaning:** Confirmation that a fax was sent successfully or failed.

**Why it matters for CARLOS:**  
Healthcare workflows often need proof that a document was transmitted.

# Cloud and API Terms

## API

**Stands for:** Application Programming Interface.

**Simple meaning:** A way for one software system to talk to another.

**Why it matters for CARLOS:**  
CARLOS would use vendor APIs to send messages, send faxes, receive replies, and check delivery status.

## SDK

**Stands for:** Software Development Kit.

**Simple meaning:** Prebuilt code from a vendor that makes their API easier to use.

**Why it matters for CARLOS:**  
Good SDKs reduce implementation time and bugs.

## CPaaS

**Stands for:** Communications Platform as a Service.

**Simple meaning:** A cloud service that lets applications send texts, make calls, verify users, or manage communications through APIs.

**Why it matters for CARLOS:**  
Twilio, Telnyx, Plivo, Sinch, SignalWire, and Bandwidth are examples of CPaaS-style vendors.

## UCaaS

**Stands for:** Unified Communications as a Service.

**Simple meaning:** Cloud-based business phone, messaging, meetings, voicemail, and collaboration tools.

**Why it matters for CARLOS:**  
Cloudli, RingCentral, Zoom Phone, and similar tools are often better for office phone systems than embedded patient messaging APIs.

## Contact Center

**Simple meaning:** Software for managing support calls, queues, agents, recordings, and customer interactions.

**Why it matters for CARLOS:**  
Could matter if CARLOS later supports call-center workflows for clinics.

## Data Residency

**Simple meaning:** Where data is stored geographically.

**Why it matters for CARLOS:**  
Canadian healthcare customers may prefer or require Canadian data storage.

## Data Retention

**Simple meaning:** How long a vendor keeps message content, fax files, logs, or metadata.

**Why it matters for CARLOS:**  
Patient information should not be stored longer than necessary, and retention should match legal and clinical requirements.

## Metadata

**Simple meaning:** Information about a message, not necessarily the message content itself.

**Examples:** sender, recipient, timestamp, status, message ID.

**Why it matters for CARLOS:**  
Even metadata can be sensitive in healthcare because it may reveal patient-provider relationships.

## SLA

**Stands for:** Service Level Agreement.

**Simple meaning:** A vendor’s formal uptime or support commitment.

**Why it matters for CARLOS:**  
At large scale, CARLOS needs reliable vendor support and clear commitments around outages.

# Healthcare and Compliance Terms

## PHI

**Stands for:** Protected Health Information.

**Simple meaning:** Health information that can identify a patient.

**Why it matters for CARLOS:**  
SMS, email, fax, logs, and message metadata can all involve PHI.

## PII

**Stands for:** Personally Identifiable Information.

**Simple meaning:** Information that can identify a person, such as name, phone number, email, or address.

**Why it matters for CARLOS:**  
Even a phone number or appointment reminder may be sensitive when tied to healthcare.

## HIPAA

**Stands for:** Health Insurance Portability and Accountability Act.

**Simple meaning:** US law that governs health information privacy and security.

**Why it matters for CARLOS:**  
Relevant if CARLOS serves US healthcare providers or patients.

## BAA

**Stands for:** Business Associate Agreement.

**Simple meaning:** A legal agreement required under HIPAA when a vendor handles PHI for a healthcare organization.

**Why it matters for CARLOS:**  
If a US healthcare customer uses CARLOS and CARLOS uses a messaging vendor, the vendor may need to sign a BAA.

## PHIPA

**Stands for:** Personal Health Information Protection Act.

**Simple meaning:** Ontario health privacy law.

**Why it matters for CARLOS:**  
Relevant if CARLOS serves Ontario healthcare providers or patients.

## PIPEDA

**Stands for:** Personal Information Protection and Electronic Documents Act.

**Simple meaning:** Canadian federal private-sector privacy law.

**Why it matters for CARLOS:**  
Relevant to how CARLOS handles personal information in Canada.

## CASL

**Stands for:** Canada's Anti-Spam Legislation.

**Simple meaning:** Canadian law covering commercial electronic messages, including texts and emails.

**Why it matters for CARLOS:**  
Consent, sender identification, and unsubscribe/opt-out handling may be required depending on message type.

## Audit Log

**Simple meaning:** A record of who did what and when.

**Why it matters for CARLOS:**  
CARLOS should track message sends, delivery results, consent changes, opt-outs, user actions, and vendor callbacks.

## Tenant Separation

**Simple meaning:** Keeping each clinic or organization’s data separated from others.

**Why it matters for CARLOS:**  
CARLOS is intended for many doctors/clinics, so one clinic should not see or affect another clinic’s communications.

# Vendor Categories

## Carrier API Provider

**Simple meaning:** A vendor that connects directly or closely to telecom networks and exposes APIs for messaging or voice.

**Examples:** Bandwidth, Telnyx.

**Why it matters for CARLOS:**  
Often stronger for scale, deliverability, and enterprise telecom operations.

## CPaaS Provider

**Simple meaning:** A developer-friendly communications API platform.

**Examples:** Twilio, Telnyx, Plivo, Sinch, SignalWire.

**Why it matters for CARLOS:**  
Usually fastest path to embedding SMS, voice, verification, and sometimes fax into an application.

## Healthcare Platform

**Simple meaning:** A product built around healthcare workflows like patient reminders, intake forms, referrals, booking, or secure messaging.

**Examples:** OceanMD, Cortico.

**Why it matters for CARLOS:**  
These may be competitors, integration partners, or benchmarks, but they are not usually raw telecom infrastructure providers.

## Cloud Platform

**Simple meaning:** A broad infrastructure provider for hosting, storage, databases, email, analytics, identity, and sometimes communications.

**Examples:** AWS, Azure, Google Cloud.

**Why it matters for CARLOS:**  
Cloud platforms may host CARLOS and provide email/SMS pieces, but may not offer complete telecom workflows such as fax.

# Terms to Watch Closely in Vendor Reviews

## “HIPAA compliant”

This can be vague. Ask which products are eligible, whether the vendor signs a BAA, and whether SMS/fax/email are included.

## “Canadian”

This can mean different things: Canadian company, Canadian office, Canadian data center, Canadian support, or Canadian legal entity. These are not the same.

## “Unlimited”

Usually subject to fair-use limits, carrier rules, or acceptable-use policies.

## “Starting at”

Usually excludes carrier fees, registration fees, number rental, support plans, or enterprise requirements.

## “SMS support”

This does not automatically mean high-volume A2P support, healthcare suitability, two-way messaging, opt-out automation, or Canadian carrier compliance.

## “Fax support”

This may mean email-to-fax, portal fax, SIP/T.38 fax, or a true fax API. These are different implementation models.
