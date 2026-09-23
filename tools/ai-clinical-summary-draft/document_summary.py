# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Single-document gateway contract. The Java host independently validates all output."""
import copy
import json
from pathlib import Path
import re
import unicodedata
import uuid

from validate_artifact import require

PATH = "/v1/document-summary"
RESOURCES = Path(__file__).resolve().parents[2] / "src/main/resources/clinical/summary"
PROMPT = (RESOURCES / "document-summary-prompt.txt").read_text(encoding="utf-8")
SCHEMA = json.loads((RESOURCES / "document-summary-schema.json").read_text(encoding="utf-8"))


def validate_request(request, notes, request_bytes):
    require(isinstance(request, dict) and set(request) == {
        "contract_version", "request_id", "workflow", "data_classification",
        "instructions", "output_schema", "sources"}, "Invalid document request fields")
    require(type(request["contract_version"]) is int and request["contract_version"] == 1
            and request["workflow"] == "single-document-summary"
            and request["data_classification"] == "clinical-document", "Invalid document contract")
    require(isinstance(request["request_id"], str) and len(request["request_id"]) == 36,
            "Invalid request ID")
    uuid.UUID(request["request_id"])
    require(request["instructions"] == PROMPT and request["output_schema"] == SCHEMA,
            "Unexpected document prompt or schema")
    sources = request["sources"]
    require(isinstance(sources, list) and len(sources) == 1, "Expected one document")
    source = sources[0]
    require(isinstance(source, dict) and set(source) == {"id", "title", "text"}
            and source["id"] == "document" and source["title"] == "Document"
            and isinstance(source["text"], str) and source["text"].strip(), "Invalid document source")
    # No caller assertion or demographic identifier authorizes cloud disclosure. Requiring the
    # complete note also prevents an arbitrary short fragment from matching the allow-list.
    require(any(source["text"] == body for _fixture, _date, body in notes),
            "Document does not match a complete committed synthetic note")
    require(len(json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            <= request_bytes, "Document request exceeds configured budget")


def provider_schema():
    schema = copy.deepcopy(SCHEMA)
    # Some provider grammars reject uniqueItems; both hosts still reject duplicate excerpts.
    schema["properties"]["points"]["items"]["properties"]["evidence"].pop("uniqueItems", None)
    return schema


def normalize(value):
    return unicodedata.normalize("NFKC", value).lower()


def words(value):
    return {word for word in re.findall(r"[^\W_]+", normalize(value))
            if len(word) >= 4 or word.isdecimal()}


def text(value, maximum):
    require(isinstance(value, str) and value.strip() and len(value) <= maximum and "\r" not in value,
            "Invalid summary text")
    return value.strip()


def validate_output(output, source):
    require(isinstance(output, dict) and set(output) == {"overview", "points"}, "Invalid document output")
    overview = text(output["overview"], 4000)
    points = output["points"]
    require(isinstance(points, list) and 0 < len(points) <= 50, "Invalid document points")
    seen = set()
    for point in points:
        require(isinstance(point, dict) and set(point) == {"text", "evidence"}, "Invalid document point")
        statement = text(point["text"], 2000)
        key = " ".join(re.findall(r"[^\W_]+", normalize(statement)))
        require(key not in seen, "Duplicate document point")
        seen.add(key)
        evidence = point["evidence"]
        require(isinstance(evidence, list) and 0 < len(evidence) <= 5, "Invalid document evidence")
        for excerpt in evidence:
            require(isinstance(excerpt, str) and excerpt.strip() and len(excerpt) <= 800
                    and excerpt in source, "Evidence is not a verbatim document excerpt")
        require(len(set(evidence)) == len(evidence), "Duplicate document evidence")
        require(words(statement) & words(" ".join(evidence)), "Point lacks lexical support")
    require(words(overview) & words(source), "Overview lacks lexical support")
