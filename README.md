# HydroTracker

HydroTracker is an Android app that helps students build a habit of drinking water. It is a course project for Mobile Systems.

- **Tracker** records daily intake in 250 ml steps and shows progress towards a 2,000 ml goal.
- **Statistics** show intake in a calendar and charts for the last 7, 30 or 365 days, or the full history.
- **Refill map** lets users mark places to refill a bottle and report problems. It uses Leaflet and OpenStreetMap.
- **Settings** let users manage their local account, switch between Polish and English, and schedule drinking reminders by day, time and interval.

## Run the app

1. Open the [project](project/) folder in Android Studio.
2. Install Android SDK Platform 37 and sync the Gradle project using Android Studio's bundled JDK.
3. Run the **app** configuration on an emulator or phone running Android 7.0 (API 24) or newer.

From the repository root on Windows, with the Android SDK and JDK configured:

```powershell
.\project\gradlew.bat -p project :app:assembleDebug
.\project\gradlew.bat -p project :app:testDebugUnitTest :app:lintDebug
.\project\gradlew.bat -p project :app:connectedDebugAndroidTest --no-configuration-cache
```

The last command requires a running emulator or a connected phone.

## Try the main flow

1. Create a local account using test details and add 250 ml in Tracker.
2. Restart the app and check the saved amount in Tracker and the calendar in Statistics.
3. Open the map, add a refill point and leave feedback on it.
4. Open Settings, choose a reminder schedule and allow Android notifications when prompted. Adding water restarts the reminder interval.

## Data and offline use

Accounts, intake history, refill points, feedback and reminder settings are stored on the device. Email consent is saved locally. The prototype does not send emails or sync data between phones.

The tracker, statistics and reminders work offline. The map needs a connection to load fresh background tiles. Saved points and feedback remain available when the map background fails to load. The coloured areas are fictional demo data and should never be used to decide whether water is safe to drink.

See [the local prototype notes](docs/local-prototype.md) and [the map notes](docs/map.md) for details and test scenarios.

## Team

- Antoni Usajewicz (257292)
- Fabian Rudnicki (257289)
- Mateusz Jurek (262701)
