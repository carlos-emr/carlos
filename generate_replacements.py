import subprocess
import re

files = [
    "src/main/java/io/github/carlos_emr/carlos/managers/DemographicManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/LabManager.java",
    "src/main/java/io/github/carlos_emr/carlos/PMmodule/service/ProgramManager.java",
    "src/main/java/io/github/carlos_emr/carlos/casemgmt/service/CaseManagementManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/RxManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/PrescriptionManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/PreventionManager.java",
    "src/main/java/io/github/carlos_emr/carlos/PMmodule/service/AdmissionManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/DrugLookUpManager.java",
    "src/main/java/io/github/carlos_emr/carlos/services/security/SecurityManager.java"
]

for fpath in files:
    try:
        orig = subprocess.check_output(['git', 'show', f'origin/main:{fpath}']).decode('utf-8').splitlines()
        curr = subprocess.check_output(['cat', fpath]).decode('utf-8').splitlines()
    except:
        continue
        
    orig_publics = {}
    for i, line in enumerate(orig):
        line_stripped = line.strip()
        if line_stripped.startswith("public "):
            orig_publics[line_stripped[7:].strip()] = line
            
    # Now patch curr
    out = []
    changed = False
    for i, line in enumerate(curr):
        line_stripped = line.strip()
        if line_stripped in orig_publics:
            # We found a line that was stripped of public
            # Restore it
            # The orig line starts with public, we should see what indentation it had
            orig_line = orig_publics[line_stripped]
            indent = line[:len(line) - len(line.lstrip())]
            out.append(indent + "public " + line_stripped)
            changed = True
        else:
            out.append(line)
            
    if changed:
        with open(fpath, 'w') as f:
            f.write('\n'.join(out) + '\n')
        print(f"Fixed {fpath}")
