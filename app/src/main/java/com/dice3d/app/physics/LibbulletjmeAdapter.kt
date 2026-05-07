package com.dice3d.app.physics

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.HullCollisionShape
import com.jme3.bullet.collision.shapes.PlaneCollisionShape
import com.jme3.bullet.objects.PhysicsBody
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Plane
import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import com.dice3d.app.engine.DiceMesh

class LibbulletjmeAdapter : PhysicsAdapter {

    private lateinit var physicsSpace: PhysicsSpace
    private val bodies = mutableListOf<LibbulletjmeBody>()
    private var collisionListener: ((Float) -> Unit)? = null

    override fun initialize() {
        physicsSpace = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
        physicsSpace.setGravity(Vector3f(0f, -9.81f, 0f))

        addGround()
        addWalls()
    }

    private fun addGround() {
        val groundShape = PlaneCollisionShape(Plane(Vector3f.UNIT_Y, 0f))
        val ground = PhysicsRigidBody(groundShape, PhysicsBody.massForStatic)
        ground.friction = 0.9f
        ground.restitution = 0.1f
        physicsSpace.addCollisionObject(ground)
    }

    private fun addWalls() {
        val wallDistance = 4f
        val wallHalfThickness = 0.05f
        val wallHalfHeight = 5f

        val positions = arrayOf(
            Vector3f(-wallDistance, wallHalfHeight, 0f),
            Vector3f(wallDistance, wallHalfHeight, 0f),
            Vector3f(0f, wallHalfHeight, -wallDistance),
            Vector3f(0f, wallHalfHeight, wallDistance)
        )

        val halfExtents = arrayOf(
            Vector3f(wallHalfThickness, wallHalfHeight, wallDistance),
            Vector3f(wallHalfThickness, wallHalfHeight, wallDistance),
            Vector3f(wallDistance, wallHalfHeight, wallHalfThickness),
            Vector3f(wallDistance, wallHalfHeight, wallHalfThickness)
        )

        for (i in positions.indices) {
            val wallShape = BoxCollisionShape(halfExtents[i])
            val wall = PhysicsRigidBody(wallShape, PhysicsBody.massForStatic)
            wall.setPhysicsLocation(positions[i])
            wall.friction = 0.5f
            wall.restitution = 0.3f
            physicsSpace.addCollisionObject(wall)
        }
    }

    override fun createDiceBody(id: Int, mesh: DiceMesh, boundingRadius: Float): DicePhysicsBody {
        val collisionShape = HullCollisionShape(*mesh.vertices)
        val mass = 0.1f
        val rigidBody = PhysicsRigidBody(collisionShape, mass)
        rigidBody.friction = 0.8f
        rigidBody.restitution = 0.2f
        rigidBody.setSleepingThresholds(0.01f, 0.02f)
        rigidBody.setAngularDamping(0.1f)

        physicsSpace.addCollisionObject(rigidBody)

        val body = LibbulletjmeBody(id, rigidBody, mesh)
        bodies.add(body)
        return body
    }

    override fun step(dt: Float) {
        physicsSpace.update(dt)
        checkCollisions()
    }

    private fun checkCollisions() {
        val threshold = 2f
        for (body in bodies) {
            val vel = body.rigidBody.getLinearVelocity(null)
            val speed = vel.length()
            if (speed > threshold) {
                collisionListener?.invoke(speed.coerceAtMost(10f))
            }
        }
    }

    override fun clear() {
        for (body in bodies) {
            physicsSpace.removeCollisionObject(body.rigidBody)
        }
        bodies.clear()
    }

    override fun getAllBodies(): List<DicePhysicsBody> = bodies.toList()

    override fun setOnCollisionListener(listener: ((Float) -> Unit)?) {
        collisionListener = listener
    }
}

private class LibbulletjmeBody(
    override val id: Int,
    val rigidBody: PhysicsRigidBody,
    private val mesh: DiceMesh
) : DicePhysicsBody {

    override var position: FloatArray
        get() {
            val pos = rigidBody.getPhysicsLocation(null)
            return floatArrayOf(pos.x, pos.y, pos.z)
        }
        set(value) {
            rigidBody.setPhysicsLocation(Vector3f(value[0], value[1], value[2]))
        }

    override var orientation: FloatArray
        get() {
            val q = rigidBody.getPhysicsRotation(null as Quaternion?)
            return floatArrayOf(q.x, q.y, q.z, q.w)
        }
        set(value) {
            val q = Quaternion(value[0], value[1], value[2], value[3])
            rigidBody.setPhysicsRotation(q)
        }

    override var linearVelocity: FloatArray
        get() {
            val vel = rigidBody.getLinearVelocity(null)
            return floatArrayOf(vel.x, vel.y, vel.z)
        }
        set(value) {
            rigidBody.setLinearVelocity(Vector3f(value[0], value[1], value[2]))
        }

    override var angularVelocity: FloatArray
        get() {
            val angVel = rigidBody.getAngularVelocity(null)
            return floatArrayOf(angVel.x, angVel.y, angVel.z)
        }
        set(value) {
            rigidBody.setAngularVelocity(Vector3f(value[0], value[1], value[2]))
        }

    override var isSleeping: Boolean
        get() = !rigidBody.isActive
        set(value) { if (!value) rigidBody.activate() }

    override fun getUpFace(): Int {
        val rot = rigidBody.getPhysicsRotation(null as Quaternion?)
        val rotMat = rot.toRotationMatrix()
        val upVector = Vector3f(0f, 1f, 0f)
        var bestDot = -2f
        var bestFace = 1

        for (faceInfo in mesh.faceInfos) {
            val normal = Vector3f(faceInfo.faceNormal[0], faceInfo.faceNormal[1], faceInfo.faceNormal[2])
            val rotatedNormal = rotMat.mult(normal, null)
            val dot = rotatedNormal.dot(upVector)
            if (dot > bestDot) {
                bestDot = dot
                bestFace = faceInfo.faceNumber
            }
        }
        return bestFace
    }

    override fun getTransformMatrix(): FloatArray {
        val result = FloatArray(16)
        val pos = rigidBody.getPhysicsLocation(null)
        val rot = rigidBody.getPhysicsRotation(null as Quaternion?)
        val mat = rot.toRotationMatrix()

        result[0] = mat.get(0, 0); result[1] = mat.get(1, 0); result[2] = mat.get(2, 0); result[3] = 0f
        result[4] = mat.get(0, 1); result[5] = mat.get(1, 1); result[6] = mat.get(2, 1); result[7] = 0f
        result[8] = mat.get(0, 2); result[9] = mat.get(1, 2); result[10] = mat.get(2, 2); result[11] = 0f
        result[12] = pos.x; result[13] = pos.y; result[14] = pos.z; result[15] = 1f

        return result
    }

    override fun applyImpulse(ix: Float, iy: Float, iz: Float) {
        rigidBody.applyCentralImpulse(Vector3f(ix, iy, iz))
    }

    override fun applyAngularImpulse(ax: Float, ay: Float, az: Float) {
        rigidBody.applyTorqueImpulse(Vector3f(ax, ay, az))
    }

    override fun wakeUp() {
        rigidBody.activate()
    }
}
