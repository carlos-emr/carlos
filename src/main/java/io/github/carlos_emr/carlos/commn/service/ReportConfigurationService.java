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
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.ReportConfigDao;
import io.github.carlos_emr.carlos.commn.dao.ReportTableFieldCaptionDao;
import io.github.carlos_emr.carlos.commn.model.ReportConfig;
import io.github.carlos_emr.carlos.commn.model.ReportTableFieldCaption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction boundaries for report editor operations that perform several writes.
 * The report DAOs use REQUIRED and join this transaction; none uses REQUIRES_NEW.
 *
 * @since 2026-11-02
 */
@Service
@Transactional
public class ReportConfigurationService {
    private final ReportConfigDao configurations;
    private final ReportTableFieldCaptionDao captions;

    /**
     * Creates the report editor transaction service.
     * @param configurations ReportConfigDao for configuration reads and writes
     * @param captions ReportTableFieldCaptionDao for caption reads and writes
     */
    public ReportConfigurationService(ReportConfigDao configurations, ReportTableFieldCaptionDao captions) {
        this.configurations = configurations;
        this.captions = captions;
    }

    /**
     * Assigns an order across saved variants and persists the generated identifier.
     * @param configuration ReportConfig to persist with its next report order
     */
    public void insertWithOrder(ReportConfig configuration) {
        configuration.setOrderNo(configurations.nextOrder(configuration.getReportId()));
        configurations.persist(configuration);
    }

    /**
     * Deletes every matching duplicate as one operation.
     * @param reportId int owning report identifier
     * @param name String field name
     * @param caption String field caption
     * @param tableName String source table name
     * @param save String saved variant name
     */
    public void deleteMatching(int reportId, String name, String caption, String tableName, String save) {
        for (ReportConfig r : configurations.findByReportIdAndNameAndCaptionAndTableNameAndSave(
                reportId, name, caption, tableName, save)) {
            configurations.remove(r.getId());
        }
    }

    /**
     * Shifts the saved variant and removes the original row once, including an empty target range.
     * @param reportId int owning report identifier
     * @param save String saved variant name
     * @param id int configuration identifier to remove
     * @param newPosition int first order number to shift
     * @throws IllegalArgumentException if the configuration is missing or belongs to another report or variant
     */
    public void shiftAndRemove(int reportId, String save, int id, int newPosition) {
        ReportConfig original = configurations.find(id);
        if (original == null || original.getReportId() != reportId
                || !java.util.Objects.equals(original.getSave(), save)) {
            throw new IllegalArgumentException("Report configuration does not belong to this saved report");
        }
        for (ReportConfig r : configurations.findByReportIdAndSaveAndGtOrderNo(reportId, save, newPosition)) {
            if (r.getId() != id) {
                r.setOrderNo(r.getOrderNo() + 1);
                configurations.merge(r);
            }
        }
        configurations.remove(id);
    }

    /**
     * Inserts a caption or updates all legacy duplicates in one transaction.
     * @param table String source table name
     * @param name String field name
     * @param caption String display caption
     */
    public void saveCaption(String table, String name, String caption) {
        var existing = captions.findByTableNameAndName(table, name);
        if (existing.isEmpty()) {
            ReportTableFieldCaption r = new ReportTableFieldCaption();
            r.setTableName(table);
            r.setName(name);
            r.setCaption(caption);
            captions.persist(r);
        } else {
            for (ReportTableFieldCaption r : existing) {
                r.setCaption(caption);
                captions.merge(r);
            }
        }
    }

    /**
     * Updates all existing caption duplicates without inserting a missing caption.
     * @param table String source table name
     * @param name String field name
     * @param caption String display caption
     */
    public void updateCaption(String table, String name, String caption) {
        for (ReportTableFieldCaption r : captions.findByTableNameAndName(table, name)) {
            r.setCaption(caption);
            captions.merge(r);
        }
    }
}
