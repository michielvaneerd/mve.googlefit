package mve.googlefit


// If we implement OnActivityResultEvent interface, we can receive native OnActivityResult events.
// See: https://github.com/appcelerator/titanium_mobile/blob/master/android/titanium/src/java/org/appcelerator/titanium/TiLifecycle.java

// If we implement OnActivityResultEvent interface, we can receive native OnActivityResult events.
// See: https://github.com/appcelerator/titanium_mobile/blob/master/android/titanium/src/java/org/appcelerator/titanium/TiLifecycle.java

// If we implement OnActivityResultEvent interface, we can receive native OnActivityResult events.
// See: https://github.com/appcelerator/titanium_mobile/blob/master/android/titanium/src/java/org/appcelerator/titanium/TiLifecycle.java

import org.appcelerator.kroll.KrollModule
import org.appcelerator.kroll.annotations.Kroll

import org.appcelerator.titanium.TiApplication
import org.appcelerator.kroll.common.Log
import org.appcelerator.kroll.common.TiConfig

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType

import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.Bucket
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.fitness.data.DataSource
import java.util.*
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.app.Activity
import org.appcelerator.titanium.TiBaseActivity
import org.appcelerator.titanium.util.TiConvert
import org.appcelerator.kroll.KrollDict
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.OnFailureListener
import java.text.DateFormat
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.Field
import java.text.SimpleDateFormat
import org.appcelerator.kroll.KrollFunction
// If we implement OnActivityResultEvent interface, we can receive native OnActivityResult events.
// See: https://github.com/appcelerator/titanium_mobile/blob/master/android/titanium/src/java/org/appcelerator/titanium/TiLifecycle.java
import org.appcelerator.titanium.TiLifecycle.OnActivityResultEvent

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.Task
import com.google.android.gms.fitness.RecordingClient
import java.util.ArrayList
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.fitness.data.Subscription
import com.google.android.gms.fitness.data.DataPoint
import java.util.HashMap


const val LCAT = "MveGoogleFit"

// See https://docs.appcelerator.com/module-apidoc/latest/android/org/appcelerator/kroll/common/TiConfig.html
// Takes value from tiap.xml:
// <property name="ti.android.debug" type="bool">false</property>
val DBG = TiConfig.LOGD

@Kroll.module(name = "MveGoogleFit", id = "mve.googlefit")
class MveGoogleFitModule : KrollModule(), OnActivityResultEvent {

    private var fitnessOptions: FitnessOptions? = null
    private var dataSource: DataSource? = null
    private var callbackAfterPermission: KrollFunction? = null
    private val dataTypeHash: HashMap<String, DataTypeInfo> = HashMap<String, DataTypeInfo>()
    private var activeDataTypes: Array<String>? = null
    private var writeAccess = false

    internal class DataTypeInfo(dataType: DataType, aggregateDataType: DataType) {
        var dataType: DataType = dataType
        var aggregateDataType: DataType = aggregateDataType
    }

    @Kroll.constant
    val TYPE_STEP_COUNT_DELTA = "com.google.step_count.delta"

    @Kroll.constant
    val TYPE_MOVE_MINUTES = "com.google.active_minutes"

    @Kroll.constant
    val TYPE_CALORIES_EXPENDED = "com.google.calories.expended"

    @Kroll.constant
    val TYPE_HEART_POINTS = "com.google.heart_minutes"

    @Kroll.constant
    val TYPE_HEART_RATE_BPM = "com.google.heart_rate.bpm"

    companion object {

        private const val MY_PERMISSION_GOOGLE_SIGNIN = 2

        @JvmStatic
        @Kroll.onAppCreate
        fun onAppCreate(app: TiApplication) {

        }

    }

    private fun permissionCallback(error: String?) {
        if (callbackAfterPermission != null) {
            val args = KrollDict()
            if (error != null) {
                args.put("error", error);
            }
            callbackAfterPermission?.call(getKrollObject(), args);
            callbackAfterPermission = null;
        }
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        Utils.log("In onActivityResult for requestCode $resultCode")
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == MY_PERMISSION_GOOGLE_SIGNIN) {
                permissionCallback(null);
            } else {
                permissionCallback("Permission error");
            }
        }
    }

    /**
     * This should be called at first.
     * @param dataTypes: all datatypes you want to read and/or write
     * @param write: true if you want to write (make sure you have the ACTIVITY_RECOGNITION permission!)
     */
    @Kroll.method
    fun setDataTypes(dataTypes: Array<String>, write: Boolean) {

        dataTypeHash[TYPE_STEP_COUNT_DELTA] = DataTypeInfo(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
        dataTypeHash[TYPE_MOVE_MINUTES] = DataTypeInfo(DataType.TYPE_MOVE_MINUTES, DataType.AGGREGATE_MOVE_MINUTES)
        dataTypeHash[TYPE_CALORIES_EXPENDED] = DataTypeInfo(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
        dataTypeHash[TYPE_HEART_POINTS] = DataTypeInfo(DataType.TYPE_HEART_POINTS, DataType.AGGREGATE_HEART_POINTS)
        dataTypeHash[TYPE_HEART_RATE_BPM] = DataTypeInfo(DataType.TYPE_HEART_RATE_BPM, DataType.AGGREGATE_HEART_RATE_SUMMARY)

        activeDataTypes = dataTypes
        writeAccess = write

        // https://developers.google.com/fit/faq#how_do_i_get_the_same_step_count_as_the_google_fit_app
        // https://developers.google.com/fit/faq#values_for_stepdistanceactive_timecalories_do_not_match_those_of_fit_app
        dataSource = DataSource.Builder()
                .setAppPackageName("com.google.android.gms")
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setType(DataSource.TYPE_DERIVED)
                .setStreamName("estimated_steps")
                .build()
        val builder = FitnessOptions.builder()
        for (name in activeDataTypes!!) {
            Utils.log("Add datatype $name")
            builder.addDataType(dataTypeHash[name]?.dataType!!, FitnessOptions.ACCESS_READ)
            if (writeAccess) {
                builder.addDataType(dataTypeHash[name]?.dataType!!, FitnessOptions.ACCESS_WRITE)
            }
            builder.addDataType(dataTypeHash[name]?.aggregateDataType!!, FitnessOptions.ACCESS_READ)
        }
        fitnessOptions = builder.build()
    }

    /**
     * Returns true if we already have the OAuth2 permissions.
     */
    @Kroll.method
    fun hasPermission(): Boolean {
        val activity = TiApplication.getInstance().currentActivity
        val account = GoogleSignIn.getAccountForExtension(activity, fitnessOptions!!)
        return GoogleSignIn.hasPermissions(account, fitnessOptions!!)
    }

    /**
     * Requests OAuth2 permissions.
     * Will show a dialog where the user can agree to which data the app will retrieve.
     * Note: make sure you have set your app id in the google api console first, otherwise this won't work
     * and won't show a dialog.
     * @param callback: Called after successful or unsuccessful request. See arg.error if there is an error.
     */
    @Kroll.method
    fun requestPermission(callback: KrollFunction) {
        val activity = TiApplication.getInstance().currentActivity
        callbackAfterPermission = callback
        (activity as TiBaseActivity).addOnActivityResultListener(this)
        val account = GoogleSignIn.getAccountForExtension(activity, fitnessOptions!!)
        GoogleSignIn.requestPermissions(activity, MY_PERMISSION_GOOGLE_SIGNIN, account, fitnessOptions!!)
    }

    /**
     * Disconnect from Google Fit
     * Note will also remove all subscriptions.
     */
    @Kroll.method
    fun disable() {
        val activity = TiApplication.getInstance().currentActivity
        Fitness.getConfigClient(activity, GoogleSignIn.getLastSignedInAccount(activity)!!).disableFit()
    }

    /**
     * Remove subscriptions.
     * @param callback: Called after removing subscriptions. See arg.error if there is an error.
     */
    @Kroll.method
    fun unsubscribe(callback: KrollFunction?) {
        val activity = TiApplication.getInstance().currentActivity
        val client = Fitness.getRecordingClient(activity, GoogleSignIn.getLastSignedInAccount(activity)!!)
        client.listSubscriptions().continueWithTask { task ->
            val tasks = ArrayList<Task<Void>>()
            for (subscription in task.result!!) {
                Utils.log("Remove subscription for datatype: " + subscription.dataType!!.name)
                tasks.add(client.unsubscribe(subscription))
            }
            Tasks.whenAll(tasks)
        }.continueWithTask<Void> { task ->
            if (callback != null) {
                val dict = KrollDict()
                if (!task.isSuccessful) {
                    dict["error"] = task.exception!!.message
                }
                callback.call(getKrollObject(), dict)
            }
            null
        }
    }

    /**
     * Get subscriptions.
     * @param callback: Called with list of subscriptions in arg.subscriptions.
     */
    @Kroll.method
    fun getSubscriptions(callback: KrollFunction) {
        val activity = TiApplication.getInstance().currentActivity
        Fitness.getRecordingClient(activity, GoogleSignIn.getLastSignedInAccount(activity)!!)
                .listSubscriptions()
                .addOnSuccessListener { subscriptions ->
                    val list = ArrayList<String>()
                    for (sc in subscriptions) {
                        list.add(sc.dataType!!.name)
                        Utils.log(sc.dataType!!.name)
                    }
                    val dict = KrollDict()
                    dict["subscriptions"] = list.toArray()
                    callback.call(getKrollObject(), dict)
                }
    }

    /**
     * Subscribe to all datatypes we set.
     * @param callback: Called after subscribing. Check arg.error for error.
     */
    @Kroll.method
    fun subscribe(callback: KrollFunction?) {
        val activity = TiApplication.getInstance().currentActivity
        val client = Fitness.getRecordingClient(activity, GoogleSignIn.getLastSignedInAccount(activity)!!)
        val tasks = ArrayList<Task<Void>>()
        for (name in activeDataTypes!!) {
            tasks.add(client.subscribe(dataTypeHash[name]!!.dataType))
        }
        Tasks.whenAll(tasks)
                .addOnCompleteListener { task ->
                    if (callback != null) {
                        val dict = KrollDict()
                        if (!task.isSuccessful) {
                            dict["error"] = task.exception!!.message
                        }
                        callback.call(getKrollObject(), dict)
                    }
                }
    }

    /**
     * Gets data from specified time range.
     * @param args: {start_date: Date, end_date: Date}
     * @param callback: Called with object: {date: {dataType: {key: value}}}
     */
    @Kroll.method
    fun getData(args: KrollDict, callback: KrollFunction?) {

        // Dus aan aanroeper verantwoordelijkheid om juiste date op te geven
        // Dus ook uren, minuten en seconden!
        val startDate = TiConvert.toDate(args["start_date"])
        val endDate = TiConvert.toDate(args["end_date"])
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate
        //startCalendar[Calendar.HOUR_OF_DAY] = 0
        val startTimeMS = startCalendar.timeInMillis
        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate
        //endCalendar[Calendar.HOUR_OF_DAY] = 24
        val endTimeMS = endCalendar.timeInMillis

        // Method if you want to get a week of results:
        // cal.add(Calendar.WEEK_OF_YEAR, -1);
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        Utils.log("Request fitness data from " + sdf.format(startCalendar.time).toString() + " up to " + sdf.format(endCalendar.time))
        val activity = TiApplication.getInstance().currentActivity
        val account = GoogleSignIn.getAccountForExtension(activity, fitnessOptions!!)
        val builder = DataReadRequest.Builder()
        for (name in activeDataTypes!!) {
            if (name == TYPE_STEP_COUNT_DELTA) {
                builder.aggregate(dataSource, dataTypeHash[name]!!.aggregateDataType)
            } else {
                builder.aggregate(dataTypeHash[name]!!.dataType, dataTypeHash[name]!!.aggregateDataType)
            }
        }
        val readRequest = builder
                .setTimeRange(startTimeMS, endTimeMS, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build()
        Fitness.getHistoryClient(activity, account)
                .readData(readRequest)
                .addOnSuccessListener { dataReadResponse ->

                    //val dict = KrollDict() // key = date, value = KrollDict(key = datatype, value = KrollDict(key = field, value = value))
                    val result = ArrayList<Any>()

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                    if (dataReadResponse.buckets.size > 0) {
                        for (bucket in dataReadResponse.buckets) {
                            val dataSets = bucket.dataSets

                            var bucketResult = KrollDict()
                            bucketResult["startDate"] = dateFormat.format(bucket.getStartTime(TimeUnit.MILLISECONDS))
                            bucketResult["endDate"] = dateFormat.format(bucket.getEndTime(TimeUnit.MILLISECONDS))
                            var dataSetsMap = KrollDict()
                            bucketResult["dataSets"] = dataSetsMap

                            for (dataSet in dataSets) {

                                var dataSetMap = KrollDict()
                                dataSetsMap[dataSet.dataType.name] = dataSetMap

                                if (dataSet.dataPoints.size == 0) {
                                    continue
                                }

                                for (point in dataSet.dataPoints) {
                                    //val dataTypeName = point.dataType.name
                                    //val dateString: String = dateFormat.format(point.getTimestamp(TimeUnit.MILLISECONDS))
                                    //var dateDict: KrollDict? = null
                                    //var dataTypeDict: KrollDict? = null
                                    //if (dict.containsKey(dateString)) {
                                    //    dateDict = dict[dateString] as KrollDict?
                                    //} else {
                                        //dateDict = KrollDict()
                                        //dict[dateString] = dateDict
                                    //}
                                    //if (dateDict!!.containsKey(dataTypeName)) {
                                    //    dataTypeDict = dateDict[dataTypeName] as KrollDict?
                                    //} else {
                                        //dataTypeDict = KrollDict()
                                        //dateDict[dataTypeName] = dataTypeDict
                                    //}
                                    for (field in point.dataType.fields) {
                                        //dataTypeDict!![field.name] = point.getValue(field).toString()
                                        dataSetMap[field.name] = point.getValue(field).toString()
                                    }
                                }
                            }
                            result.add(bucketResult)
                        }
                    }

                    Utils.log(result.toString())

                    val param = KrollDict()
                    param["result"] = result.toArray()

                    callback?.call(getKrollObject(), param)

                    // We also can fire an event:
                    // fireEvent("stepsresponse", ob);
                }
                .addOnFailureListener { e ->
                    if (callback != null) {
                        val dict = KrollDict()
                        dict["error"] = e.message
                        callback.call(getKrollObject(), dict)
                    }
                }
    }

}