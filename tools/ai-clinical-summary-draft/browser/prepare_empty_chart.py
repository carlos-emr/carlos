#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Prepare only the isolated synthetic morning trial; never clone clinical rows."""
import os, re, subprocess, sys, json, hashlib
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from openrouter_agent import committed_notes
base = Path('target/nhs-chart-update-morning').resolve()
props = dict(re.findall(r'^([^#\s=]+)=(.*)$', (base/'tomcat/conf/chart-updates.properties').read_text(), re.M))
database = 'carlos_chartupdates_morning_20260929'
assert props.get('db_name', '').split('?', 1)[0] == database
env = dict(os.environ, MYSQL_PWD=props['db_password'])
client = ['mysql', '-h', 'db', '-u', props['db_username'], '--batch', '--skip-column-names', database]
def query(sql):
    result = subprocess.run(client, input=sql, env=env, text=True, capture_output=True)
    if result.returncode: raise RuntimeError('Fixture SQL failed; no credentials or SQL printed')
    return result.stdout.strip()
def literal(text):
    return 'CONVERT(0x' + text.encode('utf-8').hex() + ' USING utf8mb4)'
notes, _ = committed_notes()
selected = [(date, body) for key, date, body in notes if key == 'NHSSYN005']
assert len(selected) == 37
combined = '\n\n'.join(f'=== Source note {i+1} | {date} ===\n{body}' for i, (date, body) in enumerate(selected))
date, longest = max(selected, key=lambda row:len(row[1]))
assert not query("SELECT demographic_no FROM demographic WHERE chart_no='AIFACT005'")
files = [ ('nhs-empty-chart-full-record.txt', 'SYNTHETIC - 37-note record - 4537 words', selected[-1][0], combined),
          ('nhs-empty-chart-longest-note.txt', 'SYNTHETIC - long clerking note - 561 words', date, longest) ]
for filename, _, _, text in files:
    path = base/'documents'/filename
    assert not path.exists()
    path.write_text(text, encoding='utf-8')
sql = """CREATE TEMPORARY TABLE fixture_guard(ok INT NOT NULL CHECK(ok=1));
INSERT INTO fixture_guard SELECT IF(DATABASE()='carlos_chartupdates_morning_20260929' AND EXISTS(SELECT 1 FROM security WHERE user_name='carlosdoc' AND provider_no='999998'),1,0);
START TRANSACTION;
INSERT INTO demographic (first_name,last_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,chart_no,provider_no,alias,lastUpdateUser,lastUpdateDate)
SELECT first_name, 'FAKE-EMPTY-CHART', year_of_birth,month_of_birth,date_of_birth,sex,'AC','AIFACT005','999998','Synthetic NHSSYN005 document trial','999998',NOW() FROM demographic WHERE chart_no='NHSSYN005';
INSERT INTO fixture_guard VALUES(IF(ROW_COUNT()=1,1,0));
SET @empty_patient=LAST_INSERT_ID();
SELECT @empty_patient;
"""
for filename,title,observed,body in files:
    sql += f"""INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,program_id,updatedatetime,status,contenttype,contentdatetime,public1,observationdate,restrictToProgram)
VALUES ('consult',{literal(title)},{literal(filename)},'999998','999998',10034,NOW(),'A','text/plain',{literal(observed)},0,{literal(observed)},0);
SET @trial_doc=LAST_INSERT_ID();
INSERT INTO ctl_document(module,module_id,document_no,status) VALUES('demographic',@empty_patient,@trial_doc,'A');
SELECT @trial_doc;
"""
sql += 'COMMIT;'
ids = list(map(int, query(sql).splitlines()))
assert len(ids) == 3
patient = ids[0]
counts = {table:int(query(f'SELECT COUNT(*) FROM {table} WHERE demographic_no={patient}'))
          for table in ['casemgmt_note','tickler','drugs','allergies','clinical_chart_update_receipt']}
assert not any(counts.values())
result = dict(chartNumber='AIFACT005', demographicId=patient, sourceFixture='NHSSYN005', baselineCounts=counts,
              documents=[dict(documentId=doc, title=title, sourceFile=str(base/'documents'/filename),
                              date=observed, characters=len(body), words=len(body.split()),
                              sha256=hashlib.sha256(body.encode()).hexdigest())
                         for doc,(filename,title,observed,body) in zip(ids[1:],files)])
(base/'empty-chart-fixture.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
