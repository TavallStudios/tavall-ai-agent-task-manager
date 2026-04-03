package toolcatalog

import (
	"fmt"
	"regexp"
	"sort"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

type entryTemplate struct {
	Name         string
	Category     string
	Summary      string
	SettingsHint string
}

type Catalog struct{}

var camelBoundaryPattern = regexp.MustCompile(`([a-z0-9])([A-Z])`)

func New() *Catalog {
	return &Catalog{}
}

func (catalog *Catalog) ForServer(documentID string, server model.ManagedServer) []model.ManagedTool {
	templates := templatesForServer(server)
	tools := make([]model.ManagedTool, 0, len(templates))
	for _, template := range templates {
		tools = append(tools, model.ManagedTool{
			Key:             toolKey(documentID, server.Name, template.Name),
			Name:            template.Name,
			DisplayName:     displayName(template.Name),
			Summary:         fallbackSummary(template, server),
			Category:        fallbackCategory(template.Category),
			Source:          toolSource(server),
			SettingsHint:    fallbackSettingsHint(template.SettingsHint, server),
			OwnerDocumentID: documentID,
			OwnerServerName: server.Name,
			OwnerPluginID:   server.PluginID,
			OwnerEnabled:    server.Enabled,
		})
	}
	sort.Slice(tools, func(left int, right int) bool {
		if tools[left].OwnerServerName == tools[right].OwnerServerName {
			return tools[left].DisplayName < tools[right].DisplayName
		}
		return tools[left].OwnerServerName < tools[right].OwnerServerName
	})
	return tools
}

func (catalog *Catalog) ForBackend(documentID string, ownerServerName string, backend model.ManagedBackend) []model.ManagedTool {
	tools := make([]model.ManagedTool, 0, len(backend.ToolCache))
	for _, cachedTool := range backend.ToolCache {
		tools = append(tools, model.ManagedTool{
			Key:             toolKey(documentID, ownerServerName, backend.ID+"."+cachedTool.Name),
			Name:            backend.ID + "." + cachedTool.Name,
			DisplayName:     fallbackString(cachedTool.DisplayName, displayName(cachedTool.Name)),
			Summary:         fallbackString(cachedTool.Summary, fmt.Sprintf("Tool proxied through the %s backend connector.", backend.DisplayName)),
			Category:        fallbackString(cachedTool.Category, "Backend Connector"),
			Source:          fallbackString(cachedTool.Source, fmt.Sprintf("%s backend connector", backend.DisplayName)),
			SettingsHint:    fmt.Sprintf("%s inherits launcher, env, and enablement from the %s backend connector.", displayName(cachedTool.Name), backend.DisplayName),
			BackendID:       backend.ID,
			BackendName:     backend.DisplayName,
			OwnerDocumentID: documentID,
			OwnerServerName: ownerServerName,
			OwnerPluginID:   "agent-task-manager",
			OwnerEnabled:    backend.Enabled,
		})
	}
	sort.Slice(tools, func(left int, right int) bool {
		if tools[left].BackendName == tools[right].BackendName {
			return tools[left].DisplayName < tools[right].DisplayName
		}
		return tools[left].BackendName < tools[right].BackendName
	})
	return tools
}

func templatesForServer(server model.ManagedServer) []entryTemplate {
	switch catalogKey(server) {
	case "agent-task-manager":
		return agentTaskManagerTemplates()
	case "chrome-devtools":
		return chromeDevToolsTemplates()
	case "filesystem":
		return filesystemTemplates()
	case "git":
		return gitTemplates()
	case "ripgrep":
		return ripgrepTemplates()
	case "tree-sitter":
		return treeSitterTemplates()
	default:
		return nil
	}
}

func catalogKey(server model.ManagedServer) string {
	if strings.EqualFold(server.PluginID, "agent-task-manager") {
		return "agent-task-manager"
	}
	if strings.EqualFold(server.PluginID, "chrome-devtools") {
		return "chrome-devtools"
	}

	name := normalizeKey(server.Name)
	switch {
	case strings.Contains(name, "agent-task-manager"):
		return "agent-task-manager"
	case strings.Contains(name, "chrome-devtools"):
		return "chrome-devtools"
	case strings.Contains(name, "filesystem"):
		return "filesystem"
	case name == "git" || strings.HasSuffix(name, "-git") || strings.HasPrefix(name, "git-"):
		return "git"
	case strings.Contains(name, "ripgrep"):
		return "ripgrep"
	case strings.Contains(name, "tree-sitter"):
		return "tree-sitter"
	}

	command := normalizeKey(server.Command + " " + strings.Join(server.Args, " "))
	switch {
	case strings.Contains(command, "agent-task-manager"):
		return "agent-task-manager"
	case strings.Contains(command, "chrome-devtools-mcp"):
		return "chrome-devtools"
	case strings.Contains(command, "filesystem"):
		return "filesystem"
	case strings.Contains(command, "ripgrep"):
		return "ripgrep"
	case strings.Contains(command, "tree-sitter"):
		return "tree-sitter"
	}
	return name
}

func normalizeKey(value string) string {
	normalized := strings.TrimSpace(strings.ToLower(value))
	normalized = strings.ReplaceAll(normalized, "_", "-")
	normalized = strings.ReplaceAll(normalized, " ", "-")
	return normalized
}

func toolKey(documentID string, serverName string, toolName string) string {
	return fmt.Sprintf("%s:%s:%s", documentID, strings.ToLower(serverName), strings.ToLower(toolName))
}

func displayName(value string) string {
	normalized := camelBoundaryPattern.ReplaceAllString(value, "$1 $2")
	replacer := strings.NewReplacer("_", " ", "-", " ")
	parts := strings.Fields(replacer.Replace(normalized))
	for index, part := range parts {
		if strings.EqualFold(part, "mcp") || strings.EqualFold(part, "json") || strings.EqualFold(part, "http") {
			parts[index] = strings.ToUpper(part)
			continue
		}
		parts[index] = strings.ToUpper(part[:1]) + part[1:]
	}
	return strings.Join(parts, " ")
}

func fallbackSummary(template entryTemplate, server model.ManagedServer) string {
	if strings.TrimSpace(template.Summary) != "" {
		return template.Summary
	}
	return fmt.Sprintf("Tool exposed by the %s MCP server.", server.Name)
}

func fallbackString(value string, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return value
}

func fallbackCategory(value string) string {
	if strings.TrimSpace(value) == "" {
		return "Operations"
	}
	return value
}

func fallbackSettingsHint(value string, server model.ManagedServer) string {
	if strings.TrimSpace(value) != "" {
		return value
	}
	return fmt.Sprintf("%s inherits transport, auth, and launcher state from the %s MCP configuration.", displayName(server.Name), server.Name)
}

func toolSource(server model.ManagedServer) string {
	switch catalogKey(server) {
	case "agent-task-manager":
		return "AgentTaskManager first-party tool catalog"
	case "chrome-devtools":
		return "Chrome DevTools MCP command surface"
	case "filesystem", "git", "ripgrep", "tree-sitter":
		return "Known downstream MCP tool catalog"
	default:
		return "Discovered MCP tool catalog"
	}
}
