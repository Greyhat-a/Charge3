# CHARGE 3D

Native Android app (Kotlin + Jetpack Compose + OpenGL ES 2.0). When you plug in a charger it
pops up a full-screen 3D animation that fills a **battery, drum or circle** as the phone charges,
with live percentage and **time left**.

## Build the APK from your tablet (no PC needed)

1. Create a free account at github.com and open it in your tablet's browser.
2. Create a new repository (any name, private is fine).
3. Upload this project: **Add file > Upload files**, then drag in everything from the unzipped
   folder. Make sure the hidden `.github` folder is included (if your file app hides it, use
   **Add file > Create new file** and name it `.github/workflows/build.yml`, then paste the
   contents of that file).
4. Open the **Actions** tab, choose **Build APK**, tap **Run workflow**. It takes about 5-8 minutes.
5. When it turns green, open the run, scroll to **Artifacts**, download **Charge3D-apk**, unzip it.
6. Tap the `.apk` and allow "Install unknown apps" for your browser or file manager.

If the build fails, open the failed step, copy the red error text and send it to me.

## First run

Open the app, switch on **Pop up when plugged in**, then work down the SET UP list
(notifications, display over other apps, full-screen notifications, run in background).
Each one is explained in the app. Tecno / Infinix / Xiaomi / Oppo / Samsung phones may also need
"Auto-start" or "Unrestricted battery" turned on in the app's system settings.

## Charging-screen gestures

Fast swipe = next/previous animation · tap = pulse · double-tap = change effect ·
drag = rotate · long-press or the gear = Studio (colours, gradients, speed, glow, particles,
size, background, sound, quality, saved themes).
