# Contributing

Contributions are welcome. This guide covers development setup, how to add new tools, and the PR process.

## Development Setup

### Prerequisites

- Java 21+ (JDK)
- Gradle 9.2+ (wrapper included, no install needed)
- Burp Suite Professional or Community Edition
- An IDE with Kotlin support (IntelliJ IDEA recommended)

### Building

```bash
git clone https://github.com/0xhackerfren/Enhanced-BURP-MCP-.git
cd Enhanced-BURP-MCP-
./gradlew shadowJar
```

### Running Tests

```bash
./gradlew test
```

Tests use MockK to mock the Burp Montoya API. Integration tests start a real Ktor server and connect an MCP client.

## Project Structure

```
src/main/kotlin/net/portswigger/
    extension/              Burp extension entry point, UI, providers
    mcp/                    MCP server, tools, security, config
        tools/Tools.kt      All 53 tool definitions live here
```

## Adding a New Tool

### 1. Define the input parameters

Create a `@Serializable` data class in `Tools.kt`:

```kotlin
@Serializable
data class MyNewTool(
    val requiredParam: String,
    val optionalParam: Int? = null
)
```

### 2. Register the tool

Add a call to `mcpTool` inside `registerTools()`:

```kotlin
mcpTool<MyNewTool>("Description of what this tool does for the LLM") {
    val result = api.someApi().doSomething(requiredParam)
    "Result: $result"
}
```

The tool name is auto-derived from the data class name in `lower_snake_case` (e.g. `MyNewTool` becomes `my_new_tool`).

### 3. For paginated tools

Implement the `Paginated` interface:

```kotlin
@Serializable
data class MyPaginatedTool(
    override val count: Int,
    override val offset: Int
) : Paginated
```

Then use `mcpPaginatedTool` instead of `mcpTool`.

### 4. Security

If your tool sends HTTP requests, use `HttpRequestSecurity.checkHttpRequestPermission()`. If it accesses history or site map, use `HistoryAccessSecurity.checkHistoryAccessPermission()`. If it performs scanner operations, use `ScannerSecurity.checkScanPermission()`.

### 5. Write tests

Add tests in `src/test/kotlin/net/portswigger/mcp/tools/ToolsKtTest.kt`. Follow the existing test patterns using MockK and the test MCP client.

## Pull Request Process

1. Fork the repository and create a feature branch
2. Make your changes with tests
3. Run `./gradlew test` and verify all tests pass
4. Update `CHANGELOG.md` if adding new tools or making user-facing changes
5. Open a PR with a clear description of what changed and why

## Code Style

- Follow the existing Kotlin code style (Kotlin official conventions)
- No unnecessary comments; code should be self-documenting
- Keep tool descriptions concise but informative (they're shown to the LLM)
- Truncate large responses to avoid overwhelming the LLM context
