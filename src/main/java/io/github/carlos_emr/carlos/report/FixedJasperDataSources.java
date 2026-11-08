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

    public static JRDataSource daySheet(List<DaySheetRow> rows) {
        return source(rows, (row, name) -> switch (name) {
            case "appointment_no" -> row.appointment_no();
            case "provider_no" -> row.provider_no();
            case "appointment_date" -> row.appointment_date();
            case "start_time" -> row.start_time();
            case "end_time" -> row.end_time();
            case "name" -> row.name();
            case "demographic_no" -> row.demographic_no();
            case "notes" -> row.notes();
            case "reason" -> row.reason();
            case "location" -> row.location();
            case "resources" -> row.resources();
            case "type" -> row.type();
            case "style" -> row.style();
            case "billing" -> row.billing();
            case "status" -> row.status();
            case "createdatetime" -> row.createdatetime();
            case "updatedatetime" -> row.updatedatetime();
            case "creator" -> row.creator();
            case "remarks" -> row.remarks();
            default -> throw new JRException("Unknown day-sheet field: " + name);
        });
    }

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
