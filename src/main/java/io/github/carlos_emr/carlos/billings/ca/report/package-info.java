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
/**
 * Report-screen helpers shared by the Ontario and British Columbia billing
 * report pages (for example the unbilled-appointments status filter).
 *
 * <p>Classes here are static and dependency-free; data access stays in the
 * DAO layer and view assembly stays in the province-specific assemblers or
 * JSP gates.</p>
 *
 * @since 2026-09-26
 */
package io.github.carlos_emr.carlos.billings.ca.report;
