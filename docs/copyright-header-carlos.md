# CARLOS Project Copyright Header

## For New Files

Use this header for **new files** created as part of the CARLOS project:

```java
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
```

## When Modifying Existing Files

When making **functional changes** (not just namespace changes) to existing files:

1. **Preserve the copyright notice** (required by GPL)
2. **Add a modification notice** after the existing copyright
3. **Update the "written for" section** to acknowledge heritage and current project

### Example - Modified McMaster File:

```java
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */
```

### Example - Modified CRICH/St. Michael's File:

```java
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * [GPL text preserved...]
 *
 * Originally written for Centre for Research on Inner City Health, St. Michael's Hospital.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */
```

## Guidelines

1. **New files**: Use the CARLOS header (GPL2+ by default)
2. **Modified files**: Preserve copyright AND GPL version, update "written for", add modification notice
3. **Namespace-only changes**: Do NOT add modification notices (documented in NOTICE.md)
4. **Never remove**: Existing copyright notices (GPL requirement)
5. **Never change**: The GPL version (GPL2, GPL2+, GPL3, etc.) - preserve exactly as-is
6. **Year**: Use the current year (2026+) for new contributions
