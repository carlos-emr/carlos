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
across all adapters, with a 60,000-byte request and 4 MiB response limit.

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
      { "id": "overview", "title": "Overview", "claim_ids": ["claim-1"] }
    ],
    "claims": [
      { "id": "claim-1", "text": "<source-supported statement>", "source_ids": ["note-123"] }
    ],
    "coverage": [
      { "source_id": "note-123", "status": "cited", "reason": "Used in claim-1." }
    ]
  }
}
```

Every claim needs a valid source citation and belongs to exactly one section.
Coverage must account for every supplied source exactly once and match actual
claim citations. Return at least one claim, at most 100 claims, 20 sections and
60 coverage entries. See [CONTRACT.md](CONTRACT.md) for all rendering invariants.

Do not return sources, model names, timestamps, patient context, fact ledger or
validation findings. CARLOS rejects extra fields, duplicate JSON keys, wrong
request IDs/versions, non-completed statuses, invalid references, oversized output
and malformed responses. Agent exception messages and HTTP error bodies are not
shown to the user. Failure preserves a newly authorized deterministic chart view.
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
