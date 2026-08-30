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
| 清空 | 擦掉全部笔画，**选的笔不变**。不弹确认，一按就干净 |
| 撤销 | 退回上一笔 |

按钮无水波纹、无动画，按下反色 —— 电纸书刷新慢，必须有明确反馈。

## 渲染

**一条渲染路径。** `StrokeRenderer.render(canvas, stroke, fromIndex)` 同时服务三处：实时预览、把新增线段烘进位图、撤销后整页重放。共用一个函数，不会出现"写的时候和最终效果不一样"。`fromIndex` = 已经画到第几个点，默认 0 = 整笔。

**位图 + 脏矩形。** 已完成笔画烘进 view 大小的 Bitmap，`invalidate(l,t,r,b)` 只重绘新线段。整个 application 关掉硬件加速：电纸书上硬件加速会让局部失效退化成整屏重绘。

**每帧开销必须与笔画长度无关。** 每帧重画整笔是单帧 O(N)、整笔 O(N²)，写得越长越卡。MOVE 时只把新增的那一小段烘进页面位图，`onDraw` 退化成纯贴图。

分段画和整笔画的结果必须一致，按笔型分两类：

| 笔型 | 分段安全 | 原因 |
|------|---------|------|
| 钢笔 / 橡皮 | 是 | 不透明，重叠画还是同一个颜色 |
| 荧光笔 | 是 | 不透明色下 DARKEN 就是取 min。抗锯齿边缘那一圈重画会再暗一点，但收敛到笔自己的灰，代价是接缝 1 像素边缘略硬 |
| 马克笔 / 铅笔 | 否 | 半透明 / 颗粒 alpha，重叠会叠深成一串黑珠子 |

不安全的两种保持整笔实时渲染 —— 它俩是宽笔，用来涂抹标记，不写长篇。

**宽度在落笔时冻结。** 逐点宽度在 `Stroke.add()` 时算好存下，不在渲染时算。用压感还是笔速由 `PressureProbe` 在落笔前定死：否则一笔写到中途压感一变，前面所有点的宽度会追溯改变，而已经烘进位图的部分改不了，接缝就露出来。代价是压感设备上第一笔用笔速估宽度，第二笔起用压感。

**撤销 / 清空前先丢弃正在写的那一笔。** 否则半截笔画已提交的部分会被整页重放擦掉，剩下半截又在抬笔时补上，画面就烂了。撤销 = 弹出最后一笔 + 整页重放，O(n)，几百笔几十毫秒，比屏幕刷新本身还快。不做增量快照。

**橡皮直接画白色**，不用 CLEAR 混合模式。底图本来就是白的，视觉一致，省掉一整套 xfermode 分支。

## 电纸书刷新波形

小米电纸书是 RK3566 机器。驱动**每次刷新都是整屏**，用的波形取自系统 UI 里那个设置：清晰模式 = `EPD_PART_GC16`（几百毫秒），均衡/快速模式 = `EPD_DU`（快得多）。所以延迟恒定、和笔画长短无关 —— 主因在这儿，不在 Skia。

`EinkFastRefresh` 用反射走系统服务，**没有编译期依赖**，拿不到就静默降级成什么都不做：

- `context.getSystemService("eink")` 取 manager（依次试 `eink`、`epd`）
- 在它的实际类上找 `setMode`，参数类型 String 或 int 都接（koreader 里这行是注释掉的，没实际发货，签名不敢当定论）
- 落笔切 `EPD_DU`；抬笔静置 1.2 秒后切回 `EPD_AUTO` 并 `sendOneFullFrame()` 清残影
- `onPause` 里强制切回，别把机器留在快刷模式

波形常量取自 koreader 的 [RK35xxEPDController.kt](https://raw.githubusercontent.com/koreader/android-luajit-launcher/master/app/src/main/java/org/koreader/launcher/device/epd/rockchip/RK35xxEPDController.kt) 头部注释。想更快可以把 `MODE_FAST` 改成 `EPD_A2`（"12"），但 A2 是二值的，荧光笔的灰和铅笔颗粒会被压没。

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
- 落笔时 `requestUnbufferedDispatch`：触摸事件默认按帧攒批下发，白等一帧

## 文件

```
.github/workflows/build.yml          JDK17 + Gradle 8.7 → assembleDebug → 上传 APK
settings.gradle.kts / build.gradle.kts / gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/htdzs/notepad/
  MainActivity.kt
  model/{PenType,PenConfig,Stroke}.kt
  ink/{PathBuilder,InkWidth,PalmRejector,PressureProbe,Grain}.kt
  device/EinkFastRefresh.kt
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
- **`setMode` 的签名没在真机上验证过**：koreader 里那行是注释掉的。String / int 两种都试，全失败就静默降级 —— 那时性能就只剩 Skia 那部分的改善
- `setMode` 可能要系统权限，`SecurityException` 一样静默降级
- 恢复时切 `EPD_AUTO`，不是切回用户原本那档 —— 没有 `getMode` 可读
- 不做：保存/导出 PNG、多页、缩放平移
