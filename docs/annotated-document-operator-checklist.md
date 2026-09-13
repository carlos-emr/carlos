# Annotating and faxing a document

Use this procedure on the patient's PDF document. Your account needs document write access; faxing also needs fax access and an active fax account. Ask your administrator for a provider signature stamp before using the Signature tool.

1. Open the document and confirm the patient, document title, date, page count, and orientation.
2. Select **Annotate**. Wait for each page image to load before marking it. If a page cannot load, the viewer shows an error: reload and check the page before continuing.
3. Select Highlight, Draw, Text, Date, or Signature, then mark the page. Select a colour first. **Remove mark** deletes a mark when you click it. Date uses the workstation's local calendar date. Signature is a placement box; the stored provider stamp is applied when saved.
4. Select **Save as new document**. Wait for the success message and new document number. **Open the saved copy**, inspect every marked page, and confirm that text, dates, signatures, and highlights are correct. The original document is retained; annotations create a separate copy. Highlights are not redactions and do not remove underlying content.
5. To continue directly to faxing, use **Save and fax** instead. At the cover page, inspect the preview and confirm the patient, recipient, and complete fax number. Directory suggestions may include both specialists and pharmacies. Verify the selected destination before sending.
6. Submit once. A queued fax is not proof of delivery: check its eventual status in the fax queue. Cancelling an unsent cover page removes its temporary preview and returns to the saved document; it does not delete the saved copy.

## If something fails

- **Source document changed:** reopen the document and review the new version before recreating your annotations.
- **Text extends beyond the page:** shorten the note or place it further left; do not accept clipped clinical text.
- **Unsupported text character:** edit the text and save again. The application refuses unsupported characters rather than silently omitting them.
- **Missing signature stamp:** ask the administrator to configure the provider's stamp, or remove the signature mark. Do not substitute another provider's signature.
- **Save could not be confirmed:** keep the viewer open and check the patient's document list with the responsible clinician or administrator before creating another copy. A lost response can occur after a save; repeated submission may create duplicates.
- **Fax status uncertain:** do not resend. Ask the administrator to inspect the queue and delivery provider status first.
- **Recipient search unavailable:** verify and enter the destination manually, or retry the lookup. An empty or failed search does not establish that the recipient has no fax number.
- **Access denied, missing document, oversized document, or repeated render failure:** stop and contact the administrator. Do not try another patient's identifier or change stored files manually.

## Technician acceptance check on a disposable test instance

Never run the browser check on a live patient chart: it creates annotated copies. The test needs synthetic PDF document data and an active test fax account. It prepares and cancels a fax; it does not transmit one.

From the repository root, with Node.js, Playwright and Chromium available, run:

```sh
BASE_URL=https://127.0.0.1/carlos \
DOC_ID=1 TEST_USER="$TEST_USER" TEST_PASSWORD="$TEST_PASSWORD" TEST_PIN="$TEST_PIN" \
CHROME_PATH=/path/to/chromium \
npm run test:annotate-document-playwright
```

Use the credentials for your disposable test account; do not put real credentials in a committed script. The account should have completed its first-login password change. Self-signed certificates are accepted only on loopback hosts. The script must exit zero and report no failed checks. Review the saved copies visually as well as the automated results. To include signature filing, configure a synthetic stamp for the test provider in the application's effective eForm image directory and set `EXPECT_SIGNATURE_STAMP=true`. Do not assume the directory from a different deployment context applies.

## Administrator upgrade check

The package preserves the annotation page's stricter Content Security Policy through nginx. `carlos-ctl init-config` upgrades the old unmodified stock proxy fragment automatically. If `/etc/carlos-emr/nginx/proxy-params.conf` has local edits, it preserves them and warns instead. Back up that file, remove the exact `proxy_hide_header Content-Security-Policy;` directive, run `sudo nginx -t`, and reload nginx only if validation succeeds. Keep the front-door baseline CSP: browsers enforce both policies. Run the browser check through the HTTPS front door to verify the application's nonce policy is actually enforced.
