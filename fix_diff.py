import subprocess
import re

files = [
    "src/main/java/io/github/carlos_emr/carlos/managers/DemographicManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/LabManager.java",
    "src/main/java/io/github/carlos_emr/carlos/PMmodule/service/ProgramManager.java",
    "src/main/java/io/github/carlos_emr/carlos/casemgmt/service/CaseManagementManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/RxManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/PrescriptionManager.java",
    "src/main/java/io/github/carlos_emr/carlos/managers/PreventionManager.java"
]

for fpath in files:
    try:
        orig = subprocess.check_output(['git', 'show', f'origin/main:{fpath}']).decode('utf-8').splitlines()
        curr = subprocess.check_output(['cat', fpath]).decode('utf-8').splitlines()
    except Exception as e:
        print(e)
        continue
        
    # Find all methods in orig that started with "    public "
    public_methods = set()
    for line in orig:
        if line.startswith("    public "):
            # Extract method name
            m = re.search(r'public\s+[\w<>,\[\]\s]+\s+(\w+)\s*\(', line)
            if m:
                public_methods.add(m.group(1))
                
    # Now patch curr
    out = []
    for line in curr:
        if line.startswith("    ") and not line.startswith("    public ") and not line.startswith("    //") and not line.startswith("    /*") and not line.startswith("    *"):
            m = re.search(r'^    (?:[\w<>,\[\] ]+)\s+(\w+)\s*\(', line)
            if m:
                method_name = m.group(1)
                if method_name in public_methods:
                    line = line.replace("    ", "    public ", 1)
        out.append(line)
        
    with open(fpath, 'w') as f:
        f.write('\n'.join(out) + '\n')
    print(f"Fixed {fpath}")
