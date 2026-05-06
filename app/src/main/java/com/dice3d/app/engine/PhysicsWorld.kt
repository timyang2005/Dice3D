package com.dice3d.app.engine

import cz.advel.jbullet.BulletGlobals
import cz.advel.jbullet.collision.broadphase.DbvtBroadphase
import cz.advel.jbullet.collision.dispatch.CollisionConfiguration
import cz.advel.jbullet.collision.dispatch.CollisionDispatcher
import cz.advel.jbullet.collision.dispatch.DefaultCollisionConfiguration
import cz.advel.jbullet.collision.shapes.ConvexHullShape
import cz.advel.jbullet.collision.shapes.StaticPlaneShape
import cz.advel.jbullet.dynamics.DiscreteDynamicsWorld
import cz.advel.jbullet.dynamics.RigidBody
import cz.advel.jbullet.dynamics.RigidBodyConstructionInfo
import cz.advel.jbullet.linearmath.DefaultMotionState
import cz.advel.jbullet.linearmath.Transform
import javax.vecmath.Vector3f
import javax.vecmath.Matrix3f
import javax.vecmath.Matrix4f
import javax.vecmath.Quat4f
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
    val boundingRadius: Float = 0.5f
) {
    var rigidBody: RigidBody? = null

    var posX: Float = 0f
        get() {
            rigidBody?.let { rb ->
                val t = Transform()
                rb.getMotionState().getWorldTransform(t)
                field = t.origin.x
            }
            return field
        }
    var posY: Float = 2f
        get() {
            rigidBody?.let { rb ->
                val t = Transform()
                rb.getMotionState().getWorldTransform(t)
                field = t.origin.y
            }
            return field
        }
    var posZ: Float = 0f
        get() {
            rigidBody?.let { rb ->
                val t = Transform()
                rb.getMotionState().getWorldTransform(t)
                field = t.origin.z
            }
            return field
        }

    var orientation: Quaternion = Quaternion()
        get() {
            rigidBody?.let { rb ->
                val t = Transform()
                rb.getMotionState().getWorldTransform(t)
                field.x = t.basis.getQuaternion(Quat4f()).x
                field.y = t.basis.getQuaternion(Quat4f()).y
                field.z = t.basis.getQuaternion(Quat4f()).z
                field.w = t.basis.getQuaternion(Quat4f()).w
            }
            return field
        }

    var isSleeping: Boolean = false
        get() {
            rigidBody?.let { rb ->
                field = rb.isActive == false
            }
            return field
        }

    fun getUpFace(): Int {
        val upVector = floatArrayOf(0f, 1f, 0f)
        var bestDot = -2f
        var bestFace = 1

        val currentOrientation = orientation
        for (faceInfo in mesh.faceInfos) {
            val rotatedNormal = currentOrientation.rotateVector(faceInfo.faceNormal)
            val dot = rotatedNormal[0] * upVector[0] + rotatedNormal[1] * upVector[1] + rotatedNormal[2] * upVector[2]
            if (dot > bestDot) {
                bestDot = dot
                bestFace = faceInfo.faceNumber
            }
        }
        return bestFace
    }

    fun getTransformMatrix(): FloatArray {
        val rb = rigidBody ?: return FloatArray(16) { if (it % 5 == 0) 1f else 0f }
        val t = Transform()
        rb.getMotionState().getWorldTransform(t)

        val result = FloatArray(16)
        val m = Matrix4f()
        t.getMatrix(m)

        for (i in 0..3) {
            for (j in 0..3) {
                result[j * 4 + i] = m.getElement(i, j)
            }
        }
        return result
    }
}

class PhysicsWorld {
    private val diceBodies = mutableListOf<DiceBody>()
    private val dynamicsWorld: DiscreteDynamicsWorld
    private val wallLimit = 4f

    var onCollision: ((Float) -> Unit)? = null

    init {
        val collisionConfig: CollisionConfiguration = DefaultCollisionConfiguration()
        val dispatcher = CollisionDispatcher(collisionConfig)
        val broadphase = DbvtBroadphase()
        dynamicsWorld = DiscreteDynamicsWorld(dispatcher, broadphase)
        dynamicsWorld.setGravity(Vector3f(0f, -15f, 0f))

        val groundShape = StaticPlaneShape(Vector3f(0f, 1f, 0f), 0f)
        val groundMotionState = DefaultMotionState(Transform().apply {
            setIdentity()
        })
        val groundCi = RigidBodyConstructionInfo(0f, groundMotionState, groundShape)
        val groundBody = RigidBody(groundCi)
        groundBody.setRestitution(0.3f)
        groundBody.setFriction(0.6f)
        dynamicsWorld.addRigidBody(groundBody)

        BulletGlobals.setDeactivationTime(0.8f)
    }

    fun addDice(body: DiceBody) {
        synchronized(diceBodies) {
            val points = mutableListOf<Vector3f>()
            for (i in body.mesh.vertices.indices step 3) {
                points.add(Vector3f(
                    body.mesh.vertices[i],
                    body.mesh.vertices[i + 1],
                    body.mesh.vertices[i + 2]
                ))
            }
            val shape = ConvexHullShape(points)
            shape.setMargin(0.02f)
            shape.recalcLocalAabb()

            val mass = 1f
            val localInertia = Vector3f()
            shape.calculateLocalInertia(mass, localInertia)

            val startTransform = Transform()
            startTransform.setIdentity()
            startTransform.origin.set(body.posX, body.posY, body.posZ)

            val motionState = DefaultMotionState(startTransform)
            val ci = RigidBodyConstructionInfo(mass, motionState, shape, localInertia)
            val rb = RigidBody(ci)
            rb.setRestitution(0.35f)
            rb.setFriction(0.5f)
            rb.setDamping(0.1f, 0.4f)
            rb.setActivationState(RigidBody.ISLAND_SLEEPING)

            body.rigidBody = rb
            dynamicsWorld.addRigidBody(rb)
            diceBodies.add(body)
        }
    }

    fun removeDice(id: Int) {
        synchronized(diceBodies) {
            val toRemove = diceBodies.filter { it.id == id }
            for (body in toRemove) {
                body.rigidBody?.let { dynamicsWorld.removeRigidBody(it) }
            }
            diceBodies.removeAll { it.id == id }
        }
    }

    fun clearDice() {
        synchronized(diceBodies) {
            for (body in diceBodies) {
                body.rigidBody?.let { dynamicsWorld.removeRigidBody(it) }
            }
            diceBodies.clear()
        }
    }

    fun getDice(): List<DiceBody> {
        synchronized(diceBodies) {
            return diceBodies.toList()
        }
    }

    fun step(dt: Float) {
        synchronized(diceBodies) {
            dynamicsWorld.stepSimulation(dt, 4, dt / 4f)

            for (body in diceBodies) {
                val rb = body.rigidBody ?: continue
                val t = Transform()
                rb.getMotionState().getWorldTransform(t)

                if (t.origin.x < -wallLimit) {
                    t.origin.x = -wallLimit
                    val vel = rb.getLinearVelocity(Vector3f())
                    vel.x = abs(vel.x) * 0.3f
                    rb.setLinearVelocity(vel)
                    rb.getMotionState().setWorldTransform(t)
                    onCollision?.invoke(abs(vel.x))
                }
                if (t.origin.x > wallLimit) {
                    t.origin.x = wallLimit
                    val vel = rb.getLinearVelocity(Vector3f())
                    vel.x = -abs(vel.x) * 0.3f
                    rb.setLinearVelocity(vel)
                    rb.getMotionState().setWorldTransform(t)
                    onCollision?.invoke(abs(vel.x))
                }
                if (t.origin.z < -wallLimit) {
                    t.origin.z = -wallLimit
                    val vel = rb.getLinearVelocity(Vector3f())
                    vel.z = abs(vel.z) * 0.3f
                    rb.setLinearVelocity(vel)
                    rb.getMotionState().setWorldTransform(t)
                    onCollision?.invoke(abs(vel.z))
                }
                if (t.origin.z > wallLimit) {
                    t.origin.z = wallLimit
                    val vel = rb.getLinearVelocity(Vector3f())
                    vel.z = -abs(vel.z) * 0.3f
                    rb.setLinearVelocity(vel)
                    rb.getMotionState().setWorldTransform(t)
                    onCollision?.invoke(abs(vel.z))
                }
            }

            val numManifolds = dynamicsWorld.getDispatcher().getNumManifolds()
            for (i in 0 until numManifolds) {
                val manifold = dynamicsWorld.getDispatcher().getManifoldByIndexInternal(i)
                val numContacts = manifold.getNumContacts()
                if (numContacts > 0) {
                    var maxImpulse = 0f
                    for (j in 0 until numContacts) {
                        val pt = manifold.getContactPoint(j)
                        val impulse = abs(pt.getAppliedImpulse())
                        if (impulse > maxImpulse) maxImpulse = impulse
                    }
                    if (maxImpulse > 0.5f) {
                        onCollision?.invoke(maxImpulse / 10f)
                    }
                }
            }
        }
    }

    fun allDiceStopped(): Boolean {
        synchronized(diceBodies) {
            return diceBodies.all { it.rigidBody?.isActive == false }
        }
    }

    fun throwDice() {
        synchronized(diceBodies) {
            for (body in diceBodies) {
                val rb = body.rigidBody ?: continue
                val spread = diceBodies.size * 0.3f
                val posX = (Math.random().toFloat() - 0.5f) * spread
                val posY = 3f + Math.random().toFloat() * 2f
                val posZ = (Math.random().toFloat() - 0.5f) * spread

                val t = Transform()
                t.setIdentity()
                t.origin.set(posX, posY, posZ)

                val axis = Vector3f(
                    Math.random().toFloat(),
                    Math.random().toFloat(),
                    Math.random().toFloat()
                )
                axis.normalize()
                val angle = Math.random().toFloat() * 6.28f
                val q = Quat4f()
                q.set(axis, angle)
                t.basis.set(q)

                rb.getMotionState().setWorldTransform(t)
                rb.setWorldTransform(t)

                rb.setLinearVelocity(Vector3f(
                    (Math.random().toFloat() - 0.5f) * 6f,
                    -2f + Math.random().toFloat() * 2f,
                    (Math.random().toFloat() - 0.5f) * 6f
                ))
                rb.setAngularVelocity(Vector3f(
                    (Math.random().toFloat() - 0.5f) * 15f,
                    (Math.random().toFloat() - 0.5f) * 15f,
                    (Math.random().toFloat() - 0.5f) * 15f
                ))

                rb.setActivationState(RigidBody.ACTIVE_TAG)
            }
        }
    }

    fun applyBounce() {
        synchronized(diceBodies) {
            for (body in diceBodies) {
                val rb = body.rigidBody ?: continue
                if (rb.isActive) {
                    val vel = rb.getLinearVelocity(Vector3f())
                    vel.y += 5f + Math.random().toFloat() * 3f
                    vel.x += (Math.random().toFloat() - 0.5f) * 3f
                    vel.z += (Math.random().toFloat() - 0.5f) * 3f
                    rb.setLinearVelocity(vel)

                    val angVel = rb.getAngularVelocity(Vector3f())
                    angVel.x += (Math.random().toFloat() - 0.5f) * 8f
                    angVel.y += (Math.random().toFloat() - 0.5f) * 8f
                    angVel.z += (Math.random().toFloat() - 0.5f) * 8f
                    rb.setAngularVelocity(angVel)

                    rb.setActivationState(RigidBody.ACTIVE_TAG)
                }
            }
        }
    }
}
