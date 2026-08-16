# Security policy

VoltWeave is a simulated virtual power plant platform. It processes tenant data,
device telemetry, dispatch commands, and reward records, so security reports are
treated as correctness issues, not optional enhancements.

## Supported versions

VoltWeave has not published a stable release yet. Security fixes currently target
the latest commit on `main` only.

| Version | Supported |
| --- | --- |
| Latest `main` | Yes |
| Older commits and forks | No |

This table will move to tagged release versions when VoltWeave reaches its V1
release gate.

## Reporting a vulnerability

Do not open a public issue or discussion for a suspected vulnerability.

Use GitHub's private vulnerability reporting flow from the repository's
**Security** tab and include:

- the affected service, endpoint, topic, or configuration;
- the commit or image version tested;
- prerequisites and minimal reproduction steps;
- observed and expected behavior;
- the likely impact and affected roles or tenants;
- logs or screenshots with credentials and personal data removed;
- any workaround already identified.

If private vulnerability reporting is unavailable, contact the repository owner
through their GitHub profile to request a private channel. Do not include exploit
details in the initial public message.

Never submit real access tokens, passwords, MQTT credentials, private keys,
customer data, or production telemetry. Revoke any credential accidentally
exposed during testing before sending the report.

## Response targets

These are response targets for this community project, not contractual service
levels:

| Stage | Target |
| --- | --- |
| Acknowledge report | 3 business days |
| Initial severity assessment | 7 business days |
| Progress update for an open report | Every 14 days |
| Coordinated disclosure | After a fix or agreed mitigation is available |

Reports are prioritized by exploitability and impact on tenant isolation,
command integrity, credential exposure, settlement correctness, and platform
availability.

## In scope

- authentication, authorization, JWT validation, and tenant isolation;
- IDOR or cross-organization access to sites, devices, VPPs, or dispatches;
- MQTT provisioning, topic ACLs, credential rotation, or command spoofing;
- Kafka event forgery, replay, duplicate processing, or cross-tenant leakage;
- secret exposure in source, images, logs, build output, or configuration;
- modification of immutable dispatch, settlement, reward, or audit records;
- injection, unsafe deserialization, path traversal, and remote code execution;
- dependency vulnerabilities with a demonstrated impact on VoltWeave.

## Out of scope

- attacks against infrastructure not owned by this repository;
- social engineering, phishing, or physical attacks;
- denial-of-service testing that degrades shared or public systems;
- automated scanner output without a reproducible security impact;
- findings that require a victim to intentionally disable documented controls;
- vulnerabilities in unsupported forks or modified deployments;
- real electrical-grid or device safety claims—the project uses simulated DERs.

## Testing expectations

Use local infrastructure, test accounts, and simulated devices. Keep request rates
low, stop if another user or system may be affected, and preserve evidence without
retaining sensitive data. Do not access, alter, or delete data that is not yours.

Good-faith research that follows this policy will not be met with legal action by
the project maintainers. If you are unsure whether a test is safe, report the
hypothesis first and wait for guidance.

## Disclosure and remediation

Maintainers will validate the report, assign severity, prepare a minimal fix and
regression test, and coordinate disclosure with the reporter. Credit is offered
unless the reporter requests anonymity. Public details may be delayed while users
have a reasonable opportunity to update.
