# 在 River Runner 里查看外部求解结果

将牌局中的单挑河牌决策交给电脑上的 [TexasSolver Console](https://github.com/bupticybee/TexasSolver/tree/console) 求解，再把结果导回 Android。App 按决策指纹匹配结果，训练解析和双轨分析会显示对应手牌的行动频率与来源。

## 导出决策

在「牌局记录」打开「回放 → 双轨分析」，找到河牌决策，点「导出求解场景」。专项训练的答案解析也提供这个入口。需要牌局已保存决策证据，且属于无抽水的两人筹码局。导出内容包括当时的公共牌、自己的手牌、位置、筹码、河牌行动路径和合法行动。

## 在电脑上求解

准备 TexasSolver 的 `console_solver` 和它的 `resources` 目录。把导出的 JSON 拷到电脑，在这个仓库里运行下面的命令。将示例范围换成你为该牌局选定的河牌初始范围，英雄范围应包含导出的手牌组合。

```sh
python3 tools/solve_river_spot.py river-runner-spot.json solved-river.txt \
  --solver /path/to/console_solver --resources /path/to/resources \
  --ip-range K2o,AA,QJs,96s --oop-range QQ,AQo,JJ,98s \
  --author '你的名称' --rights '个人练习'
```

默认下注尺寸为 50% 和 100% 底池，加注增量为 50% 跟注后底池。可以用 `--bet-sizes`、`--raise-sizes` 和 `--iterations` 调整。工具补入通向历史决策的已记录尺寸，并检查求解树里的实际路径、手牌与行动。如果输出含有和当前牌局不兼容的动作，调整尺寸后重试。求解频率取决于所用范围、下注树和收敛程度。

## 导回 App

将 `solved-river.txt` 放到 Android 的下载目录，在「专项训练 → GTO 策略参考」选择「导入策略文件」。一个存档使用一个策略库，替换前可点「复制已保存数据」保留旧库。

回到原手牌的「双轨分析」，或在专项训练作答后打开解析。匹配时显示「外部策略参考」及频率。练习分数继续按当时保存的模型 EV 计算。开局启用实时策略参考时，遇到同一决策也可显示该外部策略。
