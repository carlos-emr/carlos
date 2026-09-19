# CARLOS Communications Vendor Research Brief

Pricing is based on public vendor docs as of May 28, 2026 and should be rechecked before procurement.

# High-Level Fit

## Quick Comparison

**VoIP.ms**
- Category: VoIP/SMS/fax API
- Canadian fit: High
- Services: SMS/MMS, fax, voice
- Email: No
- CARLOS setup: Medium
- Scale confidence: Low-Medium

**Telnyx**
- Category: CPaaS carrier API
- Canadian fit: Low
- Services: SMS/MMS, fax, voice
- Email: No
- CARLOS setup: Medium
- Scale confidence: High

**Twilio**
- Category: CPaaS API suite
- Canadian fit: Low
- Services: SMS/MMS, voice, email via SendGrid
- Fax: No
- CARLOS setup: Low-Medium
- Scale confidence: High

**Bandwidth**
- Category: Carrier-grade API
- Canadian fit: Low
- Services: SMS/MMS, voice
- Fax: No/unclear
- Email: No
- CARLOS setup: Medium-High
- Scale confidence: High

**AWS**
- Category: Cloud platform
- Canadian fit: Low
- Services: SMS/MMS, email via SES, voice
- Fax: No
- CARLOS setup: Medium
- Scale confidence: High

**Microsoft / Azure**
- Category: Cloud + communications APIs
- Canadian fit: Medium
- Services: SMS, email, voice, video/chat
- Fax: No
- CARLOS setup: Medium
- Scale confidence: Medium

**Google Cloud**
- Category: Cloud/health infrastructure
- Canadian fit: Medium
- Services: healthcare cloud APIs, cloud hosting, Google Voice/Workspace adjacent services
- SMS/MMS: No direct CPaaS equivalent found
- Fax: No
- Email: Partner/Gmail API route
- CARLOS setup: High
- Scale confidence for SMS: Low

**Cloudli**
- Category: Canadian UC/fax
- Canadian fit: High
- Services: SMS/MMS, fax, voice
- Email: No
- CARLOS setup: Medium
- Scale confidence: Low-Medium

**FreePBX**
- Category: PBX software
- Canadian fit: Medium via Sangoma
- Services: PBX, SIP, voice, fax-to-email, voicemail/email notifications
- SMS/MMS: Via add-ons/provider
- CARLOS setup: High
- Scale confidence for patient SMS: Low

**OceanMD**
- Category: Canadian healthcare platform
- Canadian fit: High
- Services: secure patient messaging, reminders, forms, eReferrals, secure links
- SMS/MMS: Partial/reminders
- Fax: No
- Voice: No
- CARLOS setup: Medium
- Scale confidence: Medium

**Cortico**
- Category: Canadian healthcare platform
- Canadian fit: High
- Services: SMS/email reminders, secure messaging, telehealth, EMR plug-ins
- Fax: No
- CARLOS setup: Medium
- Scale confidence: Medium

**Plivo**
- Category: CPaaS API
- Canadian fit: Low
- Services: SMS/MMS, voice
- Fax: No
- Email: No
- CARLOS setup: Low-Medium
- Scale confidence: High

**Sinch**
- Category: CPaaS/omnichannel
- Canadian fit: Low
- Services: SMS, voice, email/omnichannel
- Fax: Unknown
- CARLOS setup: Medium
- Scale confidence: High

**SignalWire**
- Category: CPaaS voice/SMS/fax
- Canadian fit: Low
- Services: SMS/MMS, fax, voice, SIP, WebRTC
- Email: No
- CARLOS setup: Medium
- Scale confidence: Medium-High

# Vendor Details

## VoIP.ms

**What it is:**  
Montreal-based Canadian VoIP provider offering DIDs, voice, SMS/MMS, virtual fax, SIP trunking, and APIs.

**Key facts:**

- SMS/MMS via API is supported.
- SMS pricing is around $0.0075 USD sent or received.
- MMS pricing is around $0.02 USD sent or received.
- Virtual fax pricing is around $1.99 USD/month per fax DID plus $0.029 USD/min sent or received.
- Local phone numbers are around $1.10 USD/month depending on number type.
- API SMS/MMS has a default 100 messages/day limit, raisable after verification.
- Sources: https://voip.ms/pricing, https://wiki.voip.ms/article/SMS, https://wiki.voip.ms/article/Virtual_Fax, https://wiki.voip.ms/article/API_Overview

**Pros:**

- Canadian company.
- Inexpensive.
- Straightforward API.
- Supports SMS, MMS, fax, and voice.
- Good pilot candidate.

**Cons / risks:**

- Public docs do not prove easy scaling to millions of patients.
- Multi-clinic A2P registration needs direct confirmation.
- Deliverability, support SLAs, healthcare agreements, and data residency need direct confirmation.

**CARLOS fit:**  
Good for pilot or low-volume Canadian SMS/fax. Risky as the assumed production backbone for millions of patients.

---

## Telnyx

**What it is:**  
Programmable communications API provider for messaging, voice, SIP, fax, numbers, lookup, verification, AI voice, and networking.

**Key facts:**

- Messaging pricing starts around $0.004 USD/message part plus carrier fees for SMS.
- MMS starts around $0.015 outbound plus carrier fees.
- Fax API is around $0.007/page plus SIP trunking usage.
- Telnyx advertises automatic discounts and contract pricing at scale.
- Sources: https://telnyx.com/pricing/messaging, https://telnyx.com/pricing/fax, https://telnyx.com/pricing

**Pros:**

- Strong API surface.
- High-volume pricing model.
- Fax API.
- Voice and SIP support.
- Better infrastructure fit than VoIP.ms for large A2P messaging.

**Cons / risks:**

- Not Canadian.
- Healthcare compliance and data residency need confirmation.
- Carrier fees and campaign registration still apply.

**CARLOS fit:**  
Strong production candidate for SMS/MMS/fax infrastructure if compliance and Canadian data concerns are acceptable.

---

## Twilio

**What it is:**  
Large global CPaaS provider for SMS/MMS, voice, WhatsApp, verification, conversations, email via SendGrid, Flex/contact center, and data products.

**Key facts:**

- Canada SMS pricing lists long-code/toll-free outbound and inbound SMS at $0.0083 USD/message segment.
- MMS outbound is around $0.0220.
- Canadian carrier fees are extra.
- Canadian long-code numbers are around $1.15/month.
- Toll-free numbers are around $2.15/month.
- Random short codes are around $1,000/month plus setup.
- Twilio SendGrid covers email.
- Twilio Programmable Fax was sunset in 2021.
- Sources: https://www.twilio.com/en-us/sms/pricing/ca, https://www.twilio.com/en-us/pricing, https://sendgrid.com/en-us/pricing, https://help.twilio.com/articles/223136667

**Pros:**

- Mature docs and SDKs.
- Good webhook support.
- Compliance tooling.
- SendGrid email.
- Enterprise scale.
- Broad developer familiarity.

**Cons / risks:**

- Not Canadian.
- Often more expensive after carrier fees.
- No current fax API.
- HIPAA/healthcare requires BAA and eligible-product review.

**CARLOS fit:**  
Strong production SMS/email candidate, but not fax. Likely higher cost than Telnyx or Plivo at large volume.

---

## Bandwidth

**What it is:**  
Carrier-grade communications platform for voice, messaging, emergency calling, number management, and enterprise integrations.

**Key facts:**

- Offers SMS, MMS, RCS, Voice API, Emergency Calling API, number management, campaign registration, Microsoft Teams/Zoom/Webex integrations, and global SIP.
- Pricing page emphasizes quote/volume pricing and a direct-carrier model.
- Public pricing detail is less transparent than Twilio/Telnyx.
- Messaging API sends SMS/MMS over HTTP.
- Docs say Bandwidth does not store message body content, only metadata.
- Sources: https://www.bandwidth.com/products/, https://www.bandwidth.com/pricing/, https://dev.bandwidth.com/docs/messaging/, https://www.bandwidth.com/support/en/articles/12823993-hipaa-eligible-products-and-services

**Pros:**

- Strong scale story.
- Direct carrier/network positioning.
- Enterprise support.
- Good for SaaS platforms needing serious messaging and voice infrastructure.

**Cons / risks:**

- Pricing likely requires sales.
- Not Canadian.
- Fax and email are not core strengths.
- More enterprise procurement effort.

**CARLOS fit:**  
Strong contender for production SMS/voice at high scale, especially if CARLOS wants carrier-grade support.

---

## AWS

**What it is:**  
Cloud platform with End User Messaging, SES email, Amazon Connect, Pinpoint-related messaging, voice, push, WhatsApp, RCS, storage, and healthcare/cloud infrastructure.

**Key facts:**

- AWS End User Messaging supports SMS, MMS, RCS, WhatsApp, push, and voice.
- Example Canadian toll-free SMS pricing shows $0.00581/message fee plus $0.00705 carrier fee.
- SES is pay-as-you-go email.
- AWS has no native fax API comparable to Telnyx or VoIP.ms.
- Sources: https://aws.amazon.com/end-user-messaging/pricing, https://aws.amazon.com/end-user-messaging/faqs/, https://aws.amazon.com/ses/pricing/

**Pros:**

- Excellent cloud ecosystem.
- SES is cheap for email.
- Strong security/compliance tooling.
- Good fit if CARLOS is already on AWS.

**Cons / risks:**

- Messaging setup can be more complex than Twilio.
- Fax is missing.
- Support/compliance questions may require AWS enterprise processes.

**CARLOS fit:**  
Good for email/cloud backbone and possibly SMS at scale, less attractive if the team wants a simple telecom-first vendor.

---

## Microsoft / Azure

**What it is:**  
Cloud platform with Azure Communication Services, Teams Phone, email, SMS, voice/video/chat, and Microsoft 365/OneDrive/SharePoint.

**Key facts:**

- Azure Communication Services supports SMS, email, voice/video, and chat.
- Toll-free SMS leasing is listed at $2/month for US/Canada/Puerto Rico.
- US/Canada SMS usage is $0.0075 sent or received.
- Canadian carrier surcharge is listed at $0.0085 outbound.
- Canada short codes are $1,000/month.
- Canada short-code usage is $0.0268 outbound and $0.0061 inbound before surcharge.
- Sources: https://learn.microsoft.com/en-us/azure/communication-services/concepts/sms-pricing, https://azure.microsoft.com/en-us/pricing/details/communication-services/, https://azure.microsoft.com/en-us/products/communication-services/

**Pros:**

- Good if CARLOS already uses Microsoft 365/Azure.
- Email, Teams, cloud storage, identity, and compliance tooling are nearby.
- Strong enterprise trust posture.

**Cons / risks:**

- SMS footprint is narrower than Twilio/Telnyx.
- No fax API.
- More cloud-platform setup than telecom-only API.

**CARLOS fit:**  
Good broader enterprise/cloud option; moderate SMS fit; weak fax fit.

---

## Google Cloud

**What it is:**  
Cloud and healthcare infrastructure platform, not a first-party CPaaS SMS/fax provider.

**Key facts:**

- Google Cloud has Cloud Healthcare API with FHIR, HL7, DICOM, consent features, and regional data controls.
- Google recommends third-party SMTP/email services such as SendGrid, Mailgun, or Mailjet for high-volume email from App Engine.
- No first-party Google Cloud SMS/fax API comparable to Twilio or Telnyx was found.
- Sources: https://cloud.google.com/healthcare-api/pricing, https://docs.cloud.google.com/healthcare-api/docs/regions, https://docs.cloud.google.com/appengine/docs/standard/services/mail

**Pros:**

- Strong healthcare/cloud/data platform.
- Canadian region options.
- Good for hosting CARLOS infrastructure.

**Cons / risks:**

- Not a direct SMS/fax answer.
- Would still need Twilio, Telnyx, Bandwidth, or similar for telecom.

**CARLOS fit:**  
Good cloud/health-data platform, poor direct patient messaging provider.

---

## Cloudli

**What it is:**  
Canadian business communications provider offering cloud phone, SMS/MMS, virtual fax, IP fax, SIP/voice, and Microsoft 365 contact sync.

**Key facts:**

- Cloudli lists Canadian contact/address in Montreal in its terms.
- Cloudli Connect plans include local number, extensions, unlimited Canada/US calling subject to fair use, SMS-enabled numbers, and virtual fax on some bundles.
- Pricing is mostly “Contact for Pricing.”
- Fax docs support email-to-fax and portal fax.
- Sent/received fax copies are retained for 10 days; history is retained for 60 days.
- Sources: https://www.cloudli.com/en-ca/solutions/cloud/business-phone/, https://support.cloudli.com/hc/en-ca/sections/360005049233-SMS-MMS-FAQ, https://support.cloudli.com/hc/en-ca/articles/1500001519182-Quick-Start-Guide-for-Virtual-Fax-Desktop-Fax, https://www.cloudli.com/en-ca/terms-of-service

**Pros:**

- Canadian.
- Strong fax/voice positioning.
- Practical clinic-office communications vendor.

**Cons / risks:**

- Public API story and pricing are less clear.
- More UC/phone-system oriented than embedded developer API.

**CARLOS fit:**  
Worth investigating for Canadian fax/voice, less obvious as the core CARLOS messaging API.

---

## FreePBX

**What it is:**  
Open-source PBX management software for Asterisk, sponsored by Sangoma. It is software, not a telecom carrier.

**Key facts:**

- FreePBX is free/open source.
- Manages PBX functions such as extensions, IVR, queues, voicemail, SIP trunks, and fax-to-email.
- SMS requires provider/module support, such as Sangoma SIPStation or third-party trunk integrations.
- PBXact Cloud examples show $22.95/month trunk + user, $8.95/month user-only, inbound numbers $1/month, Lite Faxing $9.95/month, and High Volume Faxing $24.99/month.
- Sources: https://www.freepbx.org/, https://www.freepbx.org/get-started/, https://www.freepbx.org/sms-plus-enhanced-sms-functionality-for-freepbx-pbxact/, https://store.cloud.pbxact.com/

**Pros:**

- Flexible.
- Low software cost.
- Good for clinic phone systems.
- Supports SIP, IVR, and call routing.

**Cons / risks:**

- High operational burden.
- Not a patient messaging API.
- Requires carrier/provider, security hardening, maintenance, and telecom expertise.

**CARLOS fit:**  
Not recommended as core patient messaging infrastructure. Could be relevant if CARLOS later needs a clinic PBX integration story.

---

## OceanMD

**What it is:**  
Canadian healthcare platform for patient engagement, secure messages, forms, reminders, eReferrals, booking, and EMR integration.

**Key facts:**

- Pricing is in CAD.
- Patient Engagement Basic is $31/provider or schedule/month.
- Basic includes secure messaging, attachments/PDFs, customized reminders, optional SMS reminders at $0.11 each, and support staff.
- Plus is $60/schedule/month.
- Ocean supports secure patient messages, email/SMS reminders, forms, eReferral workflows, and APIs.
- Ocean says customer data is stored in AWS Montreal for Canadian residency.
- Sources: https://www.oceanmd.com/pricing/, https://www.oceanmd.com/patient-messages/, https://www.oceanmd.com/patient-reminders/, https://support.cognisantmd.com/hc/en-us/sections/360006922672-API-Integrations, https://www.oceanmd.com/security/

**Pros:**

- Canadian healthcare fit.
- EMR-aware.
- Secure messaging/forms.
- PHIPA-oriented posture.
- Good patient workflow layer.

**Cons / risks:**

- Not a raw SMS/fax carrier.
- Cohort/group messaging has documented operational limits.
- May compete with CARLOS rather than just supply infrastructure.

**CARLOS fit:**  
Strong benchmark or integration candidate for Canadian patient engagement, not the telecom backbone.

---

## Cortico

**What it is:**  
Canadian healthcare patient engagement and workflow automation platform with online booking, reminders, secure messaging, file sending, telehealth, and EMR plug-ins.

**Key facts:**

- Essentials: $86/FTE/month.
- Premium: $119/FTE/month.
- Elite: $199/FTE/month.
- Features include SMS/email reminders, secure patient messaging/file sending, telehealth, intake forms, mass email, and EMR plug-ins.
- Cortico documents consent/subscription handling for SMS/email and audit trails/read receipts.
- Sources: https://cortico.health/pricing/, https://help.cortico.ca/doc/cortico-privacy-practices-for-patients, https://help.cortico.ca/help/consent-to-use-electronic-communication, https://help.cortico.ca/help/cortico-plug-in-for-healthcare-providers

**Pros:**

- Canadian healthcare focus.
- Consent handling.
- Audit trails.
- Secure messaging.
- EMR workflow fit.

**Cons / risks:**

- Not a general telecom API provider.
- May overlap/compete with CARLOS product scope.
- Fax not evident.

**CARLOS fit:**  
Good product benchmark and possible integration/partner candidate; not the SMS carrier layer.

---

## Plivo

**What it is:**  
CPaaS provider for SMS and voice APIs.

**Key facts:**

- Canada long-code SMS outbound/inbound is $0.0077.
- Toll-free outbound SMS is $0.0079.
- MMS long-code is $0.018.
- Toll-free MMS is $0.020.
- Carrier surcharges apply.
- Number rental lists long codes at $0.75/month and toll-free numbers at $1/month.
- Short code is $700/month plus $4,000 one-time setup.
- Sources: https://www.plivo.com/sms/pricing/ca/, https://www.plivo.com/voice/pricing/ca/, https://docs.plivo.com/docs/account/api/pricing

**Pros:**

- Strong SMS pricing.
- Simple API.
- Credible Twilio alternative.

**Cons / risks:**

- Not Canadian.
- Fax/email are not core.
- Healthcare/BAA/data residency requires confirmation.

**CARLOS fit:**  
Strong SMS cost competitor for production comparison.

---

## Sinch

**What it is:**  
Large global CPaaS/omnichannel communications provider for SMS, voice, email, verification, WhatsApp/RCS, and engagement tooling.

**Key facts:**

- Sinch has SMS API, Voice API, email/messaging products, and pricing pages.
- Canadian detailed pricing was less directly exposed in public docs than Twilio/Plivo.
- Sinch documents toll-free registration requirements for Canada in messaging guidance.
- Sources: https://sinch.com/pricing/, https://developers.sinch.com/docs/sms/api-reference/sms, https://sinch.com/

**Pros:**

- Enterprise-scale global messaging.
- Broad channel support.
- Likely strong deliverability.

**Cons / risks:**

- Pricing is less transparent.
- Not Canadian.
- Procurement may be sales-led.

**CARLOS fit:**  
Worth including in enterprise RFP, less ideal for quick implementation without sales contact.

---

## SignalWire

**What it is:**  
Programmable communications provider for voice, SMS/MMS, fax, SIP, WebRTC, AI voice, and hosted messaging.

**Key facts:**

- Local voice inbound: $0.0066/min.
- Local voice outbound: $0.008/min.
- SIP: $0.003/min.
- Local numbers: $0.50/month.
- Toll-free numbers: $0.80/month.
- Messaging API supports SMS/MMS and hosted messaging.
- Fax API supports status webhooks and claims HIPAA-compliant PHI handling.
- Public fax price table was not fully visible in search results.
- Sources: https://signalwire.com/pricing/voice, https://signalwire.com/products/cloud-messaging, https://signalwire.com/docs/platform/messaging, https://signalwire.com/products/cloud-fax

**Pros:**

- Broad programmable stack.
- Fax API.
- Voice/SIP support.
- Hosted messaging.
- Developer-friendly.

**Cons / risks:**

- Not Canadian.
- Pricing transparency for fax/SMS needs confirmation.
- Smaller ecosystem than Twilio.

**CARLOS fit:**  
Good alternate for voice/fax/SMS evaluation, especially if fax API matters.

# Shortlist Recommendation

For CARLOS patient messaging at large scale, do not pick a single vendor yet.

Best candidates to evaluate deeply:

**Production SMS/MMS:**  
Telnyx, Bandwidth, Twilio, Plivo.

**Canadian pilot / simple fax + SMS:**  
VoIP.ms.

**Production fax:**  
Telnyx, Cloudli, SignalWire, plus fax-specialists like SRFax, Documo, and etherFAX in a second pass.

**Email:**  
AWS SES, Twilio SendGrid, Azure Communication Services Email.

**Canadian healthcare workflow benchmark:**  
OceanMD and Cortico.

**Cloud/platform fit:**  
AWS, Azure, or Google Cloud, depending on where CARLOS is hosted.

# Main Risk

The hard part is not sending messages.

The hard parts are:

- consent tracking
- opt-outs
- A2P/campaign registration
- deliverability
- PHI-safe message content
- audit logs
- tenant separation
- data residency
- healthcare compliance
- vendor support at millions-of-patients scale
