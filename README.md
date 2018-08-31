# AWARE Locations

[![jitpack-badge](https://jitpack.io/v/awareframework/com.aware.android.sensor.locations.svg)](https://jitpack.io/#awareframework/com.aware.android.sensor.locations)

The locations sensor provides the best location estimate for the users’ current location, automatically. We have built-in an [algorithm][2] that provides the user’s location with a minimum battery impact. However, we offer the flexibility to researchers to change how frequently the location gets updated, the minimum accuracy and others. In our endurance tests, we got a full day of location updates (8h and higher, depending on device usage) from the user with the default parameters.

## Public functions

### LocationsSensor

+ `start(context: Context, config: LocationsSensor.Config?)`: Starts the locations sensor with the optional configuration.
+ `stop(context: Context)`: Stops the service.

### LocationsSensor.Config

Class to hold the configuration of the sensor.

#### Fields

+ `sensorObserver: Observer?` Callback for live data updates. (default = `null`)
+ `geoFences: String?` Geofences that are going to be checked on the location updates. If within the range of these fences, then the location is accepted as a permitted update. If null, all location updates are accepted as permitted. String follows the regex in format `(?:latitude),(?:longitude)[ \t;]+`. (default = `null`)
+ `statusGps: Boolean` true or false to activate or deactivate GPS locations. (default = `true`)
+ `statusNetwork: Boolean` true or false to activate or deactivate Network locations. (default = `true`)
+ `statusPassive: Boolean` true or false to activate or deactivate passive locations. (default = `true`)
+ `frequencyGps: Int` how frequent to check the GPS location, in seconds. By default, every 180 seconds. Setting to 0 (zero) will keep the GPS location tracking always on. (default = 180)
+ `minGpsAccuracy: Int`  the minimum acceptable accuracy of GPS location, in meters. By default, 150 meters. Setting to 0 (zero) will keep the GPS location tracking always on. (default = 150)
+ `frequencyNetwork: Int` how frequently to check the network location, in seconds. By default, every 300 seconds. Setting to 0 (zero) will keep the network location tracking always on. (default = 300)
+ `minNetworkAccuracy: Int` the minimum acceptable accuracy of network location, in meters. By default, 1500 meters. Setting to 0 (zero) will keep the network location tracking always on. (default = 1500)
+ `expirationTime: Long` the amount of elapsed time, in seconds, until the location is considered outdated. By default, 300 seconds. (default = 300)
+ `saveAll: Boolean` Whether to save all the location updates or not. (default = `false`)
+ `enabled: Boolean` Sensor is enabled or not. (default = `false`)
+ `debug: Boolean` enable/disable logging to `Logcat`. (default = `false`)
+ `label: String` Label for the data. (default = "")
+ `deviceId: String` Id of the device that will be associated with the events and the sensor. (default = "")
+ `dbEncryptionKey` Encryption key for the database. (default = `null`)
+ `dbType: Engine` Which db engine to use for saving data. (default = `Engine.DatabaseType.NONE`)
+ `dbPath: String` Path of the database. (default = "aware_locations")
+ `dbHost: String` Host for syncing the database. (default = `null`)

## Broadcasts

### Fired Broadcasts

+ `LocationsSensor.ACTION_AWARE_LOCATIONS` fired when new location available.
+ `LocationsSensor.ACTION_AWARE_GPS_LOCATION_ENABLED` fired when GPS location is active.
+ `LocationsSensor.ACTION_AWARE_NETWORK_LOCATION_ENABLED` fired when network location is active.
+ `LocationsSensor.ACTION_AWARE_GPS_LOCATION_DISABLED` fired when GPS location disabled.
+ `LocationsSensor.ACTION_AWARE_NETWORK_LOCATION_DISABLED` fired when network location disabled.

### Received Broadcasts

+ `LocationsSensor.ACTION_AWARE_LOCATIONS_START`: received broadcast to start the sensor.
+ `LocationsSensor.ACTION_AWARE_LOCATIONS_STOP`: received broadcast to stop the sensor.
+ `LocationsSensor.ACTION_AWARE_LOCATIONS_SYNC`: received broadcast to send sync attempt to the host.
+ `LocationsSensor.ACTION_AWARE_LOCATIONS_SET_LABEL`: received broadcast to set the data label. Label is expected in the `LocationsSensor.EXTRA_LABEL` field of the intent extras.

## Data Representations

### Locations Data

Contains the locations profiles.

| Field     | Type   | Description                                                     |
| --------- | ------ | --------------------------------------------------------------- |
| latitude  | Double | the location’s latitude, in degrees                            |
| longitude | Double | the location’s longitude, in degrees                           |
| bearing   | Float  | the location’s bearing, in degrees                             |
| speed     | Float  | the speed if available, in meters/second over ground            |
| altitude  | Double | the altitude if available, in meters above sea level            |
| provider  | String | gps or network                                                  |
| accuracy  | Float  | the estimated location accuracy                                 |
| deviceId  | String | AWARE device UUID                                               |
| label     | String | Customizable label. Useful for data calibration or traceability |
| timestamp | Long   | unixtime milliseconds since 1970                                |
| timezone  | Int    | [Raw timezone offset][1] of the device                          |
| os        | String | Operating system of the device (ex. android)                    |

## Example usage

```kotlin
// To start the service.
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

// To stop the service
LocationsSensor.stop(appContext)
```

## License

Copyright (c) 2018 AWARE Mobile Context Instrumentation Middleware/Framework (http://www.awareframework.com)

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[1]: https://developer.android.com/reference/java/util/TimeZone#getRawOffset()
[2]: http://developer.android.com/guide/topics/location/strategies.html