package com.example.hymate.vehicle

import com.example.hymate.data.Vehicle
import com.example.hymate.data.VehicleSignal
import kotlin.math.abs

const val SPEED_TIMEOUT_NANOS = 2_000_000_000L
private const val STOP_SPEED_MPS = 0.5f / 3.6f

fun Vehicle.hasFreshSpeed(nowNanos: Long): Boolean =
    speedMps?.isFinite() == true && speedUpdatedAtNanos > 0L &&
        nowNanos - speedUpdatedAtNanos in 0..SPEED_TIMEOUT_NANOS

/** 차량 속도 × 실제 경과 시간으로만 진행해요. 데이터 공백은 나중에 몰아서 이동하지 않아요. */
class LiveDriveSession(private val allowLocationSnap: Boolean = true) {
    var distanceMeters = 0f; private set
    var elapsedSeconds = 0f; private set
    var stopped = false; private set
    var speedAvailable = false; private set
    private var lastNanos: Long? = null
    private var stoppedSince: Long? = null

    fun tick(vehicle: Vehicle, nowNanos: Long) {
        val previous = lastNanos
        lastNanos = nowNanos
        val wasAvailable = speedAvailable
        speedAvailable = vehicle.hasFreshSpeed(nowNanos)
        val continuous = previous != null && nowNanos - previous in 0..1_000_000_000L
        if (!continuous) stoppedSince = null
        val dt = if (continuous && speedAvailable && wasAvailable) {
            (nowNanos - previous) / 1e9f
        } else 0f
        elapsedSeconds += dt
        val speed = vehicle.speedMps ?: 0f
        val parked = VehicleSignal.GEAR in vehicle.knownSignals && vehicle.parked
        // 후진은 예시 경로의 전진 거리로 더하지 않아요.
        if (speedAvailable && !parked && speed > STOP_SPEED_MPS) {
            distanceMeters += speed * dt
        }
        val holding = parked || (VehicleSignal.BRAKE in vehicle.knownSignals && vehicle.brake)
        if (speedAvailable && abs(speed) <= STOP_SPEED_MPS && holding) {
            val since = stoppedSince ?: nowNanos.also { stoppedSince = it }
            stopped = nowNanos - since >= 3_000_000_000L
        } else {
            stoppedSince = null
            stopped = false
        }
    }

    /** 작은 GPS 후퇴(30m 이하)는 무시하고, 큰 적분 오차는 앞뒤 모두 보정해요. */
    fun syncDistance(meters: Float) {
        if (!allowLocationSnap || !meters.isFinite() || meters < 0f) return
        if (meters >= distanceMeters || distanceMeters - meters > 30f) distanceMeters = meters
    }

    fun remainingSeconds(destinationMeters: Float, vehicle: Vehicle): Float? {
        if (!speedAvailable || vehicle.speedMps == null || vehicle.speedMps <= STOP_SPEED_MPS ||
            (VehicleSignal.GEAR in vehicle.knownSignals && vehicle.parked)) return null
        return (destinationMeters - distanceMeters).coerceAtLeast(0f) / vehicle.speedMps
    }
}
