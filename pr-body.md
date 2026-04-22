## Fix #5832: Validate all results in multi-result ChatResponses

### Problem

`StructuredOutputValidationAdvisor.validateOutputSchema()` used `chatResponse.getResult()` which only returns the **first** generation result. When a `ChatResponse` contains multiple results (generations), subsequent results were **never validated** — invalid JSON in later results was silently ignored.

A TODO comment in the source explicitly flagged this:
```java
// TODO: should we consider validation for multiple results?
```

### Solution

Replaced `getResult()` with `getResults()` in `validateOutputSchema()`:
- Now iterates **all** generation results
- Returns the **first** validation failure encountered
- Maintains backward compatibility (empty list returns passed)

### Changes

**StructuredOutputValidationAdvisor.java:**
- `validateOutputSchema()` now calls `getResults()` instead of `getResult()`
- Iterates all generations in a for loop
- Returns early on first validation failure
- Added empty results list check

**StructuredOutputValidationAdvisorTests.java:**
- Added `createMockResponseWithMultipleResults()` helper
- `testValidationWithMultipleResultsSecondInvalid`: First valid, second invalid → caught
- `testValidationWithMultipleResultsAllValid`: All valid → no retry
- `testValidationWithMultipleResultsFirstInvalid`: First invalid → retry triggered
- Updated `testAdviseCallWithNullResult` for empty results list behavior

### Testing

All 34 tests pass including 3 new multi-result validation tests.

Fixes spring-projects/spring-ai#5832