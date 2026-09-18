# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Bounded full-record passes, shared by the standalone runner and its quality checks."""
import copy
import json

from validate_artifact import require

REQUEST_BYTES = 16000  # Mirrors ClinicalSummaryAgent.requestBytes().


def ollama_schema(schema, sources):
    """Constrain coverage and citations to this request, without a summary-length cap."""
    result = copy.deepcopy(schema)
    ids = [source["id"] for source in sources]
    coverage = result["properties"]["coverage"]
    coverage.update(minItems=len(ids), maxItems=len(ids))
    coverage["items"]["properties"]["source_id"]["enum"] = ids
    citations = result["properties"]["claims"]["items"]["properties"]["source_ids"]
    citations["maxItems"] = len(ids)
    citations["items"]["enum"] = ids
    return result


class OutputLimitError(Exception):
    """The model reached its token budget; discard the response and split the input."""


def split(sources):
    if len(sources) > 1:
        middle = len(sources) // 2
        return copy.deepcopy(sources[:middle]), copy.deepcopy(sources[middle:])
    require(len(sources) == 1, "No sources to split")
    source = sources[0]
    text = source["text"]
    # Below this floor a source cannot be divided further, so if it still does not fit, the budget is
    # too small for the prompt rather than the source being too large. Say which, or the caller sees
    # an unexplained failure on a perfectly ordinary note.
    require(len(text) >= 1024,
            "A minimal source portion could not be completed; the request budget leaves too little room "
            "beside the prompt. Raise the adapter's requestBytes or shorten the prompt.")
    middle = len(text) // 2
    boundary = text.rfind("\n", 0, middle + 1)
    if boundary < middle // 2:
        boundary = text.rfind(". ", 0, middle + 1)
    if boundary >= middle // 2:
        middle = boundary + 1
    return ([dict(source, text=text[:middle + 128])],
            [dict(source, text=text[max(0, middle - 128):])])


def plan(sources, prompt, schema, request_bytes=REQUEST_BYTES):
    # Match the host request budget rather than a point or character limit on the summary.
    request = {"contract_version": 1, "request_id": "0" * 36, "workflow": "patient-overview",
               "data_classification": "verified-synthetic", "instructions": prompt,
               "sources": sources, "output_schema": schema}
    require(type(request_bytes) is int and 10000 <= request_bytes <= 60000, "Invalid request budget")
    if len(json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode("utf-8")) <= request_bytes:
        return [copy.deepcopy(sources)]
    if len(sources) == 1:
        return [part for half in split(sources) for part in plan(half, prompt, schema, request_bytes)]
    batches, batch, kind = [], [], ""
    for source in sources:
        next_kind = source["id"].split("-", 1)[0]
        if batch and next_kind != kind:
            batches.append(batch)
            batch = []
        kind = next_kind
        batch.append(copy.deepcopy(source))
        request["sources"] = batch
        if len(json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode("utf-8")) > request_bytes:
            batch.pop()
            if batch:
                batches.append(batch)
            batch = [copy.deepcopy(source)]
            request["sources"] = batch
            if len(json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode("utf-8")) > request_bytes:
                batches.extend(plan(batch, prompt, schema, request_bytes))
                batch = []
    if batch:
        batches.append(batch)
    return batches


def merge(outputs, sources):
    if len(outputs) == 1:
        return outputs[0]
    result = {"sections": [], "claims": [], "coverage": []}
    unique, sections, reviews, owners = {}, {}, {}, {}
    for output in outputs:
        membership = {claim_id: section for section in output["sections"] for claim_id in section["claim_ids"]}
        for claim in output["claims"]:
            key = claim["text"].strip()
            section = membership[claim["id"]]
            if key in unique:
                existing = unique[key]
                existing["source_ids"] = list(dict.fromkeys(existing["source_ids"] + claim["source_ids"]))
                if owners[key] == "clinical_overview" and section["id"] != "clinical_overview":
                    sections[owners[key]]["claim_ids"].remove(existing["id"])
                    if section["id"] not in sections:
                        sections[section["id"]] = dict(section, claim_ids=[])
                        result["sections"].append(sections[section["id"]])
                    sections[section["id"]]["claim_ids"].append(existing["id"])
                    owners[key] = section["id"]
                continue
            new = dict(copy.deepcopy(claim), id=f"claim-{len(unique) + 1}")
            unique[key] = new
            result["claims"].append(new)
            owners[key] = section["id"]
            if section["id"] not in sections:
                sections[section["id"]] = dict(section, claim_ids=[])
                result["sections"].append(sections[section["id"]])
            sections[section["id"]]["claim_ids"].append(new["id"])
        for entry in output["coverage"]:
            reviews.setdefault(entry["source_id"], []).append(entry)
    cited = {source_id for claim in result["claims"] for source_id in claim["source_ids"]}
    for source in sources:
        source_id = source["id"]
        entries = reviews.get(source_id, [])
        require(entries, "Unprocessed source")
        status = "cited" if source_id in cited else (
            "excluded" if all(entry["status"] == "excluded" for entry in entries) else "reviewed_not_cited")
        reasons = list(dict.fromkeys(f"{entry['status']}: {entry['reason']}" for entry in entries))
        result["coverage"].append({"source_id": source_id, "status": status,
                                   "reason": f"{source_id} — all {len(entries)} passes processed. " + "; ".join(reasons)})
    result["sections"] = [section for section in result["sections"] if section["claim_ids"]]
    return result


def generate(sources, prompt, schema, infer, validate_part, request_bytes=REQUEST_BYTES):
    outputs = []

    def run(part):
        try:
            output = infer(part)
        except OutputLimitError:
            for smaller in split(part):
                run(smaller)
            return
        validate_part(part, output)
        outputs.append(output)

    for part in plan(sources, prompt, schema, request_bytes):
        run(part)
    return merge(outputs, sources)
