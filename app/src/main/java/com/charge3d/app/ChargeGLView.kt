package com.charge3d.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.min

/**
 * OpenGL ES 2.0 view that draws the charging scene.
 * - Frame rate is capped by [quality] and rendering stops completely while paused (screen off).
 * - Touch: tap = shockwave, double tap = change effect, drag = rotate, fast swipe = next/previous
 *   style, long press = open the customisation studio.
 */
class ChargeGLView(context: Context) : GLSurfaceView(context) {

    // ---- state written from the UI thread, read by the GL thread ----
    @Volatile var config: ThemeConfig = ThemeConfig()
    @Volatile var targetLevel: Float = 0f          // 0..1
    @Volatile var charging: Boolean = true
    @Volatile var quality: Quality = Quality.MEDIUM
        set(v) { field = v; applyQuality() }

    var onSwipe: (Int) -> Unit = {}                // +1 next style, -1 previous
    var onOpenStudio: () -> Unit = {}
    var onTapEffect: () -> Unit = {}
    var onDoubleTapEffect: () -> Unit = {}

    @Volatile private var rotX = 0f
    @Volatile private var rotY = 0f
    @Volatile private var tapX = 0f
    @Volatile private var tapY = 0f
    @Volatile private var tapAtNanos = 0L
    @Volatile private var fx = 0

    private var running = false
    private var lastFrameNs = 0L

    private val renderer = SceneRenderer()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val interval = 1_000_000_000L / quality.fps
            // Skip a vsync when we are ahead of the target frame rate (saves GPU + battery).
            if (frameTimeNanos - lastFrameNs >= interval - 2_000_000L) {
                lastFrameNs = frameTimeNanos
                requestRender()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    override fun onResume() {
        super.onResume()
        if (!running) {
            running = true
            lastFrameNs = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun onPause() {
        running = false                       // no frames at all while paused
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onPause()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyQuality()
    }

    /** Lower quality renders to a smaller buffer that the system scales up. */
    private fun applyQuality() {
        post {
            val w = width; val h = height
            if (w <= 0 || h <= 0) return@post
            val s = quality.scale
            if (s >= 0.99f) holder.setSizeFromLayout()
            else holder.setFixedSize((w * s).toInt().coerceAtLeast(2), (h * s).toInt().coerceAtLeast(2))
        }
    }

    // ---- gestures ----
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val m = min(width, height).toFloat()
            tapX = (e.x - width / 2f) / m
            tapY = (height / 2f - e.y) / m
            tapAtNanos = System.nanoTime()
            onTapEffect()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            fx = (fx + 1) % 3
            onDoubleTapEffect()
            return true
        }

        override fun onLongPress(e: MotionEvent) { onOpenStudio() }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val m = min(width, height).toFloat()
            rotX += dx / m * 2.5f
            rotY -= dy / m * 2.5f
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > 2600f && abs(vx) > 2f * abs(vy)) {
                onSwipe(if (vx < 0) 1 else -1)
                return true
            }
            return false
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestures.onTouchEvent(event)
        return true
    }

    // ---- renderer (GL thread) ----
    private inner class SceneRenderer : GLSurfaceView.Renderer {
        private var program = 0
        private lateinit var quad: FloatBuffer
        private var w = 1f
        private var h = 1f
        private var startNs = 0L
        private var level = 0f
        private var lastNs = 0L
        private val loc = HashMap<String, Int>()

        override fun onSurfaceCreated(gl: GL10?, c: EGLConfig?) {
            quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
            program = link(Shaders.VERTEX, Shaders.FRAGMENT)
            loc.clear()
            startNs = System.nanoTime(); lastNs = startNs
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            w = width.toFloat(); h = height.toFloat()
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (program == 0) return
            val now = System.nanoTime()
            val dt = ((now - lastNs) / 1e9f).coerceIn(0f, 0.1f)
            lastNs = now
            // Ease the displayed level toward the real battery level.
            level += (targetLevel - level) * min(1f, dt * 2.5f)

            val cfg = config
            GLES20.glUseProgram(program)
            val aPos = GLES20.glGetAttribLocation(program, "aPos")
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)

            f2("uRes", w, h)
            f1("uTime", (now - startNs) / 1e9f)
            f1("uLevel", level)
            f1("uCharging", if (charging) 1f else 0f)
            i1("uStyle", cfg.style.id)
            i1("uShape", cfg.shape.id)
            i1("uBg", cfg.background.id)
            val cols = cfg.colors
            i1("uNC", cols.size.coerceIn(1, 4))
            for (k in 0 until 4) {
                val c = cols[min(k, cols.size - 1)]
                f3("uC$k", ((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)
            }
            f1("uSpeed", cfg.speed)
            f1("uBright", cfg.brightness)
            f1("uGlow", cfg.glow)
            f1("uPart", cfg.particles)
            f1("uSize", cfg.size)
            f2("uRot", rotX, rotY)
            f2("uTap", tapX, tapY)
            f1("uTapT", if (tapAtNanos == 0L) 99f else (now - tapAtNanos) / 1e9f)
            i1("uFx", fx)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
        }

        private fun u(name: String) = loc.getOrPut(name) { GLES20.glGetUniformLocation(program, name) }
        private fun f1(n: String, a: Float) = GLES20.glUniform1f(u(n), a)
        private fun f2(n: String, a: Float, b: Float) = GLES20.glUniform2f(u(n), a, b)
        private fun f3(n: String, a: Float, b: Float, c: Float) = GLES20.glUniform3f(u(n), a, b, c)
        private fun i1(n: String, a: Int) = GLES20.glUniform1i(u(n), a)

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                Log.e("Charge3D", "Shader compile failed: " + GLES20.glGetShaderInfoLog(s))
                GLES20.glDeleteShader(s)
                return 0
            }
            return s
        }

        private fun link(vs: String, fs: String): Int {
            val v = compile(GLES20.GL_VERTEX_SHADER, vs)
            val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            if (v == 0 || f == 0) return 0
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f)
            GLES20.glLinkProgram(p)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) {
                Log.e("Charge3D", "Program link failed: " + GLES20.glGetProgramInfoLog(p))
                return 0
            }
            return p
        }
    }
}
