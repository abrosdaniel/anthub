# Architecture and release maintenance

AntHub uses native Minecraft rendering. No browser, HTML or CSS runtime is involved.
The UI component library and application controllers are separate layers; the network protocol
and compatibility major line remain unchanged for release 3.8.1.

## Dependency direction

- `core`: domain services, PostgreSQL repositories, command catalogue, request lifecycle and
  application controllers. It has no Minecraft, NeoForge or client imports.
- `mod/client`: native views, reusable compound components and the Minecraft transport adapter.
- `mod/server`: game-thread authorization and game integration; storage/read work runs through
  bounded worker queues and domain services in core.
- `helper` and `bootstrap`: installation and release packaging. Development UI fixtures are
  excluded from the published bundle.

## Application state

| Workspace                           | Controller                            | View responsibilities                                      |
| ----------------------------------- | ------------------------------------- | ---------------------------------------------------------- |
| Personal/group tasks                | `TaskWorkspace`                       | Tabs, layout, focus, local draft fields, dialogs           |
| Community sections and details      | `CommunityWorkspace`                  | Section presenters, selection, scroll anchors, forms       |
| Players, reports, history and inbox | `PagedMenuController` + `PagedWindow` | Filters, selection and row composition                     |
| Connection                          | `MenuTransport`                       | `ServerMenuClient` connects the queue to Minecraft packets |

Controllers receive an injected sender and clock and can be tested without the game. Each
controller owns its in-flight session, correlation, timeout and pagination. View projections
are defensive copies, refreshed on state changes; frame rendering does not repeatedly copy
controller data. Unknown fields remain available in compatibility snapshots so older wire
formats and extension fields are not silently dropped. `MenuData` decodes common page,
error, task and notification fields; domain handlers validate feature-specific values.

A form owns its draft and request session. `CommunityWorkspace.submitForm` supplies the feature
context while preserving the form's operation identity and response correlation. A form does
not write the parent controller's busy flag.

## Commands and consistency

`MenuCommands` defines known actions, read/write semantics, envelope validation and
administrative permission names. Both the server dispatcher and client scheduler use it.
It does not grant permission: the server verifies the current authenticated actor, ownership,
roles and revision in the domain operation, including immediately before asynchronous writes.

Retries keep `operationId` and rotate `request`. Mutation receipts and revisions prevent repeated
writes and conflicting updates. Reads belonging to a screen are cancelled on navigation;
already accepted writes survive it. The queue has 64 slots with a 48-slot background budget,
a 550 ms interval per action and a 15-second lifetime for reads. Cancelling a read or accepting
a mutation invalidates its cache tracking. A slower response cannot replace a newer snapshot
for the same query. Cache size and lifetime are bounded separately in `MenuReadCache`.

Pagination keeps loaded rows during refresh, deduplicates moving entries and trims stale tail
pages when the server reports the end. Paged group details merge members and related records
inside the controller, rather than inside the renderer.

## Component contracts

`UiWorkspace`, `UiPage`, `UiListDetail` and `UiTaskLayout` allocate geometry. `UiDialog` owns
modal surfaces. `UiActions` owns semantic commands and button dimensions; `UiPageFooter`
positions Refresh, Retry and navigation commands consistently. Fields, tabs, choices,
feedback and scrollbars use their respective compound components and palette roles.
Pages choose content and actions; they do not recreate component internals or mutate vanilla
Minecraft's GUI scale. Theme selection remains local to AntHub surfaces.

## Adding or changing a feature

1. Put application transitions and decoding in core; inject sender and clock.
2. Declare command semantics in `MenuCommands`, preserving existing wire meanings.
3. Enforce permissions and input/revision checks on the server. Client visibility is UX only.
4. Compose the view with existing components; keep focus, scroll and local drafts in the view.
5. Test stale replies, timeout/retry, conflicts and pagination in core.
6. Verify the actual game view across themes and GUI scales using the UI harness.

## Release checks

- `build` runs core tests, UI composition and architecture boundary checks, and bundle checks.
- `:core:postgresTest` uses real PostgreSQL; CI covers PostgreSQL 17 and 18.
- Tooling tests cover seed contracts, templates and release artifacts.
- `-PanthubUiHarness :mod:runClient` enables development-only game checks. `RemainingUiHarness`,
  `DialogUiHarness` and `NativeUiHarness` validate geometry, keyboard flows, themes and forms.
- `checkMenuArchitecture` prevents a second request/paging implementation in migrated workspace
  views and rejects Minecraft/UI imports in core.

A release candidate is built locally before publishing. Compilation or a green core test run
alone is not evidence that every visual state is correct; inspect game screenshots and run the
component/flow checks after changes to layout or state ownership.

## Bounded list geometry

ScrollLayout owns a viewport, content rectangle and scrollbar track. Its constructor rejects
out-of-owner or overlapping geometry; its thumb stays inside the track. ScrollScreen and
RowViewport consume that geometry for input and drawing. The coordinate-only scroll API
was removed from every production host and is rejected by the composition check.
ScrollScreen reserves the trailing gutter for full-width child widgets. Form grids and skin
columns allocate their width before adding children. Native multiline fields keep the native
8-pixel scrollbar footprint and native drag geometry, with shared theme-aware painting.
Community form draft recovery runs off the render thread; controls remain disabled until
pending operations have been checked. A failed check blocks submission instead of risking
a second unconfirmed write.

## In-game HUD and notification delivery

`CommunityHud` is an actor-scoped read model. It reads at most one eligible task, one participating
event, memberships and a bounded ascending notification feed. The initial response returns a
sequence ceiling and unread count without replaying old notices. Feed pages contain at most 50
notices; retained inbox data remains the source of truth. Pins never expand task permissions.

`AntHubHud` owns connection reset, correlation, polling and read acknowledgements. `HudSettings`
is one global client configuration. `HudRenderer` composes the same surfaces and hit regions in
game, the cursor mode and the position editor. HUD sizing compensates large Minecraft GUI scales.
`HudNoticeQueue` is deterministic core logic with bounded buffering, deduplication, merging,
priority bypass and presentation-only expiration. Manual dismissal marks only represented IDs
read, through recipient-scoped server mutations with retry receipts.

The optional `hud` capability retains compatibility with older version-3 peers. Older servers
provide the server/unread information they already expose, but not the new task/event feed.
Critical announcements use the existing permission `anthub.announce` and an explicit urgency
flag (administration form or `/ah announce urgent <text>`). Client master disable includes urgent
messages. Minecraft chat and vanilla toast rendering are unaffected.

HUD mini-profile: `HudProfile` owns measurement and rendering from the existing state snapshot and skin cache. Prefix/suffix use the shared legacy text parser; profile fields are persisted globally in `HudSettings`. No extra player polling or new protocol is needed.

## 3.8 domain additions

- ResourceRequirements validates disjoint alternatives and allocates real item reserves. TaskArchive verifies the complete selected revision set before any mutation.
- NoticeActions resolves the recipient-owned notification on the server and delegates to existing domain authorization. Client retries retain the same operation identity.
- ScheduledAnnouncements persists publication and recipient notifications in one transaction. Its global scheduling lock serializes limits and delivery; retries cannot duplicate publication.
- StockHolograms reads only accessible tasks from server-provided coordinates. Client rendering is depth-tested, dimension/range bounded and expires after 15 seconds. Disabling it skips the server lookup.
- LatencyHistory stores at most 120 numeric samples without player/account data. Deferred notices and progress highlights have explicit capacity limits.
- ChangeSummary uses an explicit field allowlist. Private account data and participant collections are never included in general edit diffs.
- Native CI checks geometry and contrast and preserves PNGs for review. Cross-platform pixel equality is intentionally not asserted; screenshots alone are not a pixel-baseline comparison.

## Optional compatibility adapters

- `core/auth/ServerIdentities` is an indexed, read-only public identity directory: current
  case-insensitive name → permanent server UUID; official UUIDs are separately verified aliases.
  Renaming removes the old name index. No adapter changes account UUIDs or authentication proofs.
- `server/compat/CompatibilityRegistry` is the explicit integration allowlist. Each
  `CompatibilityAdapter` owns its version support, operations and native-mod authorization.
  Add a new adapter at this registration point; keep its external bindings in `mod/compat`.
- `CompatibilityClient` is a reusable connection-scoped request transport over the negotiated
  `compatibility-adapters` capability. It works before opening the menu, bounds pending requests
  and profile caches, times out missing replies, and clears caches on connection changes.
- `AccessDeniedBindings` isolates optional reflective APIs. Optional client mixins have their
  own configuration and discovery plugin; neither absent mods nor unsupported versions become
  a hard dependency. Mojang services, vanilla packets and other mods' profile lookup APIs are
  not intercepted by these adapters.
- `core/compat/AccessRepairPlan` freezes before/after sets. The Access Denied adapter creates
  owner-bound previews with a two-minute lifetime; applying rechecks native network ownership,
  current permissions and verified account links. Unknown UUIDs remain untouched. Permissions
  persist in Access Denied's existing NBT format; no extra UUID storage or account migration.
- Compatibility fixtures live in a separate optional test mod (`compattests`); they are
  excluded from production jars. Test installed NeoForge as well as compilation: Create's
  local-variable-capture mixins cannot reliably run in the unrecompiled development runtime.
