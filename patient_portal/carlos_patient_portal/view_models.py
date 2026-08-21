# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project

"""Immutable view state for the portal's browser templates.

These follow the CARLOS `*ViewModel` contract in `docs/architecture/layer-names.md`: one view
model per rendered page, immutable, with no behaviour beyond accessors. Templates read attributes
directly, so a field rename is a type error in the assembler rather than a silently empty cell in
the rendered page — the failure mode an untyped ``dict[str, object]`` context cannot catch.

Assembling these belongs to `presenters.py`; nothing here touches a database session or a request.
"""

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class EmailPasswordRowViewModel:
    """One rendered row of the email-password table."""

    id: int
    subject: str
    provider: str
    sent_at: str
    source_reference: str | None
    is_available: bool


@dataclass(frozen=True, slots=True)
class ProviderFilterOptionViewModel:
    """One entry of the dashboard's provider filter."""

    value: str
    label: str


@dataclass(frozen=True, slots=True)
class EmailPasswordDashboardViewModel:
    """View state for the email-password module of `dashboard.jinja`.

    ``provider_option_values`` is kept alongside ``provider_options`` so the template can decide
    whether a currently-selected provider fell outside the capped option list and therefore needs
    rendering as an extra selected entry.
    """

    rows: tuple[EmailPasswordRowViewModel, ...] = ()
    search: str = ""
    provider: str = ""
    provider_options: tuple[ProviderFilterOptionViewModel, ...] = ()
    provider_option_values: tuple[str, ...] = ()
    provider_options_truncated: bool = False
    date_from: str = ""
    date_to: str = ""
    has_filters: bool = False
    filter_error: str | None = None
    page: int = 1
    total_pages: int = 1
    empty_message: str = ""
    previous_href: str | None = None
    next_href: str | None = None
