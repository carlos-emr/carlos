# Windows evaluation download

This is an **unsigned synthetic-data evaluation**, not a patient release. Signing and the
security/privacy/device release gates remain open.

## Download and install

1. Sign in to GitHub and open the [Windows evaluation builds](https://github.com/carlos-emr/carlos/actions/workflows/mycarlos-tauri-poc.yml?query=branch%3Apoc%2F3474-tauri-v2).
2. Choose the newest successful run. In its summary, click **Download Windows x64 evaluation
   installer**, or select **myCarlos-Windows-x64-Evaluation** under **Artifacts**.
3. Extract the ZIP and run `myCarlos-Evaluation-Windows-x64-setup.exe`.
4. Launch **myCarlos Evaluation** from Start. Create a fictional profile and test passphrase,
   then import PDFs from the ZIP's `sample-files` folder.

The x64 installer targets ordinary Intel/AMD Windows PCs. It installs for the current user and
includes the WebView2 bootstrapper; if the runtime is missing, setup needs internet access to
install it. No development tools are required. Windows on ARM has not been validated here.

The ZIP includes `START-HERE.txt`, the installer, sample PDFs, its SHA-256 checksum, and a
`BUILD.txt` identifying the exact source commit and workflow run. Downloads are retained for
30 days. Updates are manual; use the same link to find a newer build.

Windows may warn about an unknown publisher or block this unsigned build, particularly on
managed computers. Do not disable Windows security controls to install it. An installer produced
successfully by CI is build evidence; physical-device installation testing remains required.

To erase evaluation data, use **Security > Erase entire vault** before uninstalling. App removal
can leave app-data files behind, and vault reset cannot erase readable exports.

## Getting a trusted Windows signature

Use **Azure Artifact Signing (Public Trust)** for direct Windows downloads. Microsoft's
[code-signing guidance](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options)
lists approximately US$9.99/month and supports Canadian organizations and individuals. A
consistent publisher identity matters: signed new builds can still show SmartScreen warnings
while reputation develops. DCO sign-off on Git commits is separate from executable signing.

The account owner must complete the [Azure setup and identity validation](https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart):

1. Choose the legal publisher name that should appear in Windows and create the Azure subscription
   and Artifact Signing account under that owner.
2. Complete public identity validation and create a **Public Trust** certificate profile.
3. Configure a [GitHub Actions federated identity](https://github.com/Azure/artifact-signing-action/blob/main/docs/OIDC.md)
   with signing permission scoped to that profile.
   Keep signing in a protected release environment on a trusted branch, separate from PR builds.
4. Connect that identity and profile to Tauri's Windows `signCommand`, following the
   [Tauri Windows signing guide](https://v2.tauri.app/distribute/sign/windows/). Sign the application
   executable before packaging, then sign and timestamp the finished installer.
5. Verify both signatures with Windows `Get-AuthenticodeSignature`/SignTool before uploading
   signed downloads. Publish from the approved release pipeline after the remaining release gates.

No signing account, paid subscription, certificate, or credentials are created by this PR.
The current PR workflow intentionally has no signing permissions or credentials.

For an open-source alternative, the [SignPath Foundation](https://signpath.org/)
offers signing to qualifying projects. Eligibility and onboarding are separate from this build.
