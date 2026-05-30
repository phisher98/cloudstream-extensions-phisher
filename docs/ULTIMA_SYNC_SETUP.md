# 🚀 Ultima Sync: Firebase Setup Guide

This guide will show you how to set up your own free **Firebase Realtime Database** and configure the **Ultima** plugin in Cloudstream to enable seamless, real-time cross-device sync for your settings, bookmarks, repositories, and extensions.

---

## 📂 Step 1: Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add Project** (or **Create a project**).
3. Enter a project name (e.g., `My-Cloudstream-Sync`).
4. Click **Continue**.
5. *Optional*: Turn off **Google Analytics** for this project (it's not needed and makes creation faster).
6. Click **Create Project** and wait for it to load, then click **Continue**.

---

## 💾 Step 2: Create a Realtime Database

1. In the left-hand sidebar menu, click **Build** and select **Realtime Database**.
2. Click the **Create Database** button.
3. Select a **Database Location** close to you (e.g., United States, Europe, or Asia) and click **Next**.
4. Set the Security Rules:
   - Select **Start in test mode** (this initializes database rules to allow read/write access).
5. Click **Enable**.

---

## 🔒 Step 3: Configure Database Security Rules (Crucial)

By default, "Test Mode" rules expire after 30 days and allow anyone with your database URL to access your data. To secure your database and prevent expiration, follow these steps:

1. In your Realtime Database dashboard, click on the **Rules** tab at the top.
2. Replace the default rules with the following rules:
   ```json
   {
     "rules": {
       ".read": true,
       ".write": true
     }
   }
   ```
3. Click the **Publish** button to save and apply the new rules.

---

## 🔗 Step 4: Retrieve your Database URL

1. Click on the **Data** tab in your Realtime Database dashboard.
2. Copy the reference URL showing at the top (it looks like `https://your-project-name-default-rtdb.firebaseio.com/`).
   - *Note: Make sure the URL ends with a forward slash `/`.*

---

## ⚙️ Step 5: Configure Ultima in Cloudstream

Now that your database is ready, configure the sync settings inside the **Ultima** plugin on your devices:

1. Open **Cloudstream** on your device.
2. Go to **Settings** ➔ **Extensions** ➔ **Ultima** ➔ **Open Settings**.
3. Under the **Sync Settings** section:
   - Toggle **Use Custom Database** to **ON**.
   - **Firebase Database URL**: Paste your copied URL (`https://your-project-name-default-rtdb.firebaseio.com/`).
   - **Sync Passkey / Sync Key**: Choose a private, complex secret key (e.g. `my_super_secret_sync_key_999`). 
     - *Important: This key acts as your password. Whichever devices use the same Sync Key and Firebase URL will sync together!*
   - **Device Name**: Provide a recognizable name for the current device (e.g. `My Phone` or `Living Room TV`).
4. Choose what you want to sync:
   - Enable **Backup Device** if you want this device to upload its settings.
   - Enable **Restore Device** if you want this device to pull/receive settings from your other devices.
   - Select individual checkboxes (Bookmarks, Resume Watching, Extensions, Repositories, Theme, Layout, etc.) to customize what is synced.
5. Click **Save Settings**.
6. **For your first sync on a new/secondary device**:
   - Tap **Pull changes from the cloud** first. This fetches your existing settings from the database so they are not overwritten by the new device's empty/default settings.
   - Once the data is successfully restored, you can safely enable **Backup Device** (if you want this device to upload changes in the future) and tap **Sync Now (Force Sync)** to keep everything in sync!

---

### 🎉 Troubleshooting & Tips

- **Extensions Loading Delay**: When syncing to a new device, missing extensions will download automatically from their repositories in the background. The plugin will hot-reload them and refresh your providers list automatically—no app restart required!
- **Zero KB Error Safeguard**: Ultima contains built-in checks that detect empty or corrupted `.cs3` files and automatically repairs them by redownloading them from their source repositories.
- **Sync Loops**: The sync engine compares local data hashes and will automatically ignore redundant updates to save battery and data usage.
- **Preventing Overwriting with Empty Settings**: When linking a new/empty device to an existing sync network, always perform a **Pull changes from the cloud** first before pushing or running a full sync. This ensures you fetch your data from the database instead of pushing empty/default settings from the new device, which would overwrite your existing backups.
