package toolcatalog

func chromeDevToolsTemplates() []entryTemplate {
	return namedTemplates(
		"Browser Session", "Use the Chrome DevTools MCP settings page to change browser attach flags, telemetry controls, and executable path.",
		"click",
		"close_page",
		"drag",
		"emulate",
		"evaluate_script",
		"fill",
		"fill_form",
		"get_console_message",
		"get_network_request",
		"handle_dialog",
		"hover",
		"lighthouse_audit",
		"list_console_messages",
		"list_network_requests",
		"list_pages",
		"navigate_page",
		"new_page",
		"performance_analyze_insight",
		"performance_start_trace",
		"performance_stop_trace",
		"press_key",
		"resize_page",
		"select_page",
		"take_memory_snapshot",
		"take_screenshot",
		"take_snapshot",
		"type_text",
		"upload_file",
		"wait_for",
	)
}

func filesystemTemplates() []entryTemplate {
	return namedTemplates(
		"Filesystem", "Use the parent filesystem MCP settings to change reachable roots and file-edit behavior.",
		"create_directory",
		"directory_tree",
		"edit_file",
		"get_file_info",
		"list_allowed_directories",
		"list_directory",
		"list_directory_with_sizes",
		"move_file",
		"read_file",
		"read_media_file",
		"read_multiple_files",
		"read_text_file",
		"search_files",
		"write_file",
	)
}

func gitTemplates() []entryTemplate {
	return namedTemplates(
		"Git", "Use the parent git MCP settings for read-only repository inspection. Commit mutation should go through planGitCommit, prepareGitBranch, and createGitCommit.",
		"git_branch",
		"git_diff",
		"git_diff_staged",
		"git_diff_unstaged",
		"git_log",
		"git_show",
		"git_status",
	)
}

func ripgrepTemplates() []entryTemplate {
	return namedTemplates(
		"Search", "Use the parent ripgrep MCP settings to change search scope and matching behavior.",
		"advanced_search",
		"count_matches",
		"list_file_types",
		"list_files",
		"search",
	)
}

func treeSitterTemplates() []entryTemplate {
	return namedTemplates(
		"Code Intelligence", "Use the parent tree-sitter MCP settings to change parser access, project registration, and analysis scope.",
		"adapt_query",
		"analyze_complexity",
		"analyze_project",
		"build_query",
		"check_language_available",
		"clear_cache",
		"configure",
		"diagnose_config",
		"find_similar_code",
		"find_text",
		"find_usage",
		"get_ast",
		"get_dependencies",
		"get_file",
		"get_file_metadata",
		"get_node_at_position",
		"get_node_types",
		"get_query_template_tool",
		"get_symbols",
		"list_files",
		"list_languages",
		"list_projects_tool",
		"list_query_templates_tool",
		"register_project_tool",
		"remove_project_tool",
		"run_query",
	)
}

func namedTemplates(category string, settingsHint string, names ...string) []entryTemplate {
	templates := make([]entryTemplate, 0, len(names))
	for _, name := range names {
		templates = append(templates, entryTemplate{
			Name:         name,
			Category:     category,
			SettingsHint: settingsHint,
		})
	}
	return templates
}
