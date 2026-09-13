# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""carlos-ctl for the Debian/Ubuntu single-host CARLOS EMR deployment.

This package is the .deb counterpart of carlos-podman's ``carlos_ctl``: same
command name, same language, and the same verb names wherever the two
deployments share a concept (``check``, ``db``, ``db-migrate``, ``db-users``,
``db-dump``, ``backup full|verify|status``, ``cert-renew``, ``rotate``,
``status``) — an operator moving between a podman site and a single-VM site
should not have to relearn the tool. Verbs that only make sense here
(``init-config``, ``waf``, ``destroy-data``, service lifecycle) live in their
own namespace and collide with nothing over there.

The module layout deliberately mirrors carlos-podman's carlos_ctl
(cli/util/config/dbops/validate) so that a future shared core — one package,
two runner backends — is a refactor, not a rewrite. Until then the two trees
are separate on purpose: this one drives systemd services and a host MariaDB
over the unix socket, that one drives rootless podman pods, and a premature
abstraction over those would be worse than the duplication.
"""
