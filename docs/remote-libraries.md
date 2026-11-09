# Remote libraries

The current UI connects one AudioStream account. Catalog browsing does not require a
download folder or an offline selection. Audio playback still uses downloaded files.

`RemoteLibrarySource` identifies a provider, server address, and account. Its ID scopes
credentials, catalog files, private download folders, and local playlist mappings.
`RemoteLibraryItemKey` pairs that source ID with a provider item ID. Use the pair when
combining libraries: two servers may return the same track or playlist ID.

`RemoteLibraryClient` is the provider boundary for listing tracks/playlists and opening
a complete-file download. `AudioStreamClient` implements it; `RemoteLibraries` constructs
the installed provider. Catalog and download code do not depend on HTTP connections.

`LibraryCatalogRepository` fetches and commits a complete metadata snapshot. A failed or
cancelled fetch keeps the old snapshot. `LibraryCatalogManager` gives manual refresh its
own progress and cancellation, without changing download history. `LibrarySyncManager`
refreshes through the same repository before applying offline selections.

Private downloads use `ManagedSyncStorage`, with temporary writes and atomic replacement.
Completed files are indexed directly by Cymatic on every local rescan. Custom folders
continue to use `SafTree`; existing folder permissions and download checkpoints are kept.
Legacy credentials, catalogs, and playlist mappings migrate to the saved account before
it can be changed.

## Adding providers and streaming later

Implement provider clients for Jellyfin, Plex, Navidrome, or SMB without adding provider
branches to catalog refresh or download planning. Authentication stays in each provider:
the AudioStream username/password flow should not become a requirement for every provider.
SMB will also need to discover file tags and define stable IDs for remote files because
it does not supply the same catalog API as a music server.

For simultaneous sources, persist a list of source records and move the remaining
single-account offline preferences and scheduler jobs onto those records. The current
factory reads the saved account; this is not yet a multi-source UI. Do not add inactive
provider choices to setup.

For streaming, add provider playback resolution separately from `openDownload`: URLs or
data sources may need expiring tokens, HTTP range requests, or SMB reads. Resolve media
by `RemoteLibraryItemKey`, prefer a verified offline copy, and otherwise open the remote
source. Keep temporary playback buffering distinct from durable offline downloads.
