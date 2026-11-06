# Nearby Subtitle Files — 0.4.4-dev

## Scope and Access

Open a video from a folder selected in the application library to enable nearby-file discovery. The library records the video's known SAF parent directory and carries it through recent media, the queue, and Activity restoration. Existing history entries without this optional context remain playable; opening the video through its folder supplies that context.

Discovery queries only that granted parent and requires the video's document ID to appear among its children. It does not derive filesystem paths from document IDs or recursively search other folders. Selecting an individual video alone does not establish permission to list its parent. This follows Android's [user-selected directory access model](https://developer.android.com/training/data-storage/shared/documents-files) and [document-provider hierarchy](https://developer.android.com/guide/topics/providers/document-provider).

## Filename Rules

For a video named `Episode.mkv`:

| Nearby filename | Behavior |
| --- | --- |
| `Episode.en.srt`, `Episode.eng.vtt` | Eligible for automatic selection when unambiguous |
| `Episode.en-US.srt`, `Episode.en_GB.vtt` | Regional English tags are supported |
| `Episode.en.sdh.srt`, `Episode.cc.en.vtt` | Caption tracks are eligible when no ordinary English match exists |
| `Episode.en.forced.srt` | Manual suggestion only |
| `Episode.srt` | Manual suggestion; its language is not guessed |
| `Episode.fa.srt`, `Episode.en.commentary.srt` | Not considered an English match |
| `Episode2.en.srt`, `Episode.720p.en.srt` | Not a match for this exact video basename |
| `Episode.en.ass`, `Episode.en.srt.bak` | Outside this SRT/WebVTT sidecar feature |

Matching is case-insensitive and uses Unicode NFC normalization. IDs remain unchanged. When multiple ordinary English matches exist, no file is chosen automatically. A single ordinary English file ranks above caption variants. Forced files require an explicit choice. These rules describe declared filename tags, not language recognition from subtitle content.

## Automatic and Manual Behavior

New automatic playback profiles permit a nearby English file to take priority over automatic embedded English selection. Before attachment, the existing bounded subtitle reader must successfully parse nonempty cues. Invalid or unreadable candidates leave the current playback/subtitle source intact and remain available for manual inspection from the caption menu.

Explicit manual, off, external-file, or internal-only automatic choices disable nearby automatic attachment. The new profile field `automaticSidecars` defaults to false for older profiles, preserving their prior behavior. An unfinished automatic discovery preference can survive reopening a newer profile. A successfully attached file is retained as the preferred external subtitle source; future discovery does not silently replace it.

The caption menu lists matching files and provides a read-only refresh action. It reports loading, unavailable folders, unreadable automatic candidates, and empty results. Manual choices use the existing tree grant and do not request additional access.

If a remembered subtitle is later deleted, moved, or becomes unreadable, select another file or switch explicitly to embedded English. This version does not silently substitute a different source for a remembered external file.

## Responsiveness and Result Validity

Queries and validation run on an IO dispatcher. Folder queries use a cancellation signal; switching sources cancels the previous request. Results are tied to both an open-generation token and a preference revision, so returning to the same URI does not revive an old request. A manual source choice or word lookup during discovery blocks automatic replacement. Active study repeats also prevent attachment.

The provider query is capped at 10,000 directory entries, skips virtual subtitle documents, and rejects partial discovery if the cap is exceeded. Automatic file validation uses the existing 8 MiB subtitle limit. No media files are moved or renamed, and discovery does not change the playback position or play/pause state.

## Manual Test Kit

Generate the synthetic fixtures in PowerShell:

```powershell
.\scripts\make-sidecar-test-kit.ps1
```

The script creates a versioned ZIP under `app/build/outputs/test-kits/`, using only the existing synthetic demo video and subtitles. Transfer and extract it on the phone, then select the extracted folder through the library.

- `01-auto`: a unique tagged English file should attach on the first automatic open.
- `02-choose`: two equally ranked English files require manual selection.
- `03-untagged`: an untagged file should be offered without automatic attachment.

All cases use the same video filename in different directories to exercise parent isolation. The archive includes a Persian testing guide. Use a fresh copy when repeating a first-open test; previously retained manual/off choices are intentionally preserved.

Twenty-one new JVM tests cover matching, ranking, ambiguity, Unicode, opaque IDs, and stale-result guards. They do not exercise an Android provider, permission persistence, or actual decoding. Device verification remains pending in the [playback acceptance checklist](PLAYER_ACCEPTANCE.md).
