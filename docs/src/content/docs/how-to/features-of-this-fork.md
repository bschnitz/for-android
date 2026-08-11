---
title: Features of this fork
description: Install this fork, point it at a self-hosted Stoat instance, and choose how much you want to be notified.
template: doc
---

The official app talks to `stoat.chat` and nothing else — the server address is not
configurable, and the documented answer for anyone running their own instance is to build
the app from source. This fork exists so that you do not have to.

## What it adds over upstream

**A configurable server.** You enter the address of your instance and the app discovers the
rest. Several servers can be stored side by side, each with its own account, so one install
covers a self-hosted instance and the public one.

**Notification levels per server.** Upstream only pushes when you are mentioned by name;
everything else you have to go and look for. Here you pick a level per server, from every
message down to nothing at all.

**It installs alongside the official app.** The fork ships under the application id
`chat.stoat.fork` rather than upstream's `chat.revolt`, so Android treats the two as separate
apps instead of updates of each other. Both can be installed at the same time.

## Install

Builds are published as APK assets on the
[releases page](https://github.com/bschnitz/for-android/releases); the topmost release is the
current one. Download the APK, open it, and confirm the install. Android will ask once whether
it may install apps from this source — it has to, because the file does not come from the Play
Store. The app requires Android 8 or newer.

Later versions install as an update over the previous one as long as they come from the same
source, because they are signed with the same key.

## Connect to a server

1. Open the app. The current server is shown at the bottom of the login screen — tap it.
2. Choose **Add a server**.
3. Enter the host name of your instance, for example `chat.example.org`. The API URL is
   discovered from it, so you do not need to know it. The label next to it is optional and
   only affects how the entry is listed.
4. Save, select the new entry, then **Log In**.

The selected server persists across restarts, and switching between stored servers does not
log you out of the others.

## Choose a notification level

**Settings → Notifications** lists every server you are a member of. Each one carries one of
four levels:

| Level                     | What reaches you                           |
| ------------------------- | ------------------------------------------ |
| **All messages**          | Every message in every channel you can see |
| **Channels you write in** | Only channels you have posted in yourself  |
| **Mentions only**         | Only messages that mention you             |
| **Nothing**               | Nothing                                    |

**Mentions only** is the default, which is the usual reason for an instance that appears to
send no notifications at all.

The level is stored as a synced user setting, so it applies to every client you are logged in
with and only has to be set once. A change can take up to about half a minute to take effect,
because the server caches the setting.

Levels beyond mentions require the instance to run a push daemon that honours them. Against an
instance without that support the app still works; only the levels above **Mentions only**
behave like **Mentions only**.
