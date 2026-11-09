/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.on;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Read-only source observations, independent of the clinic's measurement extraction filter.
 * @since 2026-11-09
 */
public final class Hl7SourceResults {
    private Hl7SourceResults() { }

    public static List<Map<String, Serializable>> read(Integer patient) {
        if (patient == null || patient <= 0) return List.of();
        Set<String> visible = new LinkedHashSet<>();
        for (var route : SpringUtils.getBean(PatientLabRoutingDao.class).findByDemographicAndLabType(patient, "HL7")) {
            // Do not trust an inconsistent DAO result to widen patient visibility.
            if (patient.equals(route.getDemographicNo()) && "HL7".equals(route.getLabType())) {
                visible.add(Integer.toString(route.getLabNo()));
            }
        }
        return read(visible, Hl7textResultsData::getMatchingLabs, Factory::getHandler);
    }

    static List<Map<String, Serializable>> read(Set<String> visible, Function<String, String> chainLookup,
                                               Function<String, MessageHandler> handlers) {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Serializable>> observations = new ArrayList<>();
        for (String report : visible) {
            if (seen.contains(report)) continue;
            String chain = chainLookup.apply(report);
            List<String> versions = chain == null ? List.of() : Arrays.asList(chain.split(","));
            if (!versions.contains(report)) throw new IllegalStateException("Source version chain is unavailable");
            String current = report;
            for (String version : versions) {
                seen.add(version);
                if (visible.contains(version)) current = version;
            }
            MessageHandler handler = handlers.apply(current);
            if (handler == null) throw new IllegalStateException("Source observations are unavailable");
            for (int obr = 0; obr < handler.getOBRCount(); obr++) {
                for (int obx = 0; obx < handler.getOBXCount(obr); obx++) {
                    String status = handler.getOBXResultStatus(obr, obx);
                    if ("DNS".equals(status) || "P".equals(status) || "Pending".equals(status)
                            || handler.isTestResultBlocked(obr, obx)) continue;
                    String result = handler.getOBXResult(obr, obx);
                    if (result == null || result.isEmpty() || "DNR".equals(result)) continue;
                    Map<String, Serializable> row = new HashMap<>();
                    row.put("lab_no", current);
                    row.put("identifier", handler.getOBXIdentifier(obr, obx));
                    row.put("testName", handler.getOBXName(obr, obx));
                    row.put("result", result);
                    row.put("abn", handler.getOBXAbnormalFlag(obr, obx));
                    row.put("range", handler.getOBXReferenceRange(obr, obx));
                    row.put("units", handler.getOBXUnits(obr, obx));
                    row.put("collDate", handler.getTimeStamp(obr, obx));
                    row.put("sourceObservation", obr + "-" + obx);
                    observations.add(row);
                }
            }
        }
        return observations;
    }
}
