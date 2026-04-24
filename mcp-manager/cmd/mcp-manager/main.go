package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/app"
	"github.com/agenttaskmanager/mcp-manager/internal/bundle"
	"github.com/agenttaskmanager/mcp-manager/internal/manager"
)

func main() {
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)
	command := "serve"
	args := os.Args[1:]
	if len(args) > 0 && !strings.HasPrefix(args[0], "-") {
		command = args[0]
		args = args[1:]
	}

	flagSet := flag.NewFlagSet(command, flag.ExitOnError)
	listen := flagSet.String("listen", "127.0.0.1:47811", "listen address")
	roots := flagSet.String("roots", "", "comma-separated discovery roots")
	historyRoot := flagSet.String("history-root", "", "optional override for the manager history directory")
	outputRoot := flagSet.String("output-root", "", "output root for export-bundle")
	flagSet.Parse(args)

	workingDirectory, err := os.Getwd()
	if err != nil {
		log.Fatalf("resolve working directory: %v", err)
	}

	service, err := manager.NewService(manager.Options{
		WorkingDirectory: workingDirectory,
		Roots:            splitRoots(*roots),
		HistoryRoot:      *historyRoot,
	})
	if err != nil {
		log.Fatalf("initialize service: %v", err)
	}

	switch command {
	case "serve":
		server, err := app.NewServer(service)
		if err != nil {
			log.Fatalf("create server: %v", err)
		}
		log.Printf("[mcp-manager] serve listen=%s roots=%v history=%s", *listen, splitRoots(*roots), *historyRoot)
		fmt.Printf("mcp-manager listening on http://%s\n", *listen)
		log.Fatal(server.ListenAndServe(*listen))
	case "discover":
		documents, err := service.Discover(context.Background())
		if err != nil {
			log.Fatalf("discover documents: %v", err)
		}
		encoder := json.NewEncoder(os.Stdout)
		encoder.SetIndent("", "  ")
		if err := encoder.Encode(documents); err != nil {
			log.Fatalf("encode discovery output: %v", err)
		}
	case "export-bundle":
		targetRoot := strings.TrimSpace(*outputRoot)
		if targetRoot == "" {
			homeDirectory, err := os.UserHomeDir()
			if err != nil {
				log.Fatalf("resolve user home: %v", err)
			}
			targetRoot = filepath.Join(homeDirectory, "Documents", "MCP Manager Bundle")
		}
		if err := bundle.Export(targetRoot); err != nil {
			log.Fatalf("export bundle: %v", err)
		}
		fmt.Printf("exported bundle to %s\n", targetRoot)
	default:
		log.Fatalf("unknown command: %s", command)
	}
}

func splitRoots(value string) []string {
	if strings.TrimSpace(value) == "" {
		return nil
	}

	parts := strings.Split(value, ",")
	roots := make([]string, 0, len(parts))
	for _, part := range parts {
		trimmed := strings.TrimSpace(part)
		if trimmed != "" {
			roots = append(roots, trimmed)
		}
	}
	return roots
}
