# Select Tube

A YouTube subscription-only app. It shows videos only from channels you have selected, in the feed and everywhere else. Nothing outside those channels is visible.

NewPipe is the inspiration (it showed what's possible). Select Tube is built separately, not as a fork.

## Core features

- **Channel selection (most important feature):** the app only ever shows videos from the selected channels.
- **Background play.**
- **No ads.**

## Channel management

- Channels are added from **Settings**.
- Multiple channels can be added together in a single batch operation.
- Users stage channels into a review list, review them, and confirm the batch with their password entered once.
- The **first-ever batch** of channels can be added without waiting (all channels in this initial batch become active immediately). The **password is set** during this first batch.
- Adding channels is protected by that password. The password **can't be changed**.
- After the first batch, a request to add channels requires **waiting about 2 hours** (tentative, configured in `ChannelConfig`) before taking effect. The batch shares a common activation timestamp.
- Once the wait is over, channels **become available automatically**.
- While any channel is pending, each channel keeps its own individual **cancel addition** option. The wait is the whole point.
- **Removing** an active channel needs no password and no wait.

## Adding channels

Channels can be added by:

- Searching for them (channel search option)
- Entering channel IDs
- Entering channel URLs

## Playback

- Stream resolution is handled with NewPipe Extractor, choosing the best video-only stream up to 720p merged with the best audio stream via Media3's `MergingMediaSource` (with progressive fallback).
- In-app player screen with custom playback controls, seek bar, time display, and quality badge.
- Seamless background and lock-screen playback powered by an Android `MediaSessionService` (`mediaPlayback`), handling audio focus and headphones disconnect.
- Automatic recovery and re-resolution when stream URLs expire.

## Platforms

- **Android first.**
- Web app afterwards (convert from the Android version).

## Development

- Fully vibecoded on Google Antigravity.
- Connected to GitHub.

## License

This project is licensed under the GNU General Public License v3.0 (GPLv3) — see the [LICENSE](LICENSE) file for details.
It uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), which is licensed under GPLv3.
