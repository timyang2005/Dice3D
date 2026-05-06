package com.dice3d.app.physics

import com.dice3d.app.engine.DiceMesh

interface PhysicsAdapter {
    fun initialize()
    fun createDiceBody(id: Int, mesh: DiceMesh, boundingRadius: Float): DicePhysicsBody
    fun step(dt: Float)
    fun clear()
    fun getAllBodies(): List<DicePhysicsBody>
    fun setOnCollisionListener(listener: ((Float) -> Unit)?)
}

interface DicePhysicsBody {
    val id: Int
    var position: FloatArray
    var orientation: FloatArray
    var linearVelocity: FloatArray
    var angularVelocity: FloatArray
    var isSleeping: Boolean

    fun getUpFace(): Int
    fun getTransformMatrix(): FloatArray
    fun applyImpulse(ix: Float, iy: Float, iz: Float)
    fun applyAngularImpulse(ax: Float, ay: Float, az: Float)
    fun wakeUp()
}
