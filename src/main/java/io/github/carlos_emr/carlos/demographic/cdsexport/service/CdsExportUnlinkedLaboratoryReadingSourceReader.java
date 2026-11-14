/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportUnlinkedLaboratoryReadingDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Counts, inside the patient's capture snapshot, the laboratory readings that are not exported
 * because the HL7 report they were filed from is no longer routed to the chart (owner decision
 * 2026-11-14): an HL7 result {@code Hl7textResultsData.fileMeasurements} filed as a measurement
 * whose {@code lab_no} names no HL7 report of the patient, for example after the lab was unlinked
 * or moved to another patient. Care capture never reads a laboratory-filed reading and the
 * LaboratoryResults path exports only the reports routed to the chart, so neither path carries
 * these; they used to hold the patient ({@code CDS_EXPORT_NATIVE_CARE_LABORATORY_RESULT_UNROUTED}).
 *
 * <p>One captured row per test name and observation date, with the number of readings; no result
 * value is read. The rows reach only the encrypted package ReadMe, which lists them for the
 * patient as {@code Patient records not exported: <label>: <n>} ({@link #label}). Captured with
 * LaboratoryResults, under its rights (sensitive results included): without LaboratoryResults
 * every laboratory-filed reading is deliberately out, as the operator chose.
 *
 * @since 2026-11-14
 */
@Service
public class CdsExportUnlinkedLaboratoryReadingSourceReader implements CdsExportNativeSourceReader {
    public static final String TABLE = "unlinked_laboratory_readings";

    /** The ReadMe label's fixed start; the test name and date follow in parentheses. */
    static final String LABEL = "Laboratory readings whose report is not linked to this chart";

    private final MeasurementDao source;

    public CdsExportUnlinkedLaboratoryReadingSourceReader(MeasurementDao source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public String table() {
        return TABLE;
    }

    @Override
    public Set<String> columns() {
        return Set.of("observed_date", "reading_count", "test_name");
    }

    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.LABORATORY_RESULTS);
    }

    /** A reading's test name can name a blocked (sensitive) result. */
    @Override
    public boolean requiresSensitiveLaboratoryRead() {
        return true;
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        try (var input =
                getClass().getResourceAsStream("/cds-export/source-schema/" + TABLE + ".json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING");
            return new ObjectMapper().readValue(input, CdsExportTableSchemaDto.class);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    @Override
    public long count(int patient) {
        return count(patient, () -> {});
    }

    @Override
    public long count(int patient, Runnable checkpoint) {
        checkpoint.run();
        return source.findCdsExportUnlinkedLaboratoryReadings(patient).size();
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        capture(patient, output, () -> {});
    }

    @Override
    public void capture(
            int patient, CdsExportNativeCaptureComposer.Writer output, Runnable checkpoint)
            throws IOException {
        var readings = source.findCdsExportUnlinkedLaboratoryReadings(patient);
        for (int ordinal = 0; ordinal < readings.size(); ordinal++) {
            checkpoint.run();
            var reading = readings.get(ordinal);
            output.row(
                    CdsExportPatientTableSourceReader.key(ordinal + 1L),
                    row -> {
                        row.text("observed_date", reading.observed());
                        row.text("reading_count", Long.toString(reading.readings()));
                        row.text("test_name", reading.testName());
                    });
        }
    }

    /**
     * The package ReadMe label of one test name and date: {@value #LABEL}, then the test name and
     * the date in parentheses, each named "no test name" or "no date" when none is stored.
     */
    static String label(CdsExportUnlinkedLaboratoryReadingDto reading) {
        return LABEL
                + " ("
                + (reading.testName().isEmpty() ? "no test name" : reading.testName())
                + ", "
                + (reading.observed().isEmpty() ? "no date" : reading.observed())
                + ")";
    }

    /**
     * The ReadMe lines of the captured readings, by label: one line per test name and date, in
     * the captured order. Equal labels (a test name that reads like the "no test name" mark) add
     * up.
     */
    static Map<String, Long> notExported(List<CdsExportUnlinkedLaboratoryReadingDto> readings) {
        var result = new LinkedHashMap<String, Long>();
        for (var reading : readings) result.merge(label(reading), reading.readings(), Math::addExact);
        return result;
    }
}
