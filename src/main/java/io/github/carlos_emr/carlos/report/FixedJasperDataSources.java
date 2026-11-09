/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.util.List;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.DaySheetRow;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.InvoiceRow;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.ItemRow;

/** Explicit Jasper field contracts; unknown fields fail instead of silently printing blank cells. */
public final class FixedJasperDataSources {
    private FixedJasperDataSources() { }

    /**
     * Exposes the appointment_no, provider_no, appointment_date, start_time, end_time,
     * name, demographic_no, notes, reason, location, resources, type, style, billing,
     * status, createdatetime, updatedatetime, creator and remarks template fields.
     * Unknown or incompatible fields throw JRException when read.
     * @param rows List of DaySheetRow detached appointment rows
     * @return JRDataSource rewindable cursor retaining the supplied row order and SQL NULLs
     */
    public static JRDataSource daySheet(List<DaySheetRow> rows) {
        return source(rows, (row, name) -> switch (name) {
            case "appointment_no" -> row.appointmentNo();
            case "provider_no" -> row.providerNo();
            case "appointment_date" -> row.appointmentDate();
            case "start_time" -> row.startTime();
            case "end_time" -> row.endTime();
            case "name" -> row.name();
            case "demographic_no" -> row.demographicNo();
            case "notes" -> row.notes();
            case "reason" -> row.reason();
            case "location" -> row.location();
            case "resources" -> row.resources();
            case "type" -> row.type();
            case "style" -> row.style();
            case "billing" -> row.billing();
            case "status" -> row.status();
            case "createdatetime" -> row.createdAt();
            case "updatedatetime" -> row.updatedAt();
            case "creator" -> row.creator();
            case "remarks" -> row.remarks();
            default -> throw new JRException("Unknown day-sheet field: " + name);
        });
    }

    /**
     * Exposes id, billing_date, total, paid and items. Each items read creates an
     * independent rewindable cursor over service_code and fee. Unknown or incompatible
     * fields throw JRException when read.
     * @param rows List of InvoiceRow detached invoices with their items
     * @return JRDataSource rewindable invoice cursor preserving SQL NULLs and row order
     */
    public static JRDataSource invoices(List<InvoiceRow> rows) {
        return source(rows, (row, name) -> switch (name) {
            case "id" -> row.id();
            case "billing_date" -> row.billingDate();
            case "total" -> row.total();
            case "paid" -> row.paid();
            case "items" -> items(row.items());
            default -> throw new JRException("Unknown statement field: " + name);
        });
    }

    private static JRDataSource items(List<ItemRow> rows) {
        return source(rows, (row, name) -> switch (name) {
            case "service_code" -> row.serviceCode();
            case "fee" -> row.fee();
            default -> throw new JRException("Unknown statement item field: " + name);
        });
    }

    @FunctionalInterface
    private interface FieldReader<T> { Object read(T row, String name) throws JRException; }

    private static <T> JRDataSource source(List<T> input, FieldReader<T> reader) {
        List<T> rows = List.copyOf(input);
        return new net.sf.jasperreports.engine.JRRewindableDataSource() {
            private int index = -1;
            @Override public boolean next() { return ++index < rows.size(); }
            @Override public void moveFirst() { index = -1; }
            @Override public Object getFieldValue(JRField field) throws JRException {
                Object value = reader.read(rows.get(index), field.getName());
                if (value != null && !field.getValueClass().isInstance(value)) {
                    throw new JRException("Incompatible Jasper field type: " + field.getName());
                }
                return value;
            }
        };
    }
}
