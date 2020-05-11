package com.teampower.cicerone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class GeofenceBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "Geofencer"
    private val api: RestAPI = RestAPI()
    private lateinit var poiSerialized: String
    private lateinit var poiObject: POI

    override fun onReceive(context: Context?, intent: Intent?) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        Log.i(TAG, "Received geofence")
        if (geofencingEvent.hasError()) {
            val errorMessage = "Error when receiving geofencing event"
            Log.e(TAG, errorMessage)
        }

        // Get the transition type
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Test that the reported transition was of interest
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL
        ) {

            // Get the geofences that were triggered. A single even can trigger
            // multiple geofences
            val triggeringGeofence =
                geofencingEvent.triggeringGeofences[0] // TODO: Remove geofence when used up.


            // TODO: This is basically demo #2 for mid-term - only need to get Wikipedia info as string
            // Extract the transitionDetails
            poiSerialized = intent?.getStringExtra("POI")
                ?: "" // TODO: Handle error case better. This works only assuming we always get a serialized POI object
            poiObject = MainActivity.fromJson(poiSerialized)
            Log.i(TAG, "id" + triggeringGeofence.requestId + " got the poi name " + poiObject.name)

            getPlaceInfoSendNotification(context)
        } else {
            // Log the error
            Log.e(TAG, "Error in Geofencer - should log error here one of these days")
        }
    }

    private fun sendNotification(
        context: Context,
        title: String,
        poiObject: POI,
        placeDetailsJson: String
    ) {
        val contentText =
            "You are close to ${poiObject.name} (${poiObject.distance}m). Want to go have a look?"
        val notCon = NotificationsController()
        notCon.sendNotificationTriggeredGeofence(
            context,
            title,
            contentText,
            1337,
            placeDetailsJson
        )
    }

    private fun getPlaceInfoSendNotification(context: Context?): WikipediaPlaceInfo? {
        var placeInfo: WikipediaPlaceInfo? = null
        // Query wikipedia
        val filterName = poiObject.name.split(" (")[0]
        api.getPlaceInfo(filterName)
            .enqueue(object : Callback<WikipediaPlaceInfo> {
                override fun onResponse(
                    call: Call<WikipediaPlaceInfo>,
                    response: Response<WikipediaPlaceInfo>
                ) {
                    placeInfo = response.body()

                    println("placeInfo for ${poiObject.name}: $placeInfo")
                    // Log the information
                    Log.i(TAG, placeInfo.toString())

                    // Combine info into one object
                    val combinedInfo = PlaceDetails(poiObject, placeInfo)

                    // Send notification and log the transition details
                    if (context != null) {
                        sendNotification(
                            context,
                            "Cicerone geofence",
                            poiObject,
                            MainActivity.toJson(combinedInfo)
                        )
                    }
                    Log.i(TAG, "" + combinedInfo.wikipediaInfo?.extract)
                }

                override fun onFailure(call: Call<WikipediaPlaceInfo>, t: Throwable) =
                    t.printStackTrace()
            })
        return placeInfo
    }
}