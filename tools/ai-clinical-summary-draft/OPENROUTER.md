# OpenRouter testing for the patient summarizer

The OpenRouter gateway plugs into the existing CARLOS HTTP agent. It processes the
whole eligible synthetic chart through the existing full-record pipeline, with no
three-point limit. The default test model is `qwen/qwen3.5-9b`, restricted
to the `deepinfra` provider. Inference runs remotely and does not need local GPU
passthrough. Python 3's standard library is sufficient; no package install or
container rebuild is needed when the current summarizer build is already deployed.

Qwen 3.5 9B is the smallest Qwen 3.5 model listed in OpenRouter's
[model catalog](https://openrouter.ai/api/v1/models) as of 2026-09-14; local
Qwen 3.5 2B is not listed. The selected [DeepInfra endpoint](https://openrouter.ai/api/v1/models/qwen/qwen3.5-9b/endpoints)
advertises structured outputs and appears in the
[ZDR endpoint catalog](https://openrouter.ai/api/v1/endpoints/zdr). Comparing this
hosted 9B model with local 2B changes both model size and inference hardware.

Only the three committed NHS development fixtures are supported. CARLOS verifies
the complete authorized chart; the gateway separately checks outgoing clinical text
against the checksum-verified seed corpus. The gateway rejects other text, changed
instructions/schema and arbitrary metadata before any cloud request or cache lookup.
This testing adapter does not enable cloud inference on real patient records.

## Steps for the existing isolated local setup

Run these commands in **VS Code's terminal inside the dev container**, not Windows
PowerShell. Use the existing summary worktree:

```bash
cd /workspace/.git/codex-worktrees/summary-cache
```

1. Save your key privately:

   ```bash
   python3 tools/ai-clinical-summary-draft/openrouter_agent.py configure
   ```

   Paste the API key at the hidden prompt and press Enter. Nothing is echoed.
   The default configuration lives in the repository's shared Git directory at
   `ai-summary-runtime/openrouter/config.json`, outside tracked files, with mode
   `0600`. The key is never put in browser code, CARLOS properties, command-line
   arguments or shell history. This file survives the current workspace's
   container rebuild; ordinary container filesystem loss is still possible if
   the workspace itself is removed.

2. Check authentication and start the gateway:

   ```bash
   python3 tools/ai-clinical-summary-draft/openrouter_agent.py check && \
     python3 tools/ai-clinical-summary-draft/openrouter_agent.py serve
   ```

   The key check requests account-key metadata without model inference. It does
   not establish model/provider access or credits sufficient for a summary.
   Leave this terminal open. The gateway listens on `127.0.0.1:11437` and prints
   pass duration, cache hits and fixed error categories, without source text,
   generated prose, credentials or upstream error bodies.

3. Open a second container terminal and switch the summarizer:

   ```bash
   cd /workspace/.git/codex-worktrees/summary-cache
   python3 tools/ai-clinical-summary-draft/switch_summary_agent.py openrouter
   ```

   Wait for `Ready with openrouter`. This saves a private properties backup and
   restarts only the existing `ai-summary-runtime/tomcat-8081` Java process. It
   interrupts an in-progress summarizer request, so run it after that request
   finishes or when you are ready to abandon it. The portal on port 8090 and
   the existing 8080-to-8081 forwarder are not restarted.

4. Open [the local CARLOS app](http://127.0.0.1:8080/carlos/) and log in again if
   needed. In this seeded development runtime the login is `carlosdoc`, password
   `carlos2026`, PIN `2026`. Open the **eChart** for `NHSSYN001`, `NHSSYN002` or
   `NHSSYN003`, then **Patient overview → Generate AI draft**. Opening the eChart
   first supplies the program context needed to read the notes. The generated
   draft identifies `OpenRouter / qwen/qwen3.5-9b` as its configured agent.

5. To return to local Qwen, keep the local Ollama server on port 11436 running and use:

   ```bash
   python3 tools/ai-clinical-summary-draft/switch_summary_agent.py ollama
   ```

   You can then stop the OpenRouter terminal with Ctrl+C. Switching agents requires
   the summarizer restart but does not rebuild the app or alter patient records.

The switching helper is intentionally specific to the already-created isolated
runtime. On a different installation, run the gateway and configure your own
CARLOS development properties, then restart that CARLOS instance yourself:

```properties
clinical.ai_summary_prototype.enabled=true
clinical.ai_summary_generation.enabled=true
clinical.ai_summary_generation.agent=http
clinical.ai_summary_generation.http.port=11437
clinical.ai_summary_generation.http.path=/v1/clinical-summary
clinical.ai_summary_generation.http.name=OpenRouter / qwen/qwen3.5-9b
clinical.ai_summary_generation.http.timeoutSeconds=600
```

## Speed, caching and output checks

The gateway uses OpenRouter's [structured-output format](https://openrouter.ai/docs/guides/features/structured-outputs)
and [provider routing](https://openrouter.ai/docs/guides/routing/provider-selection).
It requires parameter support, restricts the provider, disables provider fallback,
and requests `data_collection: deny` and `zdr: true`. A rejected route fails rather
than silently relaxing these settings. These routing settings do not establish
clinical suitability. The default model/provider appeared in OpenRouter's public
endpoint catalog when checked on 2026-09-14; account policies and availability may differ.

Every response must finish normally, identify the configured model and pass the
same Python citation, coverage, section and rendering checks used by the local
runner. CARLOS independently validates the result and rechecks patient access and
chart freshness, including when the gateway returns cached work. A 16,384-token
per-call budget is a transport bound, not a summary-length target: exhausted
responses are discarded and smaller source portions are retried without dropping
text. Each gateway request has a 540-second budget; each upstream call has a
180-second timeout. There are no automatic retries for API errors, rate limiting
or malformed content, which avoids hidden repeated charges.

Validated generation passes are cached **in gateway memory only**, for at most
15 minutes, 128 entries and 16 MiB. Keys cover exact source content and metadata,
model, provider, prompt, schema and generation settings. Request UUIDs are excluded
so unchanged passes can be reused. Credentials/configuration are loaded once per
process; changing them requires stopping and restarting the gateway, clearing all
cached entries. Malformed, refused and incomplete outputs are not cached.

The Java HTTP adapter still opts out of its own result cache: OpenRouter does not
provide the immutable local model digest used by the Ollama adapter. This gateway
cache is short-lived test-result reuse; a provider can update a model behind the
same ID during that window. For independent quality or timing comparisons, stop
the gateway, rerun configure with caching disabled, then check and serve again:

```bash
python3 tools/ai-clinical-summary-draft/openrouter_agent.py configure --cache-seconds 0
```

To choose an explicit different model and compatible provider, rerun configure
with `--model author/model-id --provider provider-slug`, restart the gateway and
rerun the switch command. Configuration changes do not affect an already-running
gateway. Automatic/free model routers are not supported by this configuration.

A successful structural check does not prove medical accuracy or completeness.
The OpenRouter change has automated mock-transport and loopback HTTP tests, but
no authenticated cloud generation or measured end-to-end speed has been run.
The private-key preflight checks authentication without requesting inference. Use the existing evidence panel and full-record evaluation tooling to compare
outputs; larger models are not a guarantee of accuracy.

## Troubleshooting

- **Key rejected (401):** rerun configure and paste the intended API key.
- **Credits or spending limit exhausted (402):** check the key's allowance with the
  account owner; the setup command does not change billing or account limits.
- **No permitted endpoint (404), policy denied (403), or parameter failure (400):**
  check that the key permits the configured model/provider and structured outputs.
  The gateway deliberately does not switch provider/model on its own.
- **Rate limited (429):** wait before retrying. There is no automatic retry loop.
- **Request or generated draft failed validation:** the chart remains unchanged.
  Check that the app uses the matching branch build and original synthetic fixture.
  A generated answer can also fail citation/coverage checks; do not bypass validation.
- **Gateway unavailable:** keep its terminal open; restart `serve` after a container
  restart. `http://127.0.0.1:11437/health` is accessible inside the container and
  reports service/model identity and cache hits without credentials.
- **Switch did not complete:** the gateway must be running before switching. Check
  the isolated Tomcat log under `ai-summary-runtime/tomcat-8081/logs/catalina.out`.
  The helper refuses ambiguous process matches and never kills all Java processes.

Run the automated checks without a cloud key:

```bash
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -p 'test_*.py'
```
