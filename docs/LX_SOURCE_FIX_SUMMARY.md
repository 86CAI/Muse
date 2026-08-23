# LX Source Host Fix Summary

## Scope

This document records the verified LX source fixes, the regression that caused
`LX source did not send the inited event`, and the constraints that must be
preserved in future changes.

## Verified Timeline

- `v2.717.37` / `v2.717.38`: the stored request handler wrapper was already
  closed even though its V8 runtime was still open. Calls failed with
  `Runtime is already closed`.
- `v2.717.39`: fetching a fresh function wrapper from the JavaScript global
  object fixed the handler/runtime mismatch.
- `v2.717.50` through `v2.717.56`: initialization reached the network bridge
  (`requestCount=1`) but never sent `inited`.
- `v2.717.58`: passing the local runtime into network option serialization
  fixed initialization. Resolution then reached the next error,
  `source not match`.
- Adding `musicInfo.source` fixed `source not match`.
- `v2.804`: initialization regressed after the bootstrap replaced V8's native
  Promise globally. Logs showed `requestCount=1`, no network error, and no
  `inited` payload, which means execution stopped after the request when the
  native async continuation should have resumed.

## Required Fixes

### 1. Use The Current Runtime During Initialization

Network request options must be serialized with the runtime supplied to the
host callback:

```kotlin
val request = buildNetworkRequest(runtime, url, options)
```

Do not use `v8Runtime!!` here. During initialization, the new runtime is still
local and is not committed to `v8Runtime` until initialization succeeds.

### 2. Fetch Fresh Handler Wrappers

Javet closes callback argument wrappers after a bound `@V8Function` returns.
The JavaScript function remains alive because it is stored on the global
object, but the original Kotlin-side wrapper is not safe to call later.

Store the function in JavaScript:

```kotlin
global.set("__lxStored_$name", handler)
```

Before every request, retrieve a fresh wrapper:

```kotlin
val handler = storedFunction(runtime, "__lxStored_$EVENT_REQUEST")
```

Do not call the wrapper retained in the diagnostic `handlers` collection.

### 3. Preserve The LX Source Field

The standard LX request payload must include the provider source inside
`musicInfo`:

```kotlin
raw.put("source", track.source)
```

Without it, aggregate sources reject playback with `source not match`.

### 4. Do Not Replace Native Promise

Never assign a custom implementation to:

```javascript
globalThis.Promise
```

V8 native `async` functions are tied to the native Promise implementation.
Replacing the visible constructor with a synchronous thenable changes script
semantics and can prevent the continuation after `await` from reaching
`send(EVENT_NAMES.inited, ...)`.

The host may return a small thenable from synchronous APIs such as `lx.on()` or
`lx.send()`, but it must leave the global Promise constructor untouched.

## Runtime Lifecycle

- Create the runtime locally in `initializeBlocking()`.
- Bind callbacks and execute the bootstrap and source with that local runtime.
- Pump V8 microtasks after script execution.
- Require a valid `inited` payload before assigning the runtime to
  `v8Runtime`.
- On initialization failure, close the local runtime and leave `v8Runtime`
  null.
- On resolution, use a fresh global handler wrapper and close only that fresh
  wrapper after the call.

## Diagnostic Interpretation

```text
requestCount=0
```

The source failed before reaching the network bridge.

```text
requestCount=1, lastNetworkError=...
```

The network bridge ran and the request itself failed.

```text
requestCount=1, no network error, sentPayloads=(none)
```

The request completed, but JavaScript did not continue to `send(inited)`.
Check Promise/async semantics and response compatibility.

```text
handlerRuntime=0, handlerClosed=true, runtimeClosed=false
```

The retained Javet wrapper is closed. Retrieve a fresh wrapper from the global
object.

```text
source not match
```

Initialization succeeded. Verify `musicInfo.source` in the playback request.

## Regression Checklist

Before accepting changes to `LxSourceHost.kt`, verify all of the following:

- `buildNetworkRequest(runtime, url, options)` uses the callback runtime.
- No initialization path dereferences `v8Runtime!!`.
- Request, lyrics, search, and timer callbacks use fresh global wrappers.
- `musicInfo.source` is present.
- The bootstrap does not replace native Promise.
- Debug and Release builds complete.
- Device logs show an `inited` payload or progress beyond initialization.
- A real online resolution reaches the source and does not report
  `Runtime is already closed` or `source not match`.

## Known Non-Host Failures

The following errors can occur after the host is working and should not be
misdiagnosed as runtime regressions:

- Upstream source returns a generic `Error`.
- Source backend DNS fails, for example `No address associated with hostname`.
- A source does not declare support for the requested provider.
- An official catalog rejects a track from another provider.
