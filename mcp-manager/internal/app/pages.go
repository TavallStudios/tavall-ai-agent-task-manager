package app

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"sort"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

func (server *Server) handleIndex(writer http.ResponseWriter, request *http.Request) {
	documents, err := server.service.Discover(request.Context())
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}

	sidebarServers, sidebarTools, err := server.buildSidebarData(request.Context(), documents)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}

	selectedDocID, selectedServerName, selectedToolName, selectedServerKey, selectedToolKey := resolveSelection(request, sidebarServers, sidebarTools, documents)
	data := pageData{
		Documents:         documents,
		Servers:           sidebarServers,
		Tools:             sidebarTools,
		Plugins:           server.service.Catalog(),
		Roots:             server.service.Roots(),
		ManagedHub:        server.service.ManagedHub(),
		SelectedDocID:     selectedDocID,
		SelectedServerKey: selectedServerKey,
		SelectedToolKey:   selectedToolKey,
		Flash:             request.URL.Query().Get("flash"),
	}
	if selectedDocID != "" && selectedServerName != "" {
		data.Selected, err = server.selectedServerPage(request.Context(), selectedDocID, selectedServerName, selectedToolName)
		if err != nil {
			data.ErrorMessage = err.Error()
		}
	}
	data.Metrics = buildDashboardMetrics(documents, sidebarServers, sidebarTools, data.Selected)
	log.Printf("[mcp-manager] ui index documents=%d servers=%d tools=%d selectedServer=%s selectedTool=%s", len(documents), len(sidebarServers), len(sidebarTools), data.SelectedServerKey, data.SelectedToolKey)
	server.renderPage(writer, http.StatusOK, data)
}

func (server *Server) handleDocument(writer http.ResponseWriter, request *http.Request) {
	path := strings.TrimPrefix(request.URL.Path, "/documents/")
	path = strings.Trim(path, "/")
	if path == "" {
		http.NotFound(writer, request)
		return
	}

	parts := strings.Split(path, "/")
	documentID := parts[0]
	if len(parts) == 1 && request.Method == http.MethodPost {
		server.handleDocumentForm(writer, request, documentID)
		return
	}
	if len(parts) == 2 && parts[1] == "restore" && request.Method == http.MethodPost {
		if err := request.ParseForm(); err != nil {
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}
		selectedServer := request.FormValue("selectedServer")
		selectedTool := request.FormValue("selectedTool")
		if err := server.service.Restore(documentID, request.FormValue("backupId")); err != nil {
			http.Error(writer, err.Error(), http.StatusInternalServerError)
			return
		}
		log.Printf("[mcp-manager] restore document=%s backup=%s", documentID, request.FormValue("backupId"))
		http.Redirect(writer, request, selectionURL(documentID, selectedServer, selectedTool, "Backup restored"), http.StatusSeeOther)
		return
	}
	http.NotFound(writer, request)
}

func (server *Server) handleRoots(writer http.ResponseWriter, request *http.Request) {
	if request.Method != http.MethodPost {
		http.NotFound(writer, request)
		return
	}
	if err := request.ParseForm(); err != nil {
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return
	}
	action := request.FormValue("action")
	switch action {
	case "add-root":
		if err := server.service.AddRoot(request.FormValue("rootPath")); err != nil {
			http.Redirect(writer, request, "/?flash="+actionError(err), http.StatusSeeOther)
			return
		}
		http.Redirect(writer, request, "/?flash=Discovery root added", http.StatusSeeOther)
	case "remove-root":
		if err := server.service.RemoveRoot(request.FormValue("rootPath")); err != nil {
			http.Redirect(writer, request, "/?flash="+actionError(err), http.StatusSeeOther)
			return
		}
		http.Redirect(writer, request, "/?flash=Discovery root removed", http.StatusSeeOther)
	case "seed-hub":
		hubPath, err := server.service.SeedManagedHub()
		if err != nil {
			http.Redirect(writer, request, "/?flash="+actionError(err), http.StatusSeeOther)
			return
		}
		http.Redirect(writer, request, "/?flash="+urlQueryEscape("Managed hub updated at "+hubPath), http.StatusSeeOther)
	default:
		http.Redirect(writer, request, "/", http.StatusSeeOther)
	}
}

func (server *Server) handleDocumentForm(writer http.ResponseWriter, request *http.Request, documentID string) {
	if err := request.ParseForm(); err != nil {
		http.Error(writer, err.Error(), http.StatusBadRequest)
		return
	}

	selectedServerName := request.FormValue("selectedServer")
	selectedToolName := request.FormValue("selectedTool")
	if strings.TrimSpace(selectedServerName) == "" {
		http.Error(writer, "selectedServer is required", http.StatusBadRequest)
		return
	}

	baseProfile, err := server.service.BuildProfile(documentID)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
	backendRegistry, err := server.service.BackendRegistry(documentID)
	if err != nil {
		backendRegistry = model.BackendRegistry{}
	}
	profile, err := buildProfileFromForm(baseProfile, server.service.Catalog(), request, selectedServerName)
	if err != nil {
		server.renderWithError(writer, request, documentID, selectedServerName, selectedToolName, err.Error(), baseProfile, "")
		return
	}
	if strings.EqualFold(selectedServerName, "agent-task-manager") {
		backendRegistry, err = buildBackendRegistryFromForm(backendRegistry, request)
		if err != nil {
			server.renderWithError(writer, request, documentID, selectedServerName, selectedToolName, err.Error(), baseProfile, "")
			return
		}
	}
	action := request.FormValue("action")
	if strings.HasPrefix(action, "quick-") {
		profile = applyQuickAction(profile, server.service.Catalog(), selectedServerName, action)
		preview, normalized, err := server.service.Preview(documentID, profile)
		if err != nil {
			server.renderWithError(writer, request, documentID, selectedServerName, selectedToolName, err.Error(), profile, "")
			return
		}
		log.Printf("[mcp-manager] quick-action document=%s server=%s tool=%s action=%s", documentID, selectedServerName, selectedToolName, action)
		server.renderWithState(writer, request, documentID, selectedServerName, selectedToolName, normalized, preview, "Quick action applied.")
		return
	}

	switch action {
	case "save":
		_, preview, normalized, err := server.service.Save(documentID, profile)
		if err != nil {
			server.renderWithError(writer, request, documentID, selectedServerName, selectedToolName, err.Error(), profile, "")
			return
		}
		if strings.EqualFold(selectedServerName, "agent-task-manager") {
			if _, _, err := server.service.SaveBackendRegistry(documentID, backendRegistry); err != nil {
				server.renderWithError(writer, request, documentID, selectedServerName, selectedToolName, err.Error(), normalized, preview)
				return
			}
		}
		log.Printf("[mcp-manager] save document=%s server=%s tool=%s", documentID, selectedServerName, selectedToolName)
		server.renderWithState(writer, request, documentID, selectedServerName, selectedToolName, normalized, preview, "Saved MCP config.")
	case "preview":
		preview, normalized, err := server.service.Preview(documentID, profile)
		if err != nil {
			server.renderWithError(writer, request, documentID, selectedServerName, selectedToolName, err.Error(), profile, "")
			return
		}
		log.Printf("[mcp-manager] preview document=%s server=%s tool=%s", documentID, selectedServerName, selectedToolName)
		server.renderWithState(writer, request, documentID, selectedServerName, selectedToolName, normalized, preview, "Preview updated.")
	default:
		http.Redirect(writer, request, selectionURL(documentID, selectedServerName, selectedToolName, ""), http.StatusSeeOther)
	}
}

func (server *Server) selectedServerPage(ctx context.Context, documentID string, selectedServerName string, selectedToolName string) (*selectedServerPageData, error) {
	_ = ctx
	document, err := server.service.Document(documentID)
	if err != nil {
		return nil, err
	}
	profile, err := server.service.BuildProfile(documentID)
	if err != nil {
		return nil, err
	}
	backups, err := server.service.ListBackups(documentID)
	if err != nil {
		return nil, err
	}
	preview, _, err := server.service.Preview(documentID, profile)
	if err != nil {
		return nil, err
	}
	serverItem, ok := findServer(profile.Servers, selectedServerName)
	if !ok {
		return nil, fmt.Errorf("unknown server %s", selectedServerName)
	}
	tools := server.service.ServerTools(documentID, serverItem)
	selected := &selectedServerPageData{
		Document:     document,
		Profile:      profile,
		Server:       serverItem,
		Definition:   findDefinition(server.service.Catalog(), serverItem.PluginID),
		Backups:      backups,
		Preview:      preview,
		SelectedName: selectedServerName,
		Tools:        tools,
	}
	if strings.EqualFold(serverItem.PluginID, "agent-task-manager") || strings.EqualFold(serverItem.Name, "agent-task-manager") {
		backendRegistry, registryErr := server.service.BackendRegistry(documentID)
		if registryErr == nil {
			selected.Backends = backendRegistry
		}
	}
	if selectedToolName != "" {
		toolItem, found := findTool(tools, selectedToolName)
		if !found {
			return nil, fmt.Errorf("unknown tool %s", selectedToolName)
		}
		selected.SelectedTool = &toolItem
	}
	return selected, nil
}

func (server *Server) buildSidebarData(ctx context.Context, documents []model.DocumentSummary) ([]sidebarServer, []sidebarTool, error) {
	servers := make([]sidebarServer, 0, len(documents))
	tools := make([]sidebarTool, 0, len(documents)*4)
	for _, document := range documents {
		profile, err := server.service.BuildProfile(document.ID)
		if err != nil {
			return nil, nil, err
		}
		for _, serverItem := range profile.Servers {
			serverTools := server.service.ServerTools(document.ID, serverItem)
			servers = append(servers, sidebarServer{
				Key:        serverKey(document.ID, serverItem.Name),
				DocumentID: document.ID,
				ServerName: serverItem.Name,
				PluginID:   serverItem.PluginID,
				SourceKind: document.SourceKind,
				Enabled:    serverItem.Enabled,
				Required:   serverItem.Required,
				Health:     serverItem.HealthStatus,
				Path:       document.Path,
				ToolCount:  len(serverTools),
			})
			for _, toolItem := range serverTools {
				tools = append(tools, sidebarTool{
					Key:         toolItem.Key,
					DocumentID:  document.ID,
					ServerName:  serverItem.Name,
					ToolName:    toolItem.Name,
					DisplayName: toolItem.DisplayName,
					Category:    toolItem.Category,
					Summary:     toolItem.Summary,
					Enabled:     serverItem.Enabled,
					PluginID:    serverItem.PluginID,
					BackendID:   toolItem.BackendID,
					BackendName: toolItem.BackendName,
				})
			}
		}
	}
	sort.Slice(servers, func(left int, right int) bool {
		if servers[left].ServerName == servers[right].ServerName {
			return servers[left].DocumentID < servers[right].DocumentID
		}
		return servers[left].ServerName < servers[right].ServerName
	})
	sort.Slice(tools, func(left int, right int) bool {
		if tools[left].ServerName == tools[right].ServerName {
			return tools[left].DisplayName < tools[right].DisplayName
		}
		return tools[left].ServerName < tools[right].ServerName
	})
	_ = ctx
	return servers, tools, nil
}

func (server *Server) renderWithError(writer http.ResponseWriter, request *http.Request, documentID string, selectedServerName string, selectedToolName string, errorMessage string, profile model.ManagedProfile, preview string) {
	selected, err := server.selectedServerPage(request.Context(), documentID, selectedServerName, selectedToolName)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
	selected.Profile = profile
	selected.Preview = preview
	if serverItem, ok := findServer(profile.Servers, selectedServerName); ok {
		selected.Server = serverItem
		selected.Definition = findDefinition(server.service.Catalog(), serverItem.PluginID)
		selected.Tools = server.service.ServerTools(documentID, serverItem)
		if selectedToolName != "" {
			if toolItem, found := findTool(selected.Tools, selectedToolName); found {
				selected.SelectedTool = &toolItem
			}
		}
	}
	server.renderDashboard(writer, request, http.StatusBadRequest, documentID, selectedServerName, selectedToolName, errorMessage, "", selected)
}

func (server *Server) renderWithState(writer http.ResponseWriter, request *http.Request, documentID string, selectedServerName string, selectedToolName string, profile model.ManagedProfile, preview string, flash string) {
	selected, err := server.selectedServerPage(request.Context(), documentID, selectedServerName, selectedToolName)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
	selected.Profile = profile
	selected.Preview = preview
	if serverItem, ok := findServer(profile.Servers, selectedServerName); ok {
		selected.Server = serverItem
		selected.Definition = findDefinition(server.service.Catalog(), serverItem.PluginID)
		selected.Tools = server.service.ServerTools(documentID, serverItem)
		if selectedToolName != "" {
			if toolItem, found := findTool(selected.Tools, selectedToolName); found {
				selected.SelectedTool = &toolItem
			}
		}
	}
	server.renderDashboard(writer, request, http.StatusOK, documentID, selectedServerName, selectedToolName, "", flash, selected)
}

func (server *Server) renderDashboard(writer http.ResponseWriter, request *http.Request, status int, documentID string, selectedServerName string, selectedToolName string, errorMessage string, flash string, selected *selectedServerPageData) {
	documents := mustDiscover(request.Context(), server.service)
	sidebarServers, sidebarTools, err := server.buildSidebarData(request.Context(), documents)
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
	data := pageData{
		Documents:         documents,
		Servers:           sidebarServers,
		Tools:             sidebarTools,
		Plugins:           server.service.Catalog(),
		Roots:             server.service.Roots(),
		ManagedHub:        server.service.ManagedHub(),
		SelectedDocID:     documentID,
		SelectedServerKey: serverKey(documentID, selectedServerName),
		SelectedToolKey:   selectedToolKey(documentID, selectedServerName, selectedToolName),
		Selected:          selected,
		ErrorMessage:      errorMessage,
		Flash:             flash,
	}
	data.Metrics = buildDashboardMetrics(documents, sidebarServers, sidebarTools, selected)
	server.renderPage(writer, status, data)
}

func (server *Server) renderPage(writer http.ResponseWriter, status int, data pageData) {
	writer.Header().Set("Content-Type", "text/html; charset=utf-8")
	writer.WriteHeader(status)
	if err := server.templates.ExecuteTemplate(writer, "page.html", data); err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
	}
}

func actionError(err error) string {
	return url.QueryEscape("Action failed: " + err.Error())
}

func urlQueryEscape(value string) string { return url.QueryEscape(value) }
