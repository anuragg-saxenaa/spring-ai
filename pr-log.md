# PR Log

## 2026-04-18
### PR #5818 — Fix: toolCallbacks empty when DefaultChatOptions provided via options()
**Branch:** feature/chat-client-tool-calls
**Status:** OPEN

**Problem:** When `options()` was called with `DefaultChatOptions` (not `ToolCallingChatOptions`), toolCallbacks from `.toolCallbacks()` were not propagated to the model. The root cause was that `ModelOptionsUtils.copyToTarget()` doesn't copy fields absent from the source type.

**Fix:** In `DefaultChatClientUtils.toChatClientRequest()`, after `copyToTarget` creates `DefaultToolCallingChatOptions` from `DefaultChatOptions`, explicitly propagate toolCallbacks from the request spec.

**Files changed:**
- `spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClientUtils.java` — Added toolCallbacks propagation after copyToTarget
- `spring-ai-client-chat/src/test/java/org/springframework/ai/chat/client/DefaultChatClientUtilsTests.java` — Added regression test

**PR:** https://github.com/spring-projects/spring-ai/pull/5818

---
---

## 2026-04-20
### PR #6 — Fix #5832: Validate all results in multi-result ChatResponses
**Branch:** fix/issue-5803-schema-constraints
**Status:** OPEN

**Problem:** `StructuredOutputValidationAdvisor.validateOutputSchema()` used `getResult()` which only returns the first generation. Invalid JSON in later results was silently ignored.

**Fix:** Replaced `getResult()` with `getResults()`, iterate all generations, return first validation failure.

**Files changed:**
- `spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/StructuredOutputValidationAdvisor.java` — Iterate all results
- `spring-ai-client-chat/src/test/java/org/springframework/ai/chat/client/advisor/StructuredOutputValidationAdvisorTests.java` — 3 new tests + 1 updated

**Tests:** 34 passed (including 3 new multi-result validation tests)

**PR:** https://github.com/anuragg-saxenaa/spring-ai/pull/6

