package discovery

import (
	"os"
	"path/filepath"
	"testing"
)

func TestDiscoverDocumentsAcrossSupportedLocations(t *testing.T) {
	root := t.TempDir()
	writeFile(t, filepath.Join(root, ".codex", "config.toml"), mustReadFixture(t, filepath.Join("..", "..", "testdata", "codex-config.toml")))
	writeFile(t, filepath.Join(root, ".mcp.json"), []byte("{\"mcpServers\":{\"local\":{\"command\":\"npx\",\"args\":[\"-y\",\"filesystem-mcp\"]}}}\n"))
	writeFile(t, filepath.Join(root, "mcp-config", "agent-task-manager.json"), mustReadFixture(t, filepath.Join("..", "..", "testdata", "atm-config.json")))

	documents, err := DiscoverDocuments([]string{root})
	if err != nil {
		t.Fatalf("discover documents: %v", err)
	}
	if len(documents) != 3 {
		t.Fatalf("expected 3 documents, got %d", len(documents))
	}
}

func TestDiscoverDocumentsFromDirectMcpDirectoryRoot(t *testing.T) {
	root := filepath.Join(t.TempDir(), "mcp-servers")
	writeFile(t, filepath.Join(root, "chrome.json"), []byte("{\"mcpServers\":{\"chrome\":{\"command\":\"npx\",\"args\":[\"-y\",\"chrome-devtools-mcp@latest\"]}}}\n"))

	documents, err := DiscoverDocuments([]string{root})
	if err != nil {
		t.Fatalf("discover direct mcp directory: %v", err)
	}
	if len(documents) != 1 {
		t.Fatalf("expected 1 document from direct MCP directory, got %d", len(documents))
	}
}

func TestResolveRootsUsesExplicitRootsOnly(t *testing.T) {
	root := t.TempDir()
	explicit := filepath.Join(root, "mcp-servers")
	roots := ResolveRoots(root, []string{explicit})
	if len(roots) != 1 {
		t.Fatalf("expected only the explicit root, got %d roots: %v", len(roots), roots)
	}
	if filepath.Clean(roots[0]) != filepath.Clean(explicit) {
		t.Fatalf("expected explicit root %s, got %s", explicit, roots[0])
	}
}

func mustReadFixture(t *testing.T, relative string) []byte {
	t.Helper()
	content, err := os.ReadFile(relative)
	if err != nil {
		t.Fatalf("read fixture %s: %v", relative, err)
	}
	return content
}

func writeFile(t *testing.T, path string, content []byte) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatalf("mkdir %s: %v", filepath.Dir(path), err)
	}
	if err := os.WriteFile(path, content, 0o644); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}
