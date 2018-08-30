package com.awareframework.android.sensor.locations.model

import com.awareframework.android.core.model.AwareObject

/**
 * Contains the locations data.
 *
 * @author  sercant
 * @date 30/08/2018
 */
data class LocationsData(
        var latitude: Double? = null,
        var longitude: Double? = null,
        var bearing: Float? = null,
        var speed: Float? = null,
        var altitude: Double? = null,
        var provider: String? = null,
        var accuracy: Float = 0f
) : AwareObject(jsonVersion = 1) {

    companion object {
        const val TABLE_NAME = "locationsData"
    }

    override fun toString(): String = toJson()
}