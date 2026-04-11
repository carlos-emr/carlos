import re

with open('src/main/java/io/github/carlos_emr/OBChecklistHandler_99_12.java', 'r') as f:
    content = f.read()

# Fix empty catch block
content = re.sub(
    r'} catch \(java\.text\.ParseException pe\) {\s*}',
    r'} catch (java.text.ParseException pe) {\n            MiscUtils.getLogger().error("Error parsing date: ", pe);\n        }',
    content
)

with open('src/main/java/io/github/carlos_emr/OBChecklistHandler_99_12.java', 'w') as f:
    f.write(content)
