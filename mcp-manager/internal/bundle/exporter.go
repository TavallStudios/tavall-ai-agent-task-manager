package bundle

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"github.com/agenttaskmanager/mcp-manager/internal/backends"
	"github.com/agenttaskmanager/mcp-manager/internal/config"
	"github.com/agenttaskmanager/mcp-manager/internal/model"
	"github.com/agenttaskmanager/mcp-manager/internal/plugins"
	"github.com/agenttaskmanager/mcp-manager/internal/toolcatalog"
)

func Export(outputRoot string) error {
	absoluteOutputRoot, err := filepath.Abs(outputRoot)
	if err != nil {
		return fmt.Errorf("resolve output root: %w", err)
	}
	bundleServers := filepath.Join(absoluteOutputRoot, "mcp-servers")
	stateRoot := filepath.Join(absoluteOutputRoot, "state")
	if err := os.MkdirAll(bundleServers, 0o755); err != nil {
		return fmt.Errorf("create mcp-servers: %w", err)
	}
	if err := os.MkdirAll(stateRoot, 0o755); err != nil {
		return fmt.Errorf("create state root: %w", err)
	}
	if err := cleanupBundleServers(bundleServers); err != nil {
		return err
	}

	if err := copyExecutable(absoluteOutputRoot); err != nil {
		return err
	}

	documents, err := loadUserDocuments()
	if err != nil {
		return err
	}
	registry := plugins.NewRegistry()
	catalog := toolcatalog.New()
	launcherPath, err := writeAgentTaskManagerLauncher(absoluteOutputRoot)
	if err != nil {
		return err
	}

	atmServer := buildAgentTaskManagerServer(documents, registry, launcherPath)
	atmDocumentPath := filepath.Join(bundleServers, "01-agent-task-manager.json")
	if err := writeAgentTaskManagerDocument(atmDocumentPath, atmServer); err != nil {
		return err
	}

	backendRegistry := buildBackendRegistry(filepath.Join(bundleServers, backends.FileName), documents, registry, catalog)
	if err := writeBackendRegistry(backendRegistry); err != nil {
		return err
	}

	if err := writeBundleReadme(bundleServers); err != nil {
		return err
	}
	if err := writeManagerLauncher(absoluteOutputRoot); err != nil {
		return err
	}
	return nil
}

func cleanupBundleServers(bundleServers string) error {
	entries, err := os.ReadDir(bundleServers)
	if err != nil {
		return fmt.Errorf("read bundle servers directory: %w", err)
	}
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		if err := os.Remove(filepath.Join(bundleServers, entry.Name())); err != nil {
			return fmt.Errorf("remove %s: %w", entry.Name(), err)
		}
	}
	return nil
}

func copyExecutable(outputRoot string) error {
	executablePath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("resolve current executable: %w", err)
	}
	content, err := os.ReadFile(executablePath)
	if err != nil {
		return fmt.Errorf("read current executable: %w", err)
	}
	targetName := "mcp-manager"
	if runtime.GOOS == "windows" {
		targetName += ".exe"
	}
	targetPath := filepath.Join(outputRoot, targetName)
	if err := os.WriteFile(targetPath, content, 0o755); err != nil {
		return fmt.Errorf("write %s: %w", targetPath, err)
	}
	return nil
}

func loadUserDocuments() ([]model.SourceDocument, error) {
	homeDirectory, err := os.UserHomeDir()
	if err != nil {
		return nil, fmt.Errorf("resolve user home: %w", err)
	}
	candidates := []string{
		filepath.Join(homeDirectory, ".ai", "mcp", "mcp.json"),
		filepath.Join(homeDirectory, ".codex", "config.toml"),
	}
	documents := make([]model.SourceDocument, 0, len(candidates))
	for _, candidate := range candidates {
		if _, err := os.Stat(candidate); err != nil {
			continue
		}
		document, err := config.LoadDocument(candidate)
		if err != nil {
			return nil, err
		}
		documents = append(documents, document)
	}
	return documents, nil
}

func writeAgentTaskManagerLauncher(outputRoot string) (string, error) {
	repoRoot, err := repoRoot()
	if err != nil {
		return "", err
	}
	backendRegistryPath := filepath.Join(outputRoot, "mcp-servers", backends.FileName)
	launcherName := "agent-task-manager-mcp-stdio.sh"
	content := fmt.Sprintf("#!/usr/bin/env bash\nset -euo pipefail\nREPO_ROOT=%q\nBACKEND_REGISTRY=%q\nJAR_PATH=\"$REPO_ROOT/agent-task-manager-app/target/agent-task-manager-app-0.1.0-SNAPSHOT.jar\"\nif [ ! -f \"$JAR_PATH\" ]; then\n  mvn -q -f \"$REPO_ROOT/pom.xml\" -pl agent-task-manager-app -am package\nfi\njava -jar \"$JAR_PATH\" serve-mcp-stdio \"--app.mcp.backend-registry-path=$BACKEND_REGISTRY\" \"$@\"\n", filepath.ToSlash(repoRoot), filepath.ToSlash(backendRegistryPath))
	if runtime.GOOS == "windows" {
		launcherName = "agent-task-manager-mcp-stdio.cmd"
		content = fmt.Sprintf("@echo off\r\nsetlocal EnableExtensions\r\nset \"REPO_ROOT=%s\"\r\nset \"BACKEND_REGISTRY=%s\"\r\nset \"JAR_PATH=%%REPO_ROOT%%\\agent-task-manager-app\\target\\agent-task-manager-app-0.1.0-SNAPSHOT.jar\"\r\nif not exist \"%%JAR_PATH%%\" (\r\n  call mvn -q -f \"%%REPO_ROOT%%\\pom.xml\" -pl agent-task-manager-app -am package\r\n  if errorlevel 1 exit /b %%errorlevel%%\r\n)\r\njava -jar \"%%JAR_PATH%%\" serve-mcp-stdio --app.mcp.backend-registry-path=\"%%BACKEND_REGISTRY%%\" %%*\r\n", repoRoot, backendRegistryPath)
	}
	launcherPath := filepath.Join(outputRoot, launcherName)
	if err := os.WriteFile(launcherPath, []byte(content), 0o755); err != nil {
		return "", fmt.Errorf("write ATM launcher: %w", err)
	}
	return launcherPath, nil
}

func buildAgentTaskManagerServer(documents []model.SourceDocument, registry *plugins.Registry, launcherPath string) model.ManagedServer {
	server := model.ManagedServer{
		Name:         "agent-task-manager",
		PluginID:     "agent-task-manager",
		Enabled:      true,
		Settings:     map[string]any{},
		Scope:        "workspace",
		Source:       "bundle-export",
		HealthStatus: "unknown",
	}
	for _, document := range documents {
		for _, candidate := range document.Servers {
			applied := registry.Apply(candidate)
			if strings.EqualFold(applied.PluginID, "agent-task-manager") || strings.EqualFold(applied.Name, "agent-task-manager") {
				server = applied
				server.Scope = "workspace"
				server.Source = "bundle-export"
				server.HealthStatus = "unknown"
				break
			}
		}
	}
	if server.Settings == nil {
		server.Settings = map[string]any{}
	}
	if strings.TrimSpace(server.URL) != "" && strings.TrimSpace(fmt.Sprint(server.Settings["remoteBaseUrl"])) == "" {
		server.Settings["remoteBaseUrl"] = strings.TrimSuffix(server.URL, "/mcp")
	}
	server.Settings["connectionMode"] = "local"
	server.Settings["localLauncherPath"] = launcherPath
	server.Enabled = true
	server.Required = false
	return registry.Apply(server)
}

func writeAgentTaskManagerDocument(targetPath string, server model.ManagedServer) error {
	document := model.SourceDocument{
		Path:          targetPath,
		Format:        model.DocumentFormatJSON,
		ServerRootKey: "mcpServers",
		TopLevel:      map[string]any{},
	}
	profile := model.ManagedProfile{
		DocumentID:  filepath.Base(targetPath),
		Path:        targetPath,
		SourceKind:  "bundle-export",
		Scope:       "workspace",
		Format:      model.DocumentFormatJSON,
		GeneratedAt: time.Now().UTC(),
		Servers:     []model.ManagedServer{server},
	}
	rendered, err := config.RenderDocument(document, profile)
	if err != nil {
		return err
	}
	return os.WriteFile(targetPath, rendered, 0o644)
}

func buildBackendRegistry(
	targetPath string,
	documents []model.SourceDocument,
	registry *plugins.Registry,
	catalog *toolcatalog.Catalog,
) model.BackendRegistry {
	connectors := make([]model.ManagedBackend, 0, 8)
	seen := map[string]struct{}{}
	for _, document := range documents {
		for _, candidate := range document.Servers {
			applied := registry.Apply(candidate)
			if strings.EqualFold(applied.PluginID, "agent-task-manager") || strings.EqualFold(applied.Name, "agent-task-manager") {
				continue
			}
			key := strings.ToLower(applied.Name)
			if _, exists := seen[key]; exists {
				continue
			}
			seen[key] = struct{}{}
			toolCache := make([]model.ManagedTool, 0)
			for _, tool := range catalog.ForServer("bundle", applied) {
				toolCache = append(toolCache, model.ManagedTool{
					Name:        tool.Name,
					DisplayName: tool.DisplayName,
					Summary:     tool.Summary,
					Category:    tool.Category,
					Source:      tool.Source,
				})
			}
			connectors = append(connectors, model.ManagedBackend{
				ID:            applied.Name,
				DisplayName:   displayName(applied.Name),
				Enabled:       applied.Enabled,
				TransportKind: applied.TransportKind,
				Command:       applied.Command,
				Args:          append([]string(nil), applied.Args...),
				URL:           applied.URL,
				Env:           cloneEnv(applied.Env),
				Source:        document.SourceKind,
				HealthStatus:  "unknown",
				ToolCache:     toolCache,
			})
		}
	}
	return model.BackendRegistry{
		Path:          targetPath,
		CentralServer: "agent-task-manager",
		Connectors:    connectors,
	}
}

func writeBackendRegistry(registry model.BackendRegistry) error {
	rendered, err := backends.Render(registry)
	if err != nil {
		return err
	}
	return os.WriteFile(registry.Path, rendered, 0o644)
}

func writeBundleReadme(bundleServers string) error {
	content := "# Exported MCP Hub\n\nThis hub exposes one external AgentTaskManager MCP server plus a managed backend connector registry.\n"
	return os.WriteFile(filepath.Join(bundleServers, "README.md"), []byte(content), 0o644)
}

func writeManagerLauncher(outputRoot string) error {
	content := "@echo off\r\nset ROOT=%~dp0\r\ncd /d \"%ROOT%\"\r\n\"%ROOT%mcp-manager.exe\" serve -listen 127.0.0.1:47811 -roots \"%ROOT%mcp-servers\" -history-root \"%ROOT%state\"\r\n"
	if runtime.GOOS != "windows" {
		content = "#!/usr/bin/env bash\nset -euo pipefail\nROOT=\"$(cd \"$(dirname \"$0\")\" && pwd)\"\ncd \"$ROOT\"\n\"$ROOT/mcp-manager\" serve -listen 127.0.0.1:47811 -roots \"$ROOT/mcp-servers\" -history-root \"$ROOT/state\"\n"
	}
	fileName := "launch-mcp-manager.cmd"
	if runtime.GOOS != "windows" {
		fileName = "launch-mcp-manager.sh"
	}
	return os.WriteFile(filepath.Join(outputRoot, fileName), []byte(content), 0o755)
}

func repoRoot() (string, error) {
	current, err := os.Getwd()
	if err != nil {
		return "", fmt.Errorf("resolve working directory: %w", err)
	}
	for {
		if isRepoRoot(current) {
			return current, nil
		}
		parent := filepath.Dir(current)
		if parent == current {
			return "", fmt.Errorf("failed to locate AgentTaskManager repo root from %s", current)
		}
		current = parent
	}
}

func isRepoRoot(path string) bool {
	return fileExists(filepath.Join(path, "AGENTS.md")) &&
		fileExists(filepath.Join(path, "pom.xml")) &&
		dirExists(filepath.Join(path, "agent-task-manager-app")) &&
		dirExists(filepath.Join(path, "agent-task-manager-core"))
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}

func dirExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.IsDir()
}

func cloneEnv(source map[string]string) map[string]string {
	if source == nil {
		return map[string]string{}
	}
	result := make(map[string]string, len(source))
	for key, value := range source {
		result[key] = value
	}
	return result
}

func displayName(value string) string {
	replacer := strings.NewReplacer("-", " ", "_", " ")
	parts := strings.Fields(replacer.Replace(value))
	for index, part := range parts {
		parts[index] = strings.ToUpper(part[:1]) + part[1:]
	}
	return strings.Join(parts, " ")
}
