# HylaFax On-Premise Fax Setup

CARLOS EMR can send and receive faxes through an on-premise HylaFax server by selecting **HylaFax On-Premise** in **Administration > Faxes > Configure Fax**.

## Requirements

- A reachable HylaFax server.
- A dedicated HylaFax account with the minimum permissions needed to submit jobs, read job status, and read the configured `recvq` directory.
- For local client mode, the application server must have HylaFax client tools such as `sendfax`, `faxstat`, and `tiff2pdf`.
- For SSH mode, the application server must have an `ssh` client and the HylaFax server must have `sendfax`, `faxstat`, and `tiff2pdf`.
- For SSH mode, pre-provision a dedicated host alias in the application user's SSH config and set `hylafax.ssh.host.alias` in `carlos.properties`. The alias should define `HostName`, `User`, `IdentityFile`, and `IdentitiesOnly yes`; CARLOS EMR uses strict host-key checking.

## Configuration Fields

| Field | Description |
| --- | --- |
| HylaFax Host | Hostname or IP address of the HylaFax server. |
| HylaFax Port | HylaFax client protocol port. Defaults to `4559`. SSH mode uses the configured SSH host alias instead. |
| HylaFax Username | Dedicated HylaFax user for client-mode metadata. SSH mode uses the user configured in the SSH host alias. |
| HylaFax Modem | Optional modem identifier for deployments with multiple fax modems. |
| recvq Path | Absolute path to the HylaFax receive queue, commonly `/var/spool/hylafax/recvq`. |
| Use SSH command execution | Runs HylaFax commands over SSH instead of using local HylaFax client tools. Configure keys through the application server user's SSH config or agent. |

## Inbound Fax Handling

HylaFax stores inbound faxes in `recvq`, usually as TIFF files. CARLOS EMR reads supported files (`.pdf`, `.tif`, `.tiff`) from the configured `recvq` path and converts TIFF files to PDF with `tiff2pdf` before importing them into the document inbox. The existing fax scheduler controls polling; no separate HylaFax scheduler is required.

## Security Notes

- Use a dedicated HylaFax/SSH user rather than a shared administrative account.
- Restrict the HylaFax host to the clinic network or a segmented fax VLAN.
- Manage SSH keys through a dedicated SSH host alias for the application server user; CARLOS EMR does not write decrypted SSH key material to temporary files or target arbitrary admin-supplied SSH hosts.
- Pre-provision and rotate SSH host keys through normal infrastructure management; do not rely on trust-on-first-use for fax infrastructure.
- Do not expose real patient information in command logs or troubleshooting output.
- For air-gapped deployments, install HylaFax client tools and dependencies from trusted internal package repositories.
