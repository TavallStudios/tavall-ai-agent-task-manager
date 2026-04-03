package manager

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/agenttaskmanager/mcp-manager/internal/backends"
	"github.com/agenttaskmanager/mcp-manager/internal/config"
	"github.com/agenttaskmanager/mcp-manager/internal/discovery"
	"github.com/agenttaskmanager/mcp-manager/internal/model"
	"github.com/agenttaskmanager/mcp-manager/internal/plugins"
	"github.com/agenttaskmanager/mcp-manager/internal/storage"
	"github.com/agenttaskmanager/mcp-manager/internal/toolcatalog"
)

type Options struct {
	WorkingDirectory string
	Roots            []string
	HistoryRoot      string
}

type Service struct {
	workingDirectory string
	roots            []string
	registry         *plugins.Registry
	history          *storage.HistoryStore
	settings         *storage.SettingsStore
	tools            *toolcatalog.Catalog
	hubRoot          string

	mutex     sync.RWMutex
	documents map[string]model.SourceDocument
}

func NewService(options Options) (*Service, error) {
	historyStore, err := storage.NewHistoryStore(options.HistoryRoot)
	if err != nil {
		return nil, err
	}
	settingsStore, err := storage.NewSettingsStore(options.HistoryRoot)
	if err != nil {
		return nil, err
	}

	loadedSettings, err := settingsStore.Load()
	if err != nil {
		return nil, err
	}
	roots := discovery.ResolveRoots(options.WorkingDirectory, options.Roots)
	if len(loadedSettings.Roots) > 0 {
		roots = discovery.NormalizeRoots(loadedSettings.Roots)
	}
	hubRoot := strings.TrimSpace(loadedSettings.ManagedHub)
	if hubRoot == "" {
		hubRoot = defaultManagedHub(options.WorkingDirectory, options.Roots)
	}

	return &Service{
		workingDirectory: options.WorkingDirectory,
		roots:            roots,
		registry:         plugins.NewRegistry(),
		history:          historyStore,
		settings:         settingsStore,
		tools:            toolcatalog.New(),
		hubRoot:          hubRoot,
		documents:        map[string]model.SourceDocument{},
	}, nil
}

func (service *Service) Discover(ctx context.Context) ([]model.DocumentSummary, error) {
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
	}

	documents, err := discovery.DiscoverDocuments(service.Roots())
	if err != nil {
		return nil, err
	}

	service.mutex.Lock()
	defer service.mutex.Unlock()
	service.documents = make(map[string]model.SourceDocument, len(documents))

	summaries := make([]model.DocumentSummary, 0, len(documents))
	for _, document := range documents {
		service.documents[document.ID] = document
		summaries = append(summaries, model.DocumentSummary{
			ID:            document.ID,
			Path:          document.Path,
			SourceKind:    document.SourceKind,
			Scope:         document.Scope,
			Format:        document.Format,
			ServerCount:   len(document.Servers),
			Writable:      document.Writable,
			DiscoveredAt:  document.DiscoveredAt,
			HasManagerCfg: len(document.ManagerMetadata) > 0,
		})
	}
	return summaries, nil
}

func (service *Service) Catalog() []plugins.Definition {
	return service.registry.Catalog()
}

func (service *Service) Roots() []string {
	service.mutex.RLock()
	defer service.mutex.RUnlock()
	result := make([]string, 0, len(service.roots))
	result = append(result, service.roots...)
	return result
}

func (service *Service) ManagedHub() string {
	service.mutex.RLock()
	defer service.mutex.RUnlock()
	return service.hubRoot
}

func (service *Service) AddRoot(root string) error {
	root = strings.TrimSpace(root)
	if root == "" {
		return fmt.Errorf("root path is required")
	}
	service.mutex.Lock()
	defer service.mutex.Unlock()
	service.roots = discovery.NormalizeRoots(append(service.roots, root))
	return service.persistSettingsLocked()
}

func (service *Service) RemoveRoot(root string) error {
	root = strings.TrimSpace(root)
	if root == "" {
		return fmt.Errorf("root path is required")
	}
	service.mutex.Lock()
	defer service.mutex.Unlock()
	filtered := make([]string, 0, len(service.roots))
	for _, candidate := range service.roots {
		if strings.EqualFold(filepath.Clean(candidate), filepath.Clean(root)) {
			continue
		}
		filtered = append(filtered, candidate)
	}
	service.roots = discovery.NormalizeRoots(filtered)
	return service.persistSettingsLocked()
}

func (service *Service) SeedManagedHub() (string, error) {
	documents, err := discovery.DiscoverDocuments(service.seedSourceRoots())
	if err != nil {
		return "", err
	}
	service.mutex.Lock()
	defer service.mutex.Unlock()
	if err := os.MkdirAll(service.hubRoot, 0o755); err != nil {
		return "", fmt.Errorf("create managed hub: %w", err)
	}
	for index, document := range documents {
		content, err := os.ReadFile(document.Path)
		if err != nil {
			return "", fmt.Errorf("read %s: %w", document.Path, err)
		}
		targetPath := filepath.Join(service.hubRoot, managedHubFileName(document, index))
		if err := os.WriteFile(targetPath, content, 0o644); err != nil {
			return "", fmt.Errorf("write %s: %w", targetPath, err)
		}
	}
	service.roots = []string{service.hubRoot}
	if err := service.persistSettingsLocked(); err != nil {
		return "", err
	}
	return service.hubRoot, nil
}

func (service *Service) ToolsForProfile(documentID string, profile model.ManagedProfile) []model.ManagedTool {
	tools := make([]model.ManagedTool, 0, len(profile.Servers)*4)
	for _, server := range profile.Servers {
		tools = append(tools, service.ServerTools(documentID, server)...)
	}
	return tools
}

func (service *Service) ServerTools(documentID string, server model.ManagedServer) []model.ManagedTool {
	tools := service.tools.ForServer(documentID, server)
	if strings.EqualFold(server.PluginID, "agent-task-manager") || strings.EqualFold(server.Name, "agent-task-manager") {
		registry, err := service.BackendRegistry(documentID)
		if err == nil {
			for _, connector := range registry.Connectors {
				tools = append(tools, service.tools.ForBackend(documentID, server.Name, connector)...)
			}
		}
	}
	sort.Slice(tools, func(left int, right int) bool {
		if tools[left].BackendName == tools[right].BackendName {
			return tools[left].DisplayName < tools[right].DisplayName
		}
		return tools[left].BackendName < tools[right].BackendName
	})
	return tools
}

func (service *Service) BuildProfile(documentID string) (model.ManagedProfile, error) {
	document, err := service.document(documentID)
	if err != nil {
		return model.ManagedProfile{}, err
	}
	profile := service.buildProfile(document)
	log.Printf("[mcp-manager] build-profile document=%s servers=%d findings=%d", document.Path, len(profile.Servers), len(profile.Validation))
	return profile, nil
}

func (service *Service) Document(documentID string) (model.SourceDocument, error) {
	return service.document(documentID)
}

func (service *Service) BackendRegistry(documentID string) (model.BackendRegistry, error) {
	document, err := service.document(documentID)
	if err != nil {
		return model.BackendRegistry{}, err
	}
	return backends.Load(backends.DefaultPathForDocument(document.Path))
}

func (service *Service) SaveBackendRegistry(documentID string, registry model.BackendRegistry) (model.HistoryEntry, string, error) {
	document, err := service.document(documentID)
	if err != nil {
		return model.HistoryEntry{}, "", err
	}
	registry.Path = backends.DefaultPathForDocument(document.Path)
	rendered, err := backends.Render(registry)
	if err != nil {
		return model.HistoryEntry{}, "", err
	}
	if err := os.MkdirAll(filepath.Dir(registry.Path), 0o755); err != nil {
		return model.HistoryEntry{}, "", fmt.Errorf("create backend registry directory: %w", err)
	}

	var backup model.HistoryEntry
	currentContent, err := os.ReadFile(registry.Path)
	if err == nil {
		backup, err = service.history.Backup(registry.Path, currentContent)
		if err != nil {
			return model.HistoryEntry{}, "", err
		}
	} else if !os.IsNotExist(err) {
		return model.HistoryEntry{}, "", fmt.Errorf("read existing backend registry: %w", err)
	}

	if err := os.WriteFile(registry.Path, rendered, 0o644); err != nil {
		return model.HistoryEntry{}, "", fmt.Errorf("write backend registry: %w", err)
	}
	log.Printf("[mcp-manager] save backend-registry path=%s backup=%s", registry.Path, backup.ID)
	return backup, string(rendered), nil
}

func (service *Service) Preview(documentID string, profile model.ManagedProfile) (string, model.ManagedProfile, error) {
	document, err := service.document(documentID)
	if err != nil {
		return "", model.ManagedProfile{}, err
	}
	normalized := service.normalizeProfile(document, profile)
	rendered, err := config.RenderDocument(document, normalized)
	if err != nil {
		return "", model.ManagedProfile{}, err
	}
	log.Printf("[mcp-manager] preview document=%s servers=%d findings=%d", document.Path, len(normalized.Servers), len(normalized.Validation))
	return string(rendered), normalized, nil
}

func (service *Service) Save(documentID string, profile model.ManagedProfile) (model.HistoryEntry, string, model.ManagedProfile, error) {
	document, err := service.document(documentID)
	if err != nil {
		return model.HistoryEntry{}, "", model.ManagedProfile{}, err
	}

	rendered, normalized, err := service.Preview(documentID, profile)
	if err != nil {
		return model.HistoryEntry{}, "", model.ManagedProfile{}, err
	}
	if hasValidationErrors(normalized.Validation) {
		log.Printf("[mcp-manager] save-blocked document=%s findings=%d", document.Path, len(normalized.Validation))
		return model.HistoryEntry{}, "", model.ManagedProfile{}, fmt.Errorf("refusing to save invalid MCP configuration")
	}

	currentContent, err := os.ReadFile(document.Path)
	if err != nil {
		return model.HistoryEntry{}, "", model.ManagedProfile{}, fmt.Errorf("read existing document: %w", err)
	}
	backup, err := service.history.Backup(document.Path, currentContent)
	if err != nil {
		return model.HistoryEntry{}, "", model.ManagedProfile{}, err
	}
	if err := os.WriteFile(document.Path, []byte(rendered), 0o644); err != nil {
		return model.HistoryEntry{}, "", model.ManagedProfile{}, fmt.Errorf("write document: %w", err)
	}
	log.Printf("[mcp-manager] save document=%s backup=%s", document.Path, backup.ID)

	reloaded, err := config.LoadDocument(document.Path)
	if err == nil {
		service.mutex.Lock()
		service.documents[reloaded.ID] = reloaded
		service.mutex.Unlock()
	}
	return backup, rendered, normalized, nil
}

func (service *Service) ListBackups(documentID string) ([]model.HistoryEntry, error) {
	document, err := service.document(documentID)
	if err != nil {
		return nil, err
	}
	return service.history.List(document.Path)
}

func (service *Service) Restore(documentID string, backupID string) error {
	document, err := service.document(documentID)
	if err != nil {
		return err
	}

	currentContent, err := os.ReadFile(document.Path)
	if err != nil {
		return fmt.Errorf("read existing document: %w", err)
	}
	if _, err := service.history.Backup(document.Path, currentContent); err != nil {
		return err
	}

	backupContent, err := service.history.Read(document.Path, backupID)
	if err != nil {
		return err
	}
	if err := os.WriteFile(document.Path, backupContent, 0o644); err != nil {
		return fmt.Errorf("restore document: %w", err)
	}
	log.Printf("[mcp-manager] restore document=%s backup=%s", document.Path, backupID)

	reloaded, err := config.LoadDocument(document.Path)
	if err == nil {
		service.mutex.Lock()
		service.documents[reloaded.ID] = reloaded
		service.mutex.Unlock()
	}
	return nil
}

func (service *Service) document(documentID string) (model.SourceDocument, error) {
	service.mutex.RLock()
	document, ok := service.documents[documentID]
	service.mutex.RUnlock()
	if ok {
		return document, nil
	}

	if _, err := service.Discover(context.Background()); err != nil {
		return model.SourceDocument{}, err
	}
	service.mutex.RLock()
	document, ok = service.documents[documentID]
	service.mutex.RUnlock()
	if !ok {
		return model.SourceDocument{}, fmt.Errorf("unknown document: %s", documentID)
	}
	return document, nil
}

func (service *Service) buildProfile(document model.SourceDocument) model.ManagedProfile {
	servers := make([]model.ManagedServer, 0, len(document.Servers))
	validation := make([]model.ValidationMessage, 0, len(document.Servers))
	for _, server := range document.Servers {
		applied := service.registry.Apply(server)
		if applied.Scope == "" {
			applied.Scope = document.Scope
		}
		if applied.Source == "" {
			applied.Source = document.SourceKind
		}
		if applied.HealthStatus == "" {
			applied.HealthStatus = "unknown"
		}
		validation = append(validation, service.registry.Validate(applied)...)
		servers = append(servers, applied)
	}
	return model.ManagedProfile{
		DocumentID:  document.ID,
		Path:        document.Path,
		SourceKind:  document.SourceKind,
		Scope:       document.Scope,
		Format:      document.Format,
		GeneratedAt: time.Now().UTC(),
		Servers:     servers,
		Validation:  validation,
	}
}

func (service *Service) normalizeProfile(document model.SourceDocument, candidate model.ManagedProfile) model.ManagedProfile {
	profile := candidate
	profile.DocumentID = document.ID
	profile.Path = document.Path
	profile.SourceKind = document.SourceKind
	profile.Scope = document.Scope
	profile.Format = document.Format
	profile.GeneratedAt = time.Now().UTC()

	if profile.Servers == nil {
		profile.Servers = []model.ManagedServer{}
	}
	validation := make([]model.ValidationMessage, 0, len(profile.Servers))
	for index, server := range profile.Servers {
		profile.Servers[index] = service.registry.Apply(server)
		validation = append(validation, service.registry.Validate(profile.Servers[index])...)
	}
	profile.Validation = validation
	return profile
}

func hasValidationErrors(messages []model.ValidationMessage) bool {
	for _, message := range messages {
		if message.Severity == model.ValidationSeverityError {
			return true
		}
	}
	return false
}

func (service *Service) persistSettingsLocked() error {
	return service.settings.Save(model.RootSettings{
		Roots:      append([]string(nil), service.roots...),
		ManagedHub: service.hubRoot,
	})
}

func defaultManagedHub(workingDirectory string, explicitRoots []string) string {
	base := workingDirectory
	if len(explicitRoots) > 0 && strings.TrimSpace(explicitRoots[0]) != "" {
		base = explicitRoots[0]
	}
	if looksLikeManagedRoot(base) {
		return base
	}
	return filepath.Join(base, "mcp-hub")
}

func managedHubFileName(document model.SourceDocument, index int) string {
	baseName := filepath.Base(document.Path)
	if strings.TrimSpace(baseName) == "" || baseName == "." {
		baseName = fmt.Sprintf("document-%d.%s", index+1, document.Format)
	}
	return fmt.Sprintf("%02d-%s-%s", index+1, document.SourceKind, baseName)
}

func (service *Service) seedSourceRoots() []string {
	service.mutex.RLock()
	defer service.mutex.RUnlock()

	roots := make([]string, 0, len(service.roots))
	for _, root := range service.roots {
		if samePath(root, service.hubRoot) {
			continue
		}
		roots = append(roots, root)
	}
	if len(roots) > 0 {
		return discovery.NormalizeRoots(roots)
	}
	return discovery.DefaultRoots(service.workingDirectory)
}

func samePath(left string, right string) bool {
	return strings.EqualFold(filepath.Clean(left), filepath.Clean(right))
}

func looksLikeManagedRoot(path string) bool {
	name := strings.ToLower(filepath.Base(filepath.Clean(path)))
	switch name {
	case ".mcp", "mcp", "mcp-config", "mcp-servers":
		return true
	}
	return strings.HasSuffix(name, "-mcp") || strings.HasSuffix(name, "_mcp")
}
