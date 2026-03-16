# Burp Suite MCP Server (Extended)

An extended [Model Context Protocol](https://modelcontextprotocol.io/) server for Burp Suite that exposes **53 tools** to AI clients, enabling AI-driven penetration testing workflows.

Built on [PortSwigger's official MCP Server](https://github.com/PortSwigger/mcp-server) and extended with site map access, scanner control, authenticated crawling, scope management, cookie jar control, and more.

[![CI](https://github.com/0xhackerfren/burp-mcp-extended/actions/workflows/ci.yml/badge.svg)](https://github.com/0xhackerfren/burp-mcp-extended/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/0xhackerfren/burp-mcp-extended)](https://github.com/0xhackerfren/burp-mcp-extended/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://adoptium.net/)

---

## Quickstart

### Option A: Download a Pre-Built JAR (Recommended)

1. Go to [Releases](https://github.com/0xhackerfren/burp-mcp-extended/releases) and download `burp-mcp-all.jar`
2. In Burp Suite, go to **Extensions > Add > Java** and select the JAR
3. Open the **MCP** tab and enable the server

### Option B: Build from Source

```bash
git clone https://github.com/0xhackerfren/burp-mcp-extended.git
cd burp-mcp-extended
./gradlew shadowJar
```

Then load `build/libs/burp-mcp-all.jar` into Burp Suite as above.

> For Claude Desktop stdio support, run `./gradlew downloadProxyJar` first, then `./gradlew embedProxyJar`.

---

## Connect Your AI Client

### Cursor

Add to `.cursor/mcp.json` in your project:

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

Use the **Install to Claude Desktop** button in Burp's MCP tab (handles everything automatically), or see [manual setup](docs/CONFIGURATION.md#claude-desktop).

---

## What's Included (53 Tools)

| Category | Tools | Edition |
|----------|-------|---------|
| HTTP Requests | `send_http1_request`, `send_http2_request` | All |
| Proxy History | Browse, search, get items, highlight/annotate | All (some Pro) |
| Site Map | URLs, entries, request/response bodies, search, add | Pro |
| Scanner Control | Start crawls/audits, monitor status, get issues, generate reports | Pro |
| Authenticated Crawling | BFS crawler with cookies, headers, depth/page limits | All |
| Scope Management | Include/exclude URLs, check scope | All |
| Cookie Jar | Get/set cookies with domain, path, expiration | All |
| Tool Integration | Repeater, Intruder, Comparer, Decoder, Organizer | All |
| Collaborator | Generate payloads, poll interactions | Pro |
| Encoding | URL, Base64, HTML encode/decode, random string generation | All |
| Configuration | Export/import project and user options | All |
| Burp Control | Task engine state, proxy intercept, version info | All |
| Editor | Read/write active message editor contents | All |

See the [complete tool reference](docs/TOOLS_REFERENCE.md) for full details on every tool.

### What's Different from the Stock Extension?

| Feature | Stock Burp MCP | This Extension |
|---------|---------------|----------------|
| Site map access | Not available | Full access with search/filter |
| Scanner control | View issues only | Start crawls, audits, generate reports |
| Authenticated crawling | Not available | BFS crawler with auth support |
| Scope management | Not available | Include/exclude/check URLs |
| Cookie jar | Not available | Get/set cookies |
| Tool integration | Repeater, Intruder | + Comparer, Decoder, Organizer |
| Encoding | URL, Base64 | + HTML encode/decode |
| **Total tools** | **24** | **53** |

---

## Security Model

All tool invocations pass through a security layer before reaching Burp's APIs.

- **HTTP Request Approval** -- Every outbound request can require user confirmation. Streamline with auto-approve targets: `example.com`, `example.com:8443`, `*.example.com`.
- **History/Site Map Access** -- Access to proxy history and site map data requires approval, with "Always Allow" toggles.
- **Scanner Operations** -- Starting crawls, audits, and generating reports require approval unless "Always Allow Scanner" is enabled.

See [Configuration](docs/CONFIGURATION.md) for details on all security options.

---

## Building from Source

### Prerequisites

- **Java 21+** (JDK, not just JRE)
- **Gradle 9.2+** (wrapper included)

### Build Commands

```bash
# SSE-only build (recommended for Cursor)
./gradlew shadowJar
# Output: build/libs/burp-mcp-all.jar

# Full build with stdio proxy (for Claude Desktop)
./gradlew downloadProxyJar
./gradlew embedProxyJar

# Run tests
./gradlew test
```

### Loading into Burp Suite

1. Open Burp Suite (Professional or Community)
2. Go to **Extensions > Add**
3. Set **Extension Type** to **Java**
4. Select `build/libs/burp-mcp-all.jar`
5. Click **Next**
6. Go to the **MCP** tab and enable the server

---

## Documentation

- [Architecture](docs/ARCHITECTURE.md) -- System diagram, source layout, key components
- [Tool Reference](docs/TOOLS_REFERENCE.md) -- All 53 tools with descriptions and edition requirements
- [Configuration](docs/CONFIGURATION.md) -- Server settings, security options, client setup, troubleshooting
- [Contributing](CONTRIBUTING.md) -- Development setup, adding tools, PR process
- [Changelog](CHANGELOG.md) -- Version history and changes from upstream

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

Based on [PortSwigger/mcp-server](https://github.com/PortSwigger/mcp-server), originally authored by Daniel S and Daniel Allen at PortSwigger.
