---
schema_version: 1
open_count: 0
waived_count: 0
fixed_count: 1
total_count: 1
last_updated: 2026-10-02T09:15:57.316Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 17 | stub | src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt |  | join() mints/hashes confirm token but sends no confirmation email yet; 'rotated' unused until plan 17-02 publishes the event | fixed |  | 2026-10-02T09:04:36.211Z | 2026-10-02T09:15:57.316Z |

````json
[
  {
    "id": 1,
    "kind": "stub",
    "phase": "17",
    "file": "src/main/kotlin/com/catspell/api/waitlist/service/WaitlistService.kt",
    "line": null,
    "description": "join() mints/hashes confirm token but sends no confirmation email yet; 'rotated' unused until plan 17-02 publishes the event",
    "status": "fixed",
    "reason": "",
    "recorded_at": "2026-10-02T09:04:36.211Z",
    "resolved_at": "2026-10-02T09:15:57.316Z",
    "milestone": "v2.2"
  }
]
````
