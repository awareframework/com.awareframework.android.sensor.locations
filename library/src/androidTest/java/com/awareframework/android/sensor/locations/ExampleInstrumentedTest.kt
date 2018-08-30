package com.awareframework.android.sensor.locations

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.awareframework.android.core.db.Engine
import com.awareframework.android.sensor.locations.model.LocationsData
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 * <p>
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()

        LocationsSensor.start(appContext, LocationsSensor.Config().apply {
            sensorObserver = object : LocationsSensor.Observer {
                override fun onLocationChanged(data: LocationsData) {
                    // your code here...
                }
            }
            dbType = Engine.DatabaseType.ROOM
            debug = true
            // more configuration...
        })
    }
}
