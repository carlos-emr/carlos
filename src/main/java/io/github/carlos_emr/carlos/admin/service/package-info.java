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
 * Transactional services behind administration pages.
 *
 * <p>Each class owns one administrative write that spans more than one DAO, so the legacy JSP
 * or helper that collects the form keeps only validation and messages, and the rows the write
 * touches commit or roll back together. Authorization stays with the Struts gate in front of
 * the page; services here trust their caller to have checked it.</p>
 *
 * @since 2026-10-15
 */
package io.github.carlos_emr.carlos.admin.service;
