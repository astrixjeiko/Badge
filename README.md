# Badge

Badge is an Android budgeting app that makes tracking expenses fast. Instead
of opening the app every time you spend money, you log it from a home-screen
widget in two taps.

## Download

Get the latest APK from the
[Releases page](../../releases/latest). Open the file on your Android phone,
allow installs from your browser or file manager if asked, then add the
**Badge** widget from your home screen's widget list.

Google Play Protect may show a warning because the app isn't distributed
through the Play Store. This is normal for APKs shared outside it.

## Features

- **Home-screen widget (4×4):** tap a quick-add tile to select it, then tap
  **Confirm** to log it. The widget shows today's total and the last action.
  A **+** button opens a pop-up for custom amounts.
- **Quick-add buttons:** create your own one-tap buttons for common spending,
  like commute fare, each with its own label, amount, and category.
- **Custom amounts:** enter any amount with a numbers-only keyboard, with an
  optional label and category.
- **Dashboard:** see what you've spent today, this week, and this month, plus
  your recent expenses.
- **Budgets:** set limits per day, week, month, or year, for all spending or a
  single category. Progress bars turn red when you go over.
- **Custom categories:** create categories like Food, Transport, or
  Miscellaneous.
- **Currency picker:** choose your currency symbol in Settings.
- **Dark teal theme** that follows your phone's light and dark mode.

## Requirements

- Android 8.0 (API 26) or newer

## Planned

- Export to CSV and Google Sheets
- Budget alerts
- Spending charts by category
- Editing quick buttons and expenses

## Built with

Kotlin, Jetpack Compose, Room, and Jetpack Glance.

## Building from source

1. Clone this repository.
2. Open it in Android Studio and let Gradle sync.
3. Run the app on an emulator or a physical device.

## Notes

All data is stored locally on your device. Uninstalling the app removes it.
