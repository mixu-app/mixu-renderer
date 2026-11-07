# mixu-renderer

用 Compose Canvas 画一张**会随阅读进度生长、但不会重新洗牌**的案情板。

难点不在画线，在三点：实体位置必须稳定（同一个角色在每次揭示后都待在同一处）、
动画必须是增量的（只动新出现的部分）、E-Ink 上必须能一次画完（没有持续重绘）。

Apache-2.0。任何有"剧情图谱"的产品都能直接复用。

## 设计约束

| 约束 | 原因 |
|---|---|
| 位置确定性 | 同一 seed + 同一 edition 下，实体坐标跨 reveal 不变；否则读者会失去空间记忆 |
| 增量揭示 | 新节点从已存在节点处生长，不触发全局 relayout |
| 无强制动画 | 尊重系统"减少动态效果"；E-Ink 模式默认离散快照 |
| 颜色不是唯一编码 | 形状 + 文字标签 + 线型同时编码类别，色盲与 E-Ink 可用 |
| 命中区域 ≥ 44dp | 触控可达性；E-Ink 设备常配笔，但手指仍是主输入 |

## 计划中的 API（示意）

```kotlin
@Composable
fun GraphCanvas(
    projection: Projection,          // 已由 mixu-spoiler 过滤
    layout: GraphLayout,             // 稳定坐标，跨 reveal 复用
    interaction: GraphInteraction,   // 点击/长按/拖拽
    policy: DisplayPolicy,           // REGULAR | E_INK | REDUCED_MOTION
    onEntitySelected: (EntityId) -> Unit,
    onEvidenceRequested: (EntityId) -> Unit,
)
```

渲染器**不做过滤**：它只接收已经裁剪好的 `Projection`。如果这里出现"隐藏但保留的节点"，
就是架构错误。

## 显示策略

| 行为 | REGULAR | E_INK | REDUCED_MOTION |
|---|---|---|---|
| 节点出现 | 淡入 + 位移 | 直接出现 | 直接出现 |
| 布局 | 平滑平移/缩放 | 固定布局、整页步进 | 固定布局 |
| 连线 | 渐进绘制 | 一次性绘制 | 一次性绘制 |
| 高亮 | 颜色 + 描边 | 描边 + 形状 | 描边 |
| 重绘 | 按需 | 仅交互结束或显式刷新 | 仅交互结束 |

## 无障碍

- 每个节点是语义节点，暴露角色、已揭示名称与一条状态摘要；Canvas 内的绘制不能成为
  唯一信息来源。
- 键盘/方向键可遍历节点，焦点顺序按空间的阅读顺序而非插入顺序。
- 文本缩放 200% 时布局退化到列表视图，而不是把标签画到重叠。

## 性能（待测量）

不写猜测的数字。M1 在真实中端机与至少一台墨水屏上测：300 实体图的首帧时间、单节点
揭示的重绘范围、拖拽帧率、E-Ink 一次刷新耗时。阈值由测量结果确定，写进本文件而不是
写进宣传语。

## 非目标

- 图谱模型与查询（→ [mixu-graph](https://github.com/mixu-app/mixu-graph)）。
- 过滤策略（→ [mixu-spoiler](https://github.com/mixu-app/mixu-spoiler)）。
- 3D、物理引擎、自由手绘布局：稳定与可预测优先于炫技。

## 质量门禁

```sh
just check
```

渲染回归以语义断言为主（节点数、可点击集合、焦点顺序），像素截图作为补充而不是唯一
证据——否则跨平台字体差异会让门禁假红。

## 许可证

[Apache-2.0](LICENSE)。名称与 Logo 不在授权范围内。
