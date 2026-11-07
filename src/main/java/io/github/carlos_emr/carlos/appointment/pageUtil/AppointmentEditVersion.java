/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import io.github.carlos_emr.carlos.commn.model.Appointment;

/** A form's original record state; legacy status writers do not consistently update timestamps. */
public final class AppointmentEditVersion {
    public static final String PARAMETER = "appointmentEditVersion";

    private AppointmentEditVersion() { }

    public static String of(Appointment appointment, String mcNumber) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Object[] values = {appointment.getId(), appointment.getProviderNo(), appointment.getAppointmentDate(),
                    appointment.getStartTime(), appointment.getEndTime(), appointment.getName(),
                    appointment.getDemographicNo(), appointment.getProgramId(), appointment.getNotes(),
                    appointment.getReason(), appointment.getLocation(), appointment.getResources(),
                    appointment.getType(), appointment.getStyle(), appointment.getBilling(), appointment.getStatus(), appointment.getImportedStatus(),
                    appointment.getCreateDateTime(), appointment.getUpdateDateTime(), appointment.getCreator(),
                    appointment.getLastUpdateUser(), appointment.getRemarks(), appointment.getUrgency(),
                    appointment.getReasonCode(), appointment.getCreatorSecurityId(), appointment.getBookingSource(),
                    mcNumber};
            for (Object value : values) {
                if (value == null) {
                    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
                } else {
                    String text = value instanceof Date date ? Long.toString(date.getTime()) : value.toString();
                    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                    digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", e);
        }
    }
}
