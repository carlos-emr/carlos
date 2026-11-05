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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;

/**
 * Creates a measurement group together with its first measurement types, as one write.
 *
 * <p>A group is a {@code measurementGroupStyle} row (its name and style sheet) plus one
 * {@code measurementGroup} row per member type. Every chart's measurement menu is built from
 * the style rows alone, so a style row without members is an entry that opens an empty entry
 * form. Customize Measurements used to write the style row as soon as a name was entered and
 * add the members on a later page; when that page failed (issue #3335) or the administrator
 * closed it, the empty group stayed in every chart. Defining a name now writes nothing; this
 * service writes the style row and the members in one transaction once types are chosen, so
 * a group is either complete or absent.</p>
 *
 * <p>The duplicate-name check and the inserts share the transaction but are not serialized
 * against a concurrent create of the same name: neither table has a unique key on the name.
 * Group creation is an administrator-only configuration task, so that window is accepted
 * rather than adding a schema constraint to a release line.</p>
 *
 * @since 2026-10-15
 */
@Service
public class MeasurementGroupCreationService {

    /** Longest name {@code measurementGroup.name} and {@code measurementGroupStyle.groupName} hold. */
    public static final int MAX_GROUP_NAME_LENGTH = 100;

    /**
     * The characters the Define form has always accepted ({@code EctValidation.regCharacterExp}:
     * word characters, whitespace, comma, period, question mark), minus the control characters
     * that {@code \s} also admits. The pages that later edit or delete a group refuse names
     * with control characters, so accepting one here would create a group nobody can manage.
     */
    private static final Pattern GROUP_NAME = Pattern.compile("[\\w ,.?]+");

    /** Result of {@link #create}. Only {@link #CREATED} wrote anything. */
    public enum Outcome {
        CREATED,
        INVALID_NAME,
        NO_TYPES,
        DUPLICATE_NAME
    }

    private final MeasurementGroupStyleDao styleDao;
    private final MeasurementGroupDao groupDao;

    public MeasurementGroupCreationService(MeasurementGroupStyleDao styleDao, MeasurementGroupDao groupDao) {
        this.styleDao = styleDao;
        this.groupDao = groupDao;
    }

    /**
     * Whether {@code groupName} may name a new group: not blank, at most
     * {@value #MAX_GROUP_NAME_LENGTH} characters, and only letters, digits, underscore, space,
     * comma, period and question mark.
     *
     * @param groupName the submitted name; may be {@code null}
     * @return {@code true} when the name is acceptable
     */
    public static boolean isValidGroupName(String groupName) {
        return groupName != null
                && !groupName.isBlank()
                && groupName.length() <= MAX_GROUP_NAME_LENGTH
                && GROUP_NAME.matcher(groupName).matches();
    }

    /**
     * Parses the style-sheet choice of the Define form. The form's empty option, and anything
     * that is not a non-negative integer, mean "no style sheet" ({@code 0}), which is what the
     * Define step has always stored for them.
     *
     * @param styleSheet the submitted {@code cssID}; may be {@code null}
     * @return the style-sheet id, or {@code 0}
     */
    public static int parseCssId(String styleSheet) {
        if (styleSheet == null || styleSheet.isBlank()) {
            return 0;
        }
        try {
            int cssId = Integer.parseInt(styleSheet.trim());
            return cssId < 0 ? 0 : cssId;
        } catch (NumberFormatException _) {
            return 0;
        }
    }

    /**
     * Whether a group of this name is already defined. Matching uses the database collation,
     * which on MariaDB ignores case: two groups whose names differ only in case would share
     * their {@code measurementGroup} rows, because those are looked up by name the same way.
     *
     * @param groupName the name to look up
     * @return {@code true} when a style row with this name exists
     */
    @Transactional(readOnly = true)
    public boolean groupExists(String groupName) {
        return hasStyleRow(groupName);
    }

    /**
     * Creates the group and its members, or writes nothing.
     *
     * <p>Blank type names are ignored and repeats are stored once. The type names are not
     * checked against {@code measurementType}: the page only offers existing types, and the
     * group editor has never checked them either.</p>
     *
     * @param groupName the new group's name
     * @param cssId the style sheet, {@code 0} for none
     * @param typeDisplayNames the display names of the member types, in the order chosen
     * @return what happened; only {@link Outcome#CREATED} wrote rows
     */
    @Transactional
    public Outcome create(String groupName, int cssId, Collection<String> typeDisplayNames) {
        if (!isValidGroupName(groupName)) {
            return Outcome.INVALID_NAME;
        }
        List<String> types = distinctTypeNames(typeDisplayNames);
        if (types.isEmpty()) {
            return Outcome.NO_TYPES;
        }
        // Checked inside this method's transaction, not through groupExists(): a call on `this`
        // bypasses the Spring proxy, so that method's own transaction settings would not apply.
        if (hasStyleRow(groupName)) {
            return Outcome.DUPLICATE_NAME;
        }

        MeasurementGroupStyle style = new MeasurementGroupStyle();
        style.setGroupName(groupName);
        style.setCssId(cssId);
        styleDao.persist(style);

        for (String type : types) {
            MeasurementGroup member = new MeasurementGroup();
            member.setName(groupName);
            member.setTypeDisplayName(type);
            groupDao.persist(member);
        }
        return Outcome.CREATED;
    }

    private boolean hasStyleRow(String groupName) {
        return !styleDao.findByGroupName(groupName).isEmpty();
    }

    private static List<String> distinctTypeNames(Collection<String> typeDisplayNames) {
        Set<String> types = new LinkedHashSet<>();
        if (typeDisplayNames != null) {
            for (String type : typeDisplayNames) {
                if (type != null && !type.isBlank()) {
                    types.add(type);
                }
            }
        }
        return new ArrayList<>(types);
    }
}
