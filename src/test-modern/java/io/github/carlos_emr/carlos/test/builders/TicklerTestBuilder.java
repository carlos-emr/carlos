/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.test.builders;

import io.github.carlos_emr.carlos.commn.model.Tickler;

import java.util.Date;

/**
 * Test data builder for {@link Tickler} entities.
 *
 * <p>Provides deterministic defaults for tickler/reminder test data.
 * Uses fixed dates and standard provider/demographic IDs for reproducibility.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Tickler tickler = TicklerTestBuilder.aTickler().build();
 * Tickler completed = TicklerTestBuilder.aTickler()
 *     .withMessage("Follow up on lab results")
 *     .completed()
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class TicklerTestBuilder {

    private Integer demographicNo = 1;
    private Integer programId = 10016;
    private String message = "Test tickler message";
    private Tickler.STATUS status = Tickler.STATUS.A;
    private Date serviceDate = new Date(1704067200000L); // 2024-01-01
    private String creator = "999990";
    private String taskAssignedTo = "999990";
    private Tickler.PRIORITY priority = Tickler.PRIORITY.Normal;
    private String categoryId;

    private TicklerTestBuilder() {
    }

    /**
     * Creates a new builder with standard tickler defaults.
     *
     * @return a new builder instance
     */
    public static TicklerTestBuilder aTickler() {
        return new TicklerTestBuilder();
    }

    public TicklerTestBuilder withDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
        return this;
    }

    public TicklerTestBuilder withProgramId(Integer programId) {
        this.programId = programId;
        return this;
    }

    public TicklerTestBuilder withMessage(String message) {
        this.message = message;
        return this;
    }

    public TicklerTestBuilder withStatus(Tickler.STATUS status) {
        this.status = status;
        return this;
    }

    public TicklerTestBuilder withServiceDate(Date serviceDate) {
        this.serviceDate = serviceDate;
        return this;
    }

    public TicklerTestBuilder withCreator(String creator) {
        this.creator = creator;
        return this;
    }

    public TicklerTestBuilder withTaskAssignedTo(String taskAssignedTo) {
        this.taskAssignedTo = taskAssignedTo;
        return this;
    }

    public TicklerTestBuilder withPriority(Tickler.PRIORITY priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Creates a completed tickler.
     *
     * @return this builder
     */
    public TicklerTestBuilder completed() {
        this.status = Tickler.STATUS.C;
        return this;
    }

    /**
     * Creates a deleted tickler.
     *
     * @return this builder
     */
    public TicklerTestBuilder deleted() {
        this.status = Tickler.STATUS.D;
        return this;
    }

    /**
     * Creates a high-priority tickler.
     *
     * @return this builder
     */
    public TicklerTestBuilder highPriority() {
        this.priority = Tickler.PRIORITY.High;
        return this;
    }

    public Tickler build() {
        Tickler t = new Tickler();
        t.setDemographicNo(demographicNo);
        t.setProgramId(programId);
        t.setMessage(message);
        t.setStatus(status);
        t.setServiceDate(serviceDate);
        t.setCreator(creator);
        t.setTaskAssignedTo(taskAssignedTo);
        t.setPriority(priority);
        return t;
    }
}
