package app

import (
	"embed"
	"encoding/json"
	"fmt"
	"html/template"
	"net/http"
	"sort"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/manager"
)

//go:embed templates/*.html
var templateFS embed.FS

type Server struct {
	service   *manager.Service
	templates *template.Template
}

func NewServer(service *manager.Service) (*Server, error) {
	functions := template.FuncMap{
		"joinLines": strings.Join,
		"envBlock": func(env map[string]string) string {
			if len(env) == 0 {
				return ""
			}
			lines := make([]string, 0, len(env))
			for key, value := range env {
				lines = append(lines, fmt.Sprintf("%s=%s", key, value))
			}
			sort.Strings(lines)
			return strings.Join(lines, "\n")
		},
		"isChecked": func(value bool) string {
			if value {
				return "checked"
			}
			return ""
		},
		"settingValue": func(settings map[string]any, key string) string {
			if settings == nil {
				return ""
			}
			value, ok := settings[key]
			if !ok {
				return ""
			}
			return fmt.Sprint(value)
		},
		"settingChecked": func(settings map[string]any, key string) string {
			if settings == nil {
				return ""
			}
			value, ok := settings[key]
			if !ok {
				return ""
			}
			switch typed := value.(type) {
			case bool:
				if typed {
					return "checked"
				}
			case string:
				if strings.EqualFold(typed, "true") {
					return "checked"
				}
			}
			return ""
		},
		"fieldTypeIs": func(value any, expected string) bool {
			return strings.EqualFold(fmt.Sprint(value), expected)
		},
		"jsonBlock": func(value any) string {
			if value == nil {
				return "{}"
			}
			payload, err := json.MarshalIndent(value, "", "  ")
			if err != nil {
				return "{}"
			}
			if string(payload) == "null" {
				return "{}"
			}
			return string(payload)
		},
		"countNestedKeys": func(value map[string]any) int {
			return len(value)
		},
		"hasPreview": func(value string) bool {
			return strings.TrimSpace(value) != ""
		},
	}
	templates, err := template.New("page.html").Funcs(functions).ParseFS(templateFS, "templates/*.html")
	if err != nil {
		return nil, fmt.Errorf("parse templates: %w", err)
	}
	return &Server{service: service, templates: templates}, nil
}

func (server *Server) ListenAndServe(listenAddress string) error {
	httpServer := &http.Server{
		Addr:    listenAddress,
		Handler: server.routes(),
	}
	return httpServer.ListenAndServe()
}

func (server *Server) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/", server.handleIndex)
	mux.HandleFunc("/documents/", server.handleDocument)
	mux.HandleFunc("/roots", server.handleRoots)
	mux.HandleFunc("/api/discovery", server.handleAPIDiscovery)
	mux.HandleFunc("/api/overview", server.handleAPIOverview)
	mux.HandleFunc("/api/documents/", server.handleAPIDocument)
	return mux
}
