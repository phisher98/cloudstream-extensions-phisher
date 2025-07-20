# 🔄 Ultima Cross Device Watch Sync Setup Guide

> ⚠️ **Important:** This setup requires creating a **private GitHub project** and generating a **personal access token (PAT)**.

---

## 🚀 Step-by-Step Instructions

### 1. Log In to GitHub
- Go to [https://github.com](https://github.com) and sign in to your account.

### 2. Create a New Private Project
- Create a **new private GitHub project** (any template works).
- Note the project number (usually formatted like `#1`, `#2`, etc.).

### 3. Generate a Personal Access Token
- Navigate to: [https://github.com/settings/tokens/new](https://github.com/settings/tokens/new)  
  *(Or: Settings → Developer Settings → Personal Access Tokens → Tokens (Classic) → Generate new token)*
- Set the **expiration** to `No expiration`.
- Select **"Project"** scope with **"Full control of projects"** permission.
- Click **Generate token**.
- 🔐 **Copy and save the token** — it won't be shown again.

---

## 🔧 Configure Ultima

### 4. Open Ultima Settings
- Go to **`Ultima Settings → Cross Device Watch Sync → Login Data`**.

### 5. Fill in the Following:
- **Token**: Paste your GitHub personal access token.
- **Project number**: Enter the project number (e.g., `#1`).
- **Device name**: Choose a unique name for this device.

### 6. Repeat on Other Devices
- Repeat steps 4–5 on any other devices you want to sync, using a **different device name** for each one.

---

## 🔁 Sync Behavior

- Enable **"Sync this device"** on the main device you want to sync content **from**.
- Synced content will appear on all other devices **with Ultima installed and configured** using the same GitHub credentials.

---

## 📱 Device Management

- View linked devices:  
  Go to **Ultima Settings → Cross Device Watch Sync**.
  
- To enable sync history for a device:  
  Toggle the switch next to its name to view its synced content on Ultima’s homepage.

- Don’t forget to **click the Save icon 💾** in the top-right corner after making changes.

---

## ⚠️ Important Notes

- For synced content to **play properly** across devices:
  - Each device must have **the same list of extensions installed**.
  - Configurations of those extensions should **match exactly**.

---
