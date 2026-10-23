/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import java.io.IOException;

/** A complete transport response whose model output ran out of tokens; never render it. */
final class ClinicalSummaryOutputLimitException extends IOException {
    ClinicalSummaryOutputLimitException() { super("Incomplete model output"); }
}
