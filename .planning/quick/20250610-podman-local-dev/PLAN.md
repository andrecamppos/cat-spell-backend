---
slug: podman-local-dev
task: Replace Docker Compose with Podman for local development
status: in-progress
created: 2025-06-10
---

# Quick Task: Podman for Local Dev

Replace all Docker Compose references with Podman across planning docs, STACK.md, README, and related files. The `docker-compose.yml` file content stays the same (Podman reads compose format natively via `podman compose`).

## Changes
1. STACK.md — Docker Compose → Podman in dev tools table
2. Planning docs (SUMMARY, CONTEXT, SKELETON, VALIDATION, UAT, PLAN, ROADMAP) — `docker compose` → `podman compose`
3. README.md — Add Podman-based local dev instructions
