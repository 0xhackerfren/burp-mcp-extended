# Complete Tool Reference

This extension exposes **53 MCP tools** to AI clients. Tools marked "Pro Only" require Burp Suite Professional.

## HTTP Requests (2 tools)

| Tool | Description |
|------|-------------|
| `send_http1_request` | Send an HTTP/1.1 request and return the response |
| `send_http2_request` | Send an HTTP/2 request with pseudo-headers and return the response |

## Proxy History (6 tools)

| Tool | Description | Edition |
|------|-------------|---------|
| `get_proxy_http_history` | List proxy HTTP history items (paginated) | All |
| `get_proxy_http_history_regex` | Search proxy history by regex (paginated) | All |
| `get_proxy_history_item` | Get full request/response for a specific history item by index | Pro |
| `highlight_proxy_item` | Set highlight color and notes on a proxy history item | Pro |
| `get_proxy_websocket_history` | List proxy WebSocket history (paginated) | All |
| `get_proxy_websocket_history_regex` | Search WebSocket history by regex (paginated) | All |

## Site Map (6 tools) -- Pro Only

| Tool | Description |
|------|-------------|
| `get_site_map_urls` | Get deduplicated URLs with optional prefix filter and query string control |
| `get_site_map_entries` | Get entries with method, URL, status, MIME type, parameters (no bodies) |
| `get_site_map_request_response` | Get full request/response pairs from the site map |
| `get_site_map_issue_summary` | Get compact scanner issue summaries from the site map |
| `search_site_map` | Search site map by method, status code range, MIME type |
| `add_to_site_map` | Add a request/response pair to the site map |

## Scanner Control (7 tools) -- Pro Only

| Tool | Description |
|------|-------------|
| `start_crawl` | Start a Burp Scanner crawl with seed URLs |
| `start_audit` | Start an active or passive audit |
| `start_audit_on_requests` | Audit specific HTTP requests |
| `get_scan_status` | Get status of a running scan task (or `*` for all tasks) |
| `delete_scan_task` | Cancel and delete a running scan task |
| `get_audit_issues` | Get issues found by a specific audit task |
| `generate_scan_report` | Generate an HTML or XML report for scanner issues |

## Authenticated Crawling (3 tools)

| Tool | Description |
|------|-------------|
| `start_authenticated_crawl` | BFS crawl with cookies, custom headers, depth/page limits, rate control |
| `get_crawl_status` | Monitor crawl progress (pages visited, queued, depth, errors) |
| `stop_crawl` | Stop a running authenticated crawl |

## Scope Management (3 tools)

| Tool | Description |
|------|-------------|
| `include_in_scope` | Add a URL to Burp's target scope |
| `exclude_from_scope` | Remove a URL from Burp's target scope |
| `is_in_scope` | Check whether a URL is in scope |

## Collaborator (2 tools) -- Pro Only

| Tool | Description |
|------|-------------|
| `generate_collaborator_payload` | Generate a Collaborator payload URL for OOB testing |
| `get_collaborator_interactions` | Poll for DNS, HTTP, and SMTP interactions |

## Cookie Jar (2 tools)

| Tool | Description |
|------|-------------|
| `set_cookie_jar` | Inject a cookie into Burp's cookie jar with domain, path, and expiration |
| `get_cookie_jar` | List cookies from the cookie jar, optionally filtered by domain |

## Tool Integration (5 tools)

| Tool | Description |
|------|-------------|
| `create_repeater_tab` | Create a new Repeater tab with a request |
| `send_to_intruder` | Send a request to Intruder |
| `send_to_comparer` | Send data to Comparer |
| `send_to_decoder` | Send data to Decoder |
| `send_to_organizer` | Send a request/response pair to Organizer |

## Scanner Issues (1 tool) -- Pro Only

| Tool | Description |
|------|-------------|
| `get_scanner_issues` | List all scanner issues (paginated) |

## Encoding Utilities (7 tools)

| Tool | Description |
|------|-------------|
| `url_encode` | URL-encode a string |
| `url_decode` | URL-decode a string |
| `base64_encode` | Base64-encode a string |
| `base64_decode` | Base64-decode a string |
| `html_encode` | HTML-encode special characters to entities |
| `html_decode` | HTML-decode entities back to characters |
| `generate_random_string` | Generate a random string with specified length and character set |

## Configuration (4 tools)

| Tool | Description |
|------|-------------|
| `output_project_options` | Export current project configuration as JSON |
| `output_user_options` | Export current user configuration as JSON |
| `set_project_options` | Import/merge project configuration from JSON |
| `set_user_options` | Import/merge user configuration from JSON |

## Burp Control (3 tools)

| Tool | Description |
|------|-------------|
| `set_task_execution_engine_state` | Pause or resume the task execution engine |
| `set_proxy_intercept_state` | Enable or disable proxy intercept |
| `get_burp_version` | Get Burp Suite edition, version, and build number |

## Editor (2 tools)

| Tool | Description |
|------|-------------|
| `get_active_editor_contents` | Read the contents of the currently focused message editor |
| `set_active_editor_contents` | Write content to the currently focused message editor |
