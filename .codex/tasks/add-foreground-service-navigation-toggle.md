# Task: Add navigation foreground service + toggle

## Goal
Introduce a foreground service that keeps BLE + location updates alive during active navigation, and add a user-facing toggle in the UI to start/stop it.

## Context
We need reliable background BLE + location updates during navigation. Android requires a foreground service (with persistent notification) for this to be dependable. The user should be able to enable/disable navigation mode explicitly.

## Requirements
- Add a foreground service that:
  - Starts when navigation mode is enabled.
  - Requests location updates while active.
  - Maintains BLE connection or reconnect loop while active.
  - Shows a persistent notification with a clear title (e.g., "Navigation active").
  - Stops cleanly when navigation mode is disabled.
- Add a navigation toggle in the main UI:
  - Enables/disables navigation mode.
  - Persists state across app restarts.
  - Reflects current running status (service running vs. not).
- Ensure service start/stop is lifecycle-safe and works with the app in background/locked screen.

## Acceptance Criteria
- User can enable navigation mode via toggle and see a persistent notification.
- When the toggle is off, the service is not running and notification is removed.
- Service handles app going to background/locked screen without crashing.
- Navigation mode state persists after app restart.
- No duplicate service instances are started.

## Suggested Implementation Steps
1. Add a `NavigationForegroundService`:
   - Implement as a `Service` + `startForeground()` with a notification channel.
   - Provide `start()`/`stop()` helper methods.
2. Store navigation mode state in `DataStore` or `SharedPreferences`.
3. Add UI toggle (settings or main screen):
   - Bind to stored state.
   - Start/stop service on toggle changes.
4. Wire BLE + location update logic to the service lifecycle.
5. Add logging for start/stop and BLE/location events.

## Notes
- Use `FOREGROUND_SERVICE` permission + `POST_NOTIFICATIONS` (Android 13+) if required.
- Keep the service in the smallest scope needed; avoid waking if toggle is off.
