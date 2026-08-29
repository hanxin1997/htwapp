# 手写本 — 实施计划

电纸书手写/绘图 App。Android 原生，Kotlin，纯软件渲染，不依赖任何厂商私有 SDK。

- 包名 `com.htdzs.notepad`，应用名「手写本」
- minSdk 21 / targetSdk 34（小米电纸书 Node 是 API 30）
- 编译走 GitHub Actions（本地无 JDK/Gradle/Android SDK）

## 界面

顶部三个 88dp 大按钮，下面整块画布。

| 按钮 | 行为 |
|------|------|
| 钢笔·中 | 弹窗选笔型（钢笔/铅笔/马克笔/荧光笔/橡皮）+ 粗细（细/中/粗），点一下即选中并关闭 |
| 清空 | 擦掉全部笔画 + 笔恢复默认。**不弹确认**，给小孩用，一按就干净 |
| 撤销 | 退回上一笔 |

按钮无水波纹、无动画，按下反色 —— 电纸书刷新慢，必须有明确反馈。

## 渲染

**一条渲染路径。** `StrokeRenderer.render(canvas, stroke)` 同时服务三处：实时预览、抬笔后提交到位图、撤销后整页重放。共用一个函数，不会出现"写的时候和最终效果不一样"。

**位图 + 脏矩形。** 已完成笔画烘进 view 大小的 Bitmap，`invalidate(l,t,r,b)` 只重绘新线段。整个 application 关掉硬件加速：电纸书上硬件加速会让局部失效退化成整屏重绘。

**撤销 = 弹出最后一笔 + 整页重放。** O(n)，几百笔几十毫秒，比屏幕刷新本身还快。不做增量快照。

**橡皮直接画白色**，不用 CLEAR 混合模式。底图本来就是白的，视觉一致，省掉一整套 xfermode 分支。

## 五种笔怎么区分

| 笔型 | 实现 |
|------|------|
| 钢笔 | 逐段变宽的圆头线。有压感用压感，设备不报压感（恒 1.0）就用笔速 —— 快则细。指数平滑避免抖动 |
| 铅笔 | 64×64 程序化噪声图当 BitmapShader 平铺，固定随机种子保证重放一致 |
| 马克笔 | 半透明黑，整笔一个 Path 一次画完（避免自身重叠加深） |
| 荧光笔 | 浅灰 + DARKEN 混合，压在字上只会变深不会盖白 |
| 橡皮 | 不透明白色宽笔 |

## 输入

- 单指针跟踪 pointerId：笔抬起而手掌还压着时，线条不会跳到手掌位置
- 2 秒内出现过手写笔事件就忽略手指
- 吃 `getHistoricalX/Y`，笔迹才够顺

## 文件

```
.github/workflows/build.yml          JDK17 + Gradle 8.7 → assembleDebug → 上传 APK
settings.gradle.kts / build.gradle.kts / gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/htdzs/notepad/
  MainActivity.kt
  model/{PenType,PenConfig,Stroke}.kt
  ink/{PathBuilder,InkWidth,PalmRejector,Grain}.kt
  render/{StrokeRenderer,FountainPen,SolidPen}.kt
  view/DrawingView.kt
  ui/PenPickerDialog.kt
app/src/main/res/{layout,values,color,drawable}/
```

仓库不放 `gradle-wrapper.jar`（二进制没法手写），CI 里用 `gradle/actions/setup-gradle` 指定 8.7 直接跑。

## 风险

- **本地编译不了**，正确性只能靠 CI 验证，第一次可能要修版本兼容
- AGP 8.5.2 / Gradle 8.7 / Kotlin 1.9.24 / JDK 17 凭经验定，没联网查证
- `invalidate(l,t,r,b)` 在 API 28+ 标了 deprecated，但软件渲染下仍然生效，这正是要的效果
- 不做：保存/导出 PNG、多页、缩放平移
