# Subscriptions Architecture

## Overview
`SubscriptionSource` is a first-class entity for URL-driven server lists.
Regular imported profiles stay unchanged and continue to use existing parser/runtime logic.

Subscriptions are implemented by:
- `RoomSubscriptionRepository` for CRUD + sync strategy.
- `SubscriptionPayloadDecoder` for payload normalization (plain/base64).
- `SubscriptionParser` for line extraction + reuse of `ProfileImportParser`.
- `SubscriptionUpdateWorker` (`WorkManager`) for periodic updates.

## HWID-Gated Compatibility
Some Marzban/Happ-style providers gate payloads by device compatibility.
If client is not recognized, provider can return marker payloads (for example `0.0.0.0:1` + “app not supported”).

The app now supports a compatibility layer with client modes:
- `auto` (default)
- `generic`
- `marzban-hwid`
- `happ`

Client mode is stored in `SubscriptionSource.metadata` (`clientMode`) and persisted between refreshes.

## Request Headers
Compatibility requests send:
- `x-hwid`
- `device-os`
- `device-model`

`User-Agent` strategy:
- generic/hwid mode: `PrivateVPN-Android/1.0`
- happ mode: `Happ/1.0`

## HWID Generation and Storage
HWID is stable per app install:
1. repository reads `SharedPreferences` key `subscription_compat.compat_hwid`;
2. if absent, it derives HWID from `packageName + ANDROID_ID` and stores SHA-256 (32 chars);
3. stored HWID is reused for all later compatibility requests.

This avoids random-per-request identifiers.

## Endpoint Strategy
Subscription fetch supports both:
- base endpoint: `/sub/{token}` (or existing URL as provided)
- client-type endpoint: `/sub/{token}/{client_type}` (currently `happ`)

`auto` mode flow:
1. first request uses base endpoint (generic);
2. if response indicates compatibility gating (headers/markers), repository retries with compatibility plan:
   - base endpoint + HWID headers (`marzban-hwid`)
   - `/happ` endpoint + HWID headers (`happ`)
3. best response is selected by number of connectable entries (and fewer markers on tie).

## Provider Compatibility Detection
Detection uses combined signals:
- response headers (`X-Hwid-Active`, `X-Hwid-Not-Supported`);
- marker endpoints (`0.0.0.0:1` / zero UUID);
- marker phrases (“app not supported”, “install app”, platform hints).

If provider metadata is present in headers (`Profile-Title`, `Support-Url`, `Flclashx-Servicename`), it is extracted into subscription metadata.

## Metadata Normalization
Subscription metadata is normalized into `SubscriptionMetadataPayload`.
Headers currently supported:
- `Profile-Title`
- `Subscription-Userinfo` (`upload`, `download`, `total`, `expire`)
- `Support-Url`
- `Profile-Web-Page-Url`
- `Announce`
- `Flclashx-Servicename`
- `Flclashx-Servicelogo`
- provider hints (`Provider-*`, `Service-*`, `Tag*`, `Plan-Id`, `User-Id`, `Badge`, `Note`)

Computed fields:
- used traffic = `upload + download`
- remaining traffic = `total - used` (if `total` exists)
- expiry text from `expire`
- preferred external link priority:
  1. provider site
  2. profile web page
  3. support URL

Diagnostics store:
- metadata headers received;
- extracted metadata summary;
- ignored metadata headers.

## Parsing and Safe Persist
Pipeline:
1. fetch payload;
2. decode payload (plain/base64);
3. parse entries with existing `ProfileImportParser`;
4. classify `marker` vs `connectable`;
5. atomically replace child profiles only when valid subset exists.

If refresh fails, previous working child profiles remain intact.

## Diagnostics
Subscription diagnostics now include:
- compatibility mode;
- selected endpoint and endpoint strategy;
- used client type;
- `hwidActive` / `hwidNotSupported`;
- `retryWithHwid`;
- marker/connectable/saved counts;
- detected format + HTTP status + short error.

These fields are available in subscription details UI and logs.

## End-to-End Invariants
After refresh, repository enforces:
- selected response is logged explicitly (`selectedResponseSource`, status, connectable, marker);
- parser handoff is logged with body signature + line count;
- persistence is logged with inserted/updated/filtered/duplicate counts;
- marker-only stale records from older versions are purged (they are never treated as connectable servers).

If selected response has `connectable > 0`, those child profiles are the source for UI lists on Home/Profiles.

## UI Behavior
Home:
- compact server rows;
- subscriptions as collapsible groups;
- marker entries are filtered from child server list.

Profiles:
- detailed diagnostics for provider compatibility;
- provider title/domain/status/count presentation based on normalized metadata.

## Reliability Notes
- failures never wipe old working profiles;
- partial imports keep valid subset;
- refreshes are background-safe via WorkManager and interval gating;
- active profile linkage remains unchanged (`activeProfileId` + `lastSelectedProfileId`).
