package com.dice3d.app.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max
import kotlin.math.PI

class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun add(v: Vec3) = Vec3(x + v.x, y + v.y, z + v.z)
    fun sub(v: Vec3) = Vec3(x - v.x, y - v.y, z - v.z)
    fun mul(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(v: Vec3): Float = x * v.x + y * v.y + z * v.z
    fun cross(v: Vec3): Vec3 = Vec3(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun lengthSquared(): Float = x * x + y * y + z * z
    fun normalize(): Vec3 {
        val len = length()
        return if (len > 0.0001f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 0f)
    }
    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z)
    fun clone(): Vec3 = Vec3(x, y, z)
}

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

    fun rotateVector(v: Vec3): Vec3 {
        val qv = Quaternion(v.x, v.y, v.z, 0f)
        val result = this.multiply(qv).multiply(this.conjugate())
        return Vec3(result.x, result.y, result.z)
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
        fun fromAxisAngle(axis: Vec3, angle: Float): Quaternion {
            val halfAngle = angle * 0.5f
            val s = sin(halfAngle)
            return Quaternion(axis.x * s, axis.y * s, axis.z * s, cos(halfAngle))
        }
    }
}

class InertiaTensor(val Ixx: Float, val Iyy: Float, val Izz: Float) {
    fun inverse(): InertiaTensor {
        return InertiaTensor(
            if (Ixx > 0.0001f) 1f / Ixx else 0f,
            if (Iyy > 0.0001f) 1f / Iyy else 0f,
            if (Izz > 0.0001f) 1f / Izz else 0f
        )
    }

    fun multiply(v: Vec3): Vec3 {
        return Vec3(Ixx * v.x, Iyy * v.y, Izz * v.z)
    }
}

class DiceBody(
    val id: Int,
    val mesh: DiceMesh,
    val boundingRadius: Float = 0.5f
) {
    var pos: Vec3 = Vec3(0f, 2f, 0f)
    var vel: Vec3 = Vec3(0f, 0f, 0f)

    var orientation: Quaternion = Quaternion()
    var angVel: Vec3 = Vec3(0f, 0f, 0f)

    var isSleeping: Boolean = false
    var sleepTimer: Float = 0f

    val mass: Float = 1f
    val invMass: Float = 1f / mass
    val restitution: Float = 0.4f
    val friction: Float = 0.6f
    val linearDamping: Float = 0.995f
    val angularDamping: Float = 0.98f

    private val sleepLinearThreshold = 0.03f
    private val sleepAngularThreshold = 0.05f
    private val sleepTimeRequired = 0.4f

    val inertiaTensorLocal: InertiaTensor
    val invInertiaTensorLocal: InertiaTensor

    init {
        val side = 0.8f
        val I = (1f / 6f) * mass * side * side
        inertiaTensorLocal = InertiaTensor(I, I, I)
        invInertiaTensorLocal = inertiaTensorLocal.inverse()
    }

    fun getInertiaTensorWorld(): InertiaTensor {
        val rotMatrix = orientation.toMatrix4()
        val I = inertiaTensorLocal
        val Ixx = I.Ixx
        val Iyy = I.Iyy
        val Izz = I.Izz
        
        val r00 = rotMatrix[0]
        val r01 = rotMatrix[1]
        val r02 = rotMatrix[2]
        val r10 = rotMatrix[4]
        val r11 = rotMatrix[5]
        val r12 = rotMatrix[6]
        val r20 = rotMatrix[8]
        val r21 = rotMatrix[9]
        val r22 = rotMatrix[10]
        
        val IxxWorld = Ixx * r00 * r00 + Iyy * r01 * r01 + Izz * r02 * r02
        val IyyWorld = Ixx * r10 * r10 + Iyy * r11 * r11 + Izz * r12 * r12
        val IzzWorld = Ixx * r20 * r20 + Iyy * r21 * r21 + Izz * r22 * r22
        
        return InertiaTensor(IxxWorld, IyyWorld, IzzWorld)
    }

    fun getInvInertiaTensorWorld(): InertiaTensor {
        return getInertiaTensorWorld().inverse()
    }

    fun applyImpulse(impulse: Vec3) {
        vel = vel.add(impulse.mul(invMass))
        wakeUp()
    }

    fun applyAngularImpulse(angularImpulse: Vec3) {
        val invI = getInvInertiaTensorWorld()
        angVel = angVel.add(invI.multiply(angularImpulse))
        wakeUp()
    }

    fun applyImpulseAtPoint(impulse: Vec3, point: Vec3) {
        applyImpulse(impulse)
        val r = point.sub(pos)
        val angularImpulse = r.cross(impulse)
        applyAngularImpulse(angularImpulse)
    }

    fun getPointVelocity(point: Vec3): Vec3 {
        val r = point.sub(pos)
        return vel.add(angVel.cross(r))
    }

    fun getWorldVertices(): List<Vec3> {
        val result = mutableListOf<Vec3>()
        val vertices = mesh.vertices
        for (i in vertices.indices step 3) {
            val local = Vec3(vertices[i], vertices[i + 1], vertices[i + 2])
            val rotated = orientation.rotateVector(local)
            result.add(Vec3(
                pos.x + rotated.x,
                pos.y + rotated.y,
                pos.z + rotated.z
            ))
        }
        return result
    }

    fun getLocalPoint(worldPoint: Vec3): Vec3 {
        val translated = worldPoint.sub(pos)
        val invRot = orientation.conjugate()
        return invRot.rotateVector(translated)
    }

    fun update(dt: Float) {
        if (isSleeping) return

        pos = pos.add(vel.mul(dt))

        val angSpeed = angVel.length()
        if (angSpeed > 0.001f) {
            val axis = angVel.normalize()
            val deltaQ = Quaternion.fromAxisAngle(axis, angSpeed * dt)
            orientation = deltaQ.multiply(orientation)
            orientation.normalize()
        }

        vel = vel.mul(linearDamping)
        angVel = angVel.mul(angularDamping)

        val linearSpeed = vel.length()
        val angularSpeed = angVel.length()

        if (linearSpeed < sleepLinearThreshold && angularSpeed < sleepAngularThreshold && pos.y <= boundingRadius + 0.1f) {
            sleepTimer += dt
            if (sleepTimer >= sleepTimeRequired) {
                isSleeping = true
                vel = Vec3(0f, 0f, 0f)
                angVel = Vec3(0f, 0f, 0f)
            }
        } else {
            sleepTimer = 0f
        }
    }

    fun wakeUp() {
        isSleeping = false
        sleepTimer = 0f
    }

    fun getUpFace(): Int {
        val upVector = Vec3(0f, 1f, 0f)
        var bestDot = -2f
        var bestFace = 1

        for (faceInfo in mesh.faceInfos) {
            val normal = Vec3(faceInfo.faceNormal[0], faceInfo.faceNormal[1], faceInfo.faceNormal[2])
            val rotatedNormal = orientation.rotateVector(normal)
            val dot = rotatedNormal.dot(upVector)
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
        result[12] = pos.x
        result[13] = pos.y
        result[14] = pos.z
        result[15] = 1f
        return result
    }
}

class ContactPoint(
    val point: Vec3,
    val normal: Vec3,
    val penetration: Float
)

class PhysicsWorld {
    private val diceBodies = mutableListOf<DiceBody>()
    private val gravity = -15f
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

        val subSteps = 2
        val subDt = dt / subSteps.toFloat()

        for (s in 0 until subSteps) {
            for (dice in snapshot) {
                if (dice.isSleeping) continue
                dice.vel.y += gravity * subDt
                dice.update(subDt)
            }

            for (dice in snapshot) {
                if (dice.isSleeping) continue
                handleGroundCollision(dice)
                handleWallCollision(dice)
            }

            handleDiceDiceCollisions(snapshot)
        }
    }

    private fun findGroundContacts(dice: DiceBody): List<ContactPoint> {
        val contacts = mutableListOf<ContactPoint>()
        val worldVerts = dice.getWorldVertices()

        for (v in worldVerts) {
            val penetration = groundY - v.y
            if (penetration > -0.01f) {
                contacts.add(ContactPoint(
                    Vec3(v.x, v.y, v.z),
                    Vec3(0f, 1f, 0f),
                    max(penetration, 0f)
                ))
            }
        }

        return contacts.sortedByDescending { it.penetration }
    }

    private fun handleGroundCollision(dice: DiceBody) {
        val contacts = findGroundContacts(dice)
        if (contacts.isEmpty()) return

        val maxPenetration = contacts.first().penetration
        if (maxPenetration > 0f) {
            dice.pos.y += maxPenetration * 0.8f
        }

        for (contact in contacts.take(3)) {
            if (contact.penetration <= 0f) continue

            val r = contact.point.sub(dice.pos)
            val velAtPoint = dice.getPointVelocity(contact.point)
            val velAlongNormal = velAtPoint.dot(contact.normal)

            if (velAlongNormal > 0.5f) {
                onCollision?.invoke(min(velAlongNormal, 5f))
            }

            if (velAlongNormal > 0f) continue

            val e = dice.restitution
            var j = -(1f + e) * velAlongNormal
            j /= dice.invMass

            val invI = dice.getInvInertiaTensorWorld()
            val rCrossN = r.cross(contact.normal)
            val angTerm = rCrossN.dot(invI.multiply(rCrossN))
            j /= (1f + angTerm)

            val impulse = contact.normal.mul(j)
            dice.applyImpulseAtPoint(impulse, contact.point)

            val tangent = velAtPoint.sub(contact.normal.mul(velAlongNormal))
            val tangentLen = tangent.length()
            if (tangentLen > 0.0001f) {
                val t = tangent.mul(1f / tangentLen)
                val velAlongTangent = velAtPoint.dot(t)
                var jt = -velAlongTangent
                jt /= dice.invMass

                val rCrossT = r.cross(t)
                val angTermT = rCrossT.dot(invI.multiply(rCrossT))
                jt /= (1f + angTermT)

                if (abs(jt) < abs(j) * dice.friction) {
                    val frictionImpulse = t.mul(jt)
                    dice.applyImpulseAtPoint(frictionImpulse, contact.point)
                } else {
                    val frictionImpulse = t.mul(-j * dice.friction)
                    dice.applyImpulseAtPoint(frictionImpulse, contact.point)
                }
            }
        }
    }

    private fun handleWallCollision(dice: DiceBody) {
        val wallNormal = Vec3(1f, 0f, 0f)
        val contactPoint = Vec3(0f, dice.pos.y, dice.pos.z)

        if (dice.pos.x - dice.boundingRadius < -wallLimit) {
            contactPoint.x = -wallLimit
            dice.pos.x = -wallLimit + dice.boundingRadius
            resolveWallCollision(dice, contactPoint, Vec3(1f, 0f, 0f))
        }

        if (dice.pos.x + dice.boundingRadius > wallLimit) {
            contactPoint.x = wallLimit
            dice.pos.x = wallLimit - dice.boundingRadius
            resolveWallCollision(dice, contactPoint, Vec3(-1f, 0f, 0f))
        }

        wallNormal.x = 0f; wallNormal.z = 1f
        contactPoint.x = dice.pos.x

        if (dice.pos.z - dice.boundingRadius < -wallLimit) {
            contactPoint.z = -wallLimit
            dice.pos.z = -wallLimit + dice.boundingRadius
            resolveWallCollision(dice, contactPoint, Vec3(0f, 0f, 1f))
        }

        if (dice.pos.z + dice.boundingRadius > wallLimit) {
            contactPoint.z = wallLimit
            dice.pos.z = wallLimit - dice.boundingRadius
            resolveWallCollision(dice, contactPoint, Vec3(0f, 0f, -1f))
        }
    }

    private fun resolveWallCollision(dice: DiceBody, contactPoint: Vec3, normal: Vec3) {
        val velAtPoint = dice.getPointVelocity(contactPoint)
        val velAlongNormal = velAtPoint.dot(normal)
        if (velAlongNormal > 0f) return

        val e = dice.restitution
        val r = contactPoint.sub(dice.pos)
        var j = -(1f + e) * velAlongNormal
        j /= dice.invMass

        val invI = dice.getInvInertiaTensorWorld()
        val rCrossN = r.cross(normal)
        val angTerm = rCrossN.dot(invI.multiply(rCrossN))
        j /= (1f + angTerm)

        val impulse = normal.mul(j)
        dice.applyImpulseAtPoint(impulse, contactPoint)

        if (abs(velAlongNormal) > 0.3f) {
            onCollision?.invoke(min(abs(velAlongNormal), 5f))
        }
    }

    private fun handleDiceDiceCollisions(bodies: List<DiceBody>) {
        for (i in bodies.indices) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                if (a.isSleeping && b.isSleeping) continue

                val dx = b.pos.x - a.pos.x
                val dy = b.pos.y - a.pos.y
                val dz = b.pos.z - a.pos.z
                val distSq = dx * dx + dy * dy + dz * dz
                val minDist = a.boundingRadius + b.boundingRadius
                val minDistSq = minDist * minDist

                if (distSq < minDistSq && distSq > 0.0001f) {
                    val dist = sqrt(distSq)
                    val nx = dx / dist
                    val ny = dy / dist
                    val nz = dz / dist
                    val normal = Vec3(nx, ny, nz)

                    val overlap = minDist - dist
                    a.pos = a.pos.sub(normal.mul(overlap * 0.5f))
                    b.pos = b.pos.add(normal.mul(overlap * 0.5f))

                    val contactPointA = a.pos.add(normal.mul(a.boundingRadius))
                    val contactPointB = b.pos.sub(normal.mul(b.boundingRadius))

                    resolveDiceDiceCollision(a, b, contactPointA, contactPointB, normal)

                    a.wakeUp()
                    b.wakeUp()
                }
            }
        }
    }

    private fun resolveDiceDiceCollision(a: DiceBody, b: DiceBody, pointA: Vec3, pointB: Vec3, normal: Vec3) {
        val ra = pointA.sub(a.pos)
        val rb = pointB.sub(b.pos)

        val velA = a.getPointVelocity(pointA)
        val velB = b.getPointVelocity(pointB)
        val relVel = velA.sub(velB)
        val relVelNormal = relVel.dot(normal)

        if (relVelNormal > 0f) return

        val e = min(a.restitution, b.restitution)
        val invIA = a.getInvInertiaTensorWorld()
        val invIB = b.getInvInertiaTensorWorld()

        val raCrossN = ra.cross(normal)
        val rbCrossN = rb.cross(normal)
        val angTermA = raCrossN.dot(invIA.multiply(raCrossN))
        val angTermB = rbCrossN.dot(invIB.multiply(rbCrossN))

        var j = -(1f + e) * relVelNormal
        j /= (a.invMass + b.invMass + angTermA + angTermB)

        val impulse = normal.mul(j)
        a.applyImpulseAtPoint(impulse, pointA)
        b.applyImpulseAtPoint(impulse.mul(-1f), pointB)

        val tangent = relVel.sub(normal.mul(relVelNormal))
        val tangentLen = tangent.length()
        if (tangentLen > 0.0001f) {
            val t = tangent.mul(1f / tangentLen)
            val velAlongTangent = relVel.dot(t)

            val raCrossT = ra.cross(t)
            val rbCrossT = rb.cross(t)
            val angTermTA = raCrossT.dot(invIA.multiply(raCrossT))
            val angTermTB = rbCrossT.dot(invIB.multiply(rbCrossT))

            var jt = -velAlongTangent
            jt /= (a.invMass + b.invMass + angTermTA + angTermTB)

            val friction = min(a.friction, b.friction)
            if (abs(jt) < abs(j) * friction) {
                val frictionImpulse = t.mul(jt)
                a.applyImpulseAtPoint(frictionImpulse, pointA)
                b.applyImpulseAtPoint(frictionImpulse.mul(-1f), pointB)
            } else {
                val frictionImpulse = t.mul(-j * friction)
                a.applyImpulseAtPoint(frictionImpulse, pointA)
                b.applyImpulseAtPoint(frictionImpulse.mul(-1f), pointB)
            }
        }

        if (abs(relVelNormal) > 0.3f) {
            onCollision?.invoke(min(abs(relVelNormal), 5f))
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
                val spread = diceBodies.size * 0.4f
                dice.pos = Vec3(
                    (Math.random().toFloat() - 0.5f) * spread,
                    3.5f + Math.random().toFloat() * 2f,
                    (Math.random().toFloat() - 0.5f) * spread
                )

                dice.vel = Vec3(
                    (Math.random().toFloat() - 0.5f) * 6f,
                    -2f + Math.random().toFloat() * 3f,
                    (Math.random().toFloat() - 0.5f) * 6f
                )

                dice.angVel = Vec3(
                    (Math.random().toFloat() - 0.5f) * 15f,
                    (Math.random().toFloat() - 0.5f) * 15f,
                    (Math.random().toFloat() - 0.5f) * 15f
                )

                dice.orientation = Quaternion.fromAxisAngle(
                    Vec3(
                        Math.random().toFloat(),
                        Math.random().toFloat(),
                        Math.random().toFloat()
                    ).normalize(),
                    Math.random().toFloat() * PI * 2f
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
                    dice.vel.y += 5f + Math.random().toFloat() * 3f
                    dice.vel.x += (Math.random().toFloat() - 0.5f) * 3f
                    dice.vel.z += (Math.random().toFloat() - 0.5f) * 3f
                    dice.angVel.x += (Math.random().toFloat() - 0.5f) * 8f
                    dice.angVel.y += (Math.random().toFloat() - 0.5f) * 8f
                    dice.angVel.z += (Math.random().toFloat() - 0.5f) * 8f
                    dice.isSleeping = false
                    dice.sleepTimer = 0f
                }
            }
        }
    }
}
