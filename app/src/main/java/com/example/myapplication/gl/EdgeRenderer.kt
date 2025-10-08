package com.example.myapplication.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class EdgeRenderer : GLSurfaceView.Renderer {

    private var program = 0
    private var texId = 0
    private var uTexLoc = 0
    private var aPosLoc = 0
    private var aUvLoc = 0

    private var frameW = 0
    private var frameH = 0
    private var texAllocatedW = 0
    private var texAllocatedH = 0
    private var frameRef: ByteBuffer? = null
    private val dirty = AtomicBoolean(false)

    // 🔹 Diagnostics
    private var frameCounter = 0
    private var lastFpsTime = System.nanoTime()

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f,  1f, 0f, 1f,
                1f,  1f, 1f, 1f
            ))
            position(0)
        }

    /** Receive a direct RGBA buffer owned by the caller (zero-copy). */
    fun updateFrame(src: ByteBuffer, w: Int, h: Int) {
        frameW = w; frameH = h
        frameRef = src
        dirty.set(true)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vs = """
            attribute vec2 aPos;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """.trimIndent()

        val fs = """
            precision medium float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            void main() {
                gl_FragColor = texture2D(uTex, vUv);
            }
        """.trimIndent()

        fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("EdgeRenderer", "Shader compile error: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
            }
            return shader
        }

        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, v)
            GLES20.glAttachShader(it, f)
            GLES20.glLinkProgram(it)
        }

        aPosLoc = GLES20.glGetAttribLocation(program, "aPos")
        aUvLoc  = GLES20.glGetAttribLocation(program, "aUv")
        uTexLoc = GLES20.glGetUniformLocation(program, "uTex")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (dirty.getAndSet(false)) {
            val buf = frameRef
            if (buf != null && frameW > 0 && frameH > 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                buf.position(0)
                if (texAllocatedW != frameW || texAllocatedH != frameH) {
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0,
                        GLES20.GL_RGBA, frameW, frameH, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
                    )
                    texAllocatedW = frameW; texAllocatedH = frameH
                } else {
                    GLES20.glTexSubImage2D(
                        GLES20.GL_TEXTURE_2D, 0, 0, 0,
                        frameW, frameH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
                    )
                }
            }
        }

        GLES20.glUseProgram(program)
        quad.position(0)
        GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, quad)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        quad.position(2)
        GLES20.glVertexAttribPointer(aUvLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, quad)
        GLES20.glEnableVertexAttribArray(aUvLoc)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(uTexLoc, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // 🔹 Simple FPS counter (prints every ~1 s)
        frameCounter++
        val now = System.nanoTime()
        if (now - lastFpsTime > 1_000_000_000L) {
            Log.i("EdgeRenderer", "FPS: $frameCounter")
            frameCounter = 0
            lastFpsTime = now
        }
    }
}
