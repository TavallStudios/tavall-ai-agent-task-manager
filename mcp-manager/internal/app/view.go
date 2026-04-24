package app

import (
	"context"
	"net/http"
	"net/url"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
	"github.com/agenttaskmanager/mcp-manager/internal/plugins"
)

type pageData struct {
	Documents         []model.DocumentSummary
	Servers           []sidebarServer
	Tools             []sidebarTool
	Selected          *selectedServerPageData
	Plugins           []plugins.Definition
	Metrics           dashboardMetrics
	Roots             []string
	ManagedHub        string
	Flash             string
	ErrorMessage      string
	SelectedDocID     string
	SelectedServerKey string
	SelectedToolKey   string
}

type dashboardMetrics struct {
	DocumentCount int
	ServerCount   int
	ToolCount     int
	EnabledCount  int
	ErrorCount    int
	WarningCount  int
	BackupCount   int
}

type sidebarServer struct {
	Key        string
	DocumentID string
	ServerName string
	PluginID   string
	SourceKind string
	Enabled    bool
	Required   bool
	Health     string
	Path       string
	ToolCount  int
}

type sidebarTool struct {
	Key         string
	DocumentID  string
	ServerName  string
	ToolName    string
	DisplayName string
	Category    string
	Summary     string
	Enabled     bool
	PluginID    string
	BackendID   string
	BackendName string
}

type selectedServerPageData struct {
	Document     model.SourceDocument
	Profile      model.ManagedProfile
	Server       model.ManagedServer
	Definition   plugins.Definition
	Backends     model.BackendRegistry
	Backups      []model.HistoryEntry
	Preview      string
	SelectedName string
	Tools        []model.ManagedTool
	SelectedTool *model.ManagedTool
}

func buildDashboardMetrics(documents []model.DocumentSummary, sidebarServers []sidebarServer, sidebarTools []sidebarTool, selected *selectedServerPageData) dashboardMetrics {
	metrics := dashboardMetrics{
		DocumentCount: len(documents),
		ServerCount:   len(sidebarServers),
		ToolCount:     len(sidebarTools),
	}
	for _, item := range sidebarServers {
		if item.Enabled {
			metrics.EnabledCount++
		}
	}
	if selected == nil {
		return metrics
	}
	metrics.BackupCount = len(selected.Backups)
	for _, finding := range selected.Profile.Validation {
		switch finding.Severity {
		case model.ValidationSeverityError:
			metrics.ErrorCount++
		case model.ValidationSeverityWarning:
			metrics.WarningCount++
		}
	}
	return metrics
}

func resolveSelection(request *http.Request, sidebarServers []sidebarServer, sidebarTools []sidebarTool, documents []model.DocumentSummary) (string, string, string, string, string) {
	selectedDocID := request.URL.Query().Get("doc")
	selectedServerName := request.URL.Query().Get("server")
	selectedToolName := request.URL.Query().Get("tool")
	if selectedDocID != "" && selectedServerName != "" {
		return selectedDocID, selectedServerName, selectedToolName, serverKey(selectedDocID, selectedServerName), selectedToolKey(selectedDocID, selectedServerName, selectedToolName)
	}
	if len(sidebarTools) > 0 && selectedToolName != "" {
		first := sidebarTools[0]
		return first.DocumentID, first.ServerName, first.ToolName, serverKey(first.DocumentID, first.ServerName), first.Key
	}
	if len(sidebarServers) > 0 {
		first := sidebarServers[0]
		return first.DocumentID, first.ServerName, "", first.Key, ""
	}
	if len(documents) > 0 {
		return documents[0].ID, "", "", "", ""
	}
	return "", "", "", "", ""
}

func selectionURL(documentID string, selectedServerName string, selectedToolName string, flash string) string {
	values := url.Values{}
	if strings.TrimSpace(documentID) != "" {
		values.Set("doc", documentID)
	}
	if strings.TrimSpace(selectedServerName) != "" {
		values.Set("server", selectedServerName)
	}
	if strings.TrimSpace(selectedToolName) != "" {
		values.Set("tool", selectedToolName)
	}
	if strings.TrimSpace(flash) != "" {
		values.Set("flash", flash)
	}
	if encoded := values.Encode(); encoded != "" {
		return "/?" + encoded
	}
	return "/"
}

func serverKey(documentID string, serverName string) string {
	return documentID + ":" + strings.ToLower(serverName)
}

func selectedToolKey(documentID string, serverName string, toolName string) string {
	if strings.TrimSpace(toolName) == "" {
		return ""
	}
	return documentID + ":" + strings.ToLower(serverName) + ":" + strings.ToLower(toolName)
}

func findServer(servers []model.ManagedServer, selectedServerName string) (model.ManagedServer, bool) {
	for _, server := range servers {
		if strings.EqualFold(server.Name, selectedServerName) {
			return server, true
		}
	}
	return model.ManagedServer{}, false
}

func findDefinition(catalog []plugins.Definition, pluginID string) plugins.Definition {
	for _, definition := range catalog {
		if definition.ID == pluginID {
			return definition
		}
	}
	for _, definition := range catalog {
		if definition.ID == "generic" {
			return definition
		}
	}
	return plugins.Definition{}
}

func findTool(tools []model.ManagedTool, selectedToolName string) (model.ManagedTool, bool) {
	for _, tool := range tools {
		if strings.EqualFold(tool.Name, selectedToolName) {
			return tool, true
		}
	}
	return model.ManagedTool{}, false
}

func mustDiscover(ctx context.Context, service interface {
	Discover(context.Context) ([]model.DocumentSummary, error)
}) []model.DocumentSummary {
	documents, err := service.Discover(ctx)
	if err != nil {
		return nil
	}
	return documents
}
