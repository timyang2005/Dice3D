package com.dice3d.app.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    val boundingRadius: Float = 0.7f
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
    val restitution: Float = 0.35f
    val friction: Float = 0.4f
    val linearDamping: Float = 0.98f
    val angularDamping: Float = 0.96f

    private val sleepLinearThreshold = 0.05f
    private val sleepAngularThreshold = 0.1f
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

        if (linearSpeed < sleepLinearThreshold && angularSpeed < sleepAngularThreshold && posY <= boundingRadius + 0.01f) {
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
}

class PhysicsWorld {
    private val diceBodies = mutableListOf<DiceBody>()
    private val gravity = -15f
    private val groundY = 0f
    private val wallLimit = 4f

    var onCollision: ((Float) -> Unit)? = null

    fun addDice(body: DiceBody) {
        diceBodies.add(body)
    }

    fun removeDice(id: Int) {
        diceBodies.removeAll { it.id == id }
    }

    fun clearDice() {
        diceBodies.clear()
    }

    fun getDice(): List<DiceBody> = diceBodies.toList()

    fun step(dt: Float) {
        for (dice in diceBodies) {
            if (dice.isSleeping) continue
            dice.velY += gravity * dt
            dice.update(dt)
            handleGroundCollision(dice)
            handleWallCollision(dice)
        }
        handleDiceDiceCollisions()
    }

    private fun handleGroundCollision(dice: DiceBody) {
        if (dice.posY - dice.boundingRadius < groundY) {
            dice.posY = groundY + dice.boundingRadius

            if (dice.velY < 0f) {
                val impactSpeed = abs(dice.velY)
                val newVelY = -dice.velY * dice.restitution

                if (abs(newVelY) < 0.3f) {
                    dice.velY = 0f
                } else {
                    dice.velY = newVelY
                }

                dice.velX *= (1f - dice.friction * 0.3f)
                dice.velZ *= (1f - dice.friction * 0.3f)

                val angImpact = impactSpeed * 0.5f
                dice.angVelX += (Math.random().toFloat() - 0.5f) * angImpact
                dice.angVelZ += (Math.random().toFloat() - 0.5f) * angImpact

                onCollision?.invoke(impactSpeed)
            }
        }
    }

    private fun handleWallCollision(dice: DiceBody) {
        if (dice.posX - dice.boundingRadius < -wallLimit) {
            dice.posX = -wallLimit + dice.boundingRadius
            dice.velX = abs(dice.velX) * dice.restitution
            onCollision?.invoke(abs(dice.velX))
        }
        if (dice.posX + dice.boundingRadius > wallLimit) {
            dice.posX = wallLimit - dice.boundingRadius
            dice.velX = -abs(dice.velX) * dice.restitution
            onCollision?.invoke(abs(dice.velX))
        }
        if (dice.posZ - dice.boundingRadius < -wallLimit) {
            dice.posZ = -wallLimit + dice.boundingRadius
            dice.velZ = abs(dice.velZ) * dice.restitution
            onCollision?.invoke(abs(dice.velZ))
        }
        if (dice.posZ + dice.boundingRadius > wallLimit) {
            dice.posZ = wallLimit - dice.boundingRadius
            dice.velZ = -abs(dice.velZ) * dice.restitution
            onCollision?.invoke(abs(dice.velZ))
        }
    }

    private fun handleDiceDiceCollisions() {
        for (i in diceBodies.indices) {
            for (j in i + 1 until diceBodies.size) {
                val a = diceBodies[i]
                val b = diceBodies[j]
                if (a.isSleeping && b.isSleeping) continue

                val dx = b.posX - a.posX
                val dy = b.posY - a.posY
                val dz = b.posZ - a.posZ
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                val minDist = a.boundingRadius + b.boundingRadius

                if (dist < minDist && dist > 0.001f) {
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
                        val e = minOf(a.restitution, b.restitution)
                        val j = -(1f + e) * relVelAlongNormal / (1f / a.mass + 1f / b.mass)

                        a.velX += j * nx / a.mass
                        a.velY += j * ny / a.mass
                        a.velZ += j * nz / a.mass
                        b.velX -= j * nx / b.mass
                        b.velY -= j * ny / b.mass
                        b.velZ -= j * nz / b.mass

                        a.angVelX += (Math.random().toFloat() - 0.5f) * abs(j) * 0.3f
                        a.angVelZ += (Math.random().toFloat() - 0.5f) * abs(j) * 0.3f
                        b.angVelX += (Math.random().toFloat() - 0.5f) * abs(j) * 0.3f
                        b.angVelZ += (Math.random().toFloat() - 0.5f) * abs(j) * 0.3f

                        onCollision?.invoke(abs(relVelAlongNormal))
                    }

                    a.isSleeping = false; a.sleepTimer = 0f
                    b.isSleeping = false; b.sleepTimer = 0f
                }
            }
        }
    }

    fun allDiceStopped(): Boolean = diceBodies.all { it.isSleeping }

    fun throwDice() {
        for (dice in diceBodies) {
            val spread = diceBodies.size * 0.3f
            dice.posX = (Math.random().toFloat() - 0.5f) * spread
            dice.posY = 3f + Math.random().toFloat() * 2f
            dice.posZ = (Math.random().toFloat() - 0.5f) * spread

            dice.velX = (Math.random().toFloat() - 0.5f) * 6f
            dice.velY = -2f + Math.random().toFloat() * 2f
            dice.velZ = (Math.random().toFloat() - 0.5f) * 6f

            dice.angVelX = (Math.random().toFloat() - 0.5f) * 15f
            dice.angVelY = (Math.random().toFloat() - 0.5f) * 15f
            dice.angVelZ = (Math.random().toFloat() - 0.5f) * 15f

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

    fun applyBounce() {
        for (dice in diceBodies) {
            if (!dice.isSleeping) {
                dice.velY += 5f + Math.random().toFloat() * 3f
                dice.velX += (Math.random().toFloat() - 0.5f) * 3f
                dice.velZ += (Math.random().toFloat() - 0.5f) * 3f
                dice.angVelX += (Math.random().toFloat() - 0.5f) * 8f
                dice.angVelY += (Math.random().toFloat() - 0.5f) * 8f
                dice.angVelZ += (Math.random().toFloat() - 0.5f) * 8f
                dice.isSleeping = false
                dice.sleepTimer = 0f
            }
        }
    }
}
