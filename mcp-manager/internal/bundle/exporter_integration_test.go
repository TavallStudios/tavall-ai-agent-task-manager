package bundle

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/agenttaskmanager/mcp-manager/internal/backends"
	"github.com/agenttaskmanager/mcp-manager/internal/config"
)

func TestExportBundleCollapsesExternalServersIntoATMBackendRegistry(t *testing.T) {
	root := t.TempDir()
	useTestHome(t, root)
	writeFile(t, filepath.Join(root, ".ai", "mcp", "mcp.json"), []byte(`{
  "mcpServers": {
    "agent-task-manager": {
      "command": "cmd.exe",
      "args": ["/c", "F:\\workspace\\AgentTaskManager\\scripts\\agent-task-manager-mcp-stdio.cmd"],
      "env": {
        "AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED": "true",
        "AGENT_TASK_MANAGER_MCP_BASE_URL": "https://docs.tavall.org/agent-task-manager",
        "AGENT_TASK_MANAGER_MCP_ENDPOINT": "/mcp"
      }
    }
  }
}
`))
	writeFile(t, filepath.Join(root, ".codex", "config.toml"), []byte(`
[mcp_servers.git]
command = "git"

[mcp_servers.chrome-devtools-local]
command = "npx"
args = ["-y", "chrome-devtools-mcp@latest"]
`))

	outputRoot := filepath.Join(root, "bundle")
	if err := Export(outputRoot); err != nil {
		t.Fatalf("export bundle: %v", err)
	}

	documentPath := filepath.Join(outputRoot, "mcp-servers", "01-agent-task-manager.json")
	registryPath := filepath.Join(outputRoot, "mcp-servers", backends.FileName)

	document, err := config.LoadDocument(documentPath)
	if err != nil {
		t.Fatalf("load exported document: %v", err)
	}
	if len(document.Servers) != 1 {
		t.Fatalf("expected exactly one external server, got %d", len(document.Servers))
	}
	if document.Servers[0].Name != "agent-task-manager" {
		t.Fatalf("expected exported server to be agent-task-manager, got %s", document.Servers[0].Name)
	}
	if !strings.EqualFold(document.Servers[0].TransportKind, "stdio") {
		t.Fatalf("expected local stdio export, got %s", document.Servers[0].TransportKind)
	}

	registry, err := backends.Load(registryPath)
	if err != nil {
		t.Fatalf("load backend registry: %v", err)
	}
	if registry.CentralServer != "agent-task-manager" {
		t.Fatalf("expected central server to be agent-task-manager, got %s", registry.CentralServer)
	}
	if len(registry.Connectors) != 2 {
		t.Fatalf("expected 2 backend connectors, got %d", len(registry.Connectors))
	}

	found := map[string]bool{}
	for _, connector := range registry.Connectors {
		found[connector.ID] = true
		if connector.ID == "agent-task-manager" {
			t.Fatal("backend registry must not include agent-task-manager as a proxied connector")
		}
	}
	if !found["git"] || !found["chrome-devtools-local"] {
		t.Fatalf("expected git and chrome-devtools-local connectors, got %#v", found)
	}

	entries, err := os.ReadDir(filepath.Join(outputRoot, "mcp-servers"))
	if err != nil {
		t.Fatalf("read exported mcp-servers dir: %v", err)
	}
	configCount := 0
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := strings.ToLower(entry.Name())
		if strings.HasSuffix(name, ".json") || strings.HasSuffix(name, ".toml") {
			configCount++
		}
	}
	if configCount != 2 {
		t.Fatalf("expected one ATM config plus one backend registry, got %d config files", configCount)
	}
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
