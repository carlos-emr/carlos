# Image-backed eForm ZIP round trip (#5808)

Run `CARLOS_DISPOSABLE_HOST=true EXCLUSIVE=1 npm run test:eform-image-zip-roundtrip`
on a disposable installed host. Local image and renderer stores must be readable
and writable; `EFORM_IMAGE_DIR` and `EFORM_RENDER_TEMP_DIR` override package
defaults. Poppler and the locked PNG/browser dependencies are required.

The fixture owns one template, one patient instance and two distinct PNG assets:
a two-colour page background and a red/black inline tile. SQL/files establish
the source fixture; export, import, chart use, saving and reopening are native
application operations. Manage eForms exports the actual ZIP. An independent
reader requires exactly four unique resources with exact HTML, properties and
asset bytes, including references to both included images.

The owned source template and both images are verified absent before the native
ZIP import submits those same downloaded bytes. No replacement upload occurs.
The imported template must have a new identity and exact HTML; both restored
files must match their original bytes. Native chart launch, entered-value save
and saved-instance reopening require exact patient/template/value attribution,
decoded image dimensions and exact bytes served by the application.

The actual downloaded PDF must contain one correctly sized page, the entered
text and four independently specified colour regions, within eight RGB units
over 5x5 pixel samples at 96 dpi. This tests both images separately from text
extraction and rejects blank output or omitted/substituted image content.
Toolbar text must be absent. Thirteen omission/corruption/duplicate-resource and
rendering controls pass on Ubuntu 26.04.

Snapshot31 ON and BC each pass this workflow and both existing export/navigation
regressions together: three passes, zero failures/skips. The application source
is `e0601ced004c`, Ubuntu 26.04, Chromium 149.0.7827.55. Exact template/instance/
asset/download/server-PDF cleanup passes; complete preexisting library rows and
preexisting asset hashes remain unchanged. Full release qualification is separate.

Image publication shares a lease with image uploads. Extraction and budget
checks finish before the lease is acquired; another import cannot adopt a
provisional image, or an upload replace its inode, until publication or rollback
finishes. A JVM guard and filesystem lock coordinate cooperating application JVMs
using the same image directory. Form registration remains outside the lease.

The empty `extractFolder/.publication.lock` inode is permanent coordination
metadata. Keep it when checking or cleaning staging residue: removing it can
split waiting processes across different lock inodes. Successful-import tests
require this exact file and no leftover extraction directories. Refused ZIPs
must preserve the preexisting staging snapshot without creating this metadata.
Shared deployments give `extractFolder` the configured writer group or a
default ACL granting the writer accounts access. New markers use that group
even when the publisher belongs to it as a supplementary group. On upgrade, the
marker owner or deployment administrator adds write access for those writers
to the existing inode before resuming shared publication. When the marker
already belongs to the writer group, `chmod g+w` grants this access in place.
An owner acquiring the lease also repairs a missing group-write bit for the
configured staging group.
The ownership and lock unit suites exercise both consumer races, a separate JVM,
and refused lock operations. These checks do not extend the historical installed
qualification above to the newer production source.
