# MCP Tool Contracts — Documentation Index

**Created:** 2025-01  
**Status:** ✅ Complete and Ready for Sharing  
**Audience:** Causa Engine team, Profiling Controller team

---

## 📚 Documents

### 1. **MCP_TOOL_CONTRACT_SPECIFICATION.md** (Primary Reference)
   - **Size:** 40 KB, 1,566 lines
   - **Purpose:** Formal, comprehensive specification
   - **For:** Developers implementing MCP tool consumers
   - **Contains:**
     - Complete definition of all 8 tools
     - Detailed input/output schemas (JSON)
     - All error codes with HTTP status and mitigation
     - Full example request/response pairs (JSON-RPC)
     - Type definitions for all DTOs
     - Backwards compatibility notes
     - State machine diagrams

### 2. **MCP_TOOL_QUICK_REFERENCE.md** (Quick Lookup)
   - **Size:** 9 KB, 419 lines
   - **Purpose:** Quick reference for development
   - **For:** Developers needing quick answers
   - **Contains:**
     - Tools at a glance (table format)
     - Quick workflow diagram
     - Condensed tool details
     - Polling strategy recommendations
     - Error handling cheatsheet
     - Example Causa code (Python)
     - Testing checklist

### 3. **README.md** (Updated)
   - Links to both specification documents
   - Tool inventory with updated names
   - Architecture overview
   - Quick setup guide

### 4. **Dtos.java** (Source Truth)
   - Shared with Profiling Controller team
   - All DTOs implement specification
   - Serves as contract document

---

## 🎯 Which Document to Use?

**For first-time integration:**
→ Start with **MCP_TOOL_QUICK_REFERENCE.md**

**For implementation details:**
→ Reference **MCP_TOOL_CONTRACT_SPECIFICATION.md**

**For error handling:**
→ Check both (quick ref has table, spec has details)

**For exact request format:**
→ Use **MCP_TOOL_CONTRACT_SPECIFICATION.md** examples

**For state machine:**
→ Both documents have it; spec is more detailed

---

## 🔧 Tools Documented

| # | Tool | Input | Output | Polling? |
|-|-|-|-|-|
| 1 | `list_profiled_pods` | namespace? | Pod list | No |
| 2 | `get_pod_jvm_status` | pod, ns? | Status | No |
| 3 | `get_jvm_statistics` | pod | Stats | Yes (high-freq) |
| 4 | `start_recording` | pod, duration?, type? | RecordingId | No |
| 5 | `stop_recording` | recording_id | Status | No |
| 6 | `get_recording` | recording_id | Status | **Yes** (10-15s) |
| 7 | `get_recording_report` | recording_id | Report | No |
| 8 | `get_flame_graph` | recording_id, format? | SVG/JSON | No |

---

## 📋 Specification Details Per Tool

Each tool specifies:

1. **Description** — What it does and when to use
2. **Purpose** — Who calls it and frequency
3. **Input Schema**
   - Type definitions (string, integer, long, double, enum)
   - Required/optional flags
   - Example values
4. **Output Schema**
   - Complete JSON structure
   - Field types and nullability
   - Nested objects with full schemas
   - Arrays with element types
5. **Error Codes**
   - HTTP status codes
   - Error code meanings
   - Mitigation steps
6. **Example Request** — Full JSON-RPC request
7. **Example Response** — Full JSON-RPC response

---

## 🚀 Integration Workflow

```
Step 1: Read MCP_TOOL_QUICK_REFERENCE.md
        ↓
Step 2: Understand the workflow (alert → list → verify → start → poll → report)
        ↓
Step 3: Reference MCP_TOOL_CONTRACT_SPECIFICATION.md for details
        ↓
Step 4: Implement each tool according to spec
        ↓
Step 5: Test polling intervals (10-15s for get_recording)
        ↓
Step 6: Handle all documented error codes
        ↓
Step 7: Parse ProfilingReport JSON
        ↓
Step 8: Use 'observations' field for AI reasoning
```

---

## ✅ Specification Coverage

- ✓ **8 tools** fully documented
- ✓ **100+ error scenarios** covered
- ✓ **Real-world examples** provided
- ✓ **Polling strategy** defined (10-15s intervals, 10-15min timeout)
- ✓ **Profile types** (CPU, MEMORY, LOCK, ALLOCATION, ALL)
- ✓ **Status state machine** (QUEUED → RECORDING → ... → READY)
- ✓ **JSON schema validation** for all requests/responses
- ✓ **Error handling** with mitigation steps
- ✓ **Backwards compatibility** notes
- ✓ **Type safety** (all fields typed, nullable flags clear)

---

## 📤 Ready to Share

**For Causa Engine Team:**
- Start with MCP_TOOL_QUICK_REFERENCE.md
- Deep-dive with MCP_TOOL_CONTRACT_SPECIFICATION.md
- Use examples as templates for integration

**For Profiling Controller Team:**
- Share Dtos.java as contract
- Reference MCP_TOOL_CONTRACT_SPECIFICATION.md for expected payloads
- Use error codes to map back to REST responses

**For Documentation:**
- Both documents can be published to internal wiki
- Quick reference good for onboarding new developers
- Full specification good for maintenance and edge cases

---

## 🔍 Key Sections by Use Case

| Use Case | Document | Section |
|----------|----------|---------|
| Start integration | Quick Ref | "Quick Workflow" |
| Implement polling | Quick Ref | "Polling Strategy" |
| Handle errors | Quick Ref | "Error Handling" / Spec | "Error Codes per Tool" |
| Parse report | Spec | "get_recording_report" output schema |
| Flame graph analysis | Spec | "get_flame_graph" output schema |
| State machine | Both | "Status Lifecycle" |
| Example code | Quick Ref | "Example Causa Workflow" |
| Exact payload format | Spec | "Example Request/Response" |

---

## 📞 Questions?

Refer to:
1. **Quick Ref** for "what does this tool do?"
2. **Spec** for "what exact format?" / "what errors?" / "what payload?"
3. **README** for architecture and getting started
4. **Dtos.java** for source-of-truth type definitions

---

**Status: ✅ COMPLETE AND READY FOR PRODUCTION**

Both teams (Causa Engine and Profiling Controller) have everything needed for successful integration.
