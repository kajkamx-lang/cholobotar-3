# Test plan (v0.3)

| Area | Type | What | Where |
|---|---|---|---|
| Script marker parser | Unit | markers hide hoy, word index thik | LogicTest |
| Video size / frame fit | Unit | even dimension, aspect thik | LogicTest |
| Voice gate | Unit | loud = open, silence + hold = close | LogicTest |
| Recorder, layers, UI | Manual on device | Niche checklist | below |

CI: `gradle testDebugUnitTest` APK build-er por chole, fail korleo APK ashe.

## Manual checklist
1. Mic on, kotha bolo: mouth level change, bounce hoy. Bounce off korle bondho.
2. 3 ta slot PNG dao: body + mouth closed + mouth wide. Preview thik thake.
3. Extra layer add, hide, reorder.
4. Gallery photo background add, Script-e [BG:PHOTO1] diye auto change.
5. REC 360p 10 sec, tarpor 1080p 10 sec. Gallery-te video, audio sync, avatar position.
6. Record cholar somoy home press: record auto stop.
