# MuhaShist

- Author SpidiBoost, public MIT sources at SpidiBoostMods/MuhaShist.
- Fabric 1.21.4, Java 21; no licensing, registration, obfuscation or access controls.
- JAR muhashist-1.21.4-v<version>.jar; Mod Menu spidiboost.MuhaShist, matching version.
- New application and test code has no source comments. Preserve upstream Gradle wrapper notices.
- Keep README short, in Russian, focused on player commands.
- Never modify the user's Prism instance, live game, recording or other projects.
- Verify at least 100000 records, native HUD/drag/config/commands, updater transaction, and Windows/macOS/Linux CI before release.
- Preserve individual repeated bans. Manual unban excludes one corresponding ban; ReallyWorld neither adds points nor changes its reason weight.
- Exact decimal weights from ms.cfg. Defaults: cheats/team cheats 0.7, inspection/storage/mod reasons 1.0. Only the requested curator and dates on/after the cutoff.
- Capture raw server messages before chat-panel routing. Confirm each active ban through a sequential owned checkban reply; exclude silent bans. Inactive bans need no checkban.
- Config comments are player-facing sections. Preserve custom reason lines and comments when saving GUI or clear settings.
- Use the current instance's Fabric config directory for ms.cfg and ms.txt.
- Publish source, canonical JAR and verified manifest on GitHub. Never publish live logs, configurations, accounts or launcher arguments.
