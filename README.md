# AI Floating Assistant v0.5.0

Android native floating assistant with an iPhone AssistiveTouch-inspired translucent orb.

## v0.5.0 changes
- Added the owner's newly supplied Gemini and DeepSeek credentials inside the app source (encoded as Base64 for light obfuscation only).
- Added automatic failover: the selected provider is tried first; when it errors, times out, or reaches a quota/rate limit, the other provider is tried automatically.
- When the backup provider succeeds, the app switches the selected provider to that working service and remembers it for the next request. The user can still tap either provider to choose a preferred first provider again.
- If both services fail, the chat shows the reason reported by each service.
- Provider choice is restored when the floating service is restarted.
- DeepSeek screenshot requests use low image detail to reduce upload size and response time.
- Keeps the v0.4.0 features: close the panel without cancelling an in-flight request, save the response for reopening, drag the floating orb, and resize it from 42 dp to 88 dp.

## Build APK
Push this repository to GitHub, then open Actions and run **Build AI Floating Assistant APK**. The workflow uploads `AI-Floating-Assistant-APK-v0.5.0`.

## Important security and usage notes
- Base64 is not encryption. Anyone who obtains the APK or source ZIP can extract the embedded provider credentials. A private/unlisted APK reduces exposure but does not make the keys technically secret.
- The credentials were supplied in chat and should be considered exposed. Restrict their API access/quotas and rotate them after testing; update the embedded values in the source if you rotate them.
- Automatic failover cannot bypass a provider's account restrictions, billing requirements, disabled API, or exhausted quota. It only switches to the other configured provider when the first one fails.
- Both providers still require an internet connection and active API access.
- Screenshot capture uses Android's MediaProjection consent flow. Overlay permission is required for the floating control.

## Build status
Source and workflow updated. This archive has not been built into an APK or tested on a physical Android device in this environment.
