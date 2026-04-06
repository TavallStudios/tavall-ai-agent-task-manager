package manager

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestServiceSaveBackupRestoreAndATMCompatibility(t *testing.T) {
	root := t.TempDir()
	useTestHome(t, root)
	documentPath := filepath.Join(root, ".mcp.json")
	originalContent := mustReadFixture(t, filepath.Join("..", "..", "testdata", "atm-config.json"))
	writeFile(t, documentPath, originalContent)

	service, err := NewService(Options{
		WorkingDirectory: root,
		Roots:            []string{root},
		HistoryRoot:      filepath.Join(root, ".manager-home"),
	})
	if err != nil {
		t.Fatalf("new service: %v", err)
	}

	documents, err := service.Discover(context.Background())
	if err != nil {
		t.Fatalf("discover: %v", err)
	}
	if len(documents) != 1 {
		t.Fatalf("expected 1 document, got %d", len(documents))
	}

	profile, err := service.BuildProfile(documents[0].ID)
	if err != nil {
		t.Fatalf("build profile: %v", err)
	}
	if len(profile.Servers) != 1 {
		t.Fatalf("expected 1 server, got %d", len(profile.Servers))
	}
	server := profile.Servers[0]
	if server.PluginID != "tavall-ai" {
		t.Fatalf("expected ATM plugin, got %s", server.PluginID)
	}
	if _, exists := server.Env["AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS"]; exists {
		t.Fatalf("manager must not inject AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS")
	}

	profile.Servers[0].Settings["cleanCodeMode"] = "strict"
	backup, preview, _, err := service.Save(documents[0].ID, profile)
	if err != nil {
		t.Fatalf("save profile: %v", err)
	}
	if backup.ID == "" {
		t.Fatal("expected backup id")
	}
	if !strings.Contains(preview, "x-mcp-manager") && !strings.Contains(preview, "x_mcp_manager") {
		t.Fatalf("expected manager metadata in preview, got %s", preview)
	}

	savedContent, err := os.ReadFile(documentPath)
	if err != nil {
		t.Fatalf("read saved file: %v", err)
	}
	if !strings.Contains(string(savedContent), "cleanCodeMode") {
		t.Fatalf("expected cleanCodeMode in saved content, got %s", string(savedContent))
	}

	backups, err := service.ListBackups(documents[0].ID)
	if err != nil {
		t.Fatalf("list backups: %v", err)
	}
	if len(backups) == 0 {
		t.Fatal("expected at least one backup")
	}

	if err := service.Restore(documents[0].ID, backups[0].ID); err != nil {
		t.Fatalf("restore backup: %v", err)
	}
	restoredContent, err := os.ReadFile(documentPath)
	if err != nil {
		t.Fatalf("read restored file: %v", err)
	}
	if string(restoredContent) != string(originalContent) {
		t.Fatalf("expected restored content to match original")
	}
}

func TestRepoExampleProfilesRemainOptional(t *testing.T) {
	repoRoot, err := filepath.Abs(filepath.Join("..", "..", ".."))
	if err != nil {
		t.Fatalf("resolve repo root: %v", err)
	}
	useTestHome(t, t.TempDir())
	service, err := NewService(Options{
		WorkingDirectory: repoRoot,
		Roots:            []string{repoRoot},
		HistoryRoot:      filepath.Join(t.TempDir(), ".manager-home"),
	})
	if err != nil {
		t.Fatalf("new service: %v", err)
	}
	documents, err := service.Discover(context.Background())
	if err != nil {
		t.Fatalf("discover repo configs: %v", err)
	}

	var exampleID string
	for _, document := range documents {
		if strings.Contains(document.Path, "tavall-ai.stdio.windows.example.json") {
			exampleID = document.ID
			break
		}
	}
	if exampleID == "" {
		t.Fatal("expected repo example config to be discovered")
	}

	profile, err := service.BuildProfile(exampleID)
	if err != nil {
		t.Fatalf("build repo example profile: %v", err)
	}
	if len(profile.Servers) != 1 {
		t.Fatalf("expected 1 repo example server, got %d", len(profile.Servers))
	}
	if _, exists := profile.Servers[0].Env["AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS"]; exists {
		t.Fatal("repo example must remain optional and must not gain required MCP injection")
	}
}

func TestSaveFailsClosedOnValidationErrors(t *testing.T) {
	root := t.TempDir()
	useTestHome(t, root)
	documentPath := filepath.Join(root, ".mcp.json")
	writeFile(t, documentPath, mustReadFixture(t, filepath.Join("..", "..", "testdata", "atm-config.json")))

	service, err := NewService(Options{
		WorkingDirectory: root,
		Roots:            []string{root},
		HistoryRoot:      filepath.Join(root, ".manager-home"),
	})
	if err != nil {
		t.Fatalf("new service: %v", err)
	}
	documents, err := service.Discover(context.Background())
	if err != nil {
		t.Fatalf("discover: %v", err)
	}
	profile, err := service.BuildProfile(documents[0].ID)
	if err != nil {
		t.Fatalf("build profile: %v", err)
	}
	profile.Servers[0].Env["AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS"] = "filesystem,ripgrep"

	if _, _, _, err := service.Save(documents[0].ID, profile); err == nil {
		t.Fatal("expected save to fail when validation contains errors")
	}
}

func TestSavePreservesSiblingServersInSameDocument(t *testing.T) {
	root := t.TempDir()
	useTestHome(t, root)
	documentPath := filepath.Join(root, ".mcp.json")
	writeFile(t, documentPath, []byte("{\"mcpServers\":{\"one\":{\"command\":\"npx\",\"args\":[\"-y\",\"filesystem-mcp\"]},\"two\":{\"command\":\"npx\",\"args\":[\"-y\",\"chrome-devtools-mcp@latest\"]}}}\n"))

	service, err := NewService(Options{
		WorkingDirectory: root,
		Roots:            []string{root},
		HistoryRoot:      filepath.Join(root, ".manager-home"),
	})
	if err != nil {
		t.Fatalf("new service: %v", err)
	}
	documents, err := service.Discover(context.Background())
	if err != nil {
		t.Fatalf("discover: %v", err)
	}
	profile, err := service.BuildProfile(documents[0].ID)
	if err != nil {
		t.Fatalf("build profile: %v", err)
	}
	profile.Servers[0].Enabled = false
	if _, _, _, err := service.Save(documents[0].ID, profile); err != nil {
		t.Fatalf("save profile: %v", err)
	}

	reloaded, err := service.BuildProfile(documents[0].ID)
	if err != nil {
		t.Fatalf("reload profile: %v", err)
	}
	if len(reloaded.Servers) != 2 {
		t.Fatalf("expected both servers to remain, got %d", len(reloaded.Servers))
	}
}

func TestServiceDiscoversUserCodexServersAndMappedTools(t *testing.T) {
	root := t.TempDir()
	useTestHome(t, root)
	writeFile(t, filepath.Join(root, ".codex", "config.toml"), mustReadFixture(t, filepath.Join("..", "..", "testdata", "codex-config.toml")))

	service, err := NewService(Options{
		WorkingDirectory: root,
		HistoryRoot:      filepath.Join(root, ".manager-home"),
	})
	if err != nil {
		t.Fatalf("new service: %v", err)
	}
	documents, err := service.Discover(context.Background())
	if err != nil {
		t.Fatalf("discover: %v", err)
	}
	if len(documents) != 1 {
		t.Fatalf("expected 1 document from test home codex config, got %d", len(documents))
	}

	profile, err := service.BuildProfile(documents[0].ID)
	if err != nil {
		t.Fatalf("build profile: %v", err)
	}
	if len(profile.Servers) != 1 {
		t.Fatalf("expected 1 codex server, got %d", len(profile.Servers))
	}
	tools := service.ToolsForProfile(documents[0].ID, profile)
	if len(tools) == 0 {
		t.Fatal("expected mapped tools from codex config")
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

func useTestHome(t *testing.T, root string) {
	t.Helper()
	t.Setenv("HOME", root)
	t.Setenv("USERPROFILE", root)
	t.Setenv("HOMEDRIVE", filepath.VolumeName(root))
	separator := string(filepath.Separator)
	homePath := strings.TrimPrefix(root, filepath.VolumeName(root))
	if homePath == "" {
		homePath = separator
	}
	t.Setenv("HOMEPATH", homePath)
}

