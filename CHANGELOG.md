# Changelog

All notable changes compared to the upstream [PortSwigger/mcp-server](https://github.com/PortSwigger/mcp-server) v1.1.2.

## [2.0.0] - 2026-03-16

### Added - New Tools (26 tools added to the stock 24)

**Site Map Access (6 tools)**
- `get_site_map_urls` - Retrieve deduplicated URLs from the site map with optional prefix filter
- `get_site_map_entries` - Get site map entries with method, URL, status, MIME type, and parameters
- `get_site_map_request_response` - Retrieve full HTTP request/response pairs from the site map (paginated)
- `get_site_map_issue_summary` - Get compact issue summaries from the site map
- `search_site_map` - Search site map by URL prefix, HTTP method, status code range, and MIME type
- `add_to_site_map` - Add a request/response pair to the site map

**Scanner Control (7 tools)**
- `start_crawl` - Start a Burp Scanner crawl with seed URLs
- `start_audit` - Start an active or passive audit
- `start_audit_on_requests` - Audit specific HTTP requests
- `get_scan_status` - Get running scan status (supports wildcard `*` for all tasks)
- `delete_scan_task` - Cancel and remove a running scan
- `get_audit_issues` - Get issues from a specific audit task
- `generate_scan_report` - Generate HTML/XML scan reports

**Scope Management (3 tools)**
- `include_in_scope` - Add a URL to Burp's target scope
- `exclude_from_scope` - Remove a URL from Burp's target scope
- `is_in_scope` - Check if a URL is in the current scope

**Cookie Jar (2 tools)**
- `set_cookie_jar` - Set a cookie in Burp's cookie jar with domain, path, and expiration
- `get_cookie_jar` - List cookies from the cookie jar, optionally filtered by domain

**Tool Integration (3 new tools)**
- `send_to_comparer` - Send data to Burp's Comparer tool
- `send_to_decoder` - Send data to Burp's Decoder tool
- `send_to_organizer` - Send a request/response pair to Burp's Organizer

**Encoding (2 new tools)**
- `html_encode` - HTML-encode special characters to entities
- `html_decode` - Decode HTML entities back to characters

**Proxy History Enhancements (2 new tools)**
- `get_proxy_history_item` - Get full request/response for a specific proxy history item by index
- `highlight_proxy_item` - Set highlight color and notes on proxy history items for visual triage

**Version Info (1 tool)**
- `get_burp_version` - Return Burp Suite edition, version, and build information

### Changed

- **Truncation limit**: Increased from 5,000 to 50,000 characters. Responses now include the original size when truncated.
- **Version centralization**: Version is defined once in `BuildInfo.kt` and referenced by `KtorServerManager`, `ExtensionBase`, and the MCP server info. No more hardcoded version strings scattered across files.
- **Build chain**: `shadowJar` works standalone for SSE-only deployments (Cursor). `embedProxyJar` is optional and only needed for Claude Desktop stdio support.
- **BappManifest**: Updated to reflect the extended extension with accurate author and description.
- **Extension name**: Displays as "Burp MCP Server (Extended)" in Burp's Extensions panel.

### Added - Infrastructure

- `BuildInfo.kt` - Centralized version and name constants
- `DownloadProxyJarTask` in `build.gradle.kts` - Gradle task to fetch `mcp-proxy-all.jar` from GitHub releases
- `CHANGELOG.md` - This file
- Complete README rewrite with architecture diagram, 53-tool reference, and security model docs

### Changed - Repository Organization

- **Source structure**: Split into `extension/` (Burp plugin entry point, UI, providers) and `mcp/` (MCP server, tools, security, config) under `src/main/kotlin/net/portswigger/`
- **Documentation**: Added `docs/` directory with `ARCHITECTURE.md`, `TOOLS_REFERENCE.md`, and `CONFIGURATION.md`
- **GitHub community health**: Added `CONTRIBUTING.md`, `SECURITY.md`, issue templates, PR template
- **Release workflow**: Added `.github/workflows/release.yml` for automated GitHub Releases on version tags

### Security

The existing security model (HTTP request approval, history access approval, scanner approval, auto-approve targets) is preserved unchanged from upstream. All new tools route through the appropriate security checks.
