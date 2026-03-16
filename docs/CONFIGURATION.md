# Configuration

All configuration is managed through the **MCP** tab in Burp Suite.

## Server Settings

| Option | Default | Description |
|--------|---------|-------------|
| Enabled | Off | Toggle the MCP SSE server on/off |
| Host | `127.0.0.1` | Server bind address |
| Port | `9876` | Server port |

## Security Settings

| Option | Default | Description |
|--------|---------|-------------|
| Enable config editing tools | Off | Allow MCP tools to modify Burp project/user configuration |
| Require HTTP request approval | On | Prompt before sending outbound HTTP requests |
| Require history access approval | On | Prompt before reading proxy history or site map |
| Always allow HTTP history | Off | Skip approval for HTTP history (requires history approval enabled) |
| Always allow WebSocket history | Off | Skip approval for WebSocket history (requires history approval enabled) |
| Require scanner approval | On | Prompt before starting crawls, audits, or generating reports |

## Auto-Approve Targets

When HTTP request approval is enabled, every outbound request triggers a dialog. To streamline workflows, you can pre-approve targets:

| Format | Example | Matches |
|--------|---------|---------|
| Exact hostname | `example.com` | `example.com` on any port |
| Host with port | `example.com:8443` | `example.com` only on port 8443 |
| Wildcard | `*.example.com` | `api.example.com`, `dev.example.com`, etc. |

Targets can be managed from the "Auto-Approved HTTP Targets" panel in the MCP tab, or automatically added via the "Always Allow Host" / "Always Allow Host:Port" buttons in the approval dialog.

### Wildcard Rules

- `*.example.com` matches `sub.example.com` and `deep.sub.example.com`
- `*.example.com` does NOT match `example.com` itself (the bare domain)
- `*.com` matches all `.com` domains (use with caution)
- Matching is case-insensitive

## Client Configuration

### Cursor

Add to your project's `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "burp": {
      "url": "http://127.0.0.1:9876"
    }
  }
}
```

### Claude Desktop

Use the built-in installer in Burp's MCP tab (recommended), or manually edit `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "burp": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-proxy-all.jar", "--sse-url", "http://127.0.0.1:9876"]
    }
  }
}
```

The Claude Desktop installer in the MCP tab automatically:
1. Extracts `mcp-proxy-all.jar` to your Burp data directory
2. Detects your Java installation path
3. Updates `claude_desktop_config.json` with the correct paths

### Other MCP Clients

Any client that supports SSE transport can connect directly to `http://127.0.0.1:9876`.

For clients that only support stdio transport, use the proxy JAR:

```bash
java -jar mcp-proxy-all.jar --sse-url http://127.0.0.1:9876
```

## Troubleshooting

### Server won't start

- Check that the port is not already in use by another application
- Try a different port (any value between 1024-65535)
- Check Burp's extension output tab for error details

### Client can't connect

- Verify the server is enabled and running (check the toggle in the MCP tab)
- Confirm the host and port match between the server config and client config
- If using a non-default host/port, update your client config to match

### Tools not appearing

- Some tools are only available in Burp Suite Professional (site map, scanner, collaborator)
- Ensure the extension loaded successfully in Burp's Extensions tab
- Try disconnecting and reconnecting your MCP client

### Approval dialogs not appearing

- The approval dialog appears on the Burp Suite window; check if it's behind other windows
- If Burp is minimized, the dialog may not be visible until you restore the window
