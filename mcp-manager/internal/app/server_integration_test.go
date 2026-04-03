package app

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/agenttaskmanager/mcp-manager/internal/bundle"
	"github.com/agenttaskmanager/mcp-manager/internal/manager"
)

func TestHTTPServerDiscoveryAndHTMLShell(t *testing.T) {
	root := t.TempDir()
	useTestHome(t, root)
	writeFile(t, filepath.Join(root, ".codex", "config.toml"), mustReadFixture(t, filepath.Join("..", "..", "testdata", "codex-config.toml")))
	writeFile(t, filepath.Join(root, ".mcp.json"), mustReadFixture(t, filepath.Join("..", "..", "testdata", "atm-config.json")))

	service, err := manager.NewService(manager.Options{
		WorkingDirectory: root,
		HistoryRoot:      filepath.Join(root, ".manager-home"),
	})
	if err != nil {
		t.Fatalf("new service: %v", err)
	}
	if _, err := service.Discover(context.Background()); err != nil {
		t.Fatalf("discover: %v", err)
	}

	server, err := NewServer(service)
	if err != nil {
		t.Fatalf("new server: %v", err)
	}

	testServer := httptest.NewServer(server.routes())
	defer testServer.Close()

	response, err := http.Get(testServer.URL + "/api/discovery")
	if err != nil {
		t.Fatalf("get api discovery: %v", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 from api discovery, got %d", response.StatusCode)
	}

	pageResponse, err := http.Get(testServer.URL + "/")
	if err != nil {
		t.Fatalf("get index: %v", err)
	}
	defer pageResponse.Body.Close()
	if pageResponse.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 from index, got %d", pageResponse.StatusCode)
	}
	body, err := ioReadAll(pageResponse)
	if err != nil {
		t.Fatalf("read index body: %v", err)
	}
	if !strings.Contains(body, "MCP Manager") {
		t.Fatalf("expected MCP Manager shell, got %s", body)
	}
	if !strings.Contains(body, "Current tool surface") {
		t.Fatalf("expected tools sidebar in shell, got %s", body)
	}
	if !strings.Contains(body, "Click") {
		t.Fatalf("expected mapped Chrome DevTools tool name in shell, got %s", body)
	}

	overviewResponse, err := http.Get(testServer.URL + "/api/overview")
	if err != nil {
		t.Fatalf("get api overview: %v", err)
	}
	defer overviewResponse.Body.Close()
	overviewBody, err := ioReadAll(overviewResponse)
	if err != nil {
		t.Fatalf("read overview body: %v", err)
	}
	if !strings.Contains(overviewBody, "\"tools\"") {
		t.Fatalf("expected tools in api overview, got %s", overviewBody)
	}
}

func TestHTTPServerOverviewShowsOneExternalServerAndBackendConnectors(t *testing.T) {
	root := t.TempDir()
	useTestHome(t, root)
	writeFile(t, filepath.Join(root, ".ai", "mcp", "mcp.json"), mustReadFixture(t, filepath.Join("..", "..", "testdata", "atm-config.json")))
	writeFile(t, filepath.Join(root, ".codex", "config.toml"), []byte(`
[mcp_servers.git]
command = "git"

[mcp_servers.filesystem]
command = "npx"
args = ["-y", "filesystem-mcp"]
`))

	bundleRoot := filepath.Join(root, "bundle")
	if err := bundle.Export(bundleRoot); err != nil {
		t.Fatalf("export bundle: %v", err)
	}

	service, err := manager.NewService(manager.Options{
		WorkingDirectory: bundleRoot,
		Roots:            []string{filepath.Join(bundleRoot, "mcp-servers")},
		HistoryRoot:      filepath.Join(bundleRoot, "state"),
	})
	if err != nil {
		t.Fatalf("new service: %v", err)
	}

	server, err := NewServer(service)
	if err != nil {
		t.Fatalf("new server: %v", err)
	}

	testServer := httptest.NewServer(server.routes())
	defer testServer.Close()

	response, err := http.Get(testServer.URL + "/api/overview")
	if err != nil {
		t.Fatalf("get overview: %v", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 from api overview, got %d", response.StatusCode)
	}

	var overview struct {
		Servers []struct {
			ServerName string
		}
		Backends []struct {
			CentralServer string
			Connectors    []struct {
				ID string
			}
		}
	}
	if err := json.NewDecoder(response.Body).Decode(&overview); err != nil {
		t.Fatalf("decode overview: %v", err)
	}
	if len(overview.Servers) != 1 {
		t.Fatalf("expected 1 external server in overview, got %d", len(overview.Servers))
	}
	if overview.Servers[0].ServerName != "agent-task-manager" {
		t.Fatalf("expected agent-task-manager server, got %s", overview.Servers[0].ServerName)
	}
	if len(overview.Backends) != 1 {
		t.Fatalf("expected 1 backend registry in overview, got %d", len(overview.Backends))
	}
	if overview.Backends[0].CentralServer != "agent-task-manager" {
		t.Fatalf("expected ATM backend central server, got %s", overview.Backends[0].CentralServer)
	}
	if len(overview.Backends[0].Connectors) != 2 {
		t.Fatalf("expected 2 backend connectors, got %d", len(overview.Backends[0].Connectors))
	}

	pageResponse, err := http.Get(testServer.URL + "/")
	if err != nil {
		t.Fatalf("get index: %v", err)
	}
	defer pageResponse.Body.Close()
	body, err := ioReadAll(pageResponse)
	if err != nil {
		t.Fatalf("read index body: %v", err)
	}
	if !strings.Contains(body, "Backends") {
		t.Fatalf("expected backend section in shell, got %s", body)
	}
	if !strings.Contains(body, "git") || !strings.Contains(body, "filesystem") {
		t.Fatalf("expected proxied backend connectors in shell, got %s", body)
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

func ioReadAll(response *http.Response) (string, error) {
	content, err := io.ReadAll(response.Body)
	if err != nil {
		return "", err
	}
	return string(content), nil
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
