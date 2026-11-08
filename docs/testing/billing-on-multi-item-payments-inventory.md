# Ontario multi-item payments (#5805)

`billing-on-multi-item-payments` uses a disposable/exclusive Ontario installation,
an owned billing physician selected through the native invoice controls, three
distinct configured private service codes, an owned invoice and an unchanged
control invoice. Poppler reads the actual downloaded invoice PDFs.

The independently authored fees are 11.23, 24.57 and 38.09, totalling 73.89. The
native payment form receives these three allocations in order:

| Save | First item | Second item | Third item | Cumulative paid |
| --- | ---: | ---: | ---: | ---: |
| 1 | 3.21 | 5.43 | 0.00 | 8.64 |
| 2 | 8.02 | 4.00 | 0.00 | 20.66 |
| 3 | 0.00 | 15.14 | 38.09 | 73.89 |

Every rendered row is mapped by its actual hidden item ID; its current index is
used only to operate the matching native payment controls. An actual Save must
confirm exactly once. Independent SQL compares every item-payment against its
correct parent payment and item, requires no zero/untouched-line allocation,
reconciles transaction audit identities/amounts, and checks header paid and the
payment extension. All calculations use integer cents and fixed decimal strings.
Completing the first item must leave the second partial and the third unpaid.
The normal Save contract keeps invoice status P; full payment is not confused
with the separate Save and Settle action.

After each save, native payment-form reopening must show all three exact fees,
paid values and balances, plus the complete payment history. The payment-details
popup must show only that payment's intended lines, patient/invoice/code and
amounts, without writing financial rows. The displayed invoice and actual printed
PDF must show all line codes, invoice identity, total, cumulative payments and
remaining balance. The control's full financial snapshot and the tested line
identities/fees remain unchanged throughout.

Cleanup guards header ownership, allowed line identities/fees and item-payment
references before removing only the owned financial/supporting rows. It retains
the physician if dependent cleanup is unconfirmed. The first development attempt
caught a wrapper that dropped inherited session properties before any claim write;
the provider was recovered through its original private ownership ledger, with
exact preexisting provider/site tables preserved. The wrapper now retains its
prototype. All three native allocation stages pass on snapshot31 ON, alongside the existing
single-item invoice/payment workflow and all three email/consent workflows: five
passes, zero failures/skips. The actual PDF labels its remainder Balance Due,
while the HTML labels it Balance; both require the same exact amount. One
caller control proves inherited session bindings and dependent cleanup survive
the wrapper. Final-head qualification remains explicit in the consolidated register.
