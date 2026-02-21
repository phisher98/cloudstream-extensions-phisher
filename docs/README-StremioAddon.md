# StremioX Extension

StremioX is an **experimental Cloudstream extension** that provides two separate modes for working with Stremio addons:

- **StremioX**
- **StremioC**

Each mode serves a different purpose and should be used accordingly.

> ⚠️ Experimental feature. Do not expect a full Stremio-like experience.

---

## Available Modes

### 1. StremioX (Stream Addons)

**StremioX** is used for managing **Stremio stream addons**.

#### Features

- Add multiple Stremio **stream addon** links
- Unlimited number of links supported
- Simple UI with two actions:
  - **Add Link**
  - **List Links**
- When adding a link:
  - Enter a **name**
  - Paste the **addon URL**
  - Save
- Saved links:
  - Are used to load available streams
  - Can be viewed and deleted anytime

#### Notes

- Streaming availability depends on the addon

---

### 2. StremioC (Catalogue Addons)

**StremioC** manages **Stremio catalogue addons** and retrieves both **metadata and streams directly from the addon URL**.

#### Features

- Add Stremio **catalogue addon** URLs
- Each added addon:
  - Appears as a separate provider under Extensions
  - Uses the saved **name** as the provider name
- When adding a catalogue addon:
  - Enter a **name**
  - Paste the **addon URL**
  - Save
- Added catalogue addons:
  - Are listed for easy management
  - Can be deleted at any time
- **Stream handling**:
  - Streams are fetched from the addon itself
  - If the addon does not provide streams, the system **automatically falls back to torrent links**
- **✨AIOStreams Integration**:
  - **By wrapping both stream and catalogue addons within the AIOStreams addon**
  - **StremioC can provide simultaneous support for both functions.**

#### Notes

- Catalogue addons provide **metadata only**
- Episode playback will show:
  > **No link found**
- This mode is intended for **browsing only**
- **To enable full functionality (Catalogs + Streams), it is highly recommended to use AIOStreams to wrap your desired addons.**
---

## Limitations ⚠️

- This extension is experimental
- Not a replacement for native Stremio
- Standard catalogue addon may not provide playable links without AIOStreams wrapping.
---

## Disclaimer

This extension does **not host or distribute any content**.  
All data is loaded from user-supplied Stremio addon URLs.

Use at your own risk.
