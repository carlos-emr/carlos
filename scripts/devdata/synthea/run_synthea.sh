#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
#
# Runs the pinned Synthea release that the development-only synthetic patients (#6148) come from.
#
#   scripts/devdata/synthea/run_synthea.sh <synthea-with-dependencies.jar> <ca resources folder> <empty output folder>
#
# Both inputs are Apache-2.0 and are downloaded by hand, each to a folder of its own:
# - the jar, Synthea v4.0.0's release asset:
#   https://github.com/synthetichealth/synthea/releases/download/v4.0.0/synthea-with-dependencies.jar
# - the Canadian geography, payer and provider files of synthea-international, commit
#   83d1ab7d9a73f5a5fcd7fa0a2d3f3b4eaf725d34, folder ca/src/main/resources (its data come from Statistics Canada).
# This script downloads nothing, and refuses a jar or a Canadian file with another SHA-256. It needs Java 17 or
# newer and about 2 GB of memory. Synthea writes the CSV files that generate_dev_patients.py reads to
# <output folder>/csv. The patients are generated in Ontario; the generator then replaces every name, place and
# number with FAKE ones.
#
# Every setting that would otherwise come from the clock or the machine is fixed (seeds, the
# reference and end date, one thread, and the JVM's locale, time zone and file encoding, set below to those of
# the pinned run), so a rerun writes the same files, byte for byte. Only the Java build is not fixed: the pinned
# output was made with OpenJDK 21.0.11+10 (see README.md); generate_dev_patients.py refuses any other output.
set -euo pipefail

JAR_SHA256=ed43c20ad40ba5c3bc724503a5af032715fe3c491620b766148e7c2361e6ecc1

if [ "$#" -ne 3 ]; then
    echo "usage: $0 <synthea-with-dependencies.jar> <ca resources folder> <empty output folder>" >&2
    exit 2
fi
here=$(cd "$(dirname "$0")" && pwd)
jar=$(realpath "$1")
ca=$(realpath "$2")
out=$3

actual=$(sha256sum "$jar" | cut -d' ' -f1)
if [ "$actual" != "$JAR_SHA256" ]; then
    echo "$jar has SHA-256 $actual, not the pinned $JAR_SHA256 (Synthea v4.0.0)" >&2
    exit 1
fi
# The data files only: the folder's own synthea.properties (from 2022) must not replace v4.0.0's defaults;
# synthea-ca.properties carries the Canadian values instead.
CA_FILES=$(cat <<'SUMS'
43ff1b71e45cf21f9380979e54fe8a96df9d7358aa569ab9ae542649988d3b61  geography/demographics_ca.csv
caeaa36302adc72525bb486bbe1da854afd0c8d072e908271fc70b95bcc75c3c  geography/timezones_ca.csv
d6cbd584048062a64c106158a614ea62621e7cc85141c5466584533c5066e305  geography/zipcodes_ca.csv
a8407b973fa591e7721815965238ca61b6ad643674dfea01d50614fe8e0280d0  payers/insurance_companies_ca.csv
3bbc5874390489577a840fefc65fb4d82de4c20f3d101d8b3d4806356b85a971  payers/insurance_eligibilities.csv
96635860e597de396f13f9c6b2ec3f63f669858f312945bdefa5e56c83a23822  payers/insurance_plans.csv
b8217ec5fd22ebb1ba908ddea1dec96289d3240e3cd9e0867b185904b2a88ad8  providers/hospitals_ca.csv
7a28843482f2c23a1cec946eb4a4dd9f3b270954004c66f4ca9fbf2891d30832  providers/longterm_ca.csv
fae992fbb135c54a69bcc79a46972cfc73382a35bc09a3bf9a09f65b35c6977e  providers/nursing_ca.csv
2f91550210a754197dca246266801d8cf0559e79bd956299e1b2d19acb0643bb  providers/primary_care_facilities_ca.csv
aa3e659f6ac2664dc2a6f006f82a6af73b0b52d58968d74848cf8418a9262123  providers/urgent_care_facilities_ca.csv
aab284c0c7519648ec5b44d5667bf2f2fd61d7556947291c5fab86d0c806821d  providers/va_facilities_ca.csv
SUMS
)
printf '%s\n' "$CA_FILES" | (cd "$ca" && sha256sum --quiet -c -) || { echo "$ca: a Canadian file is missing or differs from the pinned commit" >&2; exit 1; }
if [ -e "$out" ] && [ -n "$(ls -A "$out")" ]; then
    echo "$out is not empty" >&2
    exit 1
fi
mkdir -p "$out"
out=$(realpath "$out")
cp_dir="$out/.ca-classpath"
# Exactly the 12 pinned files, so another checkout's extra CSVs stay off the classpath.
while read -r _ f; do mkdir -p "$cp_dir/$(dirname "$f")"; cp "$ca/$f" "$cp_dir/$f"; done <<< "$CA_FILES"

# -s/-cs: patient and clinician seeds. -r/-e: ages and the simulation end on 1 Nov 2026
# (generate_dev_patients.REFERENCE). -a: adults only. years_of_history=0 keeps each patient's
# whole history. Only the seven tables the generator reads are written (Synthea ignores the include
# list while its default exclude list is also set, so that list is cleared).
cd "$out"
exec java -Xmx2g -Duser.language=en -Duser.country=US -Duser.timezone=America/Toronto -Dfile.encoding=UTF-8 \
    -cp "$cp_dir:$jar" App -c "$here/synthea-ca.properties" \
    -s 6148 -cs 6148 -r 20261101 -e 20261101 -p 150 -a 25-90 \
    --generate.thread_pool_size=1 \
    --exporter.baseDirectory="$out/" \
    --exporter.years_of_history=0 \
    --exporter.csv.export=true \
    --exporter.csv.excluded_files= \
    --exporter.csv.included_files=patients.csv,conditions.csv,medications.csv,observations.csv,encounters.csv,allergies.csv,immunizations.csv \
    --exporter.fhir.export=false \
    --exporter.hospital.fhir.export=false \
    --exporter.practitioner.fhir.export=false \
    --exporter.metadata.export=false \
    Ontario
