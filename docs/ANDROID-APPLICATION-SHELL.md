# Native Android application shell

**Status:** UI navigation foundation implemented in source; not built or device-tested.

## Screens

The native Jetpack Compose shell provides four destinations:

- **Chat:** conversation entry point and explicit notice that no live model provider is connected.
- **Learn:** learning-cycle outline, research-provider status, approval boundary and scheduler status.
- **Memory:** memory/evidence policy status and a clear statement that saved memories are not yet read or displayed.
- **Profile:** Creator authentication, device identity, permissions and privacy status.

## Deliberate limits

This milestone is navigation and honest status presentation only. It does not implement sign-in, message sending, model routing, memory CRUD, voice capture, background scheduling, device actions or production backend connectivity. The UI must not imply that any of those features are active.

The screens use local Compose state for tab selection only. No user message, profile data or memory content is persisted or transmitted by this shell.

## Verification status

- Source changes committed to `feature/native-companion-navigation-shell`.
- No Gradle build, unit test, instrumentation test, CI run, APK installation or physical-device test was performed for this milestone.
- Later verification must include navigation state restoration, accessibility, small-screen layout, dark/light theme behaviour and regression checks against the domain modules.

## Next work

Connect the Creator session flow to an actual approved authentication provider, then implement a protected conversation pipeline and a usable memory manager. Keep provider credentials and production configuration out of source control.
