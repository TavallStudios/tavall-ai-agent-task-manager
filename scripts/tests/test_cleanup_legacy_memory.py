#!/usr/bin/env python3

import importlib.util
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "cleanup_legacy_memory.py"
SPEC = importlib.util.spec_from_file_location("cleanup_legacy_memory", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CleanupLegacyMemoryTest(unittest.TestCase):

    def test_raw_interaction_kinds_are_candidates(self):
        self.assertEqual(
            "legacy-raw-interaction",
            MODULE.qdrant_reason({"kind": "mcp-tool-result [chunk 1]"}),
        )

    def test_legacy_memory_id_without_explicit_write_mode_is_candidate(self):
        self.assertEqual(
            "old-memory-without-explicit-write-mode",
            MODULE.qdrant_reason({"kind": "Correction [chunk 1]", "memoryId": "mem_old"}),
        )
        self.assertEqual(
            "old-memory-without-explicit-write-mode",
            MODULE.qdrant_reason({"kind": "Task continuity", "documentId": "mem_old"}),
        )

    def test_explicit_memory_is_retained(self):
        self.assertEqual(
            "",
            MODULE.qdrant_reason({
                "kind": "Correction [chunk 1]",
                "memoryId": "mem_explicit",
                "writeMode": "explicit",
            }),
        )

    def test_unrelated_semantic_document_is_retained(self):
        self.assertEqual(
            "",
            MODULE.qdrant_reason({
                "kind": "architecture-rule",
                "documentId": "atm-memory-architecture",
                "writeMode": "",
            }),
        )


if __name__ == "__main__":
    unittest.main()
