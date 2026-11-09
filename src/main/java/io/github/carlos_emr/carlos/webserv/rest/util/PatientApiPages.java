/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Authorized offsets and totals from bounded ordered queries in a request-owned read snapshot. */
public final class PatientApiPages {
    private static final int BATCH = 500;
    private PatientApiPages() { }
    public record Page<T>(List<T> rows, int total) {
        public Page { rows = List.copyOf(rows); }
    }
    /** A zero limit retains the legacy unbounded response; raw queries remain bounded. */
    public static <T> Page<T> read(LoggedInInfo actor, SecurityInfoManager permissions, String module,
            int offset, int limit, BiFunction<Integer, Integer, List<T>> fetch, Function<T, Integer> patientId,
            Function<T, Object> identity) {
        if (offset < 0 || limit < 0 || limit > io.github.carlos_emr.carlos.commn.dao.AbstractDao.MAX_LIST_RETURN_SIZE) throw new BadRequestException("Invalid page position or size");
        var selected = new ArrayList<T>(); int rawOffset=0, readable=0; Object previousLast=null;
        while (true) {
            List<T> raw=fetch.apply(rawOffset, BATCH);
            if (raw==null || raw.size()>BATCH) throw new IllegalStateException("Invalid patient discovery batch");
            if (raw.isEmpty()) break;
            T tail=raw.getLast();
            if (tail==null) throw new IllegalStateException("Patient discovery row has no identity");
            Object last=identity.apply(tail);
            if (last==null || Objects.equals(last, previousLast)) throw new IllegalStateException("Patient discovery did not advance");
            previousLast=last;
            var decisions=new HashMap<Integer, Boolean>();
            for (T row : raw) {
                if (row==null || identity.apply(row)==null) throw new IllegalStateException("Patient discovery row has no identity");
                Integer patient=patientId.apply(row);
                if (patient==null || patient<=0) throw new IllegalStateException("Patient discovery row has no patient identity");
                boolean allowed=decisions.computeIfAbsent(patient, id -> actor!=null
                        && permissions.hasPrivilegeReadOnly(actor, module, "r", id.toString())
                        && permissions.isAllowedAccessToPatientRecord(actor, id));
                if (allowed) {
                    if (readable>=offset && (limit==0 || selected.size()<limit)) selected.add(row);
                    readable=Math.incrementExact(readable);
                }
            }
            rawOffset=Math.addExact(rawOffset, raw.size());
            if (raw.size()<BATCH) break;
        }
        return new Page<>(selected, readable);
    }
}
