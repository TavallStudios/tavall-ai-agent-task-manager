package storage

import (
	"crypto/sha1"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

type HistoryStore struct {
	root string
}

func NewHistoryStore(root string) (*HistoryStore, error) {
	baseRoot, err := resolveStoreRoot(root)
	if err != nil {
		return nil, err
	}
	root = baseRoot
	root = filepath.Join(root, "history")
	if err := os.MkdirAll(root, 0o755); err != nil {
		return nil, fmt.Errorf("create history root: %w", err)
	}
	return &HistoryStore{root: root}, nil
}

func (store *HistoryStore) Backup(documentPath string, content []byte) (model.HistoryEntry, error) {
	documentID := hashPath(documentPath)
	targetDirectory := filepath.Join(store.root, documentID)
	if err := os.MkdirAll(targetDirectory, 0o755); err != nil {
		return model.HistoryEntry{}, fmt.Errorf("create history directory: %w", err)
	}

	timestamp := time.Now().UTC()
	fileName := fmt.Sprintf("%s%s", timestamp.Format("20060102T150405.000000000Z"), filepath.Ext(documentPath))
	backupPath := filepath.Join(targetDirectory, fileName)
	if err := os.WriteFile(backupPath, content, 0o644); err != nil {
		return model.HistoryEntry{}, fmt.Errorf("write backup: %w", err)
	}

	return model.HistoryEntry{
		ID:         fileName,
		DocumentID: documentID,
		Path:       documentPath,
		BackupPath: backupPath,
		CreatedAt:  timestamp,
	}, nil
}

func (store *HistoryStore) List(documentPath string) ([]model.HistoryEntry, error) {
	documentID := hashPath(documentPath)
	targetDirectory := filepath.Join(store.root, documentID)
	entries, err := os.ReadDir(targetDirectory)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, fmt.Errorf("list backups: %w", err)
	}

	result := make([]model.HistoryEntry, 0, len(entries))
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		info, err := entry.Info()
		if err != nil {
			return nil, fmt.Errorf("backup metadata: %w", err)
		}
		result = append(result, model.HistoryEntry{
			ID:         entry.Name(),
			DocumentID: documentID,
			Path:       documentPath,
			BackupPath: filepath.Join(targetDirectory, entry.Name()),
			CreatedAt:  info.ModTime().UTC(),
		})
	}
	sort.Slice(result, func(left int, right int) bool {
		return result[left].CreatedAt.After(result[right].CreatedAt)
	})
	return result, nil
}

func (store *HistoryStore) Read(documentPath string, backupID string) ([]byte, error) {
	documentID := hashPath(documentPath)
	backupPath := filepath.Join(store.root, documentID, backupID)
	content, err := os.ReadFile(backupPath)
	if err != nil {
		return nil, fmt.Errorf("read backup: %w", err)
	}
	return content, nil
}

func hashPath(path string) string {
	hash := sha1.Sum([]byte(strings.ToLower(filepath.Clean(path))))
	return hex.EncodeToString(hash[:])
}
