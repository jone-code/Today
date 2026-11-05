# Today Mobile

React Native + Expo 53 mobile client. One codebase serves Android and iOS; the API stays in `apps/api`.

## Run

1. Start a reachable HTTPS API and initialize its database with `npm run db:init:docker` (or the configured local MySQL equivalent).
2. From the repository root, run `npm ci` and `npm run dev:mobile`.
3. Open the Expo QR code with Expo Go or a simulator. On the login screen enter the API's reachable origin, such as `https://api.example.com`. A phone cannot reach the host through `localhost`.

For local development only, `EXPO_PUBLIC_API_URL=http://<LAN-IP>:<API-PORT> npm run dev:mobile` prefills the address. Production builds require HTTPS. Credentials are stored with Expo SecureStore, not browser storage. No system notification permission is requested; follow-ups appear only in the in-app inbox.

The login screen accepts only the API origin (scheme, host and optional port), without a path, query, fragment or URL credentials. It normalizes host casing and default ports so drafts remain scoped to one consistent server address.

Android standalone builds block `SYSTEM_ALERT_WINDOW` and `VIBRATE` in Expo config; the app does not use overlays, vibration or notification delivery. After changing native permissions, regenerate the ignored `android/` directory with `npx expo prebuild --platform android --no-install` before building again.

On cold start, the saved session is checked with the API for up to eight seconds. An explicit 401 requires login again; a temporary network failure keeps the saved account available so locally stored chat drafts remain accessible. Conversations and actions still need a server connection; this is not offline chat.

Settings opens the trial feedback screen. It submits only the selected issue/idea type and the text entered by the user; chat history, photos and device logs are not attached. Pending text and its stable request ID are stored in SecureStore, scoped to the API origin and account. After a network error, retry reuses the same request ID; deleting a submitted report removes it from the list and rejects retries of that submission. Feedback stays in-app, without system notifications.

Use `npm run typecheck:mobile` and `npx expo install --check` from `apps/mobile` to check the client. `npx expo export --platform android` and `--platform ios` validate Metro bundles but do not produce signed installable apps. Android/iOS builds and photo upload still require testing on actual devices.

## Android build

Install Android Studio's SDK and a working JDK 17, then from `apps/mobile` run `npx expo prebuild --platform android --no-install`. The generated `android/` directory is intentionally ignored by Git; repeat prebuild after changing native plugin configuration.

For a development APK, run `cd android && ./gradlew :app:assembleDebug`. The output is `android/app/build/outputs/apk/debug/app-debug.apk`; it needs a running Metro server. For an **internal standalone trial** with JavaScript bundled into the APK, run `cd android && NODE_ENV=production ./gradlew :app:assembleRelease`. The output is `android/app/build/outputs/apk/release/app-release.apk`. The generated Expo native project currently signs this build with the Android **debug key**: it is not a publishable release and should not be distributed as one. The standalone build accepts only HTTPS API addresses; configure a reachable API on the login screen.

A distributable release requires a dedicated release signing key, a production HTTPS API and model configuration, and on-device acceptance testing. On iOS, native compilation additionally requires macOS and Xcode.

## Current internal artifacts

The latest Android internal APK is [dist-android/today-internal-20261103.apk](./dist-android/today-internal-20261103.apk). It is a standalone Expo bundle built from the current source and signed with the generated debug key; use it only for internal testing. The latest iOS JavaScript export is in `dist-ios/` (the Expo `metadata.json` and bundled assets); it is not an installable IPA and still requires Xcode or an Apple distribution workflow.
