#!/usr/bin/env python3

import json
import math
import sys


def normalize(values):
    magnitude = math.sqrt(sum(value * value for value in values))
    if magnitude == 0.0:
        return values
    return [value / magnitude for value in values]


def read_text(payload):
    title = (payload.get("title") or "").strip()
    text = (payload.get("text") or "").strip()
    if title:
        return f"{title}\n{text}".strip()
    return text


def main():
    payload = json.load(sys.stdin)
    try:
        from fastembed import TextEmbedding
    except ModuleNotFoundError as exception:
        raise SystemExit(
            "fastembed is not installed. Run `python3 -m pip install fastembed` on the remote host."
        ) from exception

    model_name = payload.get("model") or "BAAI/bge-small-en-v1.5"
    dimensions = int(payload.get("dimensions") or 384)
    embedder = TextEmbedding(model_name=model_name)
    values = list(embedder.embed([read_text(payload)]))[0]
    vector = [float(value) for value in values]
    if len(vector) < dimensions:
        raise SystemExit("FastEmbed returned fewer dimensions than the Qdrant collection expects.")
    vector = normalize(vector[:dimensions])
    json.dump({"provider": "local", "model": model_name, "vector": vector}, sys.stdout)


if __name__ == "__main__":
    main()
