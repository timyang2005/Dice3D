package com.dice3d.app.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import com.dice3d.app.physics.DicePhysicsBody

class GLRenderer(
    private val cameraController: CameraController
) : GLSurfaceView.Renderer {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    private var diceProgram = 0
    private var groundProgram = 0
    private var numberProgram = 0
    private var digitTextureId = 0

    private val diceRenderData = mutableMapOf<Int, DiceRenderData>()
    private var groundRenderData: GroundRenderData? = null

    private var isDarkScene = false

    private var diceColor = floatArrayOf(1f, 1f, 1f, 1f)

    private class DiceRenderData(
        val vertexBuffer: java.nio.FloatBuffer,
        val normalBuffer: java.nio.FloatBuffer,
        val texCoordBuffer: java.nio.FloatBuffer,
        val indexBuffer: java.nio.ShortBuffer,
        val indexCount: Int,
        val mesh: DiceMesh
    )

    private class GroundRenderData(
        val vertexBuffer: java.nio.FloatBuffer,
        val indexBuffer: java.nio.ShortBuffer,
        val indexCount: Int
    )

    fun setDiceColor(color: Long) {
        diceColor = floatArrayOf(
            ((color shr 16) and 0xFF) / 255f,
            ((color shr 8) and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            ((color shr 24) and 0xFF) / 255f
        )
    }

    fun setDarkScene(dark: Boolean) {
        isDarkScene = dark
    }

    fun addDiceMesh(id: Int, mesh: DiceMesh) {
        val vertexBuffer = java.nio.ByteBuffer.allocateDirect(mesh.vertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(mesh.vertices).position(0)

        val normalBuffer = java.nio.ByteBuffer.allocateDirect(mesh.normals.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        normalBuffer.put(mesh.normals).position(0)

        val texCoordBuffer = java.nio.ByteBuffer.allocateDirect(mesh.texCoords.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        texCoordBuffer.put(mesh.texCoords).position(0)

        val indexBuffer = java.nio.ByteBuffer.allocateDirect(mesh.indices.size * 2)
            .order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
        indexBuffer.put(mesh.indices).position(0)

        diceRenderData[id] = DiceRenderData(vertexBuffer, normalBuffer, texCoordBuffer, indexBuffer, mesh.indices.size, mesh)
    }

    fun removeDiceMesh(id: Int) {
        diceRenderData.remove(id)
    }

    fun clearDiceMeshes() {
        diceRenderData.clear()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.15f, 0.15f, 0.18f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        diceProgram = createProgram(DICE_VERTEX_SHADER, DICE_FRAGMENT_SHADER)
        groundProgram = createProgram(GROUND_VERTEX_SHADER, GROUND_FRAGMENT_SHADER)
        numberProgram = createProgram(NUMBER_VERTEX_SHADER, NUMBER_FRAGMENT_SHADER)

        digitTextureId = createDigitTextureAtlas()
        createGroundMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (isDarkScene) {
            GLES20.glClearColor(0.05f, 0.08f, 0.06f, 1f)
        } else {
            GLES20.glClearColor(0.75f, 0.82f, 0.78f, 1f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        cameraController.getViewMatrix(viewMatrix)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        drawGround()
        drawDice()
        drawFaceNumbers()
    }

    private fun drawDice() {
        GLES20.glUseProgram(diceProgram)

        val posHandle = GLES20.glGetAttribLocation(diceProgram, "aPosition")
        val normHandle = GLES20.glGetAttribLocation(diceProgram, "aNormal")
        val mvpHandle = GLES20.glGetUniformLocation(diceProgram, "uMVPMatrix")
        val modelHandle = GLES20.glGetUniformLocation(diceProgram, "uModelMatrix")
        val colorHandle = GLES20.glGetUniformLocation(diceProgram, "uColor")
        val lightDirHandle = GLES20.glGetUniformLocation(diceProgram, "uLightDir")
        val ambientHandle = GLES20.glGetUniformLocation(diceProgram, "uAmbient")

        val lightDir = if (isDarkScene) floatArrayOf(0.3f, 0.8f, 0.5f) else floatArrayOf(0.5f, 0.8f, 0.3f)
        val ambient = if (isDarkScene) 0.3f else 0.4f

        GLES20.glUniform3fv(lightDirHandle, 1, lightDir, 0)
        GLES20.glUniform1f(ambientHandle, ambient)

        val mvpMatrix = FloatArray(16)
        val modelMatrix = FloatArray(16)

        for ((id, data) in diceRenderData) {
            val body = physicsBodies[id] ?: continue

            val transform = body.getTransformMatrix()
            System.arraycopy(transform, 0, modelMatrix, 0, 16)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0)
            GLES20.glUniform4fv(colorHandle, 1, diceColor, 0)

            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glEnableVertexAttribArray(normHandle)

            data.vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, data.vertexBuffer)
            data.normalBuffer.position(0)
            GLES20.glVertexAttribPointer(normHandle, 3, GLES20.GL_FLOAT, false, 12, data.normalBuffer)

            data.indexBuffer.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, data.indexCount, GLES20.GL_UNSIGNED_SHORT, data.indexBuffer)

            GLES20.glDisableVertexAttribArray(posHandle)
            GLES20.glDisableVertexAttribArray(normHandle)
        }
    }

    private fun drawGround() {
        val ground = groundRenderData ?: return
        GLES20.glUseProgram(groundProgram)

        val posHandle = GLES20.glGetAttribLocation(groundProgram, "aPosition")
        val mvpHandle = GLES20.glGetUniformLocation(groundProgram, "uMVPMatrix")
        val colorHandle = GLES20.glGetUniformLocation(groundProgram, "uColor")

        val groundColor = if (isDarkScene) floatArrayOf(0.08f, 0.18f, 0.10f, 1f) else floatArrayOf(0.2f, 0.45f, 0.25f, 1f)
        GLES20.glUniform4fv(colorHandle, 1, groundColor, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, vpMatrix, 0)

        GLES20.glEnableVertexAttribArray(posHandle)
        ground.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, ground.vertexBuffer)

        ground.indexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, ground.indexCount, GLES20.GL_UNSIGNED_SHORT, ground.indexBuffer)

        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun createGroundMesh() {
        val size = 5f
        val vertices = floatArrayOf(
            -size, 0f, -size,
             size, 0f, -size,
             size, 0f,  size,
            -size, 0f,  size
        )
        val indices = shortArrayOf(0, 2, 1, 0, 3, 2)

        val vb = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(vertices).position(0)

        val ib = java.nio.ByteBuffer.allocateDirect(indices.size * 2)
            .order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(indices).position(0)

        groundRenderData = GroundRenderData(vb, ib, indices.size)
    }

    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    private val physicsBodies = mutableMapOf<Int, DicePhysicsBody>()

    fun updatePhysicsBodies(bodies: List<DicePhysicsBody>) {
        physicsBodies.clear()
        for (body in bodies) {
            physicsBodies[body.id] = body
        }
    }

    private fun createDigitTextureAtlas(): Int {
        val cellSize = 128
        val bitmap = Bitmap.createBitmap(cellSize * 10, cellSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = cellSize * 0.75f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        for (i in 0..9) {
            canvas.drawText(i.toString(), i * cellSize + cellSize / 2f, cellSize * 0.8f, paint)
        }
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return textureIds[0]
    }

    private fun drawFaceNumbers() {
        val vertexList = mutableListOf<Float>()
        val texCoordList = mutableListOf<Float>()

        val modelMatrix = FloatArray(16)

        for ((id, data) in diceRenderData) {
            val body = physicsBodies[id] ?: continue
            val transform = body.getTransformMatrix()
            System.arraycopy(transform, 0, modelMatrix, 0, 16)

            for (faceInfo in data.mesh.faceInfos) {
                val cx = faceInfo.faceCenter[0]
                val cy = faceInfo.faceCenter[1]
                val cz = faceInfo.faceCenter[2]

                val worldCenterX = modelMatrix[0] * cx + modelMatrix[4] * cy + modelMatrix[8] * cz + modelMatrix[12]
                val worldCenterY = modelMatrix[1] * cx + modelMatrix[5] * cy + modelMatrix[9] * cz + modelMatrix[13]
                val worldCenterZ = modelMatrix[2] * cx + modelMatrix[6] * cy + modelMatrix[10] * cz + modelMatrix[14]

                val nx = faceInfo.faceNormal[0]
                val ny = faceInfo.faceNormal[1]
                val nz = faceInfo.faceNormal[2]
                var wnx = modelMatrix[0] * nx + modelMatrix[4] * ny + modelMatrix[8] * nz
                var wny = modelMatrix[1] * nx + modelMatrix[5] * ny + modelMatrix[9] * nz
                var wnz = modelMatrix[2] * nx + modelMatrix[6] * ny + modelMatrix[10] * nz
                val nLen = sqrt(wnx * wnx + wny * wny + wnz * wnz)
                if (nLen > 0.0001f) { wnx /= nLen; wny /= nLen; wnz /= nLen }

                val offset = 0.015f
                val pcx = worldCenterX + wnx * offset
                val pcy = worldCenterY + wny * offset
                val pcz = worldCenterZ + wnz * offset

                val upX: Float
                val upY: Float
                val upZ: Float
                if (abs(wny) < 0.99f) { upX = 0f; upY = 1f; upZ = 0f } else { upX = 1f; upY = 0f; upZ = 0f }

                var tx = wny * upZ - wnz * upY
                var ty = wnz * upX - wnx * upZ
                var tz = wnx * upY - wny * upX
                val tLen = sqrt(tx * tx + ty * ty + tz * tz)
                if (tLen > 0.0001f) { tx /= tLen; ty /= tLen; tz /= tLen }

                val bx = wny * tz - wnz * ty
                val by = wnz * tx - wnx * tz
                val bz = wnx * ty - wny * tx

                val digits = faceInfo.faceNumber.toString().map { it - '0' }
                val digitCount = digits.size
                val digitWidth = 0.07f
                val digitHeight = 0.10f
                val totalWidth = digitWidth * digitCount
                val startX = -totalWidth / 2f

                for (di in digits.indices) {
                    val digit = digits[di]
                    val centerX = startX + digitWidth * di + digitWidth / 2f

                    val qx = pcx + tx * centerX
                    val qy = pcy + ty * centerX
                    val qz = pcz + tz * centerX

                    val hw = digitWidth / 2f
                    val hh = digitHeight / 2f

                    val blx = qx - tx * hw - bx * hh
                    val bly = qy - ty * hw - by * hh
                    val blz = qz - tz * hw - bz * hh
                    val brx = qx + tx * hw - bx * hh
                    val bry = qy + ty * hw - by * hh
                    val brz = qz + tz * hw - bz * hh
                    val trx = qx + tx * hw + bx * hh
                    val try_ = qy + ty * hw + by * hh
                    val trz = qz + tz * hw + bz * hh
                    val tlx = qx - tx * hw + bx * hh
                    val tly = qy - ty * hw + by * hh
                    val tlz = qz - tz * hw + bz * hh

                    vertexList.add(blx); vertexList.add(bly); vertexList.add(blz)
                    vertexList.add(brx); vertexList.add(bry); vertexList.add(brz)
                    vertexList.add(trx); vertexList.add(try_); vertexList.add(trz)

                    vertexList.add(blx); vertexList.add(bly); vertexList.add(blz)
                    vertexList.add(trx); vertexList.add(try_); vertexList.add(trz)
                    vertexList.add(tlx); vertexList.add(tly); vertexList.add(tlz)

                    val u0 = digit / 10f
                    val u1 = (digit + 1) / 10f

                    texCoordList.add(u0); texCoordList.add(1f)
                    texCoordList.add(u1); texCoordList.add(1f)
                    texCoordList.add(u1); texCoordList.add(0f)

                    texCoordList.add(u0); texCoordList.add(1f)
                    texCoordList.add(u1); texCoordList.add(0f)
                    texCoordList.add(u0); texCoordList.add(0f)
                }
            }
        }

        if (vertexList.isEmpty()) return

        val vertices = vertexList.toFloatArray()
        val texCoords = texCoordList.toFloatArray()

        val vb = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(vertices).position(0)

        val tb = java.nio.ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        tb.put(texCoords).position(0)

        GLES20.glUseProgram(numberProgram)

        val posHandle = GLES20.glGetAttribLocation(numberProgram, "aPosition")
        val texHandle = GLES20.glGetAttribLocation(numberProgram, "aTexCoord")
        val mvpHandle = GLES20.glGetUniformLocation(numberProgram, "uMVPMatrix")
        val colorHandle = GLES20.glGetUniformLocation(numberProgram, "uColor")
        val texUnitHandle = GLES20.glGetUniformLocation(numberProgram, "uTexture")

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, vpMatrix, 0)

        val luminance = diceColor[0] * 0.299f + diceColor[1] * 0.587f + diceColor[2] * 0.114f
        val numberColor = if (luminance > 0.5f) floatArrayOf(0.05f, 0.05f, 0.05f, 1f) else floatArrayOf(0.95f, 0.95f, 0.95f, 1f)
        GLES20.glUniform4fv(colorHandle, 1, numberColor, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, digitTextureId)
        GLES20.glUniform1i(texUnitHandle, 0)

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glEnableVertexAttribArray(texHandle)

        vb.position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, vb)
        tb.position(0)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 8, tb)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.size / 3)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    companion object {
        private const val DICE_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            attribute vec4 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vNormal = mat3(uModelMatrix) * aNormal;
                vWorldPos = (uModelMatrix * aPosition).xyz;
            }
        """

        private const val DICE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            uniform float uAmbient;
            varying vec3 vNormal;
            varying vec3 vWorldPos;
            void main() {
                vec3 normal = normalize(vNormal);
                float diff = max(dot(normal, normalize(uLightDir)), 0.0);
                float light = uAmbient + diff * (1.0 - uAmbient);
                vec3 viewDir = normalize(-vWorldPos);
                vec3 halfDir = normalize(normalize(uLightDir) + viewDir);
                float spec = pow(max(dot(normal, halfDir), 0.0), 32.0);
                vec3 color = uColor.rgb * light + vec3(1.0) * spec * 0.3;
                gl_FragColor = vec4(color, uColor.a);
            }
        """

        private const val GROUND_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            varying vec3 vPos;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vPos = aPosition.xyz;
            }
        """

        private const val GROUND_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 vPos;
            void main() {
                vec2 grid = abs(fract(vPos.xz) - 0.5);
                float line = min(grid.x, grid.y);
                float gridLine = 1.0 - smoothstep(0.0, 0.05, line);
                vec3 color = mix(uColor.rgb, uColor.rgb * 0.7, gridLine * 0.3);
                gl_FragColor = vec4(color, uColor.a);
            }
        """

        private const val NUMBER_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val NUMBER_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec4 uColor;
            varying vec2 vTexCoord;
            void main() {
                vec4 texColor = texture2D(uTexture, vTexCoord);
                gl_FragColor = vec4(uColor.rgb, uColor.a * texColor.a);
            }
        """
    }
}
