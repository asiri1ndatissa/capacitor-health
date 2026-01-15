import { WebPlugin } from '@capacitor/core';

import type {
  AuthorizationOptions,
  AuthorizationStatus,
  AvailabilityResult,
  HealthPlugin,
  QueryHydrationOptions,
  QueryHydrationResult,
  QuerySleepOptions,
  QuerySleepResult,
  QueryWorkoutsOptions,
  QueryWorkoutsResult,
} from './definitions';

export class HealthWeb extends WebPlugin implements HealthPlugin {
  async isAvailable(): Promise<AvailabilityResult> {
    return {
      available: false,
      platform: 'web',
      reason: 'Native health APIs are not accessible in a browser environment.',
    };
  }

  async requestAuthorization(_options: AuthorizationOptions): Promise<AuthorizationStatus> {
    throw this.unimplemented('Health permissions are only available on native platforms.');
  }

  async checkAuthorization(_options: AuthorizationOptions): Promise<AuthorizationStatus> {
    throw this.unimplemented('Health permissions are only available on native platforms.');
  }

  async getPluginVersion(): Promise<{ version: string }> {
    return { version: 'web' };
  }

  async openHealthConnectSettings(): Promise<void> {
    // No-op on web - Health Connect is Android only
  }

  async showPrivacyPolicy(): Promise<void> {
    // No-op on web - Health Connect privacy policy is Android only
  }

  async queryWorkouts(_options: QueryWorkoutsOptions): Promise<QueryWorkoutsResult> {
    throw this.unimplemented('Querying workouts is only available on native platforms.');
  }

  async querySleep(_options: QuerySleepOptions): Promise<QuerySleepResult> {
    throw this.unimplemented('Querying sleep sessions is only available on native platforms.');
  }

  async queryHydration(_options: QueryHydrationOptions): Promise<QueryHydrationResult> {
    throw this.unimplemented('Querying hydration records is only available on native platforms.');
  }
}
