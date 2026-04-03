package app

import (
	"encoding/json"
	"log"
	"net/http"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

func (server *Server) handleAPIDiscovery(writer http.ResponseWriter, request *http.Request) {
	documents, err := server.service.Discover(request.Context())
	if err != nil {
		http.Error(writer, err.Error(), http.StatusInternalServerError)
		return
	}
	log.Printf("[mcp-manager] api discovery documents=%d", len(documents))
	writeJSON(writer, http.StatusOK, documents)
}

func (server *Server) handleAPIOverview(writer http.ResponseWriter, request *http.Request) {
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
	backendRegistries := make([]model.BackendRegistry, 0, len(documents))
	for _, document := range documents {
		profile, profileErr := server.service.BuildProfile(document.ID)
		if profileErr != nil {
			continue
		}
		for _, serverItem := range profile.Servers {
			if !strings.EqualFold(serverItem.PluginID, "agent-task-manager") && !strings.EqualFold(serverItem.Name, "agent-task-manager") {
				continue
			}
			registry, registryErr := server.service.BackendRegistry(document.ID)
			if registryErr == nil && len(registry.Connectors) > 0 {
				backendRegistries = append(backendRegistries, registry)
			}
			break
		}
	}
	log.Printf("[mcp-manager] api overview documents=%d servers=%d tools=%d", len(documents), len(sidebarServers), len(sidebarTools))
	writeJSON(writer, http.StatusOK, map[string]any{
		"documents":  documents,
		"servers":    sidebarServers,
		"tools":      sidebarTools,
		"backends":   backendRegistries,
		"roots":      server.service.Roots(),
		"managedHub": server.service.ManagedHub(),
	})
}

func (server *Server) handleAPIDocument(writer http.ResponseWriter, request *http.Request) {
	path := strings.TrimPrefix(request.URL.Path, "/api/documents/")
	path = strings.Trim(path, "/")
	if path == "" {
		http.NotFound(writer, request)
		return
	}
	parts := strings.Split(path, "/")
	documentID := parts[0]
	selectedServer := request.URL.Query().Get("server")
	selectedTool := request.URL.Query().Get("tool")

	if len(parts) == 1 && request.Method == http.MethodGet {
		if strings.TrimSpace(selectedServer) == "" {
			profile, err := server.service.BuildProfile(documentID)
			if err != nil {
				http.Error(writer, err.Error(), http.StatusNotFound)
				return
			}
			if len(profile.Servers) > 0 {
				selectedServer = profile.Servers[0].Name
			}
		}
		page, err := server.selectedServerPage(request.Context(), documentID, selectedServer, selectedTool)
		if err != nil {
			http.Error(writer, err.Error(), http.StatusNotFound)
			return
		}
		writeJSON(writer, http.StatusOK, page)
		return
	}
	if len(parts) != 2 {
		http.NotFound(writer, request)
		return
	}

	switch {
	case request.Method == http.MethodPost && parts[1] == "preview":
		var profile model.ManagedProfile
		if err := json.NewDecoder(request.Body).Decode(&profile); err != nil {
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}
		preview, normalized, err := server.service.Preview(documentID, profile)
		if err != nil {
			http.Error(writer, err.Error(), http.StatusInternalServerError)
			return
		}
		log.Printf("[mcp-manager] api preview document=%s servers=%d", documentID, len(normalized.Servers))
		writeJSON(writer, http.StatusOK, map[string]any{"preview": preview, "profile": normalized})
	case request.Method == http.MethodPost && parts[1] == "save":
		var profile model.ManagedProfile
		if err := json.NewDecoder(request.Body).Decode(&profile); err != nil {
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}
		backup, preview, normalized, err := server.service.Save(documentID, profile)
		if err != nil {
			http.Error(writer, err.Error(), http.StatusInternalServerError)
			return
		}
		log.Printf("[mcp-manager] api save document=%s backup=%s", documentID, backup.ID)
		writeJSON(writer, http.StatusOK, map[string]any{"backup": backup, "preview": preview, "profile": normalized})
	case request.Method == http.MethodGet && parts[1] == "backups":
		backups, err := server.service.ListBackups(documentID)
		if err != nil {
			http.Error(writer, err.Error(), http.StatusInternalServerError)
			return
		}
		log.Printf("[mcp-manager] api backups document=%s count=%d", documentID, len(backups))
		writeJSON(writer, http.StatusOK, backups)
	case request.Method == http.MethodPost && parts[1] == "restore":
		var payload struct {
			BackupID string `json:"backupId"`
		}
		if err := json.NewDecoder(request.Body).Decode(&payload); err != nil {
			http.Error(writer, err.Error(), http.StatusBadRequest)
			return
		}
		if err := server.service.Restore(documentID, payload.BackupID); err != nil {
			http.Error(writer, err.Error(), http.StatusInternalServerError)
			return
		}
		log.Printf("[mcp-manager] api restore document=%s backup=%s", documentID, payload.BackupID)
		writeJSON(writer, http.StatusOK, map[string]string{"status": "restored"})
	default:
		http.NotFound(writer, request)
	}
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(status)
	encoder := json.NewEncoder(writer)
	encoder.SetIndent("", "  ")
	_ = encoder.Encode(value)
}
