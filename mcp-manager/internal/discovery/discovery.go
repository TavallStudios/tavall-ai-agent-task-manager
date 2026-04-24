package discovery

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/config"
	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

var candidatePatterns = []string{
	"config.toml",
	".codex/config.toml",
	".mcp.json",
	"mcp-config.json",
	".ai/mcp/*.json",
	".mcp/*.json",
	".mcp/*.toml",
	"mcp-config/*.json",
	"mcp-config/*.toml",
	"mcp-servers/*.json",
	"mcp-servers/*.toml",
}

var nestedDirectoryCandidates = []string{
	".ai/mcp",
	".mcp",
	"mcp-config",
	"mcp-servers",
}

func DefaultRoots(workingDirectory string) []string {
	roots := []string{workingDirectory}
	if codexHome := strings.TrimSpace(os.Getenv("CODEX_HOME")); codexHome != "" {
		roots = append(roots, codexHome)
	}
	if homeDirectory, err := os.UserHomeDir(); err == nil && homeDirectory != "" {
		roots = append(roots, homeDirectory, filepath.Join(homeDirectory, ".codex"))
	}
	return dedupePaths(roots)
}

func ResolveRoots(workingDirectory string, explicitRoots []string) []string {
	if len(explicitRoots) > 0 {
		return dedupePaths(explicitRoots)
	}
	return DefaultRoots(workingDirectory)
}

func NormalizeRoots(roots []string) []string {
	return dedupePaths(roots)
}

func DiscoverDocuments(roots []string) ([]model.SourceDocument, error) {
	paths := make([]string, 0, 16)
	log.Printf("[mcp-manager] discovery roots=%v", dedupePaths(roots))
	for _, root := range dedupePaths(roots) {
		discovered, err := discoverRoot(root)
		if err != nil {
			return nil, err
		}
		paths = append(paths, discovered...)
	}
	paths = dedupePaths(paths)
	sort.Strings(paths)

	documents := make([]model.SourceDocument, 0, len(paths))
	for _, path := range paths {
		document, err := config.LoadDocument(path)
		if err != nil {
			return nil, fmt.Errorf("load %s: %w", path, err)
		}
		documents = append(documents, document)
	}
	log.Printf("[mcp-manager] discovery documents=%d", len(documents))
	return documents, nil
}

func discoverRoot(root string) ([]string, error) {
	if strings.TrimSpace(root) == "" {
		return nil, nil
	}
	if _, err := os.Stat(root); err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, fmt.Errorf("stat root %s: %w", root, err)
	}

	paths := make([]string, 0, len(candidatePatterns))
	if isDirectMcpDirectory(root) {
		matches, err := walkConfigFiles(root)
		if err != nil {
			return nil, err
		}
		paths = append(paths, matches...)
	}
	for _, pattern := range candidatePatterns {
		matches, err := filepath.Glob(filepath.Join(root, filepath.FromSlash(pattern)))
		if err != nil {
			return nil, fmt.Errorf("glob %s: %w", pattern, err)
		}
		for _, match := range matches {
			info, err := os.Stat(match)
			if err != nil || info.IsDir() {
				continue
			}
			paths = append(paths, match)
		}
	}
	for _, nested := range nestedDirectoryCandidates {
		nestedRoot := filepath.Join(root, filepath.FromSlash(nested))
		info, err := os.Stat(nestedRoot)
		if err != nil || !info.IsDir() {
			continue
		}
		matches, err := walkConfigFiles(nestedRoot)
		if err != nil {
			return nil, err
		}
		paths = append(paths, matches...)
	}
	return dedupePaths(paths), nil
}

func isDirectMcpDirectory(root string) bool {
	name := strings.ToLower(filepath.Base(filepath.Clean(root)))
	switch name {
	case ".mcp", "mcp", "mcp-config", "mcp-servers":
		return true
	}
	return strings.HasSuffix(name, "-mcp") || strings.HasSuffix(name, "_mcp")
}

func dedupePaths(values []string) []string {
	seen := make(map[string]struct{}, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		if strings.TrimSpace(value) == "" {
			continue
		}
		absolute := value
		if resolved, err := filepath.Abs(value); err == nil {
			absolute = resolved
		}
		key := strings.ToLower(filepath.Clean(absolute))
		if _, exists := seen[key]; exists {
			continue
		}
		seen[key] = struct{}{}
		result = append(result, absolute)
	}
	return result
}

func walkConfigFiles(root string) ([]string, error) {
	result := make([]string, 0, 16)
	err := filepath.WalkDir(root, func(path string, entry os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() {
			return nil
		}
		switch strings.ToLower(filepath.Ext(path)) {
		case ".json", ".toml":
			result = append(result, path)
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("walk %s: %w", root, err)
	}
	return result, nil
}
