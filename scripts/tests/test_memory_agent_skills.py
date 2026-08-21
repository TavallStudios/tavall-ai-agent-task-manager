from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE_SKILLS = (
    'tavall-memory-bootstrap',
    'tavall-memory-investigation',
    'tavall-memory-writeback',
    'tavall-memory-review',
    'tavall-memory-validation',
)


class MemoryAgentSkillContractTest(unittest.TestCase):
    def test_source_skills_use_tavall_cloud_v2_without_changing_memory_authority(self):
        for skill_name in SOURCE_SKILLS:
            body = (ROOT / '.agents' / 'skills' / skill_name / 'SKILL.md').read_text()
            self.assertIn('## Tavall Cloud v2 execution plane', body, skill_name)
            self.assertIn('cloud_dev_session_bootstrap', body, skill_name)
            self.assertIn('cloud_catalog_invoke', body, skill_name)
            self.assertIn('Tavall Cloud is not a memory authority', body, skill_name)

    def test_unified_chatgpt_skill_preserves_all_modes_and_cloud_execution_contract(self):
        path = ROOT / 'plugins' / 'tavall-ai' / 'skills' / 'tavall-memory-plane' / 'SKILL.md'
        body = path.read_text()
        self.assertIn('name: tavall-memory-plane', body)
        for mode in ('BOOTSTRAP', 'INVESTIGATION', 'WRITEBACK', 'REVIEW', 'VALIDATION'):
            self.assertIn(f'**{mode}**', body)
        for token in ('@Tavall Cloud v2', 'memoryContext', 'recordMemory', 'cloud_dev_session_bootstrap', 'cloud_catalog_invoke'):
            self.assertIn(token, body)

    def test_docs_point_to_the_repo_owned_unified_wrapper(self):
        body = (ROOT / 'docs' / 'MEMORY_AGENT_SKILLS.md').read_text()
        self.assertIn('plugins/tavall-ai/skills/tavall-memory-plane/SKILL.md', body)


if __name__ == '__main__':
    unittest.main()
