package storage

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

type SettingsStore struct {
	path string
}

func NewSettingsStore(root string) (*SettingsStore, error) {
	baseRoot, err := resolveStoreRoot(root)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(baseRoot, 0o755); err != nil {
		return nil, fmt.Errorf("create settings root: %w", err)
	}
	return &SettingsStore{path: filepath.Join(baseRoot, "settings.json")}, nil
}

func (store *SettingsStore) Load() (model.RootSettings, error) {
	content, err := os.ReadFile(store.path)
	if err != nil {
		if os.IsNotExist(err) {
			return model.RootSettings{}, nil
		}
		return model.RootSettings{}, fmt.Errorf("read settings: %w", err)
	}
	var settings model.RootSettings
	if err := json.Unmarshal(content, &settings); err != nil {
		return model.RootSettings{}, fmt.Errorf("parse settings: %w", err)
	}
	return settings, nil
}

func (store *SettingsStore) Save(settings model.RootSettings) error {
	content, err := json.MarshalIndent(settings, "", "  ")
	if err != nil {
		return fmt.Errorf("encode settings: %w", err)
	}
	return os.WriteFile(store.path, append(content, '\n'), 0o644)
}

func resolveStoreRoot(root string) (string, error) {
	if strings.TrimSpace(root) == "" {
		homeDirectory, err := os.UserHomeDir()
		if err != nil {
			return "", fmt.Errorf("resolve user home: %w", err)
		}
		return filepath.Join(homeDirectory, ".mcp-manager"), nil
	}
	return root, nil
}
