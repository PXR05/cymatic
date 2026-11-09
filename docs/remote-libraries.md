# Remote libraries

The current UI connects one AudioStream account. Catalog browsing does not require a
download folder or an offline selection. Connecting publishes the catalog into the
normal songs, albums, artists, and playlists library. Playback streams remote songs
and prefers verified offline copies.

`RemoteLibrarySource` identifies a provider, server address, and account. Its ID scopes
credentials, catalog files, private download folders, and local playlist mappings.
`RemoteLibraryItemKey` pairs that source ID with a provider item ID. Use the pair when
combining libraries: two servers may return the same track or playlist ID.

`RemoteLibraryClient` is the provider boundary for listing tracks/playlists and opening
separate complete-file downloads and seekable playback streams. `AudioStreamClient` implements it; `RemoteLibraries` constructs
the installed provider. Catalog and download code do not depend on HTTP connections.

`LibraryCatalogRepository` fetches and commits a complete metadata snapshot. A failed or
cancelled fetch keeps the old snapshot. `LibraryCatalogManager` gives manual refresh its
own progress and cancellation, without changing download history. `LibrarySyncManager`
refreshes through the same repository before applying offline selections.

Private downloads use `ManagedSyncStorage`, with temporary writes and atomic replacement.
Completed files update the same stable remote audio rows; local rescans preserve them. Custom folders
continue to use `SafTree`; existing folder permissions and download checkpoints are kept.
Legacy credentials, catalogs, and playlist mappings migrate to the saved account before
it can be changed.

`RemoteLibraryRepository` publishes tracks and playlists in a Room transaction, using
source-scoped stable audio IDs and preserving local playlist memberships. Room schema
3 adds nullable source IDs, remote IDs, and offline locations through an automatic
migration from schema 2. `LibraryChanges` updates open library screens after imports
and downloads. The three availability badges are offline, partially offline, and stream
only; playlist availability is calculated from its tracks.

`RemoteLibraryDataSource` resolves `cymatic://source/item` playback URIs at load time,
uses readable offline files first, and otherwise opens the provider stream. AudioStream
renews expired authentication and supports HTTP ranges, including a skip fallback for
servers that ignore them. Credentials and session tokens are never written to queue
URIs. Streaming buffers in memory; it does not create durable audio downloads.

`LibraryOfflineManager` persists source-scoped track and playlist selections, then queues
download work. Selected remote playlists follow updated server membership. Downloading
one chosen item does not prune existing copies when bulk downloading is disabled.

## Adding providers later

Implement provider clients for Jellyfin, Plex, Navidrome, or SMB without adding provider
branches to catalog refresh or download planning. Authentication stays in each provider:
the AudioStream username/password flow should not become a requirement for every provider.
SMB will also need to discover file tags and define stable IDs for remote files because
it does not supply the same catalog API as a music server.

For simultaneous sources, persist a list of source records and move the remaining
single-account offline preferences and scheduler jobs onto those records. The current
factory reads the saved account; this is not yet a multi-source UI. Do not add inactive
provider choices to setup.

Each provider implements its own `openStream`, including authentication renewal,
seeking, and cancellation. HTTP providers can return ranged responses; SMB can return
seekable file reads. Keep temporary playback buffering distinct from durable offline
downloads. The current global bulk preferences and periodic scheduler still target the
active connection; move them onto source records before adding a multi-source UI.

## Queue feedback and availability filters

Offline requests persist their selections, then replace the item's availability icon
with a small animated pixel loader while queued or processing. Tracks, playlists,
albums, and artists use the same indicator. Successful requests do not show a header
message; failures still show a brief error. Additional requests are accepted during
active work. Pending requests for the same source share one follow-up pass, so repeated
selections do not create a separate full catalog scan for each item. Request snapshots
keep newly queued work visible when an earlier pass finishes. Work indicators clear on
completion, failure, or cancellation; completed offline items use their normal icon.

The hold-Menu library context menus expose All music, Offline only, and Not offline.
The choice is persisted independently of offline download selections. Songs are filtered
before album/artist grouping and when constructing new playback queues. Existing playback
queues and active downloads retain their selections. Offline playlists must be completely
available locally; partial playlists are included in Not offline.

Albums and artists show the same offline, partial, and stream indicators as playlists.
Use **Keep offline** in their context menu to queue all tracks in that album or artist,
including tracks hidden by the current library filter. Artist album rows apply only
to that artist. Indicators update as downloads finish.

Streamed playback requests byte ranges from the start and uses response range lengths
(or the catalog file size when the server omits a length) so formats such as Ogg Opus
can determine their duration and seek. The player also uses the catalog duration as
a display fallback and keeps elapsed time updating when duration is unknown.
