package com.dice3d.app.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.dice3d.app.data.DiceType
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer(
    private val cameraController: CameraController
) : GLSurfaceView.Renderer {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    private var diceProgram = 0
    private var groundProgram = 0

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

        createGroundMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (isDarkScene) {
            GLES20.glClearColor(0.07f, 0.07f, 0.09f, 1f)
        } else {
            GLES20.glClearColor(0.85f, 0.85f, 0.88f, 1f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        cameraController.getViewMatrix(viewMatrix)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        drawGround()
        drawDice()
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

        val groundColor = if (isDarkScene) floatArrayOf(0.12f, 0.12f, 0.14f, 1f) else floatArrayOf(0.6f, 0.6f, 0.65f, 1f)
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
        val indices = shortArrayOf(0, 1, 2, 0, 2, 3)

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

    private val physicsBodies = mutableMapOf<Int, DiceBody>()

    fun updatePhysicsBodies(bodies: List<DiceBody>) {
        physicsBodies.clear()
        for (body in bodies) {
            physicsBodies[body.id] = body
        }
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
    }
}
