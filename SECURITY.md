# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in this extension, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please email the maintainer directly or use GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability) feature on this repository.

### What to include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response timeline

- Acknowledgment within 48 hours
- Assessment and fix timeline within 7 days
- Public disclosure after a fix is available

## Security Model

This extension implements a multi-layer security model to protect against unauthorized access:

### DNS Rebinding Protection

The Ktor server validates Origin, Host, and Referer headers on all requests. Browser requests are blocked by User-Agent detection. Only `localhost` and `127.0.0.1` are accepted as valid origins.

### Tool Approval System

Sensitive operations require explicit user approval via Swing dialogs:

- **HTTP requests** - Outbound requests to external hosts require approval
- **History access** - Reading proxy history, WebSocket history, or site map data requires approval
- **Scanner operations** - Starting crawls, audits, and generating reports requires approval

Each approval type supports "Always Allow" options and configurable auto-approve targets.

### Default-Secure Configuration

All approval requirements are enabled by default. The extension ships in the most restrictive configuration, requiring the user to explicitly relax security as needed.

## Supported Versions

| Version | Supported |
|---------|-----------|
| 2.x     | Yes       |
| < 2.0   | No        |
