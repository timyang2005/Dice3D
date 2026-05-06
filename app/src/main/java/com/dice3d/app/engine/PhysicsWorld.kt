package com.dice3d.app.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max

class Quaternion(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f
) {
    fun normalize() {
        val len = sqrt(x * x + y * y + z * z + w * w)
        if (len > 0.0001f) {
            x /= len; y /= len; z /= len; w /= len
        }
    }

    fun multiply(q: Quaternion): Quaternion {
        return Quaternion(
            w * q.x + x * q.w + y * q.z - z * q.y,
            w * q.y - x * q.z + y * q.w + z * q.x,
            w * q.z + x * q.y - y * q.x + z * q.w,
            w * q.w - x * q.x - y * q.y - z * q.z
        )
    }

    fun conjugate(): Quaternion = Quaternion(-x, -y, -z, w)

    fun rotateVector(v: FloatArray): FloatArray {
        val qv = Quaternion(v[0], v[1], v[2], 0f)
        val result = this.multiply(qv).multiply(this.conjugate())
        return floatArrayOf(result.x, result.y, result.z)
    }

    fun toMatrix4(): FloatArray {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        return floatArrayOf(
            1f - 2f * (yy + zz), 2f * (xy - wz), 2f * (xz + wy), 0f,
            2f * (xy + wz), 1f - 2f * (xx + zz), 2f * (yz - wx), 0f,
            2f * (xz - wy), 2f * (yz + wx), 1f - 2f * (xx + yy), 0f,
            0f, 0f, 0f, 1f
        )
    }

    fun copy(): Quaternion = Quaternion(x, y, z, w)

    companion object {
        fun fromAxisAngle(axis: FloatArray, angle: Float): Quaternion {
            val halfAngle = angle * 0.5f
            val s = sin(halfAngle)
            return Quaternion(axis[0] * s, axis[1] * s, axis[2] * s, cos(halfAngle))
        }
    }
}

class DiceBody(
    val id: Int,
    val mesh: DiceMesh,
    val boundingRadius: Float = 0.5f
) {
    var posX: Float = 0f
    var posY: Float = 2f
    var posZ: Float = 0f

    var velX: Float = 0f
    var velY: Float = 0f
    var velZ: Float = 0f

    var orientation: Quaternion = Quaternion()
    var angVelX: Float = 0f
    var angVelY: Float = 0f
    var angVelZ: Float = 0f

    var isSleeping: Boolean = false
    var sleepTimer: Float = 0f

    val mass: Float = 1f
    val restitution: Float = 0.3f
    val friction: Float = 0.5f
    val linearDamping: Float = 0.98f
    val angularDamping: Float = 0.96f

    private val sleepLinearThreshold = 0.02f
    private val sleepAngularThreshold = 0.03f
    private val sleepTimeRequired = 0.5f

    fun applyImpulse(ix: Float, iy: Float, iz: Float) {
        velX += ix / mass
        velY += iy / mass
        velZ += iz / mass
        isSleeping = false
        sleepTimer = 0f
    }

    fun applyAngularImpulse(ax: Float, ay: Float, az: Float) {
        angVelX += ax
        angVelY += ay
        angVelZ += az
        isSleeping = false
        sleepTimer = 0f
    }

    fun update(dt: Float) {
        if (isSleeping) return

        posX += velX * dt
        posY += velY * dt
        posZ += velZ * dt

        val angSpeed = sqrt(angVelX * angVelX + angVelY * angVelY + angVelZ * angVelZ)
        if (angSpeed > 0.001f) {
            val axis = floatArrayOf(angVelX / angSpeed, angVelY / angSpeed, angVelZ / angSpeed)
            val deltaQ = Quaternion.fromAxisAngle(axis, angSpeed * dt)
            orientation = deltaQ.multiply(orientation)
            orientation.normalize()
        }

        velX *= linearDamping
        velY *= linearDamping
        velZ *= linearDamping
        angVelX *= angularDamping
        angVelY *= angularDamping
        angVelZ *= angularDamping

        val linearSpeed = sqrt(velX * velX + velY * velY + velZ * velZ)
        val angularSpeed = sqrt(angVelX * angVelX + angVelY * angVelY + angVelZ * angVelZ)

        if (linearSpeed < sleepLinearThreshold && angularSpeed < sleepAngularThreshold && posY <= boundingRadius + 0.05f) {
            sleepTimer += dt
            if (sleepTimer >= sleepTimeRequired) {
                isSleeping = true
                velX = 0f; velY = 0f; velZ = 0f
                angVelX = 0f; angVelY = 0f; angVelZ = 0f
            }
        } else {
            sleepTimer = 0f
        }
    }

    fun getUpFace(): Int {
        val upVector = floatArrayOf(0f, 1f, 0f)
        var bestDot = -2f
        var bestFace = 1

        for (faceInfo in mesh.faceInfos) {
            val rotatedNormal = orientation.rotateVector(faceInfo.faceNormal)
            val dot = rotatedNormal[0] * upVector[0] + rotatedNormal[1] * upVector[1] + rotatedNormal[2] * upVector[2]
            if (dot > bestDot) {
                bestDot = dot
                bestFace = faceInfo.faceNumber
            }
        }
        return bestFace
    }

    fun getTransformMatrix(): FloatArray {
        val rot = orientation.toMatrix4()
        val result = FloatArray(16)
        for (i in 0..3) {
            for (j in 0..2) {
                result[i * 4 + j] = rot[i * 4 + j]
            }
        }
        result[12] = posX
        result[13] = posY
        result[14] = posZ
        result[15] = 1f
        return result
    }

    fun getWorldVertices(): List<FloatArray> {
        val result = mutableListOf<FloatArray>()
        val vertices = mesh.vertices
        for (i in vertices.indices step 3) {
            val local = floatArrayOf(vertices[i], vertices[i + 1], vertices[i + 2])
            val rotated = orientation.rotateVector(local)
            result.add(floatArrayOf(
                posX + rotated[0],
                posY + rotated[1],
                posZ + rotated[2]
            ))
        }
        return result
    }
}

class PhysicsWorld {
    private val diceBodies = mutableListOf<DiceBody>()
    private val gravity = -12f
    private val groundY = 0f
    private val wallLimit = 4f

    var onCollision: ((Float) -> Unit)? = null

    fun addDice(body: DiceBody) {
        synchronized(diceBodies) {
            diceBodies.add(body)
        }
    }

    fun removeDice(id: Int) {
        synchronized(diceBodies) {
            diceBodies.removeAll { it.id == id }
        }
    }

    fun clearDice() {
        synchronized(diceBodies) {
            diceBodies.clear()
        }
    }

    fun getDice(): List<DiceBody> {
        synchronized(diceBodies) {
            return diceBodies.toList()
        }
    }

    fun step(dt: Float) {
        val snapshot: List<DiceBody>
        synchronized(diceBodies) {
            snapshot = diceBodies.toList()
        }
        
        for (dice in snapshot) {
            if (dice.isSleeping) continue
            
            dice.velY += gravity * dt
            dice.update(dt)
            handleGroundCollision(dice)
            handleWallCollision(dice)
        }
        
        handleDiceDiceCollisions(snapshot)
    }

    private fun handleGroundCollision(dice: DiceBody) {
        val worldVerts = dice.getWorldVertices()
        var maxPenetration = 0f
        var lowestVertIdx = -1

        for ((idx, v) in worldVerts.withIndex()) {
            val penetration = groundY - v[1]
            if (penetration > maxPenetration) {
                maxPenetration = penetration
                lowestVertIdx = idx
            }
        }

        if (maxPenetration > 0f && lowestVertIdx >= 0) {
            dice.posY += maxPenetration

            if (dice.velY < 0f) {
                val impactSpeed = abs(dice.velY)
                
                if (impactSpeed > 0.3f) {
                    onCollision?.invoke(min(impactSpeed, 5f))
                    
                    val bounceVel = -dice.velY * dice.restitution
                    dice.velY = if (abs(bounceVel) > 0.2f) bounceVel else 0f
                    
                    dice.velX *= (1f - dice.friction * 0.5f)
                    dice.velZ *= (1f - dice.friction * 0.5f)
                    
                    val localVert = floatArrayOf(
                        dice.mesh.vertices[lowestVertIdx * 3],
                        dice.mesh.vertices[lowestVertIdx * 3 + 1],
                        dice.mesh.vertices[lowestVertIdx * 3 + 2]
                    )
                    val contactArm = floatArrayOf(
                        localVert[0] * 0.5f,
                        0f,
                        localVert[2] * 0.5f
                    )
                    dice.angVelX += contactArm[2] * impactSpeed * 0.3f
                    dice.angVelZ -= contactArm[0] * impactSpeed * 0.3f
                } else {
                    dice.velY = 0f
                    dice.velX *= 0.85f
                    dice.velZ *= 0.85f
                    dice.angVelX *= 0.85f
                    dice.angVelY *= 0.85f
                    dice.angVelZ *= 0.85f
                }
            }
        }
    }

    private fun handleWallCollision(dice: DiceBody) {
        if (dice.posX - dice.boundingRadius < -wallLimit) {
            dice.posX = -wallLimit + dice.boundingRadius
            if (dice.velX < 0f) {
                dice.velX = -dice.velX * dice.restitution
                onCollision?.invoke(abs(dice.velX))
            }
        }
        if (dice.posX + dice.boundingRadius > wallLimit) {
            dice.posX = wallLimit - dice.boundingRadius
            if (dice.velX > 0f) {
                dice.velX = -dice.velX * dice.restitution
                onCollision?.invoke(abs(dice.velX))
            }
        }
        if (dice.posZ - dice.boundingRadius < -wallLimit) {
            dice.posZ = -wallLimit + dice.boundingRadius
            if (dice.velZ < 0f) {
                dice.velZ = -dice.velZ * dice.restitution
                onCollision?.invoke(abs(dice.velZ))
            }
        }
        if (dice.posZ + dice.boundingRadius > wallLimit) {
            dice.posZ = wallLimit - dice.boundingRadius
            if (dice.velZ > 0f) {
                dice.velZ = -dice.velZ * dice.restitution
                onCollision?.invoke(abs(dice.velZ))
            }
        }
    }

    private fun handleDiceDiceCollisions(bodies: List<DiceBody>) {
        for (i in bodies.indices) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                if (a.isSleeping && b.isSleeping) continue

                val dx = b.posX - a.posX
                val dy = b.posY - a.posY
                val dz = b.posZ - a.posZ
                val distSq = dx * dx + dy * dy + dz * dz
                val minDist = a.boundingRadius + b.boundingRadius
                val minDistSq = minDist * minDist

                if (distSq < minDistSq && distSq > 0.001f) {
                    val dist = sqrt(distSq)
                    val nx = dx / dist
                    val ny = dy / dist
                    val nz = dz / dist

                    val overlap = minDist - dist
                    a.posX -= nx * overlap * 0.5f
                    a.posY -= ny * overlap * 0.5f
                    a.posZ -= nz * overlap * 0.5f
                    b.posX += nx * overlap * 0.5f
                    b.posY += ny * overlap * 0.5f
                    b.posZ += nz * overlap * 0.5f

                    val relVelX = a.velX - b.velX
                    val relVelY = a.velY - b.velY
                    val relVelZ = a.velZ - b.velZ
                    val relVelAlongNormal = relVelX * nx + relVelY * ny + relVelZ * nz

                    if (relVelAlongNormal > 0f) {
                        val e = min(a.restitution, b.restitution)
                        val j = -(1f + e) * relVelAlongNormal / 2f

                        a.velX += j * nx
                        a.velY += j * ny
                        a.velZ += j * nz
                        b.velX -= j * nx
                        b.velY -= j * ny
                        b.velZ -= j * nz

                        a.angVelX += (Math.random().toFloat() - 0.5f) * abs(j) * 0.2f
                        a.angVelZ += (Math.random().toFloat() - 0.5f) * abs(j) * 0.2f
                        b.angVelX += (Math.random().toFloat() - 0.5f) * abs(j) * 0.2f
                        b.angVelZ += (Math.random().toFloat() - 0.5f) * abs(j) * 0.2f

                        onCollision?.invoke(min(abs(relVelAlongNormal), 5f))
                    }

                    a.isSleeping = false; a.sleepTimer = 0f
                    b.isSleeping = false; b.sleepTimer = 0f
                }
            }
        }
    }

    fun allDiceStopped(): Boolean {
        synchronized(diceBodies) {
            return diceBodies.all { it.isSleeping }
        }
    }

    fun throwDice() {
        synchronized(diceBodies) {
            for (dice in diceBodies) {
                val spread = diceBodies.size * 0.3f
                dice.posX = (Math.random().toFloat() - 0.5f) * spread
                dice.posY = 3f + Math.random().toFloat() * 2f
                dice.posZ = (Math.random().toFloat() - 0.5f) * spread

                dice.velX = (Math.random().toFloat() - 0.5f) * 5f
                dice.velY = -1f + Math.random().toFloat() * 2f
                dice.velZ = (Math.random().toFloat() - 0.5f) * 5f

                dice.angVelX = (Math.random().toFloat() - 0.5f) * 12f
                dice.angVelY = (Math.random().toFloat() - 0.5f) * 12f
                dice.angVelZ = (Math.random().toFloat() - 0.5f) * 12f

                dice.orientation = Quaternion.fromAxisAngle(
                    floatArrayOf(
                        Math.random().toFloat(),
                        Math.random().toFloat(),
                        Math.random().toFloat()
                    ),
                    Math.random().toFloat() * 6.28f
                )
                dice.orientation.normalize()

                dice.isSleeping = false
                dice.sleepTimer = 0f
            }
        }
    }

    fun applyBounce() {
        synchronized(diceBodies) {
            for (dice in diceBodies) {
                if (!dice.isSleeping) {
                    dice.velY += 4f + Math.random().toFloat() * 2f
                    dice.velX += (Math.random().toFloat() - 0.5f) * 2f
                    dice.velZ += (Math.random().toFloat() - 0.5f) * 2f
                    dice.angVelX += (Math.random().toFloat() - 0.5f) * 6f
                    dice.angVelY += (Math.random().toFloat() - 0.5f) * 6f
                    dice.angVelZ += (Math.random().toFloat() - 0.5f) * 6f
                    dice.isSleeping = false
                    dice.sleepTimer = 0f
                }
            }
        }
    }
}
