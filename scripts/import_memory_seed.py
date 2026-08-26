#!/usr/bin/env python3

import argparse
import hashlib
import json
import math
import os
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

PROFILE_REAL = "local_baai_bge_small_en_v1_5_384"
PROFILE_MOCK = "fixture_mock_384"
MODEL = "BAAI/bge-small-en-v1.5"
DIMENSIONS = 384
DOMAIN_SUFFIX = {
    "KNOWLEDGE_RULES": "knowledge",
    "TASK_HISTORY": "tasks",
    "CODE_REPO": "code",
    "CHAT_ARTIFACT": "artifacts",
}
MCP_PROTOCOL_VERSION = "2025-06-18"


def parse_args():
    parser = argparse.ArgumentParser(description="Import the Tavall development memory seed.")
    parser.add_argument("--seed-root", default=str(Path(__file__).resolve().parents[1] / "seed" / "tavall-memory-dev"))
    parser.add_argument("--qdrant-url", default=os.getenv("AGENT_TASK_MANAGER_QDRANT_BASE_URL", ""))
    parser.add_argument("--qdrant-api-key", default=os.getenv("AGENT_TASK_MANAGER_QDRANT_API_KEY", ""))
    parser.add_argument("--graphiti-url", default=os.getenv("AGENT_TASK_MANAGER_GRAPHITI_MCP_ENDPOINT", ""))
    parser.add_argument("--graphiti-api-key", default=os.getenv("AGENT_TASK_MANAGER_GRAPHITI_API_KEY", ""))
    parser.add_argument(
        "--knowledge-base",
        default=os.getenv("AGENT_TASK_MANAGER_KNOWLEDGE_BASE", "tavall-org"),
        help="Knowledge collection scope for GLOBAL seed documents.",
    )
    parser.add_argument("--mode", choices=("real", "mock"), default="real")
    parser.add_argument("--execute", action="store_true")
    return parser.parse_args()


def read_ndjson(path):
    entries = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError as exception:
                raise ValueError(f"{path}:{line_number} is not valid JSON: {exception.msg}") from exception
            if not isinstance(entry, dict):
                raise ValueError(f"{path}:{line_number} must contain a JSON object.")
            entries.append(entry)
    return entries


def validate_documents(documents):
    required = ("id", "projectKey", "domain", "kind", "title", "contentType", "text")
    identifiers = set()
    for index, document in enumerate(documents, start=1):
        missing = [field for field in required if not str(document.get(field, "")).strip()]
        if missing:
            raise ValueError(f"Qdrant seed document {index} is missing: {', '.join(missing)}")
        if document["id"] in identifiers:
            raise ValueError(f"Qdrant seed contains duplicate document id: {document['id']}")
        identifiers.add(document["id"])
        if document["domain"] not in DOMAIN_SUFFIX:
            raise ValueError(f"Unsupported semantic domain: {document['domain']}")


def validate_facts(facts):
    required = ("sourceNode", "edgeName", "fact", "targetNode")
    for index, fact in enumerate(facts, start=1):
        missing = [field for field in required if not str(fact.get(field, "")).strip()]
        if missing:
            raise ValueError(f"Graphiti seed fact {index} is missing: {', '.join(missing)}")


def sanitize(value):
    cleaned = []
    previous_separator = False
    for character in (value or "").strip().lower():
        if character.isalnum():
            cleaned.append(character)
            previous_separator = False
        elif not previous_separator:
            cleaned.append("_")
            previous_separator = True
    result = "".join(cleaned).strip("_")
    return result or "default"


def collection_name(document, mode):
    domain = document.get("domain", "KNOWLEDGE_RULES")
    suffix = DOMAIN_SUFFIX.get(domain)
    if suffix is None:
        raise ValueError(f"Unsupported semantic domain: {domain}")
    profile = PROFILE_REAL if mode == "real" else PROFILE_MOCK
    return f"agent_task_manager_project_{sanitize(document['projectKey'])}_{suffix}__{profile}"


def knowledge_collection_name(knowledge_base, mode):
    profile = PROFILE_REAL if mode == "real" else PROFILE_MOCK
    return f"agent_task_manager_knowledge_{sanitize(knowledge_base)}_knowledge__{profile}"


def is_global_document(document):
    metadata = document.get("metadata") or {}
    return str(metadata.get("scope", "")).strip().upper() == "GLOBAL"


def normalize(values):
    magnitude = math.sqrt(sum(value * value for value in values))
    if magnitude == 0.0:
        return values
    return [value / magnitude for value in values]


def mock_vector(document):
    seed = (document["title"] + "\n" + document["text"]).encode("utf-8")
    values = []
    counter = 0
    while len(values) < DIMENSIONS:
        digest = hashlib.sha256(seed + counter.to_bytes(4, "big")).digest()
        for byte in digest:
            values.append((byte / 127.5) - 1.0)
            if len(values) == DIMENSIONS:
                break
        counter += 1
    return normalize(values)


def real_embedder():
    try:
        from fastembed import TextEmbedding
    except ModuleNotFoundError as exception:
        raise SystemExit("fastembed is required for --mode real. Run `python3 -m pip install fastembed`.") from exception
    return TextEmbedding(model_name=MODEL)


def real_vectors(documents):
    embedder = real_embedder()
    texts = [f"{document['title']}\n{document['text']}" for document in documents]
    vectors = []
    for values in embedder.embed(texts):
        vector = [float(value) for value in values]
        if len(vector) != DIMENSIONS:
            raise RuntimeError(f"FastEmbed returned {len(vector)} dimensions; expected {DIMENSIONS}.")
        vectors.append(normalize(vector))
    return vectors


def qdrant_request(base_url, path, method, body, api_key, allowed_statuses=()):
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["api-key"] = api_key
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = response.read().decode("utf-8")
            parsed = json.loads(payload) if payload.strip() else {}
            validate_qdrant_response(parsed, path)
            return parsed
    except urllib.error.HTTPError as exception:
        body_text = exception.read().decode("utf-8", errors="replace")
        if exception.code in allowed_statuses:
            return {"_httpStatus": exception.code}
        raise RuntimeError(f"Qdrant HTTP {exception.code}: {body_text}") from exception


def validate_qdrant_response(payload, path):
    if not isinstance(payload, dict) or payload.get("status") == "error":
        raise RuntimeError(f"Qdrant returned a malformed or error response for {path}: {payload}")
    if "result" not in payload:
        raise RuntimeError(f"Qdrant response did not include result for {path}: {payload}")


def ensure_qdrant_collection(base_url, name, api_key):
    qdrant_request(
        base_url,
        f"/collections/{name}",
        "PUT",
        {"vectors": {"size": DIMENSIONS, "distance": "Cosine"}},
        api_key,
        allowed_statuses=(409,),
    )
    response = qdrant_request(base_url, f"/collections/{name}", "GET", None, api_key)
    try:
        vectors = response["result"]["config"]["params"]["vectors"]
        size = int(vectors["size"])
        distance = str(vectors["distance"])
    except (KeyError, TypeError, ValueError) as exception:
        raise RuntimeError(f"Qdrant collection {name} returned an invalid schema: {response}") from exception
    if size != DIMENSIONS or distance.lower() != "cosine":
        raise RuntimeError(
            f"Qdrant collection {name} has size={size}, distance={distance}; "
            f"expected size={DIMENSIONS}, distance=Cosine."
        )


def point_payload(document, mode, knowledge_base=""):
    payload = dict(document.get("metadata") or {})
    payload.update({
        "projectKey": document["projectKey"],
        "documentId": document["id"],
        "kind": document["kind"],
        "semanticDomain": document["domain"],
        "contentType": document["contentType"],
        "chunkKind": "seed-document",
        "chunkIndex": 0,
        "startLine": 1,
        "endLine": 1,
        "title": document["title"],
        "body": document["text"],
        "chunkText": document["text"],
        "indexedAt": datetime.now(timezone.utc).isoformat(),
        "embeddingProvider": "local" if mode == "real" else "fixture-mock",
        "embeddingModel": MODEL if mode == "real" else "deterministic-sha256-fixture",
        "embeddingDimensions": DIMENSIONS,
        "embeddingPurpose": "RETRIEVAL_DOCUMENT",
    })
    if knowledge_base:
        payload["knowledgeBase"] = knowledge_base
    return payload


def import_qdrant(documents, vectors, base_url, api_key, knowledge_base, execute):
    collections = {}
    for document, vector in zip(documents, vectors, strict=True):
        mode = "real" if document.get("_real", True) else "mock"
        global_document = is_global_document(document)
        collection = (
            knowledge_collection_name(knowledge_base, mode)
            if global_document
            else collection_name(document, mode)
        )
        collections.setdefault(collection, []).append({
            "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "tavall-memory-seed:" + document["id"])),
            "vector": vector,
            "payload": point_payload(document, mode, knowledge_base if global_document else ""),
        })
    for name, points in collections.items():
        print(f"Qdrant: {name} <- {len(points)} point(s)")
        if execute:
            if not base_url:
                raise SystemExit("--qdrant-url is required with --execute when Qdrant documents are present.")
            ensure_qdrant_collection(base_url, name, api_key)
            qdrant_request(base_url, f"/collections/{name}/points?wait=true", "PUT", {"points": points}, api_key)


def parse_mcp_body(body):
    normalized = body.strip()
    if normalized.startswith("{"):
        return json.loads(normalized)
    data_lines = [line[len("data:"):].strip() for line in normalized.splitlines() if line.startswith("data:")]
    if not data_lines:
        raise RuntimeError("MCP server returned unsupported response content.")
    return json.loads(data_lines[-1])


def mcp_post(url, payload, session_id, api_key, protocol_version=None, expect_body=True):
    url = url.strip().rstrip("/")
    headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
    if protocol_version:
        headers["MCP-Protocol-Version"] = protocol_version
    if session_id:
        headers["Mcp-Session-Id"] = session_id
    if api_key:
        headers["Authorization"] = "Bearer " + api_key
    request = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            body = response.read().decode("utf-8")
            if expect_body and not body.strip():
                raise RuntimeError("MCP server returned an empty response body.")
            envelope = parse_mcp_body(body) if expect_body else {}
            if expect_body:
                if not isinstance(envelope, dict) or envelope.get("jsonrpc") != "2.0":
                    raise RuntimeError("MCP server returned an invalid JSON-RPC envelope.")
                if envelope.get("error"):
                    raise RuntimeError(f"MCP request failed: {envelope['error']}")
                result = envelope.get("result")
                if not isinstance(result, dict):
                    raise RuntimeError("MCP response did not include a result object.")
                error = mcp_result_error(result)
                if error:
                    raise RuntimeError(f"MCP tool returned an error result: {error}")
            return envelope, response.headers.get("Mcp-Session-Id", session_id)
    except urllib.error.HTTPError as exception:
        body = exception.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"MCP HTTP {exception.code}: {body}") from exception


def mcp_result_error(result):
    """Find typed MCP tool errors, including servers that serialize them as data."""
    if not isinstance(result, dict):
        return "invalid result"
    if result.get("isError"):
        return result

    def direct_error(value):
        if not isinstance(value, dict):
            return ""
        if "provider" in value or "degraded" in value:
            return ""
        error = value.get("error")
        if isinstance(error, str) and error.strip():
            return error.strip()
        if error and not isinstance(error, (str, int, float, bool)):
            return str(error)
        return ""

    structured_error = direct_error(result.get("structuredContent"))
    if structured_error:
        return structured_error
    structured = result.get("structuredContent")
    if isinstance(structured, dict):
        structured_result = structured.get("result")
        structured_error = direct_error(structured_result)
        if structured_error:
            return structured_error

    for item in result.get("content", []):
        if not isinstance(item, dict):
            continue
        text = str(item.get("text", "")).strip()
        if text.startswith("Error:"):
            return text
        try:
            parsed = json.loads(text)
        except (TypeError, json.JSONDecodeError):
            continue
        parsed_error = direct_error(parsed)
        if parsed_error:
            return parsed_error
    return ""


def graphiti_session(url, api_key):
    url = url.strip().rstrip("/")
    initialize, session_id = mcp_post(url, {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": MCP_PROTOCOL_VERSION,
            "capabilities": {},
            "clientInfo": {"name": "tavall-memory-seed", "version": "1"},
        },
    }, None, api_key)
    negotiated = initialize.get("result", {}).get("protocolVersion", "")
    if not negotiated:
        raise RuntimeError("Graphiti initialize did not negotiate a protocol version.")
    mcp_post(
        url,
        {"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}},
        session_id,
        api_key,
        negotiated,
        False,
    )
    return session_id, negotiated


def call_tool(url, session_id, api_key, protocol_version, request_id, name, arguments):
    response, session_id = mcp_post(url, {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": "tools/call",
        "params": {"name": name, "arguments": arguments},
    }, session_id, api_key, protocol_version)
    return response.get("result", {}), session_id


def fact_is_present(result, fact):
    expected = {
        "source": str(fact.get("sourceNode", "")).strip().casefold(),
        "edge": str(fact.get("edgeName", "")).strip().casefold(),
        "fact": str(fact.get("fact", "")).strip().casefold(),
        "target": str(fact.get("targetNode", "")).strip().casefold(),
    }

    def normalize_key(key):
        return "".join(character for character in str(key).casefold() if character.isalnum())

    def visit(value):
        if isinstance(value, dict):
            fields = {}
            for key, field_value in value.items():
                normalized_key = normalize_key(key)
                aliases = {
                    "sourcenode", "sourcenodename", "source",
                    "edgename", "edge",
                    "fact", "facttext",
                    "targetnode", "targetnodename", "target",
                }
                if normalized_key in aliases:
                    fields[normalized_key] = str(field_value).strip().casefold()
            source = next((fields[key] for key in ("sourcenode", "sourcenodename", "source") if key in fields), "")
            edge = next((fields[key] for key in ("edgename", "edge") if key in fields), "")
            fact_text = next((fields[key] for key in ("fact", "facttext") if key in fields), "")
            target = next((fields[key] for key in ("targetnode", "targetnodename", "target") if key in fields), "")
            if (source, edge, fact_text, target) == (
                expected["source"], expected["edge"], expected["fact"], expected["target"]
            ):
                return True
            return any(visit(child) for child in value.values())
        if isinstance(value, list):
            return any(visit(child) for child in value)
        return False

    if visit(result):
        return True
    flattened = json.dumps(result, sort_keys=True).casefold()
    return expected["fact"] and expected["fact"] in flattened


def import_graphiti(facts, url, api_key, execute):
    for fact in facts:
        print(f"Graphiti: {fact['sourceNode']} -[{fact['edgeName']}]-> {fact['targetNode']}")
    if not execute or not facts:
        return
    if not url:
        print("Graphiti URL not supplied; skipping temporal fact import.")
        return
    url = url.strip().rstrip("/")
    session_id, protocol_version = graphiti_session(url, api_key)
    for index, fact in enumerate(facts, start=10):
        group_id = fact.get("groupId", "tavall")
        existing, session_id = call_tool(
            url,
            session_id,
            api_key,
            protocol_version,
            index,
            "search_memory_facts",
            {"query": fact["fact"], "group_ids": [group_id], "max_facts": 10},
        )
        if fact_is_present(existing, fact):
            print(f"Graphiti: skip existing fact {fact['sourceNode']} -[{fact['edgeName']}]-> {fact['targetNode']}")
            continue
        source_uuid = str(uuid.uuid5(uuid.NAMESPACE_URL, "tavall-memory-seed:graphiti-node:" + group_id + ":" + fact["sourceNode"]))
        target_uuid = str(uuid.uuid5(uuid.NAMESPACE_URL, "tavall-memory-seed:graphiti-node:" + group_id + ":" + fact["targetNode"]))
        _, session_id = call_tool(
            url,
            session_id,
            api_key,
            protocol_version,
            index + 1000,
            "add_triplet",
            {
                "source_node_name": fact["sourceNode"],
                "edge_name": fact["edgeName"],
                "fact": fact["fact"],
                "target_node_name": fact["targetNode"],
                "group_id": group_id,
                "source_node_uuid": source_uuid,
                "target_node_uuid": target_uuid,
            },
        )


def main():
    args = parse_args()
    seed_root = Path(args.seed_root)
    documents = read_ndjson(seed_root / "qdrant" / "documents.ndjson")
    facts = read_ndjson(seed_root / "graphiti" / "facts.ndjson")
    validate_documents(documents)
    validate_facts(facts)
    mode = args.mode
    for document in documents:
        document["_real"] = mode == "real"
    vectors = real_vectors(documents) if mode == "real" else [mock_vector(document) for document in documents]
    print(f"Mode: {mode}; execute={args.execute}; Qdrant documents={len(documents)}; Graphiti facts={len(facts)}")
    import_qdrant(
        documents,
        vectors,
        args.qdrant_url,
        args.qdrant_api_key,
        args.knowledge_base,
        args.execute,
    )
    import_graphiti(facts, args.graphiti_url, args.graphiti_api_key, args.execute)
    print("Seed import complete." if args.execute else "Dry run complete; no writes performed.")


if __name__ == "__main__":
    main()
