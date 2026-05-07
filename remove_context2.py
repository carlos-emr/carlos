import os
import re

directory = "src/main/resources"
pattern = re.compile(r'^(\s*(?://|\*|/\*)\s*)(?:Clinical\s*)?Context:\s*(.*)', re.IGNORECASE)

count = 0
for root, _, files in os.walk(directory):
    for file in files:
        if file.endswith(".drl") or file.endswith(".java") or file.endswith(".properties") or file.endswith(".xml"):
            filepath = os.path.join(root, file)
            try:
                with open(filepath, 'r') as f:
                    lines = f.readlines()
            except UnicodeDecodeError:
                continue
                
            changed = False
            for i in range(len(lines)):
                match = pattern.match(lines[i])
                if match:
                    # lines[i] = match.group(1) + match.group(2)
                    lines[i] = match.group(1) + match.group(2)
                    if not lines[i].endswith("\n"):
                        lines[i] += "\n"
                    changed = True
            
            if changed:
                with open(filepath, 'w') as f:
                    f.writelines(lines)
                count += 1
                print(f"Fixed {filepath}")

print(f"Total files fixed: {count}")
