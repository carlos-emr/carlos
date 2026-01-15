# Postfix Mail Server (Development)

## Overview

The devcontainer includes a local Postfix SMTP server for testing email functionality in OpenO without sending actual emails. All emails are discarded after logging.

## Quick Start

### Start the Mail Server
```bash
mail start
```

### View Email Logs
```bash
mail log
```

This shows all email activity including:
- Sender/recipient addresses
- Subject lines (if logged)
- Delivery status (all will show "discarded")

### Stop the Mail Server
```bash
mail stop
```

## Available Commands

| Command | Description |
|---------|-------------|
| `mail start` | Start the Postfix service |
| `mail stop` | Stop the Postfix service |
| `mail restart` | Restart the Postfix service |
| `mail status` | Check if Postfix is running |
| `mail log` | View mail logs in real-time (Ctrl+C to exit) |

## How It Works

1. OpenO sends emails to `localhost:25`
2. Postfix accepts the connection
3. Email details are logged to `/var/log/mail.log`
4. Email is **discarded** (never actually sent)

## Example Log Output
```
postfix/pickup[1234]: ABC123: uid=0 from=<noreply@openo-dev.local>
postfix/cleanup[1235]: ABC123: message-id=<20251119120000.ABC123@openo-dev.local>
postfix/qmgr[1236]: ABC123: from=<noreply@openo-dev.local>, size=1234, nrcpt=1
postfix/smtp[1237]: ABC123: to=<patient@example.com>, relay=discard, status=sent (discarded)
```

## Troubleshooting

### Emails Not Appearing in Logs
```bash
# Check if Postfix is running
mail status

# If not running, start it
mail start
```

### Connection Refused Errors

Ensure Postfix is started before testing email functionality in OpenO.

### No Log File

If `mail log` shows "No mail log found", start Postfix first:
```bash
mail start
mail log
```

## Technical Details

- **Port:** 25 (localhost only, not exposed externally)
- **Config:** `/etc/postfix/main.cf`
- **Logs:** `/var/log/mail.log`
- **Transport:** All emails use `discard` transport (no actual delivery)

## Testing Email Features

To test OpenO's email functionality:

1. Start Postfix: `mail start`
2. Open another terminal: `mail log`
3. In OpenO, trigger an email action (eform, etc.)
4. Watch the logs to confirm the email was "sent"
