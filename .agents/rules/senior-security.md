# Senior Security Engineering Standards

> Always-on rule for this workspace. Every change touching auth, config, secrets,
> or external integrations must pass these security checks.

## Secret Management (GitGuardian Policy)

- **No hardcoded secrets** in source code, ever — no API keys, passwords, tokens, connection strings
- All secrets via environment variables with safe `${ENV_VAR:none}` fallbacks (non-blank but non-functional)
- Secret files (`.env`, `*.properties` with real values) must be in `.gitignore`
- The `.githooks/pre-commit` hook scans staged files before every commit — do not bypass it
- Real secrets go in `.env.development` (gitignored) or a secrets manager (Vault, AWS Secrets Manager)

## OWASP Top 10 Checklist

Apply for every feature:

| Risk | How to Mitigate in This Project |
|---|---|
| A01 Broken Access Control | `@PreAuthorize` on all sensitive endpoints; never rely on obscurity |
| A02 Cryptographic Failures | Use Spring Security defaults; never roll your own crypto |
| A03 Injection | Use parameterized queries/JPQL; never string-concatenate SQL |
| A04 Insecure Design | Threat-model new features before writing code |
| A05 Security Misconfiguration | Review `SecurityConfig` on every PR; no `permitAll()` on sensitive paths |
| A06 Vulnerable Components | Run `mvn dependency-check:check` before major releases |
| A07 Auth Failures | JWT validated at filter level; refresh tokens rotated on use |
| A08 Software/Data Integrity | Validate QStash webhook signatures before processing |
| A09 Logging Failures | Log security events (login, permission denied) at INFO; never log credentials |
| A10 SSRF | Validate and allowlist all outbound URLs from user input |

## Authentication & Authorization

- JWTs validated by `JwtAuthFilter` — do not add ad-hoc auth logic in controllers
- All endpoints default to authenticated; explicitly whitelist public paths in `SecurityConfig`
- Use role-based access (`ROLE_USER`, `ROLE_ADMIN`) — never hardcode user IDs in logic
- Refresh tokens must be stored hashed (not plaintext) and single-use

## Input Validation

- All external inputs validated with Bean Validation (`@Valid`, `@NotBlank`, `@Size`, etc.)
- File uploads: validate MIME type AND magic bytes (not just extension)
- Webhook payloads: verify HMAC signature before deserializing body
- Never trust `X-Forwarded-For` without explicit proxy trust configuration

## Dependency Security

```bash
# Run before any release or major dependency update
mvn dependency-check:check

# Check for known CVEs in transitive dependencies
mvn versions:display-dependency-updates
```

Flag any dependency with a CVSS score ≥ 7.0 as a blocker for release.

## Security Review Triggers

Always perform a security review when:
- Adding or changing authentication/authorization logic
- Adding new external API integrations
- Changing file upload or user-generated content handling
- Modifying `SecurityConfig`, `JwtAuthFilter`, or any `@Configuration` class
- Adding new `@RestController` with `POST`/`PUT`/`DELETE` endpoints
