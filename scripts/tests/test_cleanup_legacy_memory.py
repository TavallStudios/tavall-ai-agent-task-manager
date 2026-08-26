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

    def test_ide_metadata_is_candidate_for_cleanup(self):
        self.assertEqual(
            "excluded-ide-metadata",
            MODULE.qdrant_reason({"sourcePath": ".idea/dataSources.xml", "kind": "project-reindex"}),
        )
        self.assertEqual(
            "excluded-ide-metadata",
            MODULE.semantic_outbox_reason({
                "operationKind": "project-upsert",
                "semanticKind": "project-reindex",
                "title": ".idea/dataSources.xml",
                "payload": {},
            }),
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

    def test_pending_legacy_semantic_outbox_write_is_candidate(self):
        self.assertEqual(
            "legacy-pending-semantic-write",
            MODULE.semantic_outbox_reason({
                "operationKind": "project-upsert",
                "semanticKind": "prompt-thread-snapshot",
                "documentId": "snapshot-1",
                "payload": {},
            }),
        )
        self.assertEqual(
            "old-memory-pending-semantic-write",
            MODULE.semantic_outbox_reason({
                "operationKind": "project-upsert",
                "semanticKind": "memory-preference",
                "documentId": "memory-1",
                "payload": {},
            }),
        )

    def test_explicit_semantic_outbox_write_and_delete_are_retained(self):
        self.assertEqual(
            "",
            MODULE.semantic_outbox_reason({
                "operationKind": "project-upsert",
                "semanticKind": "memory-project_state",
                "documentId": "mem_explicit",
                "payload": {"writeMode": "explicit"},
            }),
        )
        self.assertEqual(
            "orphaned-fixture-delete",
            MODULE.semantic_outbox_reason({
                "operationKind": "project-delete",
                "scopeKey": "fixture-repo-fa1378",
                "status": "in_progress",
                "payload": {},
            }),
        )
        self.assertEqual(
            "",
            MODULE.semantic_outbox_reason({
                "operationKind": "project-delete",
                "semanticKind": "prompt-thread-snapshot",
                "documentId": "snapshot-1",
                "payload": {},
            }),
        )
        self.assertEqual(
            "",
            MODULE.semantic_outbox_reason({
                "operationKind": "project-upsert",
                "scopeKey": "fixture-repo-fa1378",
                "semanticKind": "memory-project_state",
                "documentId": "mem_explicit",
                "payload": {"writeMode": "explicit"},
            }),
        )

    def test_fixture_semantic_outbox_write_is_candidate(self):
        self.assertEqual(
            "fixture-pending-semantic-write",
            MODULE.semantic_outbox_reason({
                "operationKind": "project-upsert",
                "scopeKey": "fixture-repo-fa1378",
                "semanticKind": "project-reindex",
                "documentId": "README.md",
                "payload": {},
            }),
        )


if __name__ == "__main__":
    unittest.main()
