# Local prototype

The app supports English and Polish. Choose a language during registration or in
Settings. The choice survives a restart. Switching languages recreates the screen;
password fields are cleared rather than saved in screen state.

## Accounts

Create an account with an email, username and password. Email notifications are
optional and unchecked initially. This prototype stores accounts on the device;
it does not verify addresses, send emails, recover passwords or synchronise data.
Use test details. Passwords are stored as salted PBKDF2 hashes, not plain text.
Android backup and transfer of the preferences are disabled.

Settings supports username, email and password changes, signing out and account
deletion. Email/password changes and deletion require the current password.
Deletion removes the account, its intake history, its refill spots and its reports.
Other local accounts are retained.

## History and statistics

Intake starts at zero. Each change is saved with the local date and account ID.
The tracker switches to a new daily total when the date changes. The calendar
shows actual recorded progress, capped visually at 100 percent; the full amount
is retained. Missing days have no entry, and future days cannot be selected.
Resetting today leaves previous days intact.

The line chart offers the last 7 days, last 30 days, last 365 days and all time.
Week/month views show daily totals. Year/all-time views show monthly totals.
Missing days count as zero when calculating the average for the selected period.
No example history is inserted.

## Refill feedback

Open a map marker and choose Feedback. Reports can mark a spot as working, not
working, without water or incorrectly located, with an optional note. A user can
update their own report. Reports survive a restart and can be seen by other local
accounts on the same device. There is no shared server or moderation service.
User reports do not change the fictional water-quality area polygons.

## Checks

Unit tests cover account validation, password/email changes, duplicate emails,
failed writes, account isolation/deletion, date boundaries, chart aggregation,
nonnegative intake and feedback replacement. Instrumented tests use separate
preference files to check persistence, account data cleanup and both languages.

Manual scenarios:

- Register, log water, restart and check the total and calendar.
- Switch English/Polish and check Tracker, Settings, Statistics and map popups.
- Try a wrong current password, then change email/password and sign in again.
- Create a second local account; check that its water history starts empty.
- Add a refill spot and feedback, restart, then update the report.
- Delete an account and confirm that its data is removed without deleting others.
- Check enlarged text, TalkBack labels and the main flow with no connection.

Verification on 2026-11-06: 25 unit tests passed, including 19 tests for accounts,
history and feedback; four isolated Android persistence/localisation tests passed;
two Compose UI tests passed for registration/consent and calendar/chart controls.
AndroidX test dependencies were updated to Espresso 3.7.0 and JUnit extension
1.3.0 because the template versions could not inject input on Android API 37.

Final debug build and Android Lint completed successfully with no lint errors.
