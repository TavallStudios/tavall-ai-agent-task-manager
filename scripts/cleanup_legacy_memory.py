#!/usr/bin/env python3

"""Dry-run-first cleanup for pre-explicit-memory durable artifacts.

The script deliberately scopes Qdrant work to real BGE profile collections and
never deletes fixture vectors. Postgres candidates must be implicit records
with a linked legacy mutation, or queued/in-progress semantic outbox writes
that would recreate legacy raw interaction or non-explicit memory points.
Explicit records and explicit semantic outbox writes are retained. Redis
cleanup only removes the exact-state working-memory namespace, because those
values are derived caches and must be rebuilt from Postgres after the
mutation.
"""

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from collections import Counter, defaultdict


REAL_PROFILE = "__local_baai_bge_small_en_v1_5_384"
WORKING_MEMORY_PATTERN = "tavall-ai:memory-runtime:working:*"
LEGACY_MUTATION_ACTIONS = (
    "CLOSE_TASK",
    "SUPERSEDE_MEMORY",
    "UPDATE_EXISTING_MEMORY",
    "UPSERT_SEMANTIC_MEMORY",
)
LEGACY_QDRANT_KINDS = {
    "mcp-memory-lookup",
    "mcp-memory-lookup-failure",
    "mcp-prompt-failure",
    "mcp-prompt-request",
    "mcp-prompt-result",
    "mcp-resource-failure",
    "mcp-resource-request",
    "mcp-resource-result",
    "mcp-tool-failure",
    "mcp-tool-request",
    "mcp-tool-result",
    "prompt-request",
    "prompt-thread-message",
    "prompt-thread-snapshot",
    "worker-final-response",
    "worker-prompt-request",
    "worker-thread-message",
}
LEGACY_QDRANT_KIND_PREFIXES = ("bridge-", "codex-", "worker-")


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--qdrant-url",
        default=os.getenv("AGENT_TASK_MANAGER_QDRANT_BASE_URL", ""),
        help="Qdrant base URL; defaults to AGENT_TASK_MANAGER_QDRANT_BASE_URL.",
    )
    parser.add_argument(
        "--qdrant-api-key",
        default=os.getenv("AGENT_TASK_MANAGER_QDRANT_API_KEY", ""),
        help="Optional Qdrant API key.",
    )
    parser.add_argument(
        "--postgres-url",
        default=os.getenv("DB_URL", ""),
        help="Postgres URL; JDBC prefix is accepted and defaults to DB_URL.",
    )
    parser.add_argument(
        "--postgres-user",
        default=os.getenv("DB_USER", ""),
        help="Postgres user; defaults to DB_USER.",
    )
    parser.add_argument(
        "--redis-host",
        default=os.getenv("AGENT_TASK_MANAGER_REDIS_HOST", "127.0.0.1"),
    )
    parser.add_argument(
        "--redis-port",
        default=os.getenv("AGENT_TASK_MANAGER_REDIS_PORT", "6379"),
    )
    parser.add_argument(
        "--redis-db",
        default=os.getenv("AGENT_TASK_MANAGER_REDIS_DB", "5"),
    )
    parser.add_argument(
        "--skip-postgres",
        action="store_true",
        help="Skip Postgres inspection; useful only for an isolated Qdrant audit.",
    )
    parser.add_argument(
        "--skip-redis",
        action="store_true",
        help="Skip Redis inspection; useful only for an isolated provider audit.",
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Apply the exact dry-run classification. Omit for a read-only report.",
    )
    return parser.parse_args()


def qdrant_request(base_url, path, method="GET", body=None, api_key=""):
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
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exception:
        detail = exception.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Qdrant HTTP {exception.code} for {path}: {detail}") from exception
    except urllib.error.URLError as exception:
        raise RuntimeError(f"Qdrant request failed for {path}: {exception.reason}") from exception
    try:
        payload = json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError as exception:
        raise RuntimeError(f"Qdrant returned invalid JSON for {path}: {raw[:400]}") from exception
    if not isinstance(payload, dict) or payload.get("status") == "error" or "result" not in payload:
        raise RuntimeError(f"Qdrant returned a malformed response for {path}: {payload}")
    return payload["result"]


def normalized_kind(payload):
    return str(payload.get("kind", "")).split(" [chunk", 1)[0]


def qdrant_reason(payload):
    if payload.get("writeMode") == "explicit":
        return ""
    kind = normalized_kind(payload)
    if kind in LEGACY_QDRANT_KINDS or kind.startswith(LEGACY_QDRANT_KIND_PREFIXES):
        return "legacy-raw-interaction"
    memory_id = str(payload.get("memoryId", ""))
    document_id = str(payload.get("documentId", ""))
    if kind.startswith("memory-") or memory_id.startswith("mem_") or document_id.startswith("mem_"):
        return "old-memory-without-explicit-write-mode"
    return ""


def semantic_outbox_reason(entry):
    """Classify only pending non-explicit writes that would recreate legacy data."""
    if entry.get("operationKind") not in ("project-upsert", "knowledge-upsert"):
        return ""
    payload = entry.get("payload") or {}
    if payload.get("writeMode") == "explicit":
        return ""
    if str(entry.get("scopeKey", "")).startswith("fixture-"):
        return "fixture-pending-semantic-write"
    kind = str(entry.get("semanticKind", "")).split(" [chunk", 1)[0]
    document_id = str(entry.get("documentId", ""))
    if kind in LEGACY_QDRANT_KINDS or kind.startswith(LEGACY_QDRANT_KIND_PREFIXES):
        return "legacy-pending-semantic-write"
    if kind.startswith("memory-") or document_id.startswith("mem_"):
        return "old-memory-pending-semantic-write"
    return ""


def inspect_qdrant(base_url, api_key):
    collections = qdrant_request(base_url, "/collections", api_key=api_key).get("collections", [])
    report = {
        "profile": REAL_PROFILE,
        "collectionsScanned": 0,
        "pointsScanned": 0,
        "candidatePoints": 0,
        "candidateByReason": Counter(),
        "candidateByKind": Counter(),
        "candidateByCollection": defaultdict(Counter),
        "pointsByCollectionBefore": {},
        "candidates": [],
    }
    for collection in sorted(collections, key=lambda item: item.get("name", "")):
        name = collection.get("name", "")
        if not name.endswith(REAL_PROFILE):
            continue
        report["collectionsScanned"] += 1
        info = qdrant_request(base_url, f"/collections/{name}", api_key=api_key)
        report["pointsByCollectionBefore"][name] = info.get("points_count", 0)
        offset = None
        while True:
            body = {"limit": 256, "with_payload": True, "with_vector": False}
            if offset is not None:
                body["offset"] = offset
            result = qdrant_request(
                base_url,
                f"/collections/{name}/points/scroll",
                method="POST",
                body=body,
                api_key=api_key,
            )
            points = result.get("points", [])
            for point in points:
                report["pointsScanned"] += 1
                payload = point.get("payload") or {}
                reason = qdrant_reason(payload)
                if not reason:
                    continue
                kind = normalized_kind(payload)
                report["candidatePoints"] += 1
                report["candidateByReason"][reason] += 1
                report["candidateByKind"][kind] += 1
                report["candidateByCollection"][name][reason] += 1
                report["candidates"].append(
                    {
                        "collection": name,
                        "pointId": point.get("id"),
                        "kind": kind,
                        "reason": reason,
                        "projectKey": payload.get("projectKey", ""),
                        "threadKey": payload.get("threadKey", ""),
                        "documentId": payload.get("documentId", ""),
                    }
                )
            offset = result.get("next_page_offset")
            if not points or offset is None:
                break
    return report


def apply_qdrant(report, base_url, api_key):
    deleted = Counter()
    by_collection = defaultdict(list)
    for candidate in report["candidates"]:
        by_collection[candidate["collection"]].append(candidate["pointId"])
    for collection, point_ids in by_collection.items():
        for start in range(0, len(point_ids), 256):
            batch = point_ids[start : start + 256]
            qdrant_request(
                base_url,
                f"/collections/{collection}/points/delete",
                method="POST",
                body={"points": batch, "wait": True},
                api_key=api_key,
            )
            deleted[collection] += len(batch)
    return deleted


def postgres_command(args):
    if not args.postgres_url:
        raise RuntimeError("Postgres URL is required unless --skip-postgres is supplied.")
    url = args.postgres_url.removeprefix("jdbc:")
    command = ["psql", "-X", "-qAt", "--dbname", url]
    if args.postgres_user:
        command.extend(["--username", args.postgres_user])
    environment = os.environ.copy()
    if environment.get("DB_PASS") and not environment.get("PGPASSWORD"):
        environment["PGPASSWORD"] = environment["DB_PASS"]
    return command, environment


def run_psql(args, sql):
    command, environment = postgres_command(args)
    completed = subprocess.run(
        command + ["-c", sql],
        env=environment,
        check=False,
        capture_output=True,
        text=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"Postgres query failed: {completed.stderr.strip()}")
    return completed.stdout


def inspect_postgres(args):
    candidate_sql = """
SELECT
  memory.memory_id,
  memory.project_id,
  memory.kind,
  memory.title,
  memory.status,
  memory.consent_level,
  COALESCE(memory.metadata->>'writeMode', '<missing>'),
  memory.created_at::text
FROM agent_task_manager.memory_records AS memory
WHERE memory.consent_level = 'implicit'
  AND memory.metadata->>'writeMode' IS DISTINCT FROM 'explicit'
  AND EXISTS (
    SELECT 1
    FROM agent_task_manager.memory_mutations AS mutation
    WHERE mutation.memory_id = memory.memory_id
      AND mutation.action = ANY (ARRAY['CLOSE_TASK', 'SUPERSEDE_MEMORY', 'UPDATE_EXISTING_MEMORY', 'UPSERT_SEMANTIC_MEMORY'])
  )
ORDER BY memory.created_at, memory.memory_id
"""
    rows = []
    for line in run_psql(args, candidate_sql).splitlines():
        fields = line.split("|")
        if len(fields) != 8:
            raise RuntimeError(f"Unexpected Postgres candidate row: {line}")
        rows.append(
            {
                "memoryId": fields[0],
                "projectId": fields[1],
                "kind": fields[2],
                "title": fields[3],
                "status": fields[4],
                "consentLevel": fields[5],
                "writeMode": fields[6],
                "createdAt": fields[7],
            }
        )
    totals = run_psql(
        args,
        "SELECT consent_level, count(*) FROM agent_task_manager.memory_records GROUP BY consent_level ORDER BY consent_level",
    ).splitlines()
    explicit_count = run_psql(
        args,
        "SELECT count(*) FROM agent_task_manager.memory_records WHERE consent_level='explicit'",
    ).strip()
    return {
        "candidateRecords": rows,
        "candidateCount": len(rows),
        "consentCounts": totals,
        "explicitRecordCount": int(explicit_count or "0"),
    }


def inspect_semantic_outbox(args):
    sql = """
SELECT COALESCE(
  json_agg(
    json_build_object(
      'outboxId', outbox_id,
      'operationKind', operation_kind,
      'scopeKey', scope_key,
      'documentId', document_id,
      'semanticKind', semantic_kind,
      'title', title,
      'status', status,
      'payload', payload
    ) ORDER BY created_at, outbox_id
  ),
  '[]'::json
)
FROM agent_task_manager.semantic_sync_outbox
WHERE status = ANY (ARRAY['queued', 'in_progress'])
"""
    raw = run_psql(args, sql).strip()
    try:
        entries = json.loads(raw or "[]")
    except json.JSONDecodeError as exception:
        raise RuntimeError(f"Unexpected semantic outbox JSON: {raw[:400]}") from exception
    if not isinstance(entries, list):
        raise RuntimeError("Semantic outbox query did not return a JSON array.")
    candidates = []
    by_reason = Counter()
    by_kind = Counter()
    for entry in entries:
        reason = semantic_outbox_reason(entry)
        if not reason:
            continue
        candidate = {
            "outboxId": entry.get("outboxId", ""),
            "operationKind": entry.get("operationKind", ""),
            "scopeKey": entry.get("scopeKey", ""),
            "documentId": entry.get("documentId", ""),
            "semanticKind": entry.get("semanticKind", ""),
            "status": entry.get("status", ""),
            "reason": reason,
        }
        candidates.append(candidate)
        by_reason[reason] += 1
        by_kind[str(entry.get("semanticKind", ""))] += 1
    return {
        "pendingCount": len(entries),
        "candidateCount": len(candidates),
        "candidateByReason": by_reason,
        "candidateByKind": by_kind,
        "candidates": candidates,
    }


def sql_literals(values):
    if not values:
        return "NULL"
    return ", ".join("'" + str(value).replace("'", "''") + "'" for value in values)


def apply_postgres(args, memory_candidates, outbox_candidates):
    memory_ids = sql_literals([candidate["memoryId"] for candidate in memory_candidates])
    outbox_ids = sql_literals([candidate["outboxId"] for candidate in outbox_candidates])
    sql = f"""
WITH deleted_mutations AS (
  DELETE FROM agent_task_manager.memory_mutations AS mutation
  WHERE mutation.memory_id IN ({memory_ids})
  RETURNING 1
), deleted_memories AS (
  DELETE FROM agent_task_manager.memory_records AS memory
  WHERE memory.memory_id IN ({memory_ids})
  RETURNING 1
), deleted_outbox AS (
  DELETE FROM agent_task_manager.semantic_sync_outbox AS outbox
  WHERE outbox.outbox_id IN ({outbox_ids})
  RETURNING 1
)
SELECT
  (SELECT count(*) FROM deleted_mutations),
  (SELECT count(*) FROM deleted_memories),
  (SELECT count(*) FROM deleted_outbox);
"""
    fields = run_psql(args, sql).strip().split("|")
    if len(fields) != 3:
        raise RuntimeError(f"Unexpected Postgres cleanup counts: {fields}")
    return {
        "deletedMutationCount": int(fields[0]),
        "deletedRecordCount": int(fields[1]),
        "deletedOutboxCount": int(fields[2]),
    }


def redis_command(args):
    command = [
        "redis-cli",
        "--raw",
        "-h",
        args.redis_host,
        "-p",
        str(args.redis_port),
        "-n",
        str(args.redis_db),
    ]
    return command, os.environ.copy()


def inspect_redis(args):
    command, environment = redis_command(args)
    completed = subprocess.run(
        command + ["--scan", "--pattern", WORKING_MEMORY_PATTERN],
        env=environment,
        check=False,
        capture_output=True,
        text=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"Redis scan failed: {completed.stderr.strip()}")
    keys = [line for line in completed.stdout.splitlines() if line]
    return {"pattern": WORKING_MEMORY_PATTERN, "keyCount": len(keys), "keys": keys}


def apply_redis(args, keys):
    if not keys:
        return 0
    command, environment = redis_command(args)
    deleted = 0
    for start in range(0, len(keys), 256):
        completed = subprocess.run(
            command + ["DEL", *keys[start : start + 256]],
            env=environment,
            check=False,
            capture_output=True,
            text=True,
        )
        if completed.returncode != 0:
            raise RuntimeError(f"Redis delete failed: {completed.stderr.strip()}")
        deleted += int(completed.stdout.strip() or "0")
    return deleted


def json_ready(value):
    if isinstance(value, Counter):
        return dict(value)
    if isinstance(value, defaultdict):
        return {key: json_ready(item) for key, item in value.items()}
    if isinstance(value, dict):
        return {key: json_ready(item) for key, item in value.items()}
    if isinstance(value, list):
        return [json_ready(item) for item in value]
    return value


def summarized_report(report, candidate_key):
    summarized = dict(report)
    candidates = summarized.pop(candidate_key, [])
    summarized["candidateSample"] = candidates[:20]
    return summarized


def main():
    args = parse_args()
    if not args.qdrant_url:
        raise RuntimeError("Qdrant URL is required; refusing to run without a durable target.")
    report = {"mode": "execute" if args.execute else "dry-run"}
    qdrant = inspect_qdrant(args.qdrant_url, args.qdrant_api_key)
    report["qdrant"] = summarized_report(qdrant, "candidates")
    if not args.skip_postgres:
        postgres = inspect_postgres(args)
        report["postgres"] = summarized_report(postgres, "candidateRecords")
        outbox = inspect_semantic_outbox(args)
        report["semanticOutbox"] = summarized_report(outbox, "candidates")
    if not args.skip_redis:
        redis = inspect_redis(args)
        report["redis"] = redis
    if args.execute:
        report["qdrant"]["deletedByCollection"] = dict(
            apply_qdrant(qdrant, args.qdrant_url, args.qdrant_api_key)
        )
        if not args.skip_postgres:
            applied = apply_postgres(
                args,
                postgres["candidateRecords"],
                outbox["candidates"],
            )
            report["postgres"].update(applied)
            report["postgres"]["applied"] = True
            report["semanticOutbox"]["applied"] = True
        if not args.skip_redis:
            report["redis"]["deletedKeyCount"] = apply_redis(args, report["redis"]["keys"])
            report["redis"]["remainingKeyCount"] = inspect_redis(args)["keyCount"]
    print(json.dumps(json_ready(report), indent=2, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError) as exception:
        print(f"cleanup_legacy_memory.py: {exception}", file=sys.stderr)
        sys.exit(2)
