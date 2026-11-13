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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPrintedFormReadDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.form.FrmRourke2017Record;
import io.github.carlos_emr.carlos.form.FrmRourke2020Record;
import io.github.carlos_emr.carlos.form.model.FormBooleanValue;
import io.github.carlos_emr.carlos.form.model.FormRourke2017;
import io.github.carlos_emr.carlos.form.model.FormRourke2020;
import io.github.carlos_emr.carlos.form.util.Rourke2017PdfReport;
import io.github.carlos_emr.carlos.form.util.Rourke2020PdfReport;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import net.sf.jasperreports.engine.JRException;
import org.springframework.stereotype.Service;

/**
 * Prints the current revision of a Rourke Baby Record 2017 or 2020 (the latest saved row of the
 * form for the patient) as CARLOS EMR prints it, through the form's fixed Jasper pages
 * ({@link Rourke2017PdfReport}, {@link Rourke2020PdfReport}: bundled DejaVu fonts only), beside
 * the revision's data summary. Earlier revisions stay data summaries only.
 *
 * <p>The form's values are the captured revision row and its captured checkbox rows ({@code
 * form_boolean_value}), read into the form's own entity so {@code toProperties()} fills the pages
 * exactly as the clinician's print does; nothing is read from the form table again. As the chart
 * does, the child's name, date of birth, postal area (FSA) and gestational age come from the
 * patient's captured demographic row. Text with no stored value prints blank. A value the pages
 * cannot print completely (a character DejaVu lacks, text that would be clipped) refuses the
 * printed chart with its category; the data summary still carries every value exactly.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportRourkeChartComposer implements CdsExportDataSummaryOccurrenceService.Printer {
    static final Map<String, String> SUB_CLASSES =
            Map.of(
                    FormRourke2017.FORM_TABLE, "Rourke Baby Record 2017",
                    FormRourke2020.FORM_TABLE, "Rourke Baby Record 2020");
    static final List<Integer> PAGES = List.of(1, 2, 3, 4);
    static final String MAPPING_MISMATCH = "FORM_MAPPING_MISMATCH";

    private final CdsExportPrintedFormReadDao references;

    public CdsExportRourkeChartComposer(CdsExportPrintedFormReadDao references) {
        this.references = Objects.requireNonNull(references);
    }

    @Override
    public boolean prints(CdsExportDataSummaryPlanner.Group group) {
        return group.kind() == CdsExportDataSummaryRegistry.Kind.ENCOUNTER_FORM
                && SUB_CLASSES.containsKey(group.table())
                && !group.detached()
                && group.revisions() > 0
                && group.revision() == group.revisions();
    }

    @Override
    public String readObject() {
        return "_form";
    }

    @Override
    public CdsExportDataSummaryOccurrenceService.Printed print(
            CdsExportDataSummaryPlanner.Group group, CdsExportDataSummaryOccurrenceService.Plan plan)
            throws IOException {
        var root = group.rows().getFirst();
        Properties props = properties(group, plan.demographic());
        var output = new ByteArrayOutputStream();
        try {
            if (FormRourke2017.FORM_TABLE.equals(group.table()))
                Rourke2017PdfReport.write(output, props, PAGES);
            else Rourke2020PdfReport.write(output, props, PAGES);
        } catch (Rourke2020PdfReport.RenderingException refused) {
            throw new CdsExportDataSummaryOccurrenceService.PrintRefusal(refused.reason().name());
        } catch (JRException failed) {
            throw new CdsExportDataSummaryOccurrenceService.PrintRefusal(
                    CdsExportDataSummaryOccurrenceService.RENDER_FAILED);
        }
        var metadata = new LinkedHashMap<String, String>();
        CdsExportDateDto event = date(root.text("formCreated"));
        if (event != null) metadata.put("formCreated", "Reports/EventDateTime");
        String providerNo = root.text("provider_no");
        var provider = providerNo == null || providerNo.isBlank() ? null : references.provider(providerNo);
        String author = provider == null ? null : provider.lastName() + ", " + provider.firstName();
        if (author != null) metadata.put("provider_no", "Reports/SourceAuthorPhysician");
        return new CdsExportDataSummaryOccurrenceService.Printed(
                output.toByteArray(), SUB_CLASSES.get(group.table()), event, author, metadata, List.of());
    }

    /**
     * The properties the form's print fills its pages with: the captured revision through the
     * form's {@code toProperties()}, then the chart's current name, date of birth, gestational age
     * and FSA, as {@code FrmRourke2017Record} and {@code FrmRourke2020Record} set them.
     */
    static Properties properties(CdsExportDataSummaryPlanner.Group group, Map<String, String> demographic)
            throws CdsExportDataSummaryOccurrenceService.PrintRefusal {
        boolean edition2017 = FormRourke2017.FORM_TABLE.equals(group.table());
        var root = group.rows().getFirst();
        var booleans = new HashMap<String, FormBooleanValue>();
        for (var row : group.rows()) {
            if (!"form_boolean_value".equals(row.table())) continue;
            String value = row.text("value");
            // A checkbox with no stored value prints as an empty box (the form's own properties
            // cannot hold it); the data summary still shows it as SQL NULL.
            if (value == null) continue;
            booleans.put(
                    row.text("field_name"),
                    new FormBooleanValue(
                            group.table(),
                            Integer.valueOf(group.id()),
                            row.text("field_name"),
                            !"0".equals(value)));
        }
        Properties props;
        Date startOfGestation;
        if (edition2017) {
            var form = entity(FormRourke2017.class, root);
            form.setBooleanValueMap(booleans);
            props = form.toProperties();
            startOfGestation = form.getStartOfGestation();
        } else {
            var form = entity(FormRourke2020.class, root);
            form.setBooleanValueMap(booleans);
            props = form.toProperties();
            startOfGestation = form.getStartOfGestation();
        }
        props.setProperty("c_pName", CdsExportConsultationLetterComposer.name(demographic));
        Date dob =
                UtilDateUtilities.calcDate(
                        demographic.get("year_of_birth"),
                        demographic.get("month_of_birth"),
                        demographic.get("date_of_birth"));
        props.setProperty("c_birthDate", dob == null ? "" : UtilDateUtilities.DateToString(dob, "dd/MM/yyyy"));
        String gestationalAge = "";
        if (startOfGestation != null && dob != null)
            gestationalAge =
                    (edition2017
                                    ? FrmRourke2017Record.gestationalAgeInWeeks(startOfGestation, dob)
                                    : FrmRourke2020Record.calculateGestationalAgeInWeeks(startOfGestation, dob))
                            + " weeks";
        props.setProperty("c_gestationalAge", gestationalAge);
        String postal = demographic.get("postal");
        if (postal != null && postal.length() >= 6)
            props.setProperty("c_fsa", postal.replaceAll("\\s+", "").substring(0, 3));
        return props;
    }

    /**
     * The form entity holding the captured revision: every persistent field takes the stored
     * value of its column. A column the capture lacks, or a value its field type cannot hold,
     * refuses the print ({@code FORM_MAPPING_MISMATCH}) rather than printing a guess.
     */
    static <T> T entity(Class<T> type, CdsExportDataSummaryPlanner.Row row)
            throws CdsExportDataSummaryOccurrenceService.PrintRefusal {
        try {
            T entity = type.getDeclaredConstructor().newInstance();
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || field.isAnnotationPresent(Transient.class)
                        || Map.class.isAssignableFrom(field.getType())) continue;
                var column = field.getAnnotation(Column.class);
                String name = column != null && !column.name().isEmpty() ? column.name() : field.getName();
                var value = row.values().get(name);
                if (value == null) throw new CdsExportDataSummaryOccurrenceService.PrintRefusal(MAPPING_MISMATCH);
                String text = value.storage() == CdsExportDataSummaryPlanner.Storage.TEXT ? value.text() : null;
                if (value.storage() == CdsExportDataSummaryPlanner.Storage.BINARY)
                    throw new CdsExportDataSummaryOccurrenceService.PrintRefusal(MAPPING_MISMATCH);
                field.setAccessible(true);
                field.set(entity, convert(field.getType(), text));
            }
            return entity;
        } catch (CdsExportDataSummaryOccurrenceService.PrintRefusal refused) {
            throw refused;
        } catch (ReflectiveOperationException | RuntimeException failed) {
            throw new CdsExportDataSummaryOccurrenceService.PrintRefusal(MAPPING_MISMATCH);
        }
    }

    private static Object convert(Class<?> type, String text)
            throws CdsExportDataSummaryOccurrenceService.PrintRefusal {
        // Text with no stored value prints blank, as an empty one does (the 2017 form's own
        // properties already treat it so; the 2020 form's would fail on it). The data summary
        // still says which it was.
        if (type == String.class) return text == null ? "" : text;
        if (text == null) return null;
        if (type == Integer.class) return Integer.valueOf(text);
        // A zero date is what MariaDB stores for "no date"; the chart reads it as none.
        if (text.startsWith("0000-00-00")) return null;
        if (type == Timestamp.class) return text.length() == 10 ? Timestamp.valueOf(text + " 00:00:00") : Timestamp.valueOf(text);
        if (type == Date.class)
            return text.length() == 10 ? java.sql.Date.valueOf(text) : Timestamp.valueOf(text);
        throw new CdsExportDataSummaryOccurrenceService.PrintRefusal(MAPPING_MISMATCH);
    }

    private static CdsExportDateDto date(String value) {
        if (value == null) return null;
        try {
            return new CdsExportDateDto(CdsExportDateDto.Precision.DATE, LocalDate.parse(value).toString());
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }
}
