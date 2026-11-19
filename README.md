# River Runner EE

River Runner EE（EE 版）是 Android 横屏离线德州扑克训练器。你可以和电脑对局，用牌局记录回看自己的决策，再把实际遇到的局面拿来练习。

**[下载正式版 APK](https://github.com/Richardc7/river-runner/releases/latest)** · [完整功能改动](https://github.com/Richardc7/river-runner/blob/exploit-edition/docs/EE_CHANGES.md) · [版本更新日志](https://github.com/Richardc7/river-runner/blob/exploit-edition/EXPLOIT_CHANGELOG.md) · [EE 版源码](https://github.com/Richardc7/river-runner/tree/exploit-edition) · Android 8.0+

[![EE build](https://github.com/Richardc7/river-runner/actions/workflows/build.yml/badge.svg?branch=exploit-edition)](https://github.com/Richardc7/river-runner/actions/workflows/build.yml?query=branch%3Aexploit-edition)

## 项目来源

原作由 **[shunzhe1121](https://github.com/shunzhe1121)** 开发。EE 版由 **[Richardc7](https://github.com/Richardc7)** 在 [原 River Runner 仓库](https://github.com/shunzhe1121/river-runner)的 [`36ada64` 提交](https://github.com/shunzhe1121/river-runner/commit/36ada64827fd9f3a379b2bc737b593c65746eeb1)基础上维护。原版提供了离线对局、筹码局与锦标赛、横屏牌桌、对手风格、牌局记录、回放、存档和新手引导。EE 版保留原作署名与 [MIT 许可](LICENSE)，Barlow Condensed 字体继续沿用 [SIL Open Font License](licenses/)。

## EE 版改了什么

| 模块 | 相对于原版的改动 |
| --- | --- |
| **开局、对手与私人局** | 扩展到最多 9 人桌。逐座位设置对手风格和初始筹码，中文对局使用中文姓名，可选姓名是否随打法风格变化。筹码局可配置 2 BB 枪口位活抓盲、抽水比例和封顶、见翻牌才抽水、自动重买及手间补码。 |
| **对手策略** | 保留原版基准 AI，另可开启动态调整。对手画像按当局公开行动的实际机会计数，样本少时沿用基准。加入按组合加权的范围估计，以及适用河牌局面的模型 EV。 |
| **牌桌与外观** | 固定显示底池和最近行动，整理右侧行动记录与下注控件。牌桌、牌面、牌背可分别搭配经典、赛博朋克、美式西部与塔罗主题。提供字号、高对比度、音效及震动设置。 |
| **牌局回放与盲测** | 逐步浏览牌局，看所选行动之前的位置、有效筹码、牌面、底池、跟注门槛与下注尺度。双轨分析比较基准与调整策略。符合条件的记录可直接进入盲测，先按当时可见的信息作答，再看实战与参考。 |
| **训练和外部策略** | 加入河牌专项题、错题间隔复习、弱点报告、13×13 翻前范围教练和河牌情景对比。支持把符合条件的单挑河牌场景交给电脑端 TexasSolver，再按原决策导回结果。 |
| **记录、统计与结算** | 按牌号、位置、日期或底牌搜索，按结果、偏离、收藏等筛选。标示自动弃牌和已复盘，支持导出牌谱和清理未收藏记录。统计显示分母与累计盈亏趋势，样本不足时暂不显示对应比率。结算区分提前退出与正式夺冠，筹码局盈亏计入重买和补码。 |
| **指南与数据** | 中英双语新手指南覆盖新增入口，术语可搜索。扑克速算包括 4/2 法则、底池赔率、纯诈唬门槛、跟注 EV、有效筹码与 SPR。可用 ZIP 备份和恢复存档，也可单独重置当前存档的训练进度。 |

内置启发式和动态调整策略用于训练。导入的外部策略只匹配对应决策，频率取决于求解时使用的范围和下注树。[完整差异和适用条件](https://github.com/Richardc7/river-runner/blob/exploit-edition/docs/EE_CHANGES.md)

## 安装与更新

- 已安装本仓库签名的 **v0.10 正式版**，可以直接覆盖安装 EE 版，保留应用数据。
- 曾安装本仓库的同包名**调试版**，请先在「设置 → 存档备份与恢复」导出 ZIP，再卸载调试版、安装正式版并恢复。
- 原作 App 和 EE 版可并存，各自使用独立的 Android 存储空间。EE 版可读取兼容的历史牌谱格式，原作的应用数据需另行保存。

发布页附有 APK 的 SHA-256、签名证书指纹和源码提交。将附件下载到同一目录后可校验：

```sh
shasum -a 256 -c SHA256SUMS.txt
apksigner verify --print-certs river-runner-ee-v*.apk
```

将第二条命令的证书 SHA-256 与 `APK_CERTIFICATE.txt` 比较。正式版升级沿用同一个 Android 包名和证书。

## Source and build

**River Runner EE** is an offline Android Texas Hold’em trainer based on [shunzhe1121’s River Runner](https://github.com/shunzhe1121/river-runner) at [`36ada64`](https://github.com/shunzhe1121/river-runner/commit/36ada64827fd9f3a379b2bc737b593c65746eeb1). Richardc7’s fork adds nine-player tables, configurable cash rules and opponents, action-by-action coaching, blind replay, targeted drills, an offline solver bridge, searchable hand history, backup and restore, and a bilingual guide with quick poker maths. The original copyright and MIT license are retained. [EE source branch](https://github.com/Richardc7/river-runner/tree/exploit-edition)

```sh
git switch exploit-edition
./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug
```

The Kotlin JVM `engine/` contains the poker rules, opponent models and replay. The Jetpack Compose `app/` contains the Android interface. For release signing and storage conventions, see [Build and migration](https://github.com/Richardc7/river-runner/blob/exploit-edition/docs/BUILD_AND_MIGRATION.md).
