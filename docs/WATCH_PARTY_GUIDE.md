# 🎉 WatchParty (Watch Together): Setup & User Guide

Watch movies, anime, and series in **perfect synchronization** with your friends across Android devices, Android TV, tablets, and phones using the **WatchParty** extension (also integrated into **Ultima**) for Cloudstream.

---

## 🌟 Features Overview

- 🔄 **Real-Time Playback Synchronization**: Play, pause, and seek actions are synced instantly from the host to all participants using Firebase Server-Sent Events (SSE).
- 🎬 **Automatic Media & Episode Following**: When the host starts a movie or switches episodes, participants automatically load the same stream if they have the provider installed.
- 💬 **In-Player Live Chat & Quick Reactions**: Chat with party members directly inside the player or react with floating emojis (❤️, 😂, 😮, 😢, 🔥, 👏, 🍿, 🚀, 💀, 💯).
- ⠿ **Draggable / Movable Overlay**: Move the reaction and chat bar anywhere on your screen so it never covers subtitles, back buttons, or player controls.
- 👑 **Host Management & Controls**:
  - **Transfer Host**: Pass host privileges to any member in the room.
  - **Mute / Unmute**: Silence disruptive members in the chat.
  - **Kick**: Remove unwanted participants from the room.
- 🔗 **Instant 1-Click Invite Links**: Share invite links that bundle your Firebase database URL and room code (`URL#ROOM_CODE`). Friends can join in one click without manually typing server URLs!
- 📺 **Android & Android TV Support**: Full D-Pad / remote navigation support with focus highlighting.

---

## 🛠️ Step 1: Firebase Database Setup (One-Time Setup)

WatchParty uses **Firebase Realtime Database** as a free, ultra-low latency, and reliable backend.

> [!TIP]
> If you already created a Firebase database for **Ultima Sync**, you can use the exact same Firebase URL for WatchParty! You don't need to create a second project.

If you don't have a Firebase Realtime Database yet:

1. Go to the [Firebase Console](https://console.firebase.google.com/) and click **Add Project** (e.g., `My-Cloudstream-Party`).
2. In the left-hand sidebar, navigate to **Build** ➔ **Realtime Database** and click **Create Database**.
3. Choose a database location near you and click **Next**.
4. Select **Start in test mode** and click **Enable**.
5. Go to the **Rules** tab and paste:
   ```json
   {
     "rules": {
       ".read": true,
       ".write": true
     }
   }
   ```
6. Click **Publish**.
7. Go to the **Data** tab and copy your reference URL (e.g. `https://my-cloudstream-party-default-rtdb.firebaseio.com/`).

---

## ⚙️ Step 2: Configure WatchParty in Cloudstream

1. Open **Cloudstream**.
2. Go to **Settings** ➔ **Extensions** ➔ **WatchParty** (or **Ultima**).
3. Tap **Open Settings** (or the gear icon).
4. In the dialog:
   - **Firebase Server URL**: Tap **Set URL** and paste your Firebase Database URL.
   - **Your Display Name**: Enter the name your friends will see (e.g. `Alex` or `GamerPro`).

---

## 👥 How to Use WatchParty

### Option A: Hosting a Watch Party (Host)

1. Open Cloudstream and go to **Settings** ➔ **Extensions** ➔ **WatchParty** ➔ **Open Settings**.
2. Tap **🎉 Create New Room**.
3. A unique 6-character room code will be generated (e.g., `A7X9K2`).
4. Tap **Copy Invite** to copy the full invite link (e.g., `https://your-firebase-db.firebaseio.com/#A7X9K2`) or tap **Code** to copy just the code.
5. Send the link or code to your friends!
6. Now, browse Cloudstream and start playing any movie or show. 
   - When you play, pause, or seek, all party members will automatically sync with you!

---

### Option B: Joining a Watch Party (Guest / Member)

1. Open Cloudstream and go to **Settings** ➔ **Extensions** ➔ **WatchParty** ➔ **Open Settings**.
2. In the **Code or Invite Link** input box, paste the invite link or type the room code provided by the host.
   > [!NOTE]
   > Pasting a full invite link (`https://db-url.firebaseio.com/#CODE`) will automatically configure your Firebase URL and join the room instantly!
3. Tap **Join**.
4. If the host is already playing a video, your app will prompt or automatically open the player and synchronize playback.

---

## 🎮 In-Player Controls & Overlay

Once you enter video playback while in an active party:

- **Party Badge (Top-Right)**:
  - Displays `🔴 Party (X) • HostName`.
  - Tap or click the badge to view room details, copy the invite link, view members, or leave the room.
- **Reaction & Chat Bar**:
  - **Drag Handle (`⠿`)**: Press and drag to move the bar anywhere on your screen. Your preferred position is saved!
  - **💬 Chat**: Tap to open the live party chat window.
  - **Quick Emoji Reactions**: Tap any emoji (❤️, 😂, 😮, 🍿, etc.) to trigger an animated floating reaction across all members' screens in real time.
- **Zero Overlay When Not in Party**:
  - When you are not in an active party room, no overlay or button is added to your player, ensuring a clean, distraction-free viewing experience.

---

## 👑 Host Moderation Tools

If you are the Host of the room, you have extra moderation controls available from the **Party Menu** (tap the top-right Party badge or open WatchParty settings):

- **Mute / Unmute**: Tap **Mute** next to a participant to prevent them from sending chat messages or spamming emojis.
- **Kick**: Tap **Kick** to remove a user from the room.
- **Transfer Host**: Tap **Host** next to a member to transfer ownership of the room to them.

---

## 💡 Important Requirements & Tips

1. **Same Content Provider / Extension**:
   - For guests to automatically load and stream the same video, guests must have the same provider/extension installed as the host (e.g., SuperStream, Sorastream, Ultima, etc.).
   - If a guest does not have the extension installed, a toast message will notify them: *"Host started 'Movie Title' on 'ProviderName'. Please install 'ProviderName'."*
2. **Network Latency & Buffering**:
   - The sync engine continuously ensures all participants stay within ±2.5 seconds of the host. If a guest falls behind due to network buffering, the player will automatically seek forward once buffering finishes.
3. **Leaving the Party**:
   - To leave, open the Watch Party dialog and tap **Leave Party**.
   - If the host leaves or closes the player, guest players are notified.
