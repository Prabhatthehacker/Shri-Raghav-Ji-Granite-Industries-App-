# Shri Raghav Granite Industries — Android app

Native Android project in Kotlin and Jetpack Compose. It does not use Expo, React Native, or a hosted runtime. Business data is stored locally in SQLite on the device.

## Get the APK from GitHub Actions

1. Unzip this project and push the contents of `raghav-granite-android` to the root of your GitHub repository.
2. Open the repository's **Actions** tab. The workflow runs on a push to `main`/`master`, pull requests, or when started with **Run workflow**.
3. Open the completed run and download the `shri-raghav-granite-erp-debug-apk` artifact.
4. Unzip that artifact and install `app-debug.apk` on an Android device.

The debug APK is signed with Android's automatically generated debug key for device testing. It is not a Play Store release package.

## Build locally

Install JDK 17, Android SDK Platform 35, Build Tools 35.0.0, and Gradle 8.9, then run:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Implemented in this build

- Dark industrial dashboard and navigation for Overview, Stock, Production, Dispatch, and Settings.
- Persistent SQLite schema for stock, production, invoices, purchases, payments, double-entry accounts, vehicles/trips, dispatch, expenses, GST rates, documents, users, and audit records.
- Stock inward/outward operations with negative-stock rejection and weighted-average costing.
- Production batch calculations and explicit approval before inventory changes; approval posts input and outputs in a database transaction.
- Buyer-owned outgoing truck details are held on dispatch records only. Dispatch stock is deducted atomically, and edits restore the earlier quantity before applying the revised one. Buyer trucks are not added to the company fleet or company vehicle expenses.
- Configurable business address, GSTIN, contact details, and optional verified density. No official business information was invented.
- Unit tests for paise arithmetic, invoice/GST totals, production yield and cost, negative stock, weighbridge net weight, journal balance, and duplicate payment references.

## Remaining ERP work

This is the initial native app build, not the entire supplied ERP scope. Full customer/supplier and invoice forms, posted payment allocation, reversals and audit review, company fleet and incoming trips, machine maintenance, GST registers, FileVault, reports, backup/restore, and PIN/role enforcement still need implementation. Do not rely on this initial build as a complete accounting or GST filing system.
