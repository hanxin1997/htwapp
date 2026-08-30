package com.htdzs.notepad.device

import android.content.Context
import android.util.Log
import java.lang.reflect.Method

/**
 * 电纸书刷新波形切换。**纯反射，没有编译期依赖** —— 拿不到就静默什么都不做。
 *
 * 小米电纸书是 RK3566 机器，驱动每次刷新都是整屏，用的波形取自系统 UI 里那个设置：
 * 清晰模式 = EPD_PART_GC16（几百毫秒一次），均衡/快速模式 = EPD_DU（快得多）。
 * 所以延迟恒定、和笔画长短无关。写字时切到 DU，抬笔后切回 AUTO 并整屏刷一次清残影。
 *
 * 波形常量取自 koreader 的 RK35xxEPDController：
 * https://github.com/koreader/android-luajit-launcher/blob/master/app/src/main/java/org/koreader/launcher/device/epd/rockchip/RK35xxEPDController.kt
 */
class EinkFastRefresh private constructor(
    private val manager: Any,
    private val setMode: Method,
    private val modeIsString: Boolean,
    private val sendOneFullFrame: Method?,
) {

    private var inFastMode = false

    /** 切到快刷。已经在快刷模式就什么都不做 —— 重复 setMode 可能自己就触发一次整屏刷新 */
    fun enter() {
        if (inFastMode) return
        if (invokeSetMode(MODE_FAST)) inFastMode = true
    }

    /** 切回默认波形并整屏刷一次清残影。快刷模式是二值/低质的，残影必须刷掉 */
    fun leave() {
        if (!inFastMode) return
        inFastMode = false
        invokeSetMode(MODE_AUTO)
        runCatching { sendOneFullFrame?.invoke(manager) }
            .onFailure { Log.w(TAG, "sendOneFullFrame 失败: $it") }
    }

    private fun invokeSetMode(mode: Int): Boolean = runCatching {
        setMode.invoke(manager, if (modeIsString) mode.toString() else mode)
        true
    }.getOrElse {
        // 可能要系统权限，也可能签名和猜的不一样。降级成不管刷新模式，功能照用
        Log.w(TAG, "setMode($mode) 失败，放弃快刷: $it")
        false
    }

    companion object {
        private const val TAG = "EinkFastRefresh"

        /** EPD_DU，小米系统 UI 里「均衡/快速模式」用的就是它。保留灰阶，比二值的 A2("12") 稳 */
        private const val MODE_FAST = 14

        /** EPD_AUTO，把波形选择交回驱动 */
        private const val MODE_AUTO = 0

        /** 厂商注册的系统服务名。eink 是 RK35xx 上查实的，epd 是常见的另一种叫法 */
        private val SERVICE_NAMES = arrayOf("eink", "epd")

        /**
         * 探测这台机器有没有可用的快刷接口。没有就返回 null，调用方当没这回事。
         *
         * 不写死类名：从 getSystemService 拿到的对象上直接找方法，
         * 厂商叫什么类都行。
         */
        fun probe(context: Context): EinkFastRefresh? {
            val manager = SERVICE_NAMES.firstNotNullOfOrNull { name ->
                runCatching { context.getSystemService(name) }.getOrNull()
            } ?: return null

            val type = manager.javaClass
            // 参数是 String 还是 int 两种都接 —— 没在真机上验证过签名
            val setMode = findMethod(type, "setMode") { method ->
                val parameter = method.parameterTypes.singleOrNull()
                parameter == String::class.java || parameter == Int::class.javaPrimitiveType
            } ?: run {
                Log.i(TAG, "${type.name} 上没有可用的 setMode，不启用快刷")
                return null
            }
            val modeIsString = setMode.parameterTypes[0] == String::class.java
            val sendOneFullFrame = findMethod(type, "sendOneFullFrame") { it.parameterTypes.isEmpty() }

            Log.i(TAG, "快刷可用: ${type.name}.setMode(${if (modeIsString) "String" else "int"})")
            return EinkFastRefresh(manager, setMode, modeIsString, sendOneFullFrame)
        }

        /**
         * 按名字找方法。[Class.getMethods] 只给 public 的（含继承），
         * [Class.getDeclaredMethods] 能给非 public 的但不含继承 —— 隐藏 API 两边都要找。
         */
        private fun findMethod(type: Class<*>, name: String, matches: (Method) -> Boolean): Method? =
            (type.methods.asSequence() + type.declaredMethods.asSequence())
                .firstOrNull { it.name == name && matches(it) }
                ?.also { it.isAccessible = true }
    }
}
