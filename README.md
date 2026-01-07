# @asiriindatissa/capacitor-health

Capacitor plugin to read and write health metrics via Health Connect on Android. The TypeScript API provides a unified interface for health data access.

## Why Capacitor Health?

The only **free**, **unified** health data plugin for Capacitor supporting the latest native APIs:

- **Health Connect (Android)** - Uses Google's newest health platform (replaces deprecated Google Fit)
- **Unified API** - TypeScript interface with consistent units
- **Multiple metrics** - Steps, distance, calories, heart rate, weight, height
- **Read & Write** - Query historical data and save new health entries
- **Modern standards** - Supports Android 8.0+

Perfect for fitness apps, health trackers, wellness platforms, and medical applications.

## Install

```bash
npm install @asiriindatissa/capacitor-health
npx cap sync
```

## Android Setup

This plugin now uses [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) instead of Google Fit. Make sure your app meets the requirements below:

1. **Min SDK 26+.** Health Connect is only available on Android 8.0 (API 26) and above. The plugin's Gradle setup already targets this level.
2. **Declare Health permissions.** The plugin manifest ships with the required `<uses-permission>` declarations (`READ_/WRITE_STEPS`, `READ_/WRITE_DISTANCE`, `READ_/WRITE_ACTIVE_CALORIES_BURNED`, `READ_/WRITE_HEART_RATE`, `READ_/WRITE_WEIGHT`, `READ_/WRITE_HEIGHT`, `READ_EXERCISE`). Your app does not need to duplicate them, but you must surface a user-facing rationale because the permissions are considered health sensitive.
3. **Ensure Health Connect is installed.** Devices on Android 14+ include it by default. For earlier versions the user must install _Health Connect by Android_ from the Play Store. The `Health.isAvailable()` helper exposes the current status so you can prompt accordingly.
4. **Request runtime access.** The plugin opens the Health Connect permission UI when you call `requestAuthorization`. You should still handle denial flows (e.g., show a message if `checkAuthorization` reports missing scopes).
5. **Provide a Privacy Policy.** Health Connect requires apps to display a privacy policy explaining how health data is used. See the [Privacy Policy Setup](#privacy-policy-setup) section below.

If you already used Google Fit in your project you can remove the associated dependencies (`play-services-fitness`, `play-services-auth`, OAuth configuration, etc.).

### Privacy Policy Setup

Health Connect requires your app to provide a privacy policy that explains how you handle health data. When users tap "Privacy policy" in the Health Connect permissions dialog, your app must display this information.

**Option 1: HTML file in assets (recommended for simple cases)**

Place an HTML file at `android/app/src/main/assets/public/privacypolicy.html`:

```html
<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Privacy Policy</title>
  </head>
  <body>
    <h1>Privacy Policy</h1>
    <p>Your privacy policy content here...</p>
    <h2>Health Data</h2>
    <p>Explain how you collect, use, and protect health data...</p>
  </body>
</html>
```

**Option 2: Custom URL (recommended for hosted privacy policies)**

Add a string resource to your app's `android/app/src/main/res/values/strings.xml`:

```xml
<resources>
    <!-- Your other strings... -->
    <string name="health_connect_privacy_policy_url">https://yourapp.com/privacy-policy</string>
</resources>
```

This URL will be loaded in a WebView when the user requests to see your privacy policy.

**Programmatic access:**

You can also show the privacy policy or open Health Connect settings from your app:

```ts
// Show the privacy policy screen
await Health.showPrivacyPolicy();

// Open Health Connect settings (useful for managing permissions)
await Health.openHealthConnectSettings();
```

## Usage

```ts
import { Health } from '@asiriindatissa/capacitor-health';

// Verify that the native health SDK is present on this device
const availability = await Health.isAvailable();
if (!availability.available) {
  console.warn('Health access unavailable:', availability.reason);
}

// Ask for separate read/write access scopes
// Include 'workouts' if you need to query workout sessions
await Health.requestAuthorization({
  read: ['steps', 'weight', 'workouts'],
  write: ['weight'],
});

// Query the last 50 step samples from the past 24 hours
const { samples } = await Health.readSamples({
  dataType: 'steps',
  startDate: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
  endDate: new Date().toISOString(),
  limit: 50,
});

// Persist a new body-weight entry (kilograms by default)
await Health.saveSample({
  dataType: 'weight',
  value: 74.3,
});
```

### Supported data types

| Identifier | Default unit  | Notes                      |
| ---------- | ------------- | -------------------------- |
| `steps`    | `count`       | Step count deltas          |
| `distance` | `meter`       | Walking / running distance |
| `calories` | `kilocalorie` | Active energy burned       |
| `weight`   | `kilogram`    | Body mass                  |
| `height`   | `meter`       | Body height                |

All write operations expect the default unit shown above. On Android the `metadata` option is currently ignored by Health Connect.

### Workouts

To query workout sessions, you need to request read permission for `'workouts'`:

```ts
// Request permission to read workouts
// IMPORTANT: To see totalEnergyBurned and totalDistance in workout data,
// you MUST also request permissions for 'calories' and 'distance'
await Health.requestAuthorization({
  read: ['workouts', 'calories', 'distance'], // Include data types for workout metrics
  write: [],
});

// Query recent workouts
const { workouts } = await Health.queryWorkouts({
  startDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
  endDate: new Date().toISOString(),
  limit: 10,
});

// Each workout may include:
// - workoutType, duration, startDate, endDate (always present)
// - totalEnergyBurned (if calories permission granted and data exists)
// - totalDistance (if distance permission granted and data exists)
```

**Note:**

- `'workouts'` is a special read-only permission type. You cannot write workouts with this plugin.
- Workout energy and distance data are aggregated from separate Health Connect records during the workout time period. If you don't request permissions for `calories` and `distance`, these fields will be missing from workout results.
- If `totalEnergyBurned` or `totalDistance` are missing despite having permissions, it means no calorie or distance data was recorded during that workout period in Health Connect.

### Sleep

To query sleep sessions, you need to request read permission for `'sleep'`:

```ts
// Request permission to read sleep data
await Health.requestAuthorization({
  read: ['sleep'],
  write: [],
});

// Query recent sleep sessions
const { sleepSessions } = await Health.querySleep({
  startDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
  endDate: new Date().toISOString(),
  limit: 10,
});

// Each sleep session includes:
// - duration (in seconds), startDate, endDate (always present)
// - title (optional, if provided by the source app)
// - stages (optional array of sleep stage records if available)
// - sourceName, sourceId (source app information)

// Sleep stages can be: 'unknown', 'awake', 'sleeping', 'outOfBed',
// 'awakeInBed', 'light', 'deep', 'rem'
```

**Supported Sleep Stages:**

| Stage        | Description                                    |
| ------------ | ---------------------------------------------- |
| `unknown`    | Unspecified or unknown if the user is sleeping |
| `awake`      | The user is awake within a sleep cycle         |
| `sleeping`   | Generic or non-granular sleep description      |
| `outOfBed`   | The user gets out of bed during sleep session  |
| `awakeInBed` | The user is awake in bed                       |
| `light`      | Light sleep cycle                              |
| `deep`       | Deep sleep cycle                               |
| `rem`        | REM sleep cycle                                |

**Note:**

- `'sleep'` is a special read-only permission type. You cannot write sleep data with this plugin.
- Not all sleep sessions include detailed sleep stages. Some apps may only record the overall sleep duration.

## API

<docgen-index>

* [`isAvailable()`](#isavailable)
* [`requestAuthorization(...)`](#requestauthorization)
* [`checkAuthorization(...)`](#checkauthorization)
* [`readSamples(...)`](#readsamples)
* [`saveSample(...)`](#savesample)
* [`getPluginVersion()`](#getpluginversion)
* [`openHealthConnectSettings()`](#openhealthconnectsettings)
* [`showPrivacyPolicy()`](#showprivacypolicy)
* [`queryWorkouts(...)`](#queryworkouts)
* [`querySleep(...)`](#querysleep)
* [`queryHydration(...)`](#queryhydration)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### isAvailable()

```typescript
isAvailable() => Promise<AvailabilityResult>
```

Returns whether the current platform supports the native health SDK.

**Returns:** <code>Promise&lt;<a href="#availabilityresult">AvailabilityResult</a>&gt;</code>

--------------------


### requestAuthorization(...)

```typescript
requestAuthorization(options: AuthorizationOptions) => Promise<AuthorizationStatus>
```

Requests read/write access to the provided data types.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#authorizationoptions">AuthorizationOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#authorizationstatus">AuthorizationStatus</a>&gt;</code>

--------------------


### checkAuthorization(...)

```typescript
checkAuthorization(options: AuthorizationOptions) => Promise<AuthorizationStatus>
```

Checks authorization status for the provided data types without prompting the user.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#authorizationoptions">AuthorizationOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#authorizationstatus">AuthorizationStatus</a>&gt;</code>

--------------------


### readSamples(...)

```typescript
readSamples(options: QueryOptions) => Promise<ReadSamplesResult>
```

Reads samples for the given data type within the specified time frame.

| Param         | Type                                                  |
| ------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#queryoptions">QueryOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#readsamplesresult">ReadSamplesResult</a>&gt;</code>

--------------------


### saveSample(...)

```typescript
saveSample(options: WriteSampleOptions) => Promise<void>
```

Writes a single sample to the native health store.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#writesampleoptions">WriteSampleOptions</a></code> |

--------------------


### getPluginVersion()

```typescript
getPluginVersion() => Promise<{ version: string; }>
```

Get the native Capacitor plugin version

**Returns:** <code>Promise&lt;{ version: string; }&gt;</code>

--------------------


### openHealthConnectSettings()

```typescript
openHealthConnectSettings() => Promise<void>
```

Opens the Health Connect settings screen.

Use this to direct users to manage their Health Connect permissions
or to install Health Connect if not available.

--------------------


### showPrivacyPolicy()

```typescript
showPrivacyPolicy() => Promise<void>
```

Shows the app's privacy policy for Health Connect.

This displays the same privacy policy screen that Health Connect shows
when the user taps "Privacy policy" in the permissions dialog.

The privacy policy URL can be configured by adding a string resource
named "health_connect_privacy_policy_url" in your app's strings.xml,
or by placing an HTML file at www/privacypolicy.html in your assets.

--------------------


### queryWorkouts(...)

```typescript
queryWorkouts(options: QueryWorkoutsOptions) => Promise<QueryWorkoutsResult>
```

Queries workout sessions from the native health store on Android (Health Connect).

| Param         | Type                                                                  | Description                                                                             |
| ------------- | --------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#queryworkoutsoptions">QueryWorkoutsOptions</a></code> | Query options including optional workout type filter, date range, limit, and sort order |

**Returns:** <code>Promise&lt;<a href="#queryworkoutsresult">QueryWorkoutsResult</a>&gt;</code>

--------------------


### querySleep(...)

```typescript
querySleep(options: QuerySleepOptions) => Promise<QuerySleepResult>
```

Queries sleep sessions from the native health store on Android (Health Connect).

| Param         | Type                                                            | Description                                               |
| ------------- | --------------------------------------------------------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#querysleepoptions">QuerySleepOptions</a></code> | Query options including date range, limit, and sort order |

**Returns:** <code>Promise&lt;<a href="#querysleepresult">QuerySleepResult</a>&gt;</code>

--------------------


### queryHydration(...)

```typescript
queryHydration(options: QueryHydrationOptions) => Promise<QueryHydrationResult>
```

Queries hydration records from the native health store on Android (Health Connect).

| Param         | Type                                                                    | Description                                               |
| ------------- | ----------------------------------------------------------------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#queryhydrationoptions">QueryHydrationOptions</a></code> | Query options including date range, limit, and sort order |

**Returns:** <code>Promise&lt;<a href="#queryhydrationresult">QueryHydrationResult</a>&gt;</code>

--------------------


### Interfaces


#### AvailabilityResult

| Prop            | Type                            | Description                                            |
| --------------- | ------------------------------- | ------------------------------------------------------ |
| **`available`** | <code>boolean</code>            |                                                        |
| **`platform`**  | <code>'android' \| 'web'</code> | Platform specific details (for debugging/diagnostics). |
| **`reason`**    | <code>string</code>             |                                                        |


#### AuthorizationStatus

| Prop                  | Type                                 | Description                                                 |
| --------------------- | ------------------------------------ | ----------------------------------------------------------- |
| **`readAuthorized`**  | <code>ReadAuthorizationType[]</code> | Data types (and 'workouts') that are authorized for reading |
| **`readDenied`**      | <code>ReadAuthorizationType[]</code> | Data types (and 'workouts') that are denied for reading     |
| **`writeAuthorized`** | <code>HealthDataType[]</code>        | Data types that are authorized for writing                  |
| **`writeDenied`**     | <code>HealthDataType[]</code>        | Data types that are denied for writing                      |


#### AuthorizationOptions

| Prop        | Type                                 | Description                                                                                                  |
| ----------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| **`read`**  | <code>ReadAuthorizationType[]</code> | Data types that should be readable after authorization. Include 'workouts' to enable queryWorkouts() method. |
| **`write`** | <code>HealthDataType[]</code>        | Data types that should be writable after authorization.                                                      |


#### ReadSamplesResult

| Prop          | Type                        |
| ------------- | --------------------------- |
| **`samples`** | <code>HealthSample[]</code> |


#### HealthSample

| Prop             | Type                                                      |
| ---------------- | --------------------------------------------------------- |
| **`dataType`**   | <code><a href="#healthdatatype">HealthDataType</a></code> |
| **`value`**      | <code>number</code>                                       |
| **`unit`**       | <code><a href="#healthunit">HealthUnit</a></code>         |
| **`startDate`**  | <code>string</code>                                       |
| **`endDate`**    | <code>string</code>                                       |
| **`sourceName`** | <code>string</code>                                       |
| **`sourceId`**   | <code>string</code>                                       |


#### QueryOptions

| Prop            | Type                                                      | Description                                                        |
| --------------- | --------------------------------------------------------- | ------------------------------------------------------------------ |
| **`dataType`**  | <code><a href="#healthdatatype">HealthDataType</a></code> | The type of data to retrieve from the health store.                |
| **`startDate`** | <code>string</code>                                       | Inclusive ISO 8601 start date (defaults to now - 1 day).           |
| **`endDate`**   | <code>string</code>                                       | Exclusive ISO 8601 end date (defaults to now).                     |
| **`limit`**     | <code>number</code>                                       | Maximum number of samples to return (defaults to 100).             |
| **`ascending`** | <code>boolean</code>                                      | Return results sorted ascending by start date (defaults to false). |


#### WriteSampleOptions

| Prop            | Type                                                            | Description                                                                                                                                                                                                                               |
| --------------- | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`dataType`**  | <code><a href="#healthdatatype">HealthDataType</a></code>       |                                                                                                                                                                                                                                           |
| **`value`**     | <code>number</code>                                             |                                                                                                                                                                                                                                           |
| **`unit`**      | <code><a href="#healthunit">HealthUnit</a></code>               | Optional unit override. If omitted, the default unit for the data type is used (count for `steps`, meter for `distance`, kilocalorie for `calories`, kilogram for `weight`, meter for `height`). |
| **`startDate`** | <code>string</code>                                             | ISO 8601 start date for the sample. Defaults to now.                                                                                                                                                                                      |
| **`endDate`**   | <code>string</code>                                             | ISO 8601 end date for the sample. Defaults to startDate.                                                                                                                                                                                  |
| **`metadata`**  | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Metadata key-value pairs forwarded to the native APIs where supported.                                                                                                                                                                    |


#### QueryWorkoutsResult

| Prop           | Type                   |
| -------------- | ---------------------- |
| **`workouts`** | <code>Workout[]</code> |


#### Workout

| Prop                    | Type                                                            | Description                                         |
| ----------------------- | --------------------------------------------------------------- | --------------------------------------------------- |
| **`workoutType`**       | <code><a href="#workouttype">WorkoutType</a></code>             | The type of workout.                                |
| **`duration`**          | <code>number</code>                                             | Duration of the workout in seconds.                 |
| **`totalEnergyBurned`** | <code>number</code>                                             | Total energy burned in kilocalories (if available). |
| **`totalDistance`**     | <code>number</code>                                             | Total distance in meters (if available).            |
| **`startDate`**         | <code>string</code>                                             | ISO 8601 start date of the workout.                 |
| **`endDate`**           | <code>string</code>                                             | ISO 8601 end date of the workout.                   |
| **`sourceName`**        | <code>string</code>                                             | Source name that recorded the workout.              |
| **`sourceId`**          | <code>string</code>                                             | Source bundle identifier.                           |
| **`metadata`**          | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Additional metadata (if available).                 |


#### QueryWorkoutsOptions

| Prop              | Type                                                | Description                                                               |
| ----------------- | --------------------------------------------------- | ------------------------------------------------------------------------- |
| **`workoutType`** | <code><a href="#workouttype">WorkoutType</a></code> | Optional workout type filter. If omitted, all workout types are returned. |
| **`startDate`**   | <code>string</code>                                 | Inclusive ISO 8601 start date (defaults to now - 1 day).                  |
| **`endDate`**     | <code>string</code>                                 | Exclusive ISO 8601 end date (defaults to now).                            |
| **`limit`**       | <code>number</code>                                 | Maximum number of workouts to return (defaults to 100).                   |
| **`ascending`**   | <code>boolean</code>                                | Return results sorted ascending by start date (defaults to false).        |


#### QuerySleepResult

| Prop                | Type                        |
| ------------------- | --------------------------- |
| **`sleepSessions`** | <code>SleepSession[]</code> |


#### SleepSession

| Prop             | Type                                                            | Description                                              |
| ---------------- | --------------------------------------------------------------- | -------------------------------------------------------- |
| **`title`**      | <code>string</code>                                             | Title/name of the sleep session (if available).          |
| **`duration`**   | <code>number</code>                                             | Duration of the sleep session in seconds.                |
| **`startDate`**  | <code>string</code>                                             | ISO 8601 start date of the sleep session.                |
| **`endDate`**    | <code>string</code>                                             | ISO 8601 end date of the sleep session.                  |
| **`stages`**     | <code>SleepStageRecord[]</code>                                 | Array of sleep stages during the session (if available). |
| **`sourceName`** | <code>string</code>                                             | Source name that recorded the sleep session.             |
| **`sourceId`**   | <code>string</code>                                             | Source bundle identifier.                                |
| **`metadata`**   | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Additional metadata (if available).                      |


#### SleepStageRecord

| Prop            | Type                                              | Description                              |
| --------------- | ------------------------------------------------- | ---------------------------------------- |
| **`stage`**     | <code><a href="#sleepstage">SleepStage</a></code> | The sleep stage type.                    |
| **`startDate`** | <code>string</code>                               | ISO 8601 start date of this sleep stage. |
| **`endDate`**   | <code>string</code>                               | ISO 8601 end date of this sleep stage.   |


#### QuerySleepOptions

| Prop            | Type                 | Description                                                        |
| --------------- | -------------------- | ------------------------------------------------------------------ |
| **`startDate`** | <code>string</code>  | Inclusive ISO 8601 start date (defaults to now - 1 day).           |
| **`endDate`**   | <code>string</code>  | Exclusive ISO 8601 end date (defaults to now).                     |
| **`limit`**     | <code>number</code>  | Maximum number of sleep sessions to return (defaults to 100).      |
| **`ascending`** | <code>boolean</code> | Return results sorted ascending by start date (defaults to false). |


#### QueryHydrationResult

| Prop                   | Type                           |
| ---------------------- | ------------------------------ |
| **`hydrationRecords`** | <code>HydrationRecord[]</code> |


#### HydrationRecord

| Prop             | Type                                                            | Description                                  |
| ---------------- | --------------------------------------------------------------- | -------------------------------------------- |
| **`volume`**     | <code>number</code>                                             | Volume of water consumed in liters.          |
| **`startDate`**  | <code>string</code>                                             | ISO 8601 start date of the hydration record. |
| **`endDate`**    | <code>string</code>                                             | ISO 8601 end date of the hydration record.   |
| **`sourceName`** | <code>string</code>                                             | Source name that recorded the hydration.     |
| **`sourceId`**   | <code>string</code>                                             | Source bundle identifier.                    |
| **`metadata`**   | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Additional metadata (if available).          |


#### QueryHydrationOptions

| Prop            | Type                 | Description                                                        |
| --------------- | -------------------- | ------------------------------------------------------------------ |
| **`startDate`** | <code>string</code>  | Inclusive ISO 8601 start date (defaults to now - 1 day).           |
| **`endDate`**   | <code>string</code>  | Exclusive ISO 8601 end date (defaults to now).                     |
| **`limit`**     | <code>number</code>  | Maximum number of hydration records to return (defaults to 100).   |
| **`ascending`** | <code>boolean</code> | Return results sorted ascending by start date (defaults to false). |


### Type Aliases


#### ReadAuthorizationType

Data types that can be requested for read authorization.
Includes 'workouts' for querying workout sessions via queryWorkouts().
Includes 'sleep' for querying sleep sessions via querySleep().
Includes 'hydration' for querying hydration records via queryHydration().

<code><a href="#healthdatatype">HealthDataType</a> | 'workouts' | 'sleep' | 'hydration'</code>


#### HealthDataType

<code>'steps' | 'distance' | 'calories' | 'weight' | 'height'</code>


#### HealthUnit

<code>'count' | 'meter' | 'kilocalorie' | 'bpm' | 'kilogram'</code>


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>


#### WorkoutType

<code>'running' | 'cycling' | 'walking' | 'swimming' | 'yoga' | 'strengthTraining' | 'hiking' | 'tennis' | 'basketball' | 'soccer' | 'americanFootball' | 'baseball' | 'crossTraining' | 'elliptical' | 'rowing' | 'stairClimbing' | 'traditionalStrengthTraining' | 'waterFitness' | 'waterPolo' | 'waterSports' | 'wrestling' | 'other'</code>


#### SleepStage

<code>'unknown' | 'awake' | 'sleeping' | 'outOfBed' | 'awakeInBed' | 'light' | 'deep' | 'rem'</code>

</docgen-api>

### Credits:

this plugin was inspired by the work of https://github.com/perfood/capacitor-google-fit for Android
