# ADR-001: Cholobotar recording and avatar pipeline

**Status:** Accepted (v0.3)

## Context
Creator phone-e kaj korbe, laptop chhara. Video-te shudhu stage (background + avatar) thakbe, prompter/button na. Free tooling only (GitHub Actions build). Build ekhane verify kora jay na, tai simple, kom-dependency design dorkar.

## Decision
- Single Gradle module, Kotlin + Jetpack Compose, no native code, no external SDK.
- One scene model (StudioViewModel). Compose UI ar video recorder duita-i oi model theke porhe (avatarLayers(), bounce, background).
- Recorder: SceneDrawer (Android Canvas) -> MediaCodec input Surface (H.264). Mic PCM -> MediaCodec AAC. Duita track-i ekta monotonic clock (baseUs) theke timestamp pay, tai A/V sync thake. MediaMuxer MediaStore-e (Movies/Cholobotar) likhe.
- Avatar = same-size PNG layer stack. Mouth level = mic volume thresholds (4 level), blink = timer.
- Frame-relative coordinates: avatar position/size frame-er fraction, tai UI ar video eki jaygay dekhay.

## Options considered
| Option | Complexity | Result |
|---|---|---|
| MediaProjection screen record | Low | UI chrome video-te ashe, crop kothin. Rejected |
| Canvas -> MediaCodec (chosen) | Medium | Clean stage-only video, resolution full control |
| OpenGL ES renderer | High | Best perf, kintu verify korte pari na. Later |

## Trade-offs / consequences
- Video background recording-e shudhu prothom frame ashe.
- Sound effect video-te mix hoy na (alada PCM mixer lagbe).
- 1080p60 kichhu phone-e fail korte pare. App error dekhay.

## Revisit when
Tracking (MediaPipe), voice changer (Oboe), cloud sync (Firebase/Drive), multi-viseme shape, GL renderer.
