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

**StremioC** is used for managing **Stremio catalogue addons** only.

#### Features

- Add Stremio **catalogue addon** links
- Each added addon:
  - Appears as a separate provider in Extensions
  - Uses the saved **name** as the provider name
- When adding a catalogue addon:
  - Enter a **name**
  - Paste the **addon URL**
  - Save
- Added catalogue addons:
  - Can be viewed in a list
  - Can be deleted anytime

#### Notes

- Catalogue addons provide **metadata only**
- Episode playback will show:
  > **No link found**
- This mode is intended for **browsing only**

---

## Limitations ⚠️

- This extension is experimental
- Not a replacement for native Stremio
- Catalogue addons do not provide playable links

---

## Disclaimer

This extension does **not host or distribute any content**.  
All data is loaded from user-supplied Stremio addon URLs.

Use at your own risk.
