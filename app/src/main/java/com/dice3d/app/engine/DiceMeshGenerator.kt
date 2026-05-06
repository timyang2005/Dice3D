package com.dice3d.app.engine

import com.dice3d.app.data.DiceType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DiceMesh(
    val vertices: FloatArray,
    val normals: FloatArray,
    val texCoords: FloatArray,
    val indices: ShortArray,
    val faceInfos: List<FaceInfo>
)

data class FaceInfo(
    val faceNormal: FloatArray,
    val faceNumber: Int,
    val faceCenter: FloatArray
)

object DiceMeshGenerator {

    fun generateMesh(diceType: DiceType): DiceMesh {
        return when (diceType) {
            DiceType.D4 -> generateTetrahedron()
            DiceType.D6 -> generateCube()
            DiceType.D8 -> generateOctahedron()
            DiceType.D10 -> generateTrapezohedron10()
            DiceType.D12 -> generateDodecahedron()
            DiceType.D20 -> generateIcosahedron()
            DiceType.D100 -> generateTrapezohedron100()
        }
    }

    private fun generateTetrahedron(): DiceMesh {
        val s = sqrt(2.0f)
        val verts = floatArrayOf(
             1f, 1f, 1f,
             1f, -1f, -1f,
            -1f, 1f, -1f,
            -1f, -1f, 1f
        )
        val scale = 0.5f
        for (i in verts.indices) verts[i] *= scale

        val faces = listOf(
            intArrayOf(0, 1, 2),
            intArrayOf(0, 2, 3),
            intArrayOf(0, 3, 1),
            intArrayOf(1, 3, 2)
        )
        val faceNumbers = listOf(1, 2, 3, 4)
        return buildMesh(verts, faces, faceNumbers)
    }

    private fun generateCube(): DiceMesh {
        val s = 0.5f
        val verts = floatArrayOf(
            -s, -s,  s,   s, -s,  s,   s,  s,  s,  -s,  s,  s,
            -s, -s, -s,  -s,  s, -s,   s,  s, -s,   s, -s, -s,
            -s,  s, -s,  -s,  s,  s,   s,  s,  s,   s,  s, -s,
            -s, -s, -s,   s, -s, -s,   s, -s,  s,  -s, -s,  s,
             s, -s, -s,   s,  s, -s,   s,  s,  s,   s, -s,  s,
            -s, -s, -s,  -s, -s,  s,  -s,  s,  s,  -s,  s, -s
        )
        val faces = listOf(
            intArrayOf(0, 1, 2, 3),
            intArrayOf(4, 5, 6, 7),
            intArrayOf(8, 9, 10, 11),
            intArrayOf(12, 13, 14, 15),
            intArrayOf(16, 17, 18, 19),
            intArrayOf(20, 21, 22, 23)
        )
        val faceNumbers = listOf(1, 6, 2, 5, 3, 4)
        return buildMesh(verts, faces, faceNumbers)
    }

    private fun generateOctahedron(): DiceMesh {
        val s = 0.7f
        val verts = floatArrayOf(
             0f,  s,  0f,
             s,  0f,  0f,
             0f,  0f,  s,
            -s,  0f,  0f,
             0f,  0f, -s,
             0f, -s,  0f
        )
        val faces = listOf(
            intArrayOf(0, 1, 2),
            intArrayOf(0, 2, 3),
            intArrayOf(0, 3, 4),
            intArrayOf(0, 4, 1),
            intArrayOf(5, 2, 1),
            intArrayOf(5, 3, 2),
            intArrayOf(5, 4, 3),
            intArrayOf(5, 1, 4)
        )
        val faceNumbers = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        return buildMesh(verts, faces, faceNumbers)
    }

    private fun generateTrapezohedron10(): DiceMesh {
        val n = 5
        val h = 0.8f
        val r = 0.55f
        val twist = PI.toFloat() / n

        val vertexList = mutableListOf<Float>()
        vertexList.add(0f); vertexList.add(h); vertexList.add(0f)
        for (i in 0 until n) {
            val angle = 2f * PI.toFloat() * i / n
            vertexList.add(r * cos(angle)); vertexList.add(0f); vertexList.add(r * sin(angle))
        }
        for (i in 0 until n) {
            val angle = 2f * PI.toFloat() * i / n + twist
            vertexList.add(r * 0.6f * cos(angle)); vertexList.add(0f); vertexList.add(r * 0.6f * sin(angle))
        }
        vertexList.add(0f); vertexList.add(-h); vertexList.add(0f)

        val topIdx = 0
        val mid1Start = 1
        val mid2Start = 1 + n
        val botIdx = 1 + 2 * n

        val faces = mutableListOf<IntArray>()
        for (i in 0 until n) {
            val next = (i + 1) % n
            faces.add(intArrayOf(topIdx, mid1Start + i, mid2Start + i))
            faces.add(intArrayOf(topIdx, mid2Start + i, mid1Start + next))
            faces.add(intArrayOf(botIdx, mid2Start + i, mid1Start + i))
            faces.add(intArrayOf(botIdx, mid1Start + next, mid2Start + i))
        }

        val faceNumbers = listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10)
        return buildMesh(vertexList.toFloatArray(), faces, faceNumbers)
    }

    private fun generateTrapezohedron100(): DiceMesh {
        val n = 5
        val h = 0.8f
        val r = 0.55f
        val twist = PI.toFloat() / n

        val vertexList = mutableListOf<Float>()
        vertexList.add(0f); vertexList.add(h); vertexList.add(0f)
        for (i in 0 until n) {
            val angle = 2f * PI.toFloat() * i / n
            vertexList.add(r * cos(angle)); vertexList.add(0f); vertexList.add(r * sin(angle))
        }
        for (i in 0 until n) {
            val angle = 2f * PI.toFloat() * i / n + twist
            vertexList.add(r * 0.6f * cos(angle)); vertexList.add(0f); vertexList.add(r * 0.6f * sin(angle))
        }
        vertexList.add(0f); vertexList.add(-h); vertexList.add(0f)

        val topIdx = 0
        val mid1Start = 1
        val mid2Start = 1 + n
        val botIdx = 1 + 2 * n

        val faces = mutableListOf<IntArray>()
        for (i in 0 until n) {
            val next = (i + 1) % n
            faces.add(intArrayOf(topIdx, mid1Start + i, mid2Start + i))
            faces.add(intArrayOf(topIdx, mid2Start + i, mid1Start + next))
            faces.add(intArrayOf(botIdx, mid2Start + i, mid1Start + i))
            faces.add(intArrayOf(botIdx, mid1Start + next, mid2Start + i))
        }

        val faceNumbers = listOf(0, 0, 10, 10, 20, 20, 30, 30, 40, 40, 50, 50, 60, 60, 70, 70, 80, 80, 90, 90)
        return buildMesh(vertexList.toFloatArray(), faces, faceNumbers)
    }

    private fun generateDodecahedron(): DiceMesh {
        val phi = (1f + sqrt(5f)) / 2f
        val scale = 0.35f

        val cubeVerts = mutableListOf<Float>()
        for (x in floatArrayOf(-1f, 1f)) {
            for (y in floatArrayOf(-1f, 1f)) {
                for (z in floatArrayOf(-1f, 1f)) {
                    cubeVerts.add(x * scale); cubeVerts.add(y * scale); cubeVerts.add(z * scale)
                }
            }
        }

        val edgeVerts = mutableListOf<Float>()
        for (x in floatArrayOf(-1f, 1f)) {
            for (y in floatArrayOf(-1f, 1f)) {
                edgeVerts.add(0f); edgeVerts.add(y * phi * scale); edgeVerts.add(x * phi * scale)
                edgeVerts.add(y * phi * scale); edgeVerts.add(x * phi * scale); edgeVerts.add(0f)
                edgeVerts.add(x * phi * scale); edgeVerts.add(0f); edgeVerts.add(y * phi * scale)
            }
        }

        val allVerts = (cubeVerts + edgeVerts).toFloatArray()

        val faces = listOf(
            intArrayOf(0, 16, 2, 10, 9),
            intArrayOf(0, 9, 4, 18, 16),
            intArrayOf(0, 12, 1, 10, 2),
            intArrayOf(0, 2, 16, 18, 4),
            intArrayOf(1, 12, 0, 9, 10),
            intArrayOf(1, 10, 2, 16, 9),
            intArrayOf(3, 17, 5, 11, 8),
            intArrayOf(3, 8, 6, 19, 17),
            intArrayOf(3, 13, 1, 11, 5),
            intArrayOf(3, 5, 17, 19, 6),
            intArrayOf(7, 14, 4, 18, 6),
            intArrayOf(7, 6, 19, 15, 14)
        )

        val faceNumbers = (1..12).toList()
        return buildMesh(allVerts, faces, faceNumbers)
    }

    private fun generateIcosahedron(): DiceMesh {
        val phi = (1f + sqrt(5f)) / 2f
        val scale = 0.42f

        val verts = floatArrayOf(
            -1f,  phi, 0f,   1f,  phi, 0f,  -1f, -phi, 0f,   1f, -phi, 0f,
             0f, -1f,  phi,  0f,  1f,  phi,   0f, -1f, -phi,  0f,  1f, -phi,
             phi, 0f, -1f,   phi, 0f,  1f,  -phi, 0f, -1f,  -phi, 0f,  1f
        )
        for (i in verts.indices) verts[i] *= scale

        val faces = listOf(
            intArrayOf(0, 11, 5),  intArrayOf(0, 5, 1),   intArrayOf(0, 1, 7),
            intArrayOf(0, 7, 10),  intArrayOf(0, 10, 11),  intArrayOf(1, 5, 9),
            intArrayOf(5, 11, 4),  intArrayOf(11, 10, 2),  intArrayOf(10, 7, 6),
            intArrayOf(7, 1, 8),   intArrayOf(3, 9, 4),    intArrayOf(3, 4, 2),
            intArrayOf(3, 2, 6),   intArrayOf(3, 6, 8),    intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5),   intArrayOf(2, 4, 11),   intArrayOf(6, 2, 10),
            intArrayOf(8, 6, 7),   intArrayOf(9, 8, 1)
        )
        val faceNumbers = (1..20).toList()
        return buildMesh(verts, faces, faceNumbers)
    }

    private fun buildMesh(
        rawVerts: FloatArray,
        faces: List<IntArray>,
        faceNumbers: List<Int>
    ): DiceMesh {
        val vertexList = mutableListOf<Float>()
        val normalList = mutableListOf<Float>()
        val texCoordList = mutableListOf<Float>()
        val indexList = mutableListOf<Short>()
        val faceInfosList = mutableListOf<FaceInfo>()

        var vertexOffset = 0

        for (fi in faces.indices) {
            val face = faces[fi]
            val faceNormal = computeFaceNormal(rawVerts, face)

            val center = floatArrayOf(0f, 0f, 0f)
            for (idx in face) {
                center[0] += rawVerts[idx * 3]
                center[1] += rawVerts[idx * 3 + 1]
                center[2] += rawVerts[idx * 3 + 2]
            }
            center[0] /= face.size; center[1] /= face.size; center[2] /= face.size

            val len = sqrt(faceNormal[0] * faceNormal[0] + faceNormal[1] * faceNormal[1] + faceNormal[2] * faceNormal[2])
            val unitNormal = if (len > 0.0001f) {
                floatArrayOf(faceNormal[0] / len, faceNormal[1] / len, faceNormal[2] / len)
            } else {
                floatArrayOf(0f, 1f, 0f)
            }

            faceInfosList.add(FaceInfo(unitNormal.copyOf(), faceNumbers[fi], floatArrayOf(
                center[0] + unitNormal[0] * 0.02f,
                center[1] + unitNormal[1] * 0.02f,
                center[2] + unitNormal[2] * 0.02f
            )))

            for (idx in face) {
                val x = rawVerts[idx * 3]
                val y = rawVerts[idx * 3 + 1]
                val z = rawVerts[idx * 3 + 2]

                val bevelX = x + unitNormal[0] * 0.02f
                val bevelY = y + unitNormal[1] * 0.02f
                val bevelZ = z + unitNormal[2] * 0.02f

                vertexList.add(bevelX); vertexList.add(bevelY); vertexList.add(bevelZ)
                normalList.add(unitNormal[0]); normalList.add(unitNormal[1]); normalList.add(unitNormal[2])

                val u = 0.5f + (x - center[0]) * 0.4f
                val v = 0.5f + (y - center[1]) * 0.4f
                texCoordList.add(u); texCoordList.add(v)
            }

            for (i in 1 until face.size - 1) {
                indexList.add((vertexOffset).toShort())
                indexList.add((vertexOffset + i).toShort())
                indexList.add((vertexOffset + i + 1).toShort())
            }

            vertexOffset += face.size
        }

        return DiceMesh(
            vertices = vertexList.toFloatArray(),
            normals = normalList.toFloatArray(),
            texCoords = texCoordList.toFloatArray(),
            indices = indexList.toShortArray(),
            faceInfos = faceInfosList
        )
    }

    private fun computeFaceNormal(verts: FloatArray, face: IntArray): FloatArray {
        if (face.size < 3) return floatArrayOf(0f, 1f, 0f)
        val ax = verts[face[1] * 3] - verts[face[0] * 3]
        val ay = verts[face[1] * 3 + 1] - verts[face[0] * 3 + 1]
        val az = verts[face[1] * 3 + 2] - verts[face[0] * 3 + 2]
        val bx = verts[face[2] * 3] - verts[face[0] * 3]
        val by = verts[face[2] * 3 + 1] - verts[face[0] * 3 + 1]
        val bz = verts[face[2] * 3 + 2] - verts[face[0] * 3 + 2]
        val nx = ay * bz - az * by
        val ny = az * bx - ax * bz
        val nz = ax * by - ay * bx
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        return if (len > 0.0001f) floatArrayOf(nx / len, ny / len, nz / len) else floatArrayOf(0f, 1f, 0f)
    }
}
