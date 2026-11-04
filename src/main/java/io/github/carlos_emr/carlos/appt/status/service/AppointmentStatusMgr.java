/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.appt.status.service;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;

/**
 * Appointment status administration: listing, enabling/disabling, styling and reset.
 *
 * <p>Style edits (description, colour, icon) are open for every status, including the seeded core
 * statuses whose {@code editable} flag is 0: in CARLOS {@code appointment_status.editable} gates
 * only enabling and disabling (issue #3888), because clinics recolour and rename the core
 * statuses. {@link #changeStatus(int, int)} is the only operation that refuses a status with
 * {@code editable=0}.</p>
 */
public interface AppointmentStatusMgr {

    /**
     * Width of the {@code appointment_status.description} column.
     *
     * @since 2026-11-04
     */
    int DESCRIPTION_MAX_LENGTH = 30;

    /**
     * The images under {@code /images} a status may use as its icon. The day sheet shows a signed
     * or verified appointment with the image's {@code S}- or {@code V}-prefixed copy, so only images
     * that have both copies are listed. Stored values outside the list are kept until changed.
     *
     * @since 2026-11-04
     */
    List<String> ICON_SET = List.of(
            "starbill.gif", "todo.gif", "here.gif", "picked.gif", "empty.gif", "noshow.gif", "cancel.gif",
            "billed.gif", "thumb.png",
            "1.gif", "2.gif", "3.gif", "4.gif", "5.gif", "6.gif", "7.gif", "8.gif",
            "9.gif", "10.gif", "11.gif", "12.gif", "13.gif", "14.gif", "15.gif", "16.gif");

    public List<AppointmentStatus> getAllStatus();

    public List<AppointmentStatus> getAllActiveStatus();

    public AppointmentStatus getStatus(int ID);

    /**
     * Enables or disables a status whose {@code editable} flag is 1.
     *
     * @param ID int the appointment_status id
     * @param iActive int 1 to enable, 0 to disable
     * @return boolean true when saved; false when there is no such status or its
     *         {@code editable} flag is 0
     * @throws AppointmentStatusStyleException if {@code iActive} is neither 0 nor 1
     */
    public boolean changeStatus(int ID, int iActive);

    /**
     * Renames a status. Applies to every status, whatever its {@code editable} flag.
     *
     * @param id int the appointment_status id
     * @param description String the new description; surrounding whitespace is trimmed
     * @return boolean true when saved, false when there is no such status
     * @throws AppointmentStatusStyleException if the trimmed description is blank or longer than
     *         {@link #DESCRIPTION_MAX_LENGTH}
     * @since 2026-11-04
     */
    public boolean updateDescription(int id, String description);

    /**
     * Sets a status's background colour. Applies to every status, whatever its {@code editable}
     * flag. Replacing a legacy, non-hexadecimal stored colour is an explicit save of this field.
     *
     * @param id int the appointment_status id
     * @param colour String the new colour as {@code #rrggbb}; surrounding whitespace is trimmed
     * @return boolean true when saved, false when there is no such status
     * @throws AppointmentStatusStyleException if the colour is not {@code #rrggbb}
     * @since 2026-11-04
     */
    public boolean updateColour(int id, String colour);

    /**
     * Sets a status's icon. Applies to every status, whatever its {@code editable} flag.
     *
     * @param id int the appointment_status id
     * @param icon String the new icon, one of {@link #ICON_SET}
     * @return boolean true when saved, false when there is no such status
     * @throws AppointmentStatusStyleException if the icon is not in {@link #ICON_SET}
     * @since 2026-11-04
     */
    public boolean updateIcon(int id, String icon);

    public int checkStatusUsuage(List<AppointmentStatus> allStatus);

    /**
     * Restores every seeded status's description, colour and icon, matched by status code rather
     * than row id because ids differ between installs. Whether a status is active is left alone.
     */
    public void reset();
}
