# Postfix Mail Capture (Development)

## Overview

The devcontainer includes a local Postfix SMTP server for testing CARLOS email
functionality. In development, Postfix accepts mail on `localhost:25` and routes
**every** accepted message to a local capture file. It does **not** deliver
messages externally.

- **Capture file:** `/var/log/carlos-mail-capture.eml`
- **Postfix log:** `/var/log/mail.log`

The capture file holds the full raw MIME message — headers, body, and
base64-encoded attachments — so you can verify exactly what CARLOS generated.

## Quick Start

Start Postfix:

```bash
mail start
```

Watch captured emails as they arrive:

```bash
mail capture
```

Send a sample message through Postfix:

```bash
mail test
```

Clear captured messages:

```bash
mail clear
```

## How It Works

1. CARLOS sends email to `localhost:25` (see the seeded `emailConfig` row:
   `SMTP` / `LOCAL` / `{"host":"localhost","port":"25"}`).
2. Postfix accepts the connection from localhost only.
3. Postfix routes all recipients to the `devcapture` pipe transport.
4. The pipe writes the raw MIME message to `/var/log/carlos-mail-capture.eml`.
5. No message is delivered to the public internet.

Outbound delivery is blocked by defense in depth: a `transport_maps` regexp map
routes every recipient to `devcapture`, and `default_transport`,
`relay_transport`, and `local_transport` are all overridden to `devcapture` so
no accepted message can fall back to Postfix's normal `smtp` transport.
`inet_interfaces=loopback-only` keeps port 25 reachable only from inside the
container.

## Commands

| Command | Description |
|---------|-------------|
| `mail start` | Start Postfix |
| `mail stop` | Stop Postfix |
| `mail restart` | Restart Postfix |
| `mail status` | Show Postfix status and capture-file location |
| `mail log` | Tail `/var/log/mail.log` |
| `mail capture` | Tail `/var/log/carlos-mail-capture.eml` |
| `mail show` | Print captured emails |
| `mail clear` | Clear captured emails |
| `mail test` | Send a sample message through local Postfix |

## Testing CARLOS Email

1. Start Postfix: `mail start`
2. Open a second terminal and tail the capture file: `mail capture`
3. In CARLOS, trigger an email action (provider-to-patient email, eform, etc.).
4. Confirm the message appears in `/var/log/carlos-mail-capture.eml`.
5. Check CARLOS email logs / admin status if needed.

## Privacy Warning

The capture file contains full raw email content. For provider-to-patient email
flows it may include PHI-like test data, encrypted content, and attachments.

- **Do not commit the capture file.**
- Clear it with `mail clear` when finished.
- Use fake/test patients and fake recipient addresses.
- Treat `/var/log/carlos-mail-capture.eml` as sensitive.

## Troubleshooting

### Connection Refused on localhost:25

Postfix is not running. CARLOS will report
`Mail server connection failed. Couldn't connect to host, port: localhost, 25`.

```bash
mail start
mail status
```

### No Captured Email

Check the Postfix log:

```bash
mail log
```

Send a test message and watch the capture file:

```bash
mail test
mail capture
```

### Verify Routing Is Safe

Inside the devcontainer:

```bash
postconf -n | grep transport
postconf -M | grep devcapture
```

Expected: `transport_maps` points at `/etc/postfix/carlos-transport-regexp`,
`default_transport`/`relay_transport`/`local_transport` are `devcapture:`, and a
`devcapture` pipe service is present in `master.cf`.

## Technical Details

- **Port:** 25 (localhost only, not exposed externally)
- **Config:** `/etc/postfix/main.cf`, `/etc/postfix/master.cf`
- **Transport map:** `/etc/postfix/carlos-transport-regexp`
- **Capture script:** `/usr/local/bin/postfix-capture-mail` (runs as `nobody`)
- **Capture file:** `/var/log/carlos-mail-capture.eml`
- **Log:** `/var/log/mail.log`
