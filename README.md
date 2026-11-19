# River Runner ♠

<p align="center">
  <img src="docs/banner.png" alt="River Runner — offline Texas Hold'em practice" width="100%">
</p>

<p align="center">
  <b>An offline Texas Hold'em drill partner for Android.</b><br>
  <sub>Exploit Edition · No account · no ads · no in-app purchases · no network permission</sub>
</p>

<p align="center">
  <a href="https://github.com/Richardc7/river-runner/releases/latest"><b>Download Exploit Edition APK / 下载 APK</b></a>
  <br>
  <sub><a href="https://github.com/Richardc7/river-runner/releases">Releases / 发布记录</a> · <a href="EXPLOIT_CHANGELOG.md">更新日志 / changelog</a> · Android 8.0+</sub>
</p>

<p align="center">
  <img alt="build" src="https://github.com/Richardc7/river-runner/actions/workflows/build.yml/badge.svg?branch=exploit-edition">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-1E7048?style=flat-square&labelColor=0C1310&logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&labelColor=0C1310&logo=kotlin&logoColor=white">
  <img alt="No permissions" src="https://img.shields.io/badge/permissions-none-4FC88A?style=flat-square&labelColor=0C1310">
  <img alt="licence-MIT" src="https://img.shields.io/badge/license-MIT-E6B54F?style=flat-square&labelColor=0C1310">
</p>

## What it is

Most poker trainers are subscriptions. Most free poker apps are online, ad-supported, or quietly steering you toward a deposit.

River Runner is neither. It is a study tool that runs entirely on your phone: you play hands against bots, and while you decide it shows the numbers a cash-game player actually uses — **pot odds, required equity, SPR** — without showing your own equity, because working that out is the exercise. After the hand you step back through it with **every hole card face up**, see what the GTO approximation would have done on each street, and whether you agreed with it.

No real money, no chips to buy, no login, nothing that phones home.

## 这是什么

市面上的德州训练软件要么按订阅收费，要么是带广告、引导充值的在线房间。

River Runner 两者都不是：一个完全跑在手机本地的练习工具。你和 NPC 对局，决策时它实时给出真实牌局里真正用得到的数字——**底池赔率、所需胜率、SPR**——但刻意不显示你自己的胜率，因为那部分正是要你自己算的。每手结束后可以逐街复盘，**所有底牌全部翻开**，每条街给出 GTO 建议并标记你的偏差。

没有真钱、没有充值、不用登录、不联网。

## Practice mode — the numbers, not the answer

<p align="center">
  <img src="docs/shots/shot-table.png" alt="Practice mode: pot odds, required equity and SPR beside the table" width="100%">
</p>

<p align="center"><sub>Jade opens to 2.5 BB, you are in the small blind with Q8o. Pot odds 1:2, required equity 33 %, SPR 24.9. Your own equity stays hidden — that is the part you are meant to work out.</sub></p>

## Replay — every card face up, with a verdict

<p align="center">
  <img src="docs/shots/shot-replay.png" alt="Replay: all hole cards shown, GTO suggestion with frequencies and deviation flag" width="100%">
</p>

<p align="center"><sub>Board K♠7♦2♣, you hold Q♥Q♠ in the small blind. The solver approximation checks 61 % and raises 39 % here, and your check is marked ✓. Both sides' equity is put on the table for that street.</sub></p>

## A table that behaves like one

<p align="center">
  <img src="docs/shots/shot-home.png" alt="Lobby with save slots and career stats" width="31%">
  <img src="docs/shots/shot-showdown.png" alt="Showdown with the winning hand spelled out" width="31%">
  <img src="docs/shots/shot-achievements.png" alt="35 achievement chips across six categories" width="31%">
</p>

<p align="center"><sub>Lobby with independent save slots · showdown with the winning hand spelled out · 35 achievement chips across six categories</sub></p>

## Features / 功能

| Area | What you get |
|---|---|
| **Tables / 牌桌** | Heads-up through 8-handed, landscape / 单挑到 8 人桌，横屏 |
| **Bots / NPC** | GTO table, or a mixed table of TAG / LAG / calling station / nit / maniac / GTO 桌或混合风格桌 |
| **Practice HUD / 练习面板** | Pot odds, required equity, SPR as you act — never your own equity / 实时底池赔率、所需胜率、SPR（不显示你的胜率） |
| **Replay / 复盘** | All hole cards, per-street equity, GTO suggestion with a deviation flag / 显示所有底牌、每条街胜率、GTO 建议与偏差标记 |
| **History / 记录** | Last 1000 hands kept automatically; star a hand to keep it forever / 自动保留最近 1000 手，收藏永久保存 |
| **Stats / 统计** | VPIP, PFR, 3-bet, WTSD, W$SD, BB/100, by position / 入池率、加注率、摊牌率、百手盈亏 |
| **Saves / 存档** | 6 slots, each with its own stats, history and achievements / 6 个独立存档 |
| **Achievements / 成就** | 35 chips across six categories / 6 类共 35 个成就 |
| **Language / 语言** | 中文 / English, switchable in-app |

<details>
<summary>Table feel, guide, tournaments and everything else / 牌桌手感、新手指南、锦标赛等</summary>

| Area | What you get |
|---|---|
| **Modes / 模式** | Cash (standings by chips) and single-table tournament with rising blinds and ICM payouts / 筹码局 + 单桌锦标赛 |
| **Stacks / 筹码** | Starting stack set in big blinds / 以大盲数设置起始筹码 |
| **Table feel / 牌桌体验** | Dealing and card-flip animations, chips sliding to the pot and to the winner, pulsing turn indicator, vibration on your turn, live action log; every hand waits for **Next hand** / 发牌翻牌动画、筹码动画、行动提示与震动、行动记录 |
| **Beginner guide / 新手指南** | Rules, hand rankings, positions, glossary, tips — also reachable from the table via **?** / 玩法、牌型、位置、术语、新手建议 |
| **Sound / 音效** | Synthesised game sounds, toggle on the table / 合成音效，牌桌上可开关 |

</details>

## Install / 安装

Download the signed Exploit Edition APK from the [GitHub release](https://github.com/Richardc7/river-runner/releases/latest). Each release includes its source commit, APK checksum and signing certificate fingerprint.

以前安装过同包名的调试版本时，请先在「设置 → 存档备份与恢复」导出需要保留的存档。正式版使用独立的发布签名，Android 会拒绝跨签名覆盖安装。安装正式版后，可以从备份恢复存档。

If you installed a debug build of Exploit Edition, export your saves from Settings → Save backup and restore before switching. Android requires the signing certificate to match for an in-place update. You can restore the backup in the signed release.

## How the bots decide / NPC 如何决策

True GTO solutions only exist for heads-up play; no one can solve 5–8 handed games exactly. The bots use an approximation built from published solver output:

- **Preflop** — position-based raise-first-in, 3-bet, 4-bet and calling ranges (e.g. BTN ≈ 44 %, CO ≈ 28 %, UTG 6-max ≈ 17 % opens) on a 169-hand ordering that blends simulated equity with playability. Borderline hands are played as mixed strategies.
- **Short stacks** — Nash-style push/fold widths by stack depth and players left to act; calls against all-ins compare equity vs. the shover's range to pot odds — or to **ICM** required equity in tournaments.
- **Postflop** — Monte Carlo equity against each opponent's range (narrowed by their actions), board-texture-based bet sizing, value/bluff frequencies, pot odds with equity realisation, and minimum-defence guards.

Styles (TAG, LAG, station, nit, maniac) shift range widths, aggression, bluffing and call-down thresholds, so a mixed table has exploitable opponents.

真正的 GTO 只对单挑存在可靠解，多人局使用近似解：翻前按位置的范围表 + 混合策略；短筹码使用推/弃与 ICM；翻后根据对手范围的蒙特卡洛胜率、牌面结构、底池赔率做混合决策。

## Under the hood

- **`engine/`** — a plain Kotlin JVM module with no Android dependency: hand evaluator, NLHE state machine with side pots and all-ins, ICM, Monte Carlo equity, bot ranges, hand records and replay, stats. `./gradlew :engine:test` runs it all on a laptop.
- **`app/`** — Jetpack Compose, landscape, one hand-written design system (see [DESIGN.md](DESIGN.md)).
- **The screenshots above are generated, not taken.** Robolectric + Roborazzi render them on the JVM in CI (`./gradlew :app:recordRoborazziDebug`) and publish to the [`screenshots`](https://github.com/shunzhe1121/river-runner/tree/screenshots) branch — deterministic, no emulator, no device. The copies in `docs/shots/` are pulled from that branch.

## Build / 构建

```bash
./gradlew :engine:test          # rules, side pots, ICM, replay
./gradlew :app:assembleRelease  # APK in app/build/outputs/apk/release/
```

Barlow Condensed is bundled under the SIL Open Font License (`licenses/`).

## Signing and verification / 签名与校验

Production APKs use the Exploit Edition release key. Signing credentials are stored outside the Git repository. CI publishes debug APKs for testing.

```bash
shasum -a 256 -c SHA256SUMS.txt
apksigner verify --print-certs river-runner-exploit-v*.apk
```

Compare the printed certificate SHA-256 fingerprint with `APK_CERTIFICATE.txt` from the same release. Future signed updates use the same package ID and certificate with an increased Android version code.

For maintainers, `EXPLOIT_VERSION` sets the tag and app version, and `EXPLOIT_VERSION_CODE` sets the monotonic Android version code. Run `:app:assembleRelease` with the private `RR_KEYSTORE_PATH`, `RR_STORE_PASSWORD`, `RR_KEY_ALIAS`, and `RR_KEY_PASSWORD` environment variables.

## If this is useful to you

There is no subscription, no ad and no telemetry in River Runner — the only budget it runs on is attention, and on GitHub stars are the signal that decides whether the next person searching for an offline poker trainer ever finds it.

- **⭐ Star the repo** if you want more people to find a free trainer. It costs one click and it is the whole marketing department.
- **🐞 Open an issue** if something is wrong or missing. Reports get read and fixed; that is also how you tell whether a project is alive.
- **🍴 Fork it** if you are building something similar. The engine is MIT — take it, the maths is the hard part.

如果它对你有点用，一个 star 就是最便宜的支持方式。项目没有订阅、没有广告、也没有埋点。

## License

MIT
