package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
	toml "github.com/pelletier/go-toml/v2"
)

var serverRootCandidates = []string{"mcpServers", "mcp_servers", "mcp-servers"}

func LoadDocument(path string) (model.SourceDocument, error) {
	absolutePath, err := filepath.Abs(path)
	if err != nil {
		return model.SourceDocument{}, fmt.Errorf("resolve absolute path: %w", err)
	}

	content, err := os.ReadFile(absolutePath)
	if err != nil {
		return model.SourceDocument{}, fmt.Errorf("read config document: %w", err)
	}

	format := detectFormat(absolutePath)
	root := map[string]any{}
	switch format {
	case model.DocumentFormatJSON:
		if err := json.Unmarshal(content, &root); err != nil {
			return model.SourceDocument{}, fmt.Errorf("parse json: %w", err)
		}
	case model.DocumentFormatTOML:
		if err := toml.Unmarshal(content, &root); err != nil {
			return model.SourceDocument{}, fmt.Errorf("parse toml: %w", err)
		}
	default:
		return model.SourceDocument{}, fmt.Errorf("unsupported config format for %s", absolutePath)
	}

	serverRootKey, servers := extractServerBlock(root)
	metadataKey := managerMetadataKey(format)
	managerMetadata := asMap(root[metadataKey])
	delete(root, metadataKey)
	if serverRootKey != "" {
		delete(root, serverRootKey)
	}

	document := model.SourceDocument{
		ID:              stableID(absolutePath),
		Path:            absolutePath,
		SourceKind:      detectSourceKind(absolutePath),
		Scope:           detectScope(absolutePath),
		Format:          format,
		ServerRootKey:   defaultServerRootKey(serverRootKey, format),
		Writable:        true,
		DiscoveredAt:    time.Now().UTC(),
		TopLevel:        root,
		Servers:         parseServers(servers, managerMetadata),
		ManagerMetadata: managerMetadata,
	}
	return document, nil
}

func RenderDocument(document model.SourceDocument, profile model.ManagedProfile) ([]byte, error) {
	root := cloneMap(document.TopLevel)
	serverBlock := make(map[string]any, len(profile.Servers))
	managerServers := make(map[string]any, len(profile.Servers))
	for _, server := range profile.Servers {
		serverBlock[server.Name] = renderServer(server)
		managerServers[server.Name] = renderManagerMetadata(server)
	}

	root[defaultServerRootKey(document.ServerRootKey, profile.Format)] = serverBlock
	if len(managerServers) > 0 {
		root[managerMetadataKey(profile.Format)] = map[string]any{
			"version":     1,
			"generatedAt": profile.GeneratedAt.UTC().Format(time.RFC3339),
			"servers":     managerServers,
		}
	}

	switch profile.Format {
	case model.DocumentFormatJSON:
		payload, err := json.MarshalIndent(root, "", "  ")
		if err != nil {
			return nil, fmt.Errorf("render json: %w", err)
		}
		return append(payload, '\n'), nil
	case model.DocumentFormatTOML:
		payload, err := toml.Marshal(root)
		if err != nil {
			return nil, fmt.Errorf("render toml: %w", err)
		}
		return payload, nil
	default:
		return nil, fmt.Errorf("unsupported render format %s", profile.Format)
	}
}

func detectFormat(path string) model.DocumentFormat {
	switch strings.ToLower(filepath.Ext(path)) {
	case ".toml":
		return model.DocumentFormatTOML
	default:
		return model.DocumentFormatJSON
	}
}

func detectSourceKind(path string) string {
	normalized := strings.ToLower(filepath.ToSlash(path))
	switch {
	case strings.Contains(normalized, "/.codex/"):
		return "codex-config"
	case strings.Contains(normalized, "/mcp-config/"):
		return "repo-example"
	case strings.Contains(normalized, "/.ai/mcp/"):
		return "workspace-ai"
	case strings.Contains(normalized, "/.mcp/"):
		return "workspace-directory"
	default:
		return "workspace-config"
	}
}

func detectScope(path string) string {
	normalized := strings.ToLower(filepath.ToSlash(path))
	switch {
	case strings.Contains(normalized, "/.codex/"):
		return "user-or-workspace"
	case strings.Contains(normalized, "/mcp-config/"), strings.Contains(normalized, "/mcp-servers/"):
		return "repository"
	default:
		return "workspace"
	}
}

func extractServerBlock(root map[string]any) (string, map[string]any) {
	for _, key := range serverRootCandidates {
		servers := asMap(root[key])
		if len(servers) > 0 {
			return key, servers
		}
	}
	for _, key := range serverRootCandidates {
		if _, ok := root[key]; ok {
			return key, map[string]any{}
		}
	}
	return "", map[string]any{}
}

func parseServers(servers map[string]any, managerMetadata map[string]any) []model.ManagedServer {
	serverMetadata := asMap(managerMetadata["servers"])
	result := make([]model.ManagedServer, 0, len(servers))
	for _, name := range orderedKeys(servers) {
		serverMap := asMap(servers[name])
		meta := asMap(serverMetadata[name])
		settings := asMap(meta["settings"])

		extra := cloneMap(serverMap)
		delete(extra, "command")
		delete(extra, "args")
		delete(extra, "env")
		delete(extra, "url")
		delete(extra, "transport")
		delete(extra, "required")
		delete(extra, "disabled")

		result = append(result, model.ManagedServer{
			Name:          name,
			PluginID:      fallbackString(asString(meta["pluginId"]), asString(meta["plugin_id"])),
			Enabled:       !asBool(serverMap["disabled"]),
			Required:      asBool(serverMap["required"]),
			TransportKind: fallbackString(asString(serverMap["transport"]), inferTransport(serverMap)),
			Command:       asString(serverMap["command"]),
			Args:          asStringSlice(serverMap["args"]),
			URL:           asString(serverMap["url"]),
			Env:           asStringMap(serverMap["env"]),
			Settings:      settings,
			Preset:        fallbackString(asString(meta["preset"]), asString(meta["presetId"])),
			Scope:         asString(meta["scope"]),
			Source:        asString(meta["source"]),
			HealthStatus:  asString(meta["healthStatus"]),
			Extra:         extra,
		})
	}
	return result
}

func renderServer(server model.ManagedServer) map[string]any {
	result := cloneMap(server.Extra)
	if server.Command != "" {
		result["command"] = server.Command
	}
	if len(server.Args) > 0 {
		args := make([]any, 0, len(server.Args))
		for _, arg := range server.Args {
			args = append(args, arg)
		}
		result["args"] = args
	}
	if server.URL != "" {
		result["url"] = server.URL
	}
	if server.TransportKind != "" {
		result["transport"] = server.TransportKind
	}
	if len(server.Env) > 0 {
		env := make(map[string]any, len(server.Env))
		for key, value := range server.Env {
			env[key] = value
		}
		result["env"] = env
	}
	if !server.Enabled {
		result["disabled"] = true
	} else {
		delete(result, "disabled")
	}
	if server.Required {
		result["required"] = true
	} else {
		delete(result, "required")
	}
	return result
}

func renderManagerMetadata(server model.ManagedServer) map[string]any {
	settings := make(map[string]any, len(server.Settings))
	for key, value := range server.Settings {
		settings[key] = value
	}

	result := map[string]any{
		"pluginId": server.PluginID,
		"settings": settings,
	}
	if server.Preset != "" {
		result["preset"] = server.Preset
	}
	if server.Scope != "" {
		result["scope"] = server.Scope
	}
	if server.Source != "" {
		result["source"] = server.Source
	}
	if server.HealthStatus != "" {
		result["healthStatus"] = server.HealthStatus
	}
	return result
}

func managerMetadataKey(format model.DocumentFormat) string {
	if format == model.DocumentFormatTOML {
		return "x_mcp_manager"
	}
	return "x-mcp-manager"
}

func defaultServerRootKey(value string, format model.DocumentFormat) string {
	if strings.TrimSpace(value) != "" {
		return value
	}
	if format == model.DocumentFormatTOML {
		return "mcp_servers"
	}
	return "mcpServers"
}

func fallbackString(value string, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return value
}

func inferTransport(serverMap map[string]any) string {
	if asString(serverMap["url"]) != "" {
		return "http"
	}
	if asString(serverMap["command"]) != "" {
		return "stdio"
	}
	return "unknown"
}
