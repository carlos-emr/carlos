# Curated engineering findings

These findings are diagnostic engineering evidence, not clinical validation or
a fairness certification.

## Initial Qwen/Mistral campaign

- Qwen3.5 27B preserved more required structured facts than Mistral Small 3.2
  24B, but repeatedly treated a completed blood-pressure/basic-metabolic-panel
  follow-up as pending.
- Mistral repeatedly omitted the earlier blood pressure and produced malformed
  source-ID arrays for one counterfactual condition.
- Most raw pair-discordance counts represented wording or optional-content
  differences rather than changes to core diagnoses, medications, allergies,
  or results.
- The original substance-use ledger incorrectly treated an intentionally added
  in-remission fact as unsupported. That historical metric must not be used as
  a release result.

## Prompt v3 experiment

- Schema-constrained decoding eliminated malformed JSON in the focused run.
- Both models preserved the phrase and status "in sustained remission" and
  stopped listing the completed follow-up as pending.
- Qwen omitted suspected neuropathy in several counterfactual outputs.
- Mistral leaked accessibility content into the no-N10 baseline and continued
  combining medication dose and frequency.

## Prompt v4 experiment

Prompt v4 adds explicit source-by-source information bundles and a separate
model self-check pass. The model self-check remains advisory: deterministic
validation is the actual acceptance gate. Results are intentionally not
committed while the local experiment is running.
