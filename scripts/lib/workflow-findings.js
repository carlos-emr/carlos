/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/** Preserve each finding; only a genuinely single failure may match a step pin. */
function throwFindings(findings) {
  if (!findings.length) return;
  const error = new AggregateError(findings, findings.map(item => item.failedStep + ': ' + item.message).join('; '));
  error.failedStep = findings.length === 1 ? findings[0].failedStep : `${findings.length} independent findings`;
  throw error;
}
module.exports = {throwFindings};
