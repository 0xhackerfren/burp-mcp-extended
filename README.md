# Burp Suite MCP Server (Extended)

An extended [Model Context Protocol](https://modelcontextprotocol.io/) server for Burp Suite that exposes **50 tools** to AI clients, enabling AI-driven penetration testing workflows.

Built on [PortSwigger's official MCP Server](https://github.com/PortSwigger/mcp-server) and extended with site map access, scanner control, scope management, cookie jar control, and more.

[![CI](https://github.com/0xhackerfren/Enhanced-BURP-MCP/actions/workflows/ci.yml/badge.svg)](https://github.com/0xhackerfren/Enhanced-BURP-MCP/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://adoptium.net/)
[![MCP](https://img.shields.io/badge/MCP-Compatible-green)](https://modelcontextprotocol.io/)

> [!NOTE]
> This extension works with both **Burp Suite Professional** and **Community Edition**. Some tools (site map, scanner, collaborator) require Professional.

---

## Quick Start

### Option A: Download a Pre-Built JAR (Recommended)

1. Go to [Releases](https://github.com/0xhackerfren/Enhanced-BURP-MCP/releases) and download `burp-mcp-sse.jar` or `burp-mcp-full.jar`
2. In Burp Suite, go to **Extensions > Add > Java** and select the JAR
3. Open the **MCP** tab and enable the server

### Option B: Build from Source

```bash
git clone https://github.com/0xhackerfren/Enhanced-BURP-MCP.git
cd Enhanced-BURP-MCP
./gradlew shadowJar
```

Then load `build/libs/burp-mcp-all.jar` into Burp Suite.

> [!TIP]
> For Claude Desktop stdio support, run `./gradlew downloadProxyJar` first, then `./gradlew embedProxyJar`.

---

## Connect Your AI Client

### Cursor

Add to `.cursor/mcp.json` in your project (see [`.cursor/mcp.json.example`](.cursor/mcp.json.example)):

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

### Other MCP Clients

Any client supporting SSE transport can connect to `http://127.0.0.1:9876`. For stdio-only clients, use the bundled proxy JAR:

```bash
java -jar mcp-proxy-all.jar --sse-url http://127.0.0.1:9876
```

---

## Tools (50 Total)

| Category | Tools | Edition |
|----------|-------|---------|
| **HTTP Requests** | `send_http1_request`, `send_http2_request` | All |
| **Proxy History** | Browse, search, get items, highlight/annotate | All (some Pro) |
| **Site Map** | URLs, entries, request/response bodies, search, add | Pro |
| **Scanner Control** | Start crawls/audits, monitor status, get issues, generate reports | Pro |
| **Scope Management** | Include/exclude URLs, check scope | All |
| **Cookie Jar** | Get/set cookies with domain, path, expiration | All |
| **Tool Integration** | Repeater, Intruder, Comparer, Decoder, Organizer | All |
| **Collaborator** | Generate payloads, poll interactions | Pro |
| **Encoding** | URL, Base64, HTML encode/decode, random string generation | All |
| **Configuration** | Export/import project and user options | All |
| **Burp Control** | Task engine state, proxy intercept, version info | All |
| **Editor** | Read/write active message editor contents | All |

See the [complete tool reference](docs/TOOLS_REFERENCE.md) for details on every tool.

### What's Different from the Stock Extension?

| Feature | Stock Burp MCP | This Extension |
|---------|---------------|----------------|
| Site map access | Not available | Full access with search/filter |
| Scanner control | View issues only | Start crawls, audits, generate reports |
| Scope management | Not available | Include/exclude/check URLs |
| Cookie jar | Not available | Get/set cookies |
| Tool integration | Repeater, Intruder | + Comparer, Decoder, Organizer |
| Encoding | URL, Base64 | + HTML encode/decode |
| **Total tools** | **24** | **50** |

---

## Security Model

> [!IMPORTANT]
> All tool invocations pass through a security layer before reaching Burp's APIs. The extension ships in the most restrictive configuration by default.

- **HTTP Request Approval** - Every outbound request can require user confirmation. Streamline with auto-approve targets: `example.com`, `example.com:8443`, `*.example.com`.
- **History/Site Map Access** - Access to proxy history and site map data requires approval, with "Always Allow" toggles.
- **Scanner Operations** - Starting crawls, audits, and generating reports require approval unless "Always Allow Scanner" is enabled.
- **DNS Rebinding Protection** - Origin, Host, and Referer header validation on all requests. Only localhost connections accepted.

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

## Project Structure

```
src/main/kotlin/net/portswigger/
    extension/              Burp Suite plugin layer
        ExtensionBase.kt    Entry point
        providers/          Claude Desktop installer, proxy JAR manager
        ui/                 Config UI panels, design system, dialogs
    mcp/                    MCP server layer
        tools/              All 50 tool definitions
        security/           Approval handlers (HTTP, history, scanner)
        config/             Persistent configuration
        schema/             JSON schema generation, serialization
```

---

## Documentation

- [Architecture](docs/ARCHITECTURE.md) - System diagram, source layout, key components
- [Tool Reference](docs/TOOLS_REFERENCE.md) - All 50 tools with descriptions and edition requirements
- [Configuration](docs/CONFIGURATION.md) - Server settings, security options, client setup, troubleshooting
- [Contributing](CONTRIBUTING.md) - Development setup, adding tools, PR process
- [Changelog](CHANGELOG.md) - Version history and changes from upstream
- [Security Policy](SECURITY.md) - Vulnerability reporting and security model details

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

Based on [PortSwigger/mcp-server](https://github.com/PortSwigger/mcp-server), originally authored by Daniel S and Daniel Allen at PortSwigger.
