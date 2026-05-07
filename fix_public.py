import re
import sys

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
    with open(fpath, "r") as f:
        lines = f.readlines()
    
    out_lines = []
    for line in lines:
        if line.startswith("    ") and not line.startswith("    public ") and not line.startswith("    //") and not line.startswith("    /*") and not line.startswith("    *"):
            # Check if it looks like a method signature
            if re.match(r'^    [\w<>,\[\] ]+\s+\w+\(.*', line):
                line = line.replace("    ", "    public ", 1)
        out_lines.append(line)
        
    with open(fpath, "w") as f:
        f.writelines(out_lines)

print("Done")
