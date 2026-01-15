/**
 * Data types that can be requested for read authorization.
 * Includes 'workouts' for querying workout sessions via queryWorkouts().
 * Includes 'sleep' for querying sleep sessions via querySleep().
 * Includes 'hydration' for querying hydration records via queryHydration().
 */
export type ReadAuthorizationType = 'workouts' | 'sleep' | 'hydration';

export type WorkoutType =
  | 'running'
  | 'cycling'
  | 'walking'
  | 'swimming'
  | 'yoga'
  | 'strengthTraining'
  | 'hiking'
  | 'tennis'
  | 'basketball'
  | 'soccer'
  | 'americanFootball'
  | 'baseball'
  | 'crossTraining'
  | 'elliptical'
  | 'rowing'
  | 'stairClimbing'
  | 'traditionalStrengthTraining'
  | 'waterFitness'
  | 'waterPolo'
  | 'waterSports'
  | 'wrestling'
  | 'other';

export interface AuthorizationOptions {
  /**
   * Data types that should be readable after authorization.
   */
  read?: ReadAuthorizationType[];
}

export interface AuthorizationStatus {
  /** Data types that are authorized for reading */
  readAuthorized: ReadAuthorizationType[];
  /** Data types that are denied for reading */
  readDenied: ReadAuthorizationType[];
  /** Data types that are authorized for writing (always empty) */
  writeAuthorized: [];
  /** Data types that are denied for writing (always empty) */
  writeDenied: [];
}

export interface AvailabilityResult {
  available: boolean;
  /** Platform specific details (for debugging/diagnostics). */
  platform?: 'android' | 'web';
  reason?: string;
}

export type SleepStage = 'unknown' | 'awake' | 'sleeping' | 'outOfBed' | 'awakeInBed' | 'light' | 'deep' | 'rem';

export interface QueryWorkoutsOptions {
  /** Optional workout type filter. If omitted, all workout types are returned. */
  workoutType?: WorkoutType;
  /** Inclusive ISO 8601 start date (defaults to now - 1 day). */
  startDate?: string;
  /** Exclusive ISO 8601 end date (defaults to now). */
  endDate?: string;
  /** Maximum number of workouts to return (defaults to 100). */
  limit?: number;
  /** Return results sorted ascending by start date (defaults to false). */
  ascending?: boolean;
}

export interface Workout {
  /** The type of workout. */
  workoutType: WorkoutType;
  /** Duration of the workout in seconds. */
  duration: number;
  /** ISO 8601 start date of the workout. */
  startDate: string;
  /** ISO 8601 end date of the workout. */
  endDate: string;
  /** Source name that recorded the workout. */
  sourceName?: string;
  /** Source bundle identifier. */
  sourceId?: string;
  /** Additional metadata (if available). */
  metadata?: Record<string, string>;
}

export interface QueryWorkoutsResult {
  workouts: Workout[];
}

export interface QuerySleepOptions {
  /** Inclusive ISO 8601 start date (defaults to now - 1 day). */
  startDate?: string;
  /** Exclusive ISO 8601 end date (defaults to now). */
  endDate?: string;
  /** Maximum number of sleep sessions to return (defaults to 100). */
  limit?: number;
  /** Return results sorted ascending by start date (defaults to false). */
  ascending?: boolean;
}

export interface SleepStageRecord {
  /** The sleep stage type. */
  stage: SleepStage;
  /** ISO 8601 start date of this sleep stage. */
  startDate: string;
  /** ISO 8601 end date of this sleep stage. */
  endDate: string;
}

export interface SleepSession {
  /** Title/name of the sleep session (if available). */
  title?: string;
  /** Duration of the sleep session in seconds. */
  duration: number;
  /** ISO 8601 start date of the sleep session. */
  startDate: string;
  /** ISO 8601 end date of the sleep session. */
  endDate: string;
  /** Array of sleep stages during the session (if available). */
  stages?: SleepStageRecord[];
  /** Source name that recorded the sleep session. */
  sourceName?: string;
  /** Source bundle identifier. */
  sourceId?: string;
  /** Additional metadata (if available). */
  metadata?: Record<string, string>;
}

export interface QuerySleepResult {
  sleepSessions: SleepSession[];
}

export interface QueryHydrationOptions {
  /** Inclusive ISO 8601 start date (defaults to now - 1 day). */
  startDate?: string;
  /** Exclusive ISO 8601 end date (defaults to now). */
  endDate?: string;
  /** Maximum number of hydration records to return (defaults to 100). */
  limit?: number;
  /** Return results sorted ascending by start date (defaults to false). */
  ascending?: boolean;
}

export interface HydrationRecord {
  /** Volume of water consumed in liters. */
  volume: number;
  /** ISO 8601 start date of the hydration record. */
  startDate: string;
  /** ISO 8601 end date of the hydration record. */
  endDate: string;
  /** Source name that recorded the hydration. */
  sourceName?: string;
  /** Source bundle identifier. */
  sourceId?: string;
  /** Additional metadata (if available). */
  metadata?: Record<string, string>;
}

export interface QueryHydrationResult {
  hydrationRecords: HydrationRecord[];
}

export interface HealthPlugin {
  /** Returns whether the current platform supports the native health SDK. */
  isAvailable(): Promise<AvailabilityResult>;
  /** Requests read/write access to the provided data types. */
  requestAuthorization(options: AuthorizationOptions): Promise<AuthorizationStatus>;
  /** Checks authorization status for the provided data types without prompting the user. */
  checkAuthorization(options: AuthorizationOptions): Promise<AuthorizationStatus>;

  /**
   * Get the native Capacitor plugin version
   *
   * @returns {Promise<{ version: string }>} a Promise with version for this device
   * @throws An error if something went wrong
   */
  getPluginVersion(): Promise<{ version: string }>;

  /**
   * Opens the Health Connect settings screen.
   *
   * Use this to direct users to manage their Health Connect permissions
   * or to install Health Connect if not available.
   *
   * @throws An error if Health Connect settings cannot be opened
   */
  openHealthConnectSettings(): Promise<void>;

  /**
   * Shows the app's privacy policy for Health Connect.
   *
   * This displays the same privacy policy screen that Health Connect shows
   * when the user taps "Privacy policy" in the permissions dialog.
   *
   * The privacy policy URL can be configured by adding a string resource
   * named "health_connect_privacy_policy_url" in your app's strings.xml,
   * or by placing an HTML file at www/privacypolicy.html in your assets.
   *
   * @throws An error if the privacy policy cannot be displayed
   */
  showPrivacyPolicy(): Promise<void>;

  /**
   * Queries workout sessions from the native health store on Android (Health Connect).
   *
   * @param options Query options including optional workout type filter, date range, limit, and sort order
   * @returns A promise that resolves with the workout sessions
   * @throws An error if something went wrong
   */
  queryWorkouts(options: QueryWorkoutsOptions): Promise<QueryWorkoutsResult>;

  /**
   * Queries sleep sessions from the native health store on Android (Health Connect).
   *
   * @param options Query options including date range, limit, and sort order
   * @returns A promise that resolves with the sleep sessions
   * @throws An error if something went wrong
   */
  querySleep(options: QuerySleepOptions): Promise<QuerySleepResult>;

  /**
   * Queries hydration records from the native health store on Android (Health Connect).
   *
   * @param options Query options including date range, limit, and sort order
   * @returns A promise that resolves with the hydration records
   * @throws An error if something went wrong
   */
  queryHydration(options: QueryHydrationOptions): Promise<QueryHydrationResult>;
}
