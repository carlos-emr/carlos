/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.List;

/** A bounded page; the position describes the last returned, authorized row, never a hidden row. */
public record AppointmentUpdatePage<T>(List<T> items, boolean hasMore, Long updatedMillis, Integer afterId, Long nextUpdatedMillis) {
    public AppointmentUpdatePage { items = List.copyOf(items); }
}
