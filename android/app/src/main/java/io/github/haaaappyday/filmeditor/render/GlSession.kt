package io.github.haaaappyday.filmeditor.render

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.opengl.EGL14 as EGL
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.GLES30 as GL
import io.github.haaaappyday.filmeditor.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class GlFailure(message: String, val contextLost: Boolean = false) : Exception(message)

/** EGL display initialization is shared across our independent renderer instances. */
private object DisplayOwner {
    private var display: EGLDisplay = EGL.EGL_NO_DISPLAY
    private var owners = 0
    @Synchronized fun acquire(): EGLDisplay {
        if (owners == 0) {
            display = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            if (display == EGL.EGL_NO_DISPLAY || !EGL.eglInitialize(display, version, 0, version, 1)) {
                display = EGL.EGL_NO_DISPLAY
                throw GlFailure("Cannot initialize EGL")
            }
        }
        owners++
        return display
    }
    @Synchronized fun release() {
        if (owners > 0 && --owners == 0) {
            EGL.eglTerminate(display)
            display = EGL.EGL_NO_DISPLAY
        }
    }
}

/** All calls, including close, belong to the renderer's dedicated GL thread. */
internal class GlSession(shaderReader: (String) -> String) {
    private var display: EGLDisplay = EGL.EGL_NO_DISPLAY
    private var context = EGL.EGL_NO_CONTEXT
    private var surface = EGL.EGL_NO_SURFACE
    private var program = 0
    private var vao = 0
    private var maxTile = 0
    private val luts = mutableMapOf<String, Int>()
    val diagnostics: Map<String, String>

    init {
        try {
            display = DisplayOwner.acquire()
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attributes = intArrayOf(EGL.EGL_SURFACE_TYPE, EGL.EGL_PBUFFER_BIT,
                EGL.EGL_RENDERABLE_TYPE, 0x40, EGL.EGL_RED_SIZE, 8, EGL.EGL_GREEN_SIZE, 8,
                EGL.EGL_BLUE_SIZE, 8, EGL.EGL_ALPHA_SIZE, 8, EGL.EGL_NONE)
            eglCheck(EGL.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0, "choose config")
            context = EGL.eglCreateContext(display, configs[0], EGL.EGL_NO_CONTEXT,
                intArrayOf(EGL.EGL_CONTEXT_CLIENT_VERSION, 3, EGL.EGL_NONE), 0)
            eglCheck(context != EGL.EGL_NO_CONTEXT, "create context")
            surface = EGL.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL.EGL_WIDTH, 1, EGL.EGL_HEIGHT, 1, EGL.EGL_NONE), 0)
            eglCheck(surface != EGL.EGL_NO_SURFACE, "create surface")
            makeCurrent()
            program = link(shaderReader("shaders/film.vert"), shaderReader("shaders/film.frag"))
            vao = generate { GL.glGenVertexArrays(1, it, 0) }
            val textureLimit = IntArray(1); val viewportLimit = IntArray(2)
            GL.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE, textureLimit, 0)
            GL.glGetIntegerv(GL.GL_MAX_VIEWPORT_DIMS, viewportLimit, 0)
            maxTile = minOf(textureLimit[0], viewportLimit[0], viewportLimit[1], 2048)
            if (maxTile < 1) throw GlFailure("Invalid GL limits")
            val precision = IntArray(1); val range = IntArray(2)
            GL.glGetShaderPrecisionFormat(GL.GL_FRAGMENT_SHADER, GL.GL_HIGH_FLOAT, range, 0, precision, 0)
            diagnostics = mapOf("vendor" to GL.glGetString(GL.GL_VENDOR).orEmpty(),
                "renderer" to GL.glGetString(GL.GL_RENDERER).orEmpty(), "version" to GL.glGetString(GL.GL_VERSION).orEmpty(),
                "fragmentHighpBits" to precision[0].toString(), "maxTextureSize" to textureLimit[0].toString(), "tileLimit" to maxTile.toString())
            glCheck("initialize")
        } catch (error: Throwable) { close(); throw error }
    }

    fun render(input: Bitmap, cube: Cube, params: EditParams, requestedTile: Int, checkCancelled: () -> Unit): Bitmap {
        makeCurrent()
        checkCancelled()
        val lut = luts[params.presetId] ?: uploadCube(cube).also { luts[params.presetId] = it }
        val edge = minOf(requestedTile, maxTile)
        val capacity = minOf(edge, input.width) * minOf(edge, input.height)
        val pixels = IntArray(capacity)
        val buffer = ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder())
        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888, false, ColorSpace.get(ColorSpace.Named.SRGB))
        output.density = Bitmap.DENSITY_NONE
        var sourceTexture = 0; var targetTexture = 0; var framebuffer = 0
        try {
            sourceTexture = generate { GL.glGenTextures(1, it, 0) }
            targetTexture = generate { GL.glGenTextures(1, it, 0) }
            framebuffer = generate { GL.glGenFramebuffers(1, it, 0) }
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, framebuffer)
            GL.glBindVertexArray(vao)
            GL.glUseProgram(program)
            GL.glDisable(GL.GL_BLEND); GL.glDisable(GL.GL_DITHER); GL.glDisable(GL.GL_SCISSOR_TEST)
            GL.glDisable(GL.GL_DEPTH_TEST); GL.glDisable(GL.GL_CULL_FACE)
            GL.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1); GL.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1)
            GL.glUniform1i(uniform("sourceImage"), 0); GL.glUniform1i(uniform("filmLut"), 1)
            GL.glUniform1i(uniform("lutSize"), cube.size)
            GL.glUniform3fv(uniform("domainMin"), 1, cube.domainMin, 0)
            GL.glUniform3fv(uniform("domainMax"), 1, cube.domainMax, 0)
            GL.glUniform1f(uniform("strength"), params.strength)
            GL.glUniform1f(uniform("exposureEv"), params.exposureEv)
            GL.glUniform1f(uniform("temperature"), params.temperature)
            GL.glActiveTexture(GL.GL_TEXTURE1); GL.glBindTexture(GL.GL_TEXTURE_3D, lut)
            for (y in 0 until input.height step edge) for (x in 0 until input.width step edge) {
                checkCancelled()
                val width = minOf(edge, input.width - x); val height = minOf(edge, input.height - y)
                input.getPixels(pixels, 0, width, x, y, width, height)
                buffer.clear()
                for (i in 0 until width * height) {
                    val pixel = pixels[i]
                    if (pixel ushr 24 != 255) throw EditorException(EditorErrorCode.INVALID_PARAMS)
                    buffer.put((pixel ushr 16).toByte()).put((pixel ushr 8).toByte()).put(pixel.toByte()).put(255.toByte())
                }
                buffer.flip()
                GL.glActiveTexture(GL.GL_TEXTURE0)
                GL.glBindTexture(GL.GL_TEXTURE_2D, sourceTexture); textureParameters(GL.GL_TEXTURE_2D)
                GL.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buffer)
                GL.glBindTexture(GL.GL_TEXTURE_2D, targetTexture); textureParameters(GL.GL_TEXTURE_2D)
                GL.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null)
                GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, targetTexture, 0)
                if (GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER) != GL.GL_FRAMEBUFFER_COMPLETE) throw GlFailure("Incomplete framebuffer")
                GL.glBindTexture(GL.GL_TEXTURE_2D, sourceTexture)
                GL.glViewport(0, 0, width, height)
                GL.glDrawArrays(GL.GL_TRIANGLES, 0, 3)
                buffer.clear()
                GL.glReadPixels(0, 0, width, height, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buffer)
                glCheck("render tile")
                // Upload row0 and readback row0 both refer to GL's bottom row. Assigning
                // row0 back to Bitmap's top row preserves the original image orientation.
                buffer.rewind()
                for (i in 0 until width * height) {
                    val r = buffer.get().toInt() and 255; val g = buffer.get().toInt() and 255
                    val b = buffer.get().toInt() and 255; buffer.get()
                    pixels[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                }
                output.setPixels(pixels, 0, width, x, y, width, height)
            }
            checkCancelled()
            output.setHasAlpha(false)
            return output
        } catch (error: Throwable) { output.recycle(); throw error }
        finally {
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0)
            GL.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GL.glDeleteTextures(2, intArrayOf(sourceTexture, targetTexture), 0)
        }
    }

    private fun uploadCube(cube: Cube): Int {
        val texture = generate { GL.glGenTextures(1, it, 0) }
        try {
            val floats = ByteBuffer.allocateDirect(cube.size*cube.size*cube.size*16).order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (i in cube.values.indices step 3) floats.put(cube.values[i]).put(cube.values[i+1]).put(cube.values[i+2]).put(1f)
            floats.flip()
            GL.glActiveTexture(GL.GL_TEXTURE1); GL.glBindTexture(GL.GL_TEXTURE_3D, texture)
            textureParameters(GL.GL_TEXTURE_3D)
            GL.glTexParameteri(GL.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_R, GL.GL_CLAMP_TO_EDGE)
            GL.glTexImage3D(GL.GL_TEXTURE_3D, 0, GL.GL_RGBA32F, cube.size, cube.size, cube.size, 0, GL.GL_RGBA, GL.GL_FLOAT, floats)
            glCheck("upload LUT")
            return texture
        } catch (error: Throwable) { GL.glDeleteTextures(1, intArrayOf(texture), 0); throw error }
    }
    private fun textureParameters(target: Int) {
        GL.glTexParameteri(target, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST)
        GL.glTexParameteri(target, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST)
        GL.glTexParameteri(target, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
        GL.glTexParameteri(target, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
    }
    private fun uniform(name: String): Int = GL.glGetUniformLocation(program, name).also { if (it < 0) throw GlFailure("Missing uniform $name") }
    private fun makeCurrent() = eglCheck(EGL.eglMakeCurrent(display, surface, surface, context), "make current")
    private fun eglCheck(ok: Boolean, operation: String) {
        if (!ok) { val error = EGL.eglGetError(); throw GlFailure("EGL $operation: $error", error == EGL.EGL_CONTEXT_LOST) }
    }
    private fun glCheck(operation: String) {
        val error = GL.glGetError()
        if (error != GL.GL_NO_ERROR) throw GlFailure("GL $operation: $error", error == 0x0507)
    }
    private fun generate(call: (IntArray) -> Unit): Int = IntArray(1).also(call)[0].also { if (it == 0) throw GlFailure("GL allocation failed") }
    private fun compile(type: Int, source: String): Int {
        val shader = GL.glCreateShader(type)
        if (shader == 0) throw GlFailure("Cannot create shader")
        GL.glShaderSource(shader, source); GL.glCompileShader(shader)
        val status = IntArray(1); GL.glGetShaderiv(shader, GL.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) { val log = GL.glGetShaderInfoLog(shader); GL.glDeleteShader(shader); throw GlFailure("Shader: $log") }
        return shader
    }
    private fun link(vertex: String, fragment: String): Int {
        val vs = compile(GL.GL_VERTEX_SHADER, vertex)
        var fs = 0; var result = 0
        try {
            fs = compile(GL.GL_FRAGMENT_SHADER, fragment)
            result = GL.glCreateProgram()
            GL.glAttachShader(result, vs); GL.glAttachShader(result, fs); GL.glLinkProgram(result)
            val status = IntArray(1); GL.glGetProgramiv(result, GL.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) throw GlFailure("Program: ${GL.glGetProgramInfoLog(result)}")
            return result
        } catch (error: Throwable) { if (result != 0) GL.glDeleteProgram(result); throw error }
        finally { GL.glDeleteShader(vs); if (fs != 0) GL.glDeleteShader(fs) }
    }
    fun close() {
        if (display == EGL.EGL_NO_DISPLAY) return
        if (context != EGL.EGL_NO_CONTEXT && surface != EGL.EGL_NO_SURFACE && EGL.eglMakeCurrent(display, surface, surface, context)) {
            for (texture in luts.values) GL.glDeleteTextures(1, intArrayOf(texture), 0)
            if (vao != 0) GL.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            if (program != 0) GL.glDeleteProgram(program)
        }
        luts.clear(); vao = 0; program = 0
        EGL.eglMakeCurrent(display, EGL.EGL_NO_SURFACE, EGL.EGL_NO_SURFACE, EGL.EGL_NO_CONTEXT)
        if (surface != EGL.EGL_NO_SURFACE) EGL.eglDestroySurface(display, surface)
        if (context != EGL.EGL_NO_CONTEXT) EGL.eglDestroyContext(display, context)
        EGL.eglReleaseThread()
        context = EGL.EGL_NO_CONTEXT; surface = EGL.EGL_NO_SURFACE
        DisplayOwner.release(); display = EGL.EGL_NO_DISPLAY
    }
}
