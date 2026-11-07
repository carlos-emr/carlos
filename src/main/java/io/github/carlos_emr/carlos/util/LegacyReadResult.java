/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.util;

import java.util.List;
import java.util.function.Supplier;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/** Adapts typed reads for old callers whose absence/error sentinel is null. */
public final class LegacyReadResult {
    private LegacyReadResult() { }

    public static <T> T orNull(Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Legacy clinical lookup failed (causeType={})", e.getClass().getName());
            return null;
        }
    }

    public static <T> List<T> listOrNull(Supplier<List<T>> read) {
        List<T> result = orNull(read);
        return result == null || result.isEmpty() ? null : result;
    }
}
