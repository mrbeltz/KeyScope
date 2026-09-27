import { Capacitor } from '@capacitor/core';

/** True inside the Android app, false in a browser or an installed PWA. */
export const isNative = Capacitor.isNativePlatform();
