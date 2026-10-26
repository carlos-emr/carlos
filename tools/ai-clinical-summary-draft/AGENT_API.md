# Pluggable clinical summary agents

CARLOS owns chart authorization, the source snapshot, the fact ledger, validation
and the review UI. An agent owns only the proposed sections, cited claims and
coverage. Switching agents does not change that boundary.

The default adapter is `OllamaClinicalSummaryAgent`, retaining the existing local
Qwen workflow. `HttpClinicalSummaryAgent` lets a separate process implement any
agent framework behind the contract below, without rebuilding CARLOS to switch
implementations. This is a **CARLOS agent API**, not the OpenAI API wire format:
an existing agent needs a small wrapper that translates this request and response.
Its internal workflow may involve multiple models, steps or tools; CARLOS supplies
no database credentials, session tokens, chart-write tools or conversation memory.

A ready-to-run [OpenRouter testing gateway](OPENROUTER.md) implements this contract,
with private key configuration, verified synthetic text checks and bounded memory caching.

## Configure an HTTP agent

Run your gateway on numeric loopback in the same network namespace as CARLOS.
Set these server properties and restart CARLOS:

```properties
clinical.ai_summary_prototype.enabled=true
clinical.ai_summary_generation.enabled=true
clinical.ai_summary_generation.agent=http
clinical.ai_summary_generation.http.port=11435
clinical.ai_summary_generation.http.path=/v1/clinical-summary
clinical.ai_summary_generation.http.name=My agent
clinical.ai_summary_generation.http.timeoutSeconds=600
```

`http.name` is operator-owned provenance displayed alongside the unverified draft.
The browser cannot select agents, endpoints or models. Unknown adapter names fail
closed. Set `clinical.ai_summary_generation.agent=ollama` (the default) to restore
the existing `clinical.ai_summary_generation.ollama.*` settings.

HTTP is deliberately limited to `127.0.0.1`, with proxies and redirects disabled.
The path cannot contain query parameters, credentials or an external URL. Only
HTTP 200 and a completed response are accepted. Configure downstream credentials
inside your gateway, never in CARLOS browser requests or the protocol payload.
Remote gateways, authentication negotiation, streaming and asynchronous jobs are
not implemented in v1. The configured read timeout is 1-1800 seconds (default
600), not an overall workflow deadline. CARLOS permits one generation at a time
across all adapters. The host partitions the full source snapshot into requests of at most
10,000 serialized bytes; transport requests are capped at 60,000 bytes and responses/final generated JSON at 4 MiB.
Each request may contain only a portion of the chart or a consecutive, overlapping
portion of one long source. Use only the supplied text and IDs. The host validates
every response, assembles all claims, and retains the original full sources for review.

**The HTTP gateway is trusted operator code.** Loopback does not guarantee its
downstream inference stays local. Review its providers, tools, retention and
logging before enabling it. All CARLOS adapters remain restricted to the three
complete, checksum-verified NHS synthetic fixtures. This is not authorization for
real-patient inference. The example's classification check alone is not a privacy
or de-identification mechanism; CARLOS performs the actual fixture verification.

## Request v1

CARLOS sends a fixed-length `application/json` POST with exactly these fields:

```json
{
  "contract_version": 1,
  "request_id": "03437b0a-90c8-4f37-9fa8-b0886a4b922f",
  "workflow": "patient-overview",
  "data_classification": "verified-synthetic",
  "instructions": "<contents of generation-prompt.txt>",
  "sources": [
    {
      "id": "note-123",
      "patient_id": "demographic-3003",
      "title": "Signed encounter note (note-123)",
      "date": "2020-01-01",
      "text": "<synthetic note text>"
    }
  ],
  "output_schema": { "type": "object", "...": "full schema supplied by CARLOS" }
}
```

The example schema above is abbreviated; the actual request embeds the complete
[output-schema.json](output-schema.json). Source text is untrusted evidence, not
agent instructions. Use the supplied schema and preserve source IDs exactly.
No existing generated claims, patient context object, fact ledger or validation
findings are sent. Patient identifiers and names can be present in the sources.

## Response v1

Return exactly these four envelope fields, echoing version and request ID:

```json
{
  "contract_version": 1,
  "request_id": "03437b0a-90c8-4f37-9fa8-b0886a4b922f",
  "status": "completed",
  "output": {
    "sections": [
      { "id": "clinical_overview", "title": "Clinical overview", "claim_ids": ["claim-1"] }
    ],
    "claims": [
      { "id": "claim-1", "text": "<source-supported statement>", "source_ids": ["note-123"] }
    ],
    "coverage": [
      { "source_id": "note-124", "status": "reviewed_not_cited", "reason": "Repeats the plan recorded in note-123." }
    ]
  }
}
```

Every claim needs a valid source citation and belongs to exactly one non-empty
section. Sections use one of the five fixed ID/title pairs in the supplied schema:
Clinical overview, Active problems, Medications and allergies, Results and
observations, or Plan and follow-up. Omit empty sections. Coverage must review
every supplied source that no claim cites, exactly once, as `reviewed_not_cited`
or `excluded`. Omit cited sources: the host records those from the citations, so
an agent need not spend output describing a source it used. A `cited` review is
still accepted, and its status always follows the actual claim citations. Return
as many atomic single-paragraph claims as the supplied clinical content requires.
There is no claim-count, character, citation-count or source-count cap. Return zero
claims and sections only when the supplied portion contains no clinical facts.
There are at most five sections. A source that is neither cited nor reviewed is
recorded by the host as unexplained and raised as a validation warning; it does not
fail the draft. Coverage reasons must be source-specific.

## Repair operation (contract version 2, optional)

After the host has applied its own checks it may `POST` to the agent path with
`/repair` appended. The host decides which statements are faulted and whether a
rewrite is accepted; the agent only writes. An agent that does not implement the
operation answers 404, and the host keeps the faulted statements with their
warnings. Request:

```json
{
  "contract_version": 2,
  "request_id": "<uuid>",
  "workflow": "patient-overview",
  "data_classification": "verified-synthetic",
  "instructions": "<the fixed repair instructions the host and agent share>",
  "statements": [
    { "id": "c7", "text": "<the faulted statement>", "source_ids": ["note-123"],
      "problems": ["names a person or gives a patient identifier (Saoirse Keogh); refer to people by role only"] }
  ],
  "sources": [ <only the notes those statements cite, in the generate-request shape> ]
}
```

Response: `contract_version`, `request_id`, `status` (`completed`) and `statements`,
each with `id` and `text`. A statement the agent omits is one it judged to restate
another; the host drops it only if that was the problem raised. The host keeps a
rewrite only if it can no longer fault it and the draft still validates, and gives
it the ID prefix `repaired-`. The synthetic gateway validates the notes against the
committed corpus before any cloud request, as for generation. See
[CONTRACT.md](CONTRACT.md) for all rendering invariants.

Do not return sources, model names, timestamps, patient context, fact ledger or
validation findings. CARLOS rejects extra fields, duplicate JSON keys, wrong
request IDs/versions, non-completed statuses, invalid references, oversized output
malformed responses, identical claim prose or normalized duplicate coverage reasons,
fixture metadata presented as clinical claims, and claims without basic lexical
overlap with their cited evidence. The lexical check recognizes a small fixed set
of common clinical abbreviations and their expanded forms. Agent exception
messages and HTTP error bodies
are not shown to the user. Failure preserves a newly authorized deterministic chart view.
After generation, CARLOS reloads chart authorization and evidence; a changed source
snapshot invalidates the draft. No draft is saved to the chart or session.

Structural validity is not clinical verification. An agent can still fabricate
a claim while citing a valid source; drafts remain explicitly unverified.

## Runnable contract demonstration

```bash
python3 tools/ai-clinical-summary-draft/example_agent.py --port 11435
```

Use the HTTP configuration above with
`clinical.ai_summary_generation.http.name=Contract demo (no AI)`, restart CARLOS,
and generate from a seeded NHS patient's overview. This server only lists source
metadata. **It does not run an AI model or produce a clinical summary.** It proves
the transport, validation and UI connection independently of model latency.

Replace `run_agent(request)` with your agent invocation, returning the same
envelope. The standard-library HTTP server is development-only, not a production
gateway. It does not persist or log payloads. Your implementation should explicitly
bound its tools, costs, execution time and downstream data handling.

To run the existing browser smoke check against this demo, set:

```bash
AI_SUMMARY_AGENT_LABEL='Contract demo (no AI) via agent API v1'
```

alongside the URL, storage state and synthetic demographic environment variables
in [README.md](README.md#verification). Export the variable or prefix the test
command with it. Restore `agent=ollama` and restart to return to actual local AI.

## In-process Java agents

Implement `ClinicalSummaryAgent` (`displayName()` and `generate(JsonNode request)`),
then inject it into `ClinicalSummaryGenerationService`. The service provides an
isolated request copy and validates output before combining it with host evidence.
Register a server-configured implementation explicitly in `ClinicalSummaryAgents`
when using it from the web action. This route requires a rebuild; HTTP does not.
In-process adapters are trusted code and must implement their own execution bounds.

## Result caching

The built-in HTTP adapter does not cache results: protocol v1 does not expose an
immutable revision for the gateway's downstream models, prompts and tools. The
Ollama adapter supports the bounded memory cache documented in README.md. An
in-process Java adapter can opt in with `cacheIdentity()`, returning a stable
revision covering every output-affecting setting and revalidating the backend on
every call. Its default `null` return disables caching. Display names must never
be used as revision identifiers. Existing HTTP gateways require no changes.
