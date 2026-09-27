import type { CapacitorConfig } from '@capacitor/cli';

/** The Android app wraps the same web build (`dist/`) in a native shell. */
const config: CapacitorConfig = {
  appId: 'com.mrbeltz.dailyframe',
  appName: 'Daily Frame',
  webDir: 'dist',
  backgroundColor: '#0b0420',
  android: {
    backgroundColor: '#0b0420',
  },
};

export default config;
