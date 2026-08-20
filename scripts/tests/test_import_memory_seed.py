#!/usr/bin/env python3

import importlib.util
import math
import tempfile
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "import_memory_seed.py"
SPEC = importlib.util.spec_from_file_location("import_memory_seed", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ImportMemorySeedTest(unittest.TestCase):

    def setUp(self):
        self.document = {
            "id": "test-memory",
            "projectKey": "Tavall AI/Test",
            "domain": "KNOWLEDGE_RULES",
            "kind": "architecture-rule",
            "title": "Test memory",
            "contentType": "DOCUMENTATION",
            "text": "A deterministic fixture document.",
            "metadata": {"scope": "PROJECT", "status": "active", "tombstoned": False},
        }

    def test_real_and_mock_collections_are_isolated(self):
        real = MODULE.collection_name(self.document, "real")
        mock = MODULE.collection_name(self.document, "mock")
        self.assertTrue(real.endswith("__local_baai_bge_small_en_v1_5_384"))
        self.assertTrue(mock.endswith("__fixture_mock_384"))
        self.assertNotEqual(real, mock)

    def test_global_seed_documents_use_the_configured_knowledge_collection(self):
        global_document = {
            **self.document,
            "metadata": {"scope": "GLOBAL"},
        }
        self.assertEqual(
            "agent_task_manager_knowledge_tavall_org_knowledge__local_baai_bge_small_en_v1_5_384",
            MODULE.knowledge_collection_name("tavall-org", "real"),
        )
        self.assertTrue(MODULE.is_global_document(global_document))
        payload = MODULE.point_payload(global_document, "real", "tavall-org")
        self.assertEqual("tavall-org", payload["knowledgeBase"])

    def test_mock_vector_is_deterministic_normalized_and_384_dimensions(self):
        first = MODULE.mock_vector(self.document)
        second = MODULE.mock_vector(self.document)
        self.assertEqual(first, second)
        self.assertEqual(384, len(first))
        magnitude = math.sqrt(sum(value * value for value in first))
        self.assertAlmostEqual(1.0, magnitude, places=9)

    def test_payload_preserves_semantic_provenance(self):
        payload = MODULE.point_payload(self.document, "mock")
        self.assertEqual("fixture-mock", payload["embeddingProvider"])
        self.assertEqual("deterministic-sha256-fixture", payload["embeddingModel"])
        self.assertEqual("RETRIEVAL_DOCUMENT", payload["embeddingPurpose"])
        self.assertEqual(self.document["text"], payload["chunkText"])
        self.assertFalse(payload["tombstoned"])

    def test_sanitize_matches_java_collection_style(self):
        self.assertEqual("tavall_ai_test", MODULE.sanitize("Tavall AI/Test"))
        self.assertEqual("default", MODULE.sanitize("---"))

    def test_seed_validation_rejects_missing_and_duplicate_documents(self):
        with self.assertRaisesRegex(ValueError, "missing: text"):
            MODULE.validate_documents([{**self.document, "text": ""}])
        with self.assertRaisesRegex(ValueError, "duplicate document id"):
            MODULE.validate_documents([self.document, dict(self.document)])

    def test_seed_validation_rejects_unknown_domains_and_incomplete_facts(self):
        with self.assertRaisesRegex(ValueError, "Unsupported semantic domain"):
            MODULE.validate_documents([{**self.document, "domain": "UNKNOWN"}])
        with self.assertRaisesRegex(ValueError, "missing: targetNode"):
            MODULE.validate_facts([{
                "sourceNode": "a",
                "edgeName": "USES",
                "fact": "a uses b",
                "targetNode": "",
            }])

    def test_ndjson_parser_reports_malformed_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "broken.ndjson"
            path.write_text("{not-json\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "broken.ndjson:1 is not valid JSON"):
                MODULE.read_ndjson(path)

    def test_qdrant_response_validation_is_fail_closed(self):
        MODULE.validate_qdrant_response({"result": {}}, "/collections/example")
        with self.assertRaisesRegex(RuntimeError, "did not include result"):
            MODULE.validate_qdrant_response({"status": "ok"}, "/collections/example")
        with self.assertRaisesRegex(RuntimeError, "malformed or error"):
            MODULE.validate_qdrant_response({"status": "error"}, "/collections/example")

    def test_mcp_result_error_detects_serialized_provider_errors(self):
        self.assertEqual("Graphiti unavailable", MODULE.mcp_result_error({
            "isError": False,
            "structuredContent": {"result": {"error": "Graphiti unavailable"}},
        }))
        self.assertEqual("Error: malformed", MODULE.mcp_result_error({
            "content": [{"type": "text", "text": "Error: malformed"}],
        }))
        self.assertEqual("", MODULE.mcp_result_error({
            "structuredContent": {"result": {"message": "ok", "facts": []}},
        }))
        self.assertEqual("", MODULE.mcp_result_error({
            "content": [{"type": "text", "text": "{\"providerContexts\":[{\"error\":\"degraded\"}]}"}],
        }))
        self.assertEqual("", MODULE.mcp_result_error({
            "content": [{"type": "text", "text": "{\"provider\":\"graphify\",\"degraded\":true,\"error\":\"down\"}"}],
        }))

    def test_graphiti_duplicate_detection_requires_all_fact_parts(self):
        fact = {
            "sourceNode": "source",
            "edgeName": "USES",
            "fact": "source uses target",
            "targetNode": "target",
        }
        self.assertTrue(MODULE.fact_is_present({"content": [fact]}, fact))
        self.assertFalse(MODULE.fact_is_present({"content": [dict(fact, fact="source uses other", targetNode="other")]}, fact))


if __name__ == "__main__":
    unittest.main()
