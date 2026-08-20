#!/usr/bin/env python3

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any


DEFAULT_MODEL = "BAAI/bge-small-en-v1.5"
DEFAULT_DIMENSIONS = 384
DEFAULT_PREFIXES = ("agent_task_manager_project", "agent_task_manager_knowledge")


def normalize_url(value: str) -> str:
    return value.rstrip("/")


def sanitize_profile(raw_value: str) -> str:
    normalized = raw_value.strip().lower()
    normalized = re.sub(r"[^a-z0-9]+", "_", normalized)
    normalized = re.sub(r"^_+|_+$", "", normalized)
    return normalized or "default"


def local_profile(model: str, dimensions: int) -> str:
    return sanitize_profile(f"local_{model}_{dimensions}")


def target_collection(source_collection: str, model: str, dimensions: int) -> str:
    return f"{source_collection}__{local_profile(model, dimensions)}"


@dataclass(frozen=True)
class MigrationResult:
    source: str
    target: str
    migrated: int
    skipped: int


class QdrantRestClient:
    def __init__(self, base_url: str, api_key: str = "") -> None:
        self.base_url = normalize_url(base_url)
        self.api_key = api_key.strip()

    def request(self, path: str, method: str = "GET", body: Any | None = None) -> dict[str, Any]:
        data = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["api-key"] = self.api_key
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=data,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = response.read().decode("utf-8")
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as exception:
            payload = exception.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Qdrant {method} {path} failed ({exception.code}): {payload}") from exception

    def collections(self) -> list[str]:
        payload = self.request("/collections")
        result = payload.get("result", {})
        return [item["name"] for item in result.get("collections", []) if item.get("name")]

    def ensure_collection(self, collection: str, dimensions: int) -> None:
        try:
            self.request(
                f"/collections/{collection}",
                method="PUT",
                body={"vectors": {"size": dimensions, "distance": "Cosine"}},
            )
        except RuntimeError as exception:
            if "409" not in str(exception):
                raise

    def scroll(self, collection: str, offset: Any | None = None, limit: int = 256) -> tuple[list[dict[str, Any]], Any | None]:
        body: dict[str, Any] = {
            "limit": limit,
            "with_payload": True,
            "with_vector": False,
        }
        if offset is not None:
            body["offset"] = offset
        payload = self.request(f"/collections/{collection}/points/scroll", method="POST", body=body)
        result = payload.get("result", {})
        return result.get("points", []), result.get("next_page_offset")

    def upsert(self, collection: str, points: list[dict[str, Any]]) -> None:
        self.request(
            f"/collections/{collection}/points?wait=true",
            method="PUT",
            body={"points": points},
        )


def discover_sources(
    all_collections: list[str],
    explicit_sources: list[str],
    source_prefixes: list[str],
) -> list[str]:
    if explicit_sources:
        return sorted(dict.fromkeys(explicit_sources))
    prefixes = source_prefixes or list(DEFAULT_PREFIXES)
    return sorted(
        collection
        for collection in all_collections
        if "__" not in collection and any(collection.startswith(prefix) for prefix in prefixes)
    )


def chunk_text(payload: dict[str, Any]) -> str:
    return str(payload.get("chunkText") or payload.get("body") or "").strip()


def chunk_title(payload: dict[str, Any]) -> str:
    return str(payload.get("title") or payload.get("kind") or "").strip()


def load_embedder(model: str):
    try:
        from fastembed import TextEmbedding
    except ModuleNotFoundError as exception:
        raise RuntimeError("fastembed is not installed. Run `python3 -m pip install fastembed`.") from exception
    return TextEmbedding(model_name=model)


def normalized_vector(values: Any, dimensions: int) -> list[float]:
    vector = [float(value) for value in values]
    if len(vector) != dimensions:
        raise RuntimeError(
            f"Local model returned {len(vector)} dimensions but the migration expects {dimensions}. "
            "Use the model's native dimension or change --dimensions."
        )
    magnitude = sum(value * value for value in vector) ** 0.5
    if magnitude == 0.0:
        return vector
    return [value / magnitude for value in vector]


def migrate_collection(
    client: QdrantRestClient,
    source: str,
    model: str,
    dimensions: int,
    batch_size: int,
    execute: bool,
    embedder: Any | None,
) -> MigrationResult:
    target = target_collection(source, model, dimensions)
    if not execute:
        return MigrationResult(source, target, 0, 0)

    client.ensure_collection(target, dimensions)
    migrated = 0
    skipped = 0
    offset: Any | None = None

    while True:
        points, next_offset = client.scroll(source, offset=offset)
        candidates: list[tuple[dict[str, Any], str]] = []
        for point in points:
            payload = dict(point.get("payload") or {})
            text = chunk_text(payload)
            if not text:
                skipped += 1
                continue
            title = chunk_title(payload)
            input_text = f"{title}\n{text}".strip() if title else text
            candidates.append((point, input_text))

        for start in range(0, len(candidates), batch_size):
            batch = candidates[start : start + batch_size]
            vectors = list(embedder.embed([text for _, text in batch]))
            upserts: list[dict[str, Any]] = []
            for (point, _), raw_vector in zip(batch, vectors, strict=True):
                payload = dict(point.get("payload") or {})
                payload["embeddingProvider"] = "local"
                payload["embeddingModel"] = model
                payload["embeddingDimensions"] = dimensions
                payload["embeddingPurpose"] = "RETRIEVAL_DOCUMENT"
                payload["reembeddedFromCollection"] = source
                upserts.append(
                    {
                        "id": point["id"],
                        "vector": normalized_vector(raw_vector, dimensions),
                        "payload": payload,
                    }
                )
            if upserts:
                client.upsert(target, upserts)
                migrated += len(upserts)

        if next_offset is None:
            break
        offset = next_offset

    return MigrationResult(source, target, migrated, skipped)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Re-embed legacy Tavall AI Qdrant memory into profile-isolated local FastEmbed collections."
    )
    parser.add_argument(
        "--qdrant-url",
        default=os.environ.get("AGENT_TASK_MANAGER_QDRANT_BASE_URL", ""),
        help="Qdrant base URL. Defaults to AGENT_TASK_MANAGER_QDRANT_BASE_URL.",
    )
    parser.add_argument(
        "--api-key",
        default=os.environ.get("AGENT_TASK_MANAGER_QDRANT_API_KEY", ""),
        help="Optional Qdrant API key.",
    )
    parser.add_argument("--source", action="append", default=[], help="Exact source collection. Repeat as needed.")
    parser.add_argument(
        "--source-prefix",
        action="append",
        default=[],
        help="Migrate unsuffixed collections matching this prefix. Repeat as needed.",
    )
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--dimensions", type=int, default=DEFAULT_DIMENSIONS)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Perform writes. Without this flag the command is a dry-run and only reports source/target collections.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.qdrant_url:
        print("Qdrant URL is required via --qdrant-url or AGENT_TASK_MANAGER_QDRANT_BASE_URL.", file=sys.stderr)
        return 2
    if args.dimensions <= 0 or args.batch_size <= 0:
        print("--dimensions and --batch-size must be positive.", file=sys.stderr)
        return 2

    client = QdrantRestClient(args.qdrant_url, args.api_key)
    sources = discover_sources(client.collections(), args.source, args.source_prefix)
    if not sources:
        print("No legacy Qdrant collections matched. Nothing to migrate.")
        return 0

    embedder = load_embedder(args.model) if args.execute else None
    mode = "EXECUTE" if args.execute else "DRY-RUN"
    print(f"[{mode}] local embedding profile: {local_profile(args.model, args.dimensions)}")

    total_migrated = 0
    total_skipped = 0
    for source in sources:
        result = migrate_collection(
            client,
            source,
            args.model,
            args.dimensions,
            args.batch_size,
            args.execute,
            embedder,
        )
        print(
            f"{result.source} -> {result.target}: "
            f"migrated={result.migrated}, skipped={result.skipped}"
        )
        total_migrated += result.migrated
        total_skipped += result.skipped

    if args.execute:
        print(f"Completed local re-embedding: migrated={total_migrated}, skipped={total_skipped}")
    else:
        print("Dry-run only. Re-run with --execute after reviewing the target collection names.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
