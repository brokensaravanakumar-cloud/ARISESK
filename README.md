# ARISE - Solo Leveling voice assistant for Android

Say **"Arise"** and give a command. Native Kotlin + Jetpack Compose app, no backend server. You paste your own **Gemini** key (or OpenAI / OpenRouter / Groq / any OpenAI-compatible key) inside the app.

## Honest status - read first

- **No APK is included in this ZIP.** The workspace that wrote this project has no Android SDK and no access to Google's download servers, so the app could not be compiled or run there. The pure logic (command parser, alarm/timer parsing, calculator, wake-phrase matching, payment-safety rules) was compiled and passes 96 automated tests. The Android and UI code was written carefully and syntax-checked, but **has not been compiled or tested on a phone**. Expect to fix a few compile errors on first build; paste them to Claude and they are quick to fix.
- Wake word uses Android's speech recogniser, not sherpa-onnx (see "Wake word" below).
- Gmail reading, WhatsApp sending, YouTube auto-play and Play Store install all drive other apps through Android's Accessibility API. They depend on those apps' screen layouts and can break when the apps update.

## Get the APK (pick one)

### A. From your phone with GitHub (no PC needed)
1. Install **Termux** from F-Droid (the Play Store version is outdated) and a free account on github.com.
2. In Termux: `pkg update && pkg install git unzip`, then `termux-setup-storage` and allow access.
3. Put `AriseAI.zip` in your phone's Downloads, then:
   ```
   cd ~ && cp ~/storage/downloads/AriseAI.zip . && unzip AriseAI.zip && cd JinWooAI
   ```
4. On github.com create an empty repository named `arise` (private is fine). Create a token: Settings > Developer settings > Personal access tokens > Fine-grained > this repo > Contents: read/write.
5. Push:
   ```
   git init -b main && git add -A && git commit -m "ARISE"
   git remote add origin https://github.com/YOUR_NAME/arise.git
   git push -u origin main
   ```
   (username = your GitHub name, password = the token)
6. On github.com open the repo > **Actions** > "Build ARISE debug APK". It starts by itself after the push (about 5-8 minutes). Open the finished run and download **arise-debug-apk**, unzip it, and open `app-debug.apk`.
7. Allow "Install unknown apps" for your browser/file manager when Android asks.
If the build fails, open the failed step, copy the red error lines and send them to Claude.

### B. Android Studio on a PC
Open the `JinWooAI` folder in Android Studio (Ladybug or newer), let Gradle sync, then Build > Build APK(s). The APK appears in `app/build/outputs/apk/debug/`. Copy it to your phone.

## First run
1. Open ARISE, read the key warning, tap **CONFIG > AI PROVIDER AND KEYS**.
2. Choose **Google Gemini**, tap GET A KEY (aistudio.google.com/apikey), paste the key, SAVE KEY, then TEST CONNECTION. Model `gemini-2.5-flash` is the default; use SELECT MODEL to see what your key can use. Google's free tier is rate-limited and its terms can change; OpenAI has **no** free API tier.
3. Tap **AWAKEN** and allow microphone, notifications, contacts and phone permissions.
4. **CONFIG > PHONE CONTROL**: switch on the three items there (battery exemption, display over other apps, accessibility). These make 24-hour listening and hands-free app opening work.
5. Say "Arise" ... "Yes, Master" ... then your command.

## Things to say
- "Play Believer on YouTube" / "search lofi on YouTube"
- "Call Mom" / "call 9876543210" (asks yes/no first)
- "WhatsApp Rahul saying I will be late" (reads the message back, asks yes/no, then presses Send)
- "Set an alarm for 6:30 AM", "set a timer for 10 minutes", "open Instagram", "open Chrome"
- "Install Telegram" (opens the Play Store page and presses Install after your yes)
- "Tell me a story about a dragon", any question, "what is 12 times 5", "what time is it"
- "Do I have new emails?" (needs Gmail setup), "send an email to john at gmail dot com saying ..." (opens a draft; say "tap send")
- "Book tickets for Kalki on BookMyShow", then step by step: "tap search", "type Kalki", "press enter", "tap the first movie", "tap book tickets", "tap 7 PM". After the first screen command ARISE asks once for permission to operate the screen for 5 minutes.
- "Stop" silences it. "Go to sleep" turns listening off.

## What ARISE will not do
- It **never taps payment, purchase, UPI, card or delete controls** and never types into password/PIN/OTP fields. At the payment step it tells you to finish yourself.
- It does not install APK files from websites. "Install X" always goes through the Play Store (sideloading unknown APKs by voice is a malware risk). Paid Play Store apps are not purchased.
- Screen control is limited to Play Store, Chrome, Gmail, BookMyShow, WhatsApp and YouTube.

## 24-hour listening - realities
- Uses a foreground service with a permanent notification. Exempt the app from battery optimisation and set it to Unrestricted/Autostart in your phone maker's own battery menu (Xiaomi, Oppo, Vivo, Samsung, OnePlus all differ).
- Android 14+ will not let an app start microphone listening by itself after a reboot, so open ARISE and tap AWAKEN once after each restart.
- Another app using the microphone (calls, recording) pauses listening. Continuous speech recognition costs battery; expect a noticeable drain compared with a keyword spotter.
- Android blocks background apps from opening other apps. "Display over other apps" fixes this on most phones; otherwise ARISE posts a tap-to-continue notification.

## Wake word
This build uses Android's `SpeechRecognizer` (on-device when the language pack is installed) and matches "arise", "hey arise", "jin woo" and close mishearings. A word as common as "arise" can false-trigger; turn on "Require Hey" in CONFIG > WAKE WORD. For a real low-power offline spotter, see `docs/SHERPA_ONNX.md`.

## Voice
Android text-to-speech, pitched down (0.7) and slowed (0.92). CONFIG > VOICE lets you pick the deepest English voice installed and tune pitch/speed. Install better voices in Android Settings > Text-to-speech. The personality is original and not a clone of any actor.

## Sounds
Original activation, confirm, error and notify sounds are in `app/src/main/res/raw/`. CONFIG > ACTIVATION SOUND imports your own audio file (cut at 6 seconds) or silences it.

## Gmail (optional, one-time Google setup)
Google requires every app that reads Gmail to be registered:
1. console.cloud.google.com > new project > APIs & Services > enable **Gmail API**.
2. OAuth consent screen: External, Testing, add your own Gmail address under **Test users**.
3. Credentials > Create OAuth client ID > **Android**. Package name `com.jinwoo.ai`. SHA-1 fingerprint: `45:1E:D6:4B:6E:DF:67:A7:B9:E8:34:E1:08:9E:4D:92:37:6F:8E:4F` (the fixed key in `app/arise-sideload.jks`, so GitHub and Android Studio builds both match).
4. In ARISE: CONFIG > PHONE CONTROL > CONNECT GMAIL, then approve. Only read-only access is requested. Subjects are read aloud and are not sent to the AI.
`app/arise-sideload.jks` is a sideload key with a published password; it is not suitable for publishing to the Play Store.

## Privacy
No ARISE server exists. Speech goes to Android's speech service; question text goes to the AI provider you picked; contacts are searched only on the phone; history is stored only on the phone and can be switched off or wiped in CONFIG > PRIVACY. API keys are AES-256-GCM encrypted with the Android Keystore. A key inside a phone app can never be fully protected - use a key with a spending limit.

## Project map
`core/` command parser, calculator, alarm parsing, executor, safety guard | `service/AssistantService.kt` always-on listener | `ai/` providers and HTTP | `access/` accessibility service | `ui/` Solo Leveling Compose screens | `docs/` extras | `.github/workflows/build-apk.yml` cloud build.
