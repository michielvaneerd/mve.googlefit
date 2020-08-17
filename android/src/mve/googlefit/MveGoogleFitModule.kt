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
//import org.appcelerator.kroll.common.Log
import org.appcelerator.kroll.common.TiConfig

import com.google.android.gms.auth.api.signin.GoogleSignIn
//import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType

import com.google.android.gms.fitness.Fitness
//import com.google.android.gms.fitness.data.Bucket
import com.google.android.gms.fitness.request.DataReadRequest
//import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.fitness.data.DataSource
import java.util.*
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.app.Activity
import android.util.Log
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.SessionReadRequest
import org.appcelerator.titanium.TiBaseActivity
import org.appcelerator.titanium.util.TiConvert
import org.appcelerator.kroll.KrollDict
//import com.google.android.gms.tasks.OnSuccessListener
//import com.google.android.gms.tasks.OnFailureListener
//import java.text.DateFormat
//import com.google.android.gms.fitness.data.DataSet
//import com.google.android.gms.fitness.data.Field
import java.text.SimpleDateFormat
import org.appcelerator.kroll.KrollFunction
// If we implement OnActivityResultEvent interface, we can receive native OnActivityResult events.
// See: https://github.com/appcelerator/titanium_mobile/blob/master/android/titanium/src/java/org/appcelerator/titanium/TiLifecycle.java
import org.appcelerator.titanium.TiLifecycle.OnActivityResultEvent

//import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.Task
//import com.google.android.gms.fitness.RecordingClient
import java.util.ArrayList
//import com.google.android.gms.tasks.Continuation
//import com.google.android.gms.fitness.data.Subscription
//import com.google.android.gms.fitness.data.DataPoint
import java.util.HashMap


const val LCAT = "MveGoogleFit"

// See https://docs.appcelerator.com/module-apidoc/latest/android/org/appcelerator/kroll/common/TiConfig.html
// Takes value from tiap.xml:
// <property name="ti.android.debug" type="bool">false</property>
val DBG = TiConfig.LOGD

@Kroll.module(name = "MveGoogleFit", id = "mve.googlefit")
class MveGoogleFitModule : KrollModule(), OnActivityResultEvent {

    private var callbackAfterPermission: KrollFunction? = null

    internal class DataTypeInfo(dataType: DataType, aggregateDataType: DataType) {
        var dataType: DataType = dataType
        var aggregateDataType: DataType = aggregateDataType
    }

    internal class SessionInfo(id: Int, title: String, startTime: Long, endTime: Long) {
        val id = id
        val title = title
        val startTime = startTime
        val endTime = endTime
    }

    companion object {

        private const val MY_PERMISSION_GOOGLE_SIGNIN = 2

        private var dataSource: DataSource? = null
        private val dataTypeHash: HashMap<String, DataTypeInfo> = HashMap<String, DataTypeInfo>()

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

        @Kroll.constant
        val TIME_FRAME_SECONDS = "seconds"

        @Kroll.constant
        val TIME_FRAME_MINUTE = "minute"

        @Kroll.constant
        val TIME_FRAME_HOUR = "hour"

        @Kroll.constant
        val TIME_FRAME_DAY = "day"

        @Kroll.constant
        val TIME_FRAME_WEEK = "week"

        @JvmStatic
        @Kroll.onAppCreate
        fun onAppCreate(app: TiApplication) {

            // https://developers.google.com/fit/faq#how_do_i_get_the_same_step_count_as_the_google_fit_app
            // https://developers.google.com/fit/faq#values_for_stepdistanceactive_timecalories_do_not_match_those_of_fit_app
            dataSource = DataSource.Builder()
                    .setAppPackageName("com.google.android.gms")
                    .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                    .setType(DataSource.TYPE_DERIVED)
                    .setStreamName("estimated_steps")
                    .build()

            dataTypeHash[TYPE_STEP_COUNT_DELTA] = DataTypeInfo(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
            dataTypeHash[TYPE_MOVE_MINUTES] = DataTypeInfo(DataType.TYPE_MOVE_MINUTES, DataType.AGGREGATE_MOVE_MINUTES)
            dataTypeHash[TYPE_CALORIES_EXPENDED] = DataTypeInfo(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
            dataTypeHash[TYPE_HEART_POINTS] = DataTypeInfo(DataType.TYPE_HEART_POINTS, DataType.AGGREGATE_HEART_POINTS)
            dataTypeHash[TYPE_HEART_RATE_BPM] = DataTypeInfo(DataType.TYPE_HEART_RATE_BPM, DataType.AGGREGATE_HEART_RATE_SUMMARY)

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
     * Returns FitnessOptions for specified data types and read/write access.
     *
     * @param dataTypes: all datatypes you want to read and/or write
     * @param write: true if you want to write
     * @return options: FitnessOptions
     */
    //@Kroll.method
    private fun getFitnessOptions(dataTypes: Array<String>, write: Boolean): FitnessOptions {

        //activeDataTypes = dataTypes
        //writeAccess = write

        val builder = FitnessOptions.builder()
        for (name in dataTypes) {
            Utils.log("Add datatype $name")
            builder.addDataType(dataTypeHash[name]?.dataType!!, FitnessOptions.ACCESS_READ)
            if (write) {
                builder.addDataType(dataTypeHash[name]?.dataType!!, FitnessOptions.ACCESS_WRITE)
            }
            builder.addDataType(dataTypeHash[name]?.aggregateDataType!!, FitnessOptions.ACCESS_READ)
        }
        return builder.build()
    }

    /**
     * Returns true if we already have the OAuth2 permissions.
     *
     * @param dataTypes: all datatypes you want to read and/or write
     * @param write: true if you want to write
     */
    @Kroll.method
    fun hasPermissions(dataTypes: Array<String>, write: Boolean): Boolean {
        val fitnessOptions = getFitnessOptions(dataTypes, write)
        val activity = TiApplication.getInstance().currentActivity
        val account = GoogleSignIn.getAccountForExtension(activity, fitnessOptions)
        return GoogleSignIn.hasPermissions(account, fitnessOptions)
    }

    /**
     * Requests OAuth2 permissions.
     * Will show a dialog where the user can agree to which data the app will retrieve.
     * Note: make sure you have set your app id in the google api console first, otherwise this won't work
     * and won't show a dialog.
     *
     * @param dataTypes: all datatypes you want to read and/or write
     * @param write: true if you want to write
     * @param callback: Called after successful or unsuccessful request. See arg.error if there is an error.
     */
    @Kroll.method
    fun requestPermissions(dataTypes: Array<String>, write: Boolean, callback: KrollFunction) {
        val fitnessOptions = getFitnessOptions(dataTypes, write)
        val activity = TiApplication.getInstance().currentActivity
        callbackAfterPermission = callback
        (activity as TiBaseActivity).addOnActivityResultListener(this)
        val account = GoogleSignIn.getAccountForExtension(activity, fitnessOptions)
        GoogleSignIn.requestPermissions(activity, MY_PERMISSION_GOOGLE_SIGNIN, account, fitnessOptions)
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
     *
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
     *
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
     * No problem to call this more times - If the requested subscription already exists, the request will be a no-op.
     * https://developers.google.com/android/reference/com/google/android/gms/fitness/RecordingClient
     *
     * @param dataTypes: all datatypes you want to read
     * @param callback: Called after subscribing. Check arg.error for error.
     */
    @Kroll.method
    fun subscribe(dataTypes: Array<String>, callback: KrollFunction?) {
        val activity = TiApplication.getInstance().currentActivity
        val client = Fitness.getRecordingClient(activity, GoogleSignIn.getLastSignedInAccount(activity)!!)
        val tasks = ArrayList<Task<Void>>()
        for (name in dataTypes) {
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
     * Get all sessions between a specific timeframe
     *
     * @param args: {startDate: Date, endDate: Date, packageName: String (e.g. fi.polar.beat)}
     * @param callback: Called with object with "result" key.
     *
     */
    @Kroll.method
    fun getSessions(args: KrollDict, callback: KrollFunction?) {

        val packageName = TiConvert.toString(args["packageName"])
        val startDate = TiConvert.toDate(args["startDate"])
        val endDate = TiConvert.toDate(args["endDate"])
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate
        val startTimeMS = startCalendar.timeInMillis
        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate
        val endTimeMS = endCalendar.timeInMillis

        var mySessions = KrollDict()

        val activity = TiApplication.getInstance().currentActivity

        val readRequest = SessionReadRequest.Builder()
                .setTimeInterval(startTimeMS, endTimeMS, TimeUnit.MILLISECONDS)
                .read(DataType.TYPE_WORKOUT_EXERCISE)
                .read(DataType.AGGREGATE_HEART_RATE_SUMMARY) // see: https://developers.google.com/fit/datatypes/aggregate#body
                //==> 3 punten Each data point represents the user's average, maximum and minimum heart rate over the time period, in beats per minute.
                // Hier staan dan ook de velden in die je kan gebruiken voor de fields property (3 stuks) - wel bug want min BPM heeft dezelfde waarde als de max BPM!
                .readSessionsFromAllApps()
                .enableServerQueries()
                //.setSessionName("Jogging")
                .build()

        Fitness.getSessionsClient(activity, GoogleSignIn.getLastSignedInAccount(activity)!!)
                .readSession(readRequest)
                .addOnSuccessListener {
                    var sessions = it.sessions
                    var sessionCounter = 0
                    for (session in sessions) {

                        if (packageName != "") {
                            if (packageName != session.appPackageName) {
                                continue
                            }
                        }

                        sessionCounter++

                        var sessionInfo = KrollDict()
                        sessionInfo["id"] = sessionCounter
                        sessionInfo["name"] = session.name!!
                        sessionInfo["startTime"] = Date(session.getStartTime(TimeUnit.MILLISECONDS))
                        sessionInfo["endTime"] = Date(session.getEndTime(TimeUnit.MILLISECONDS))
                        sessionInfo["hrAvg"] = 0
                        sessionInfo["hrMax"] = 0

                        mySessions[sessionCounter.toString()] = sessionInfo

                        Utils.log(session.appPackageName + " - " + session.identifier!! + "; name = " + session.name!! + "; Start: " + Date(session.getStartTime(TimeUnit.MILLISECONDS)) + "; End: " + Date(session.getEndTime(TimeUnit.MILLISECONDS)) + "; desc = " + session.description)
                        var dataSets = it.getDataSet(session)
                        for (dataSet in dataSets) {
                            Utils.log("dataset " + dataSet.dataType.toString())
                            if (dataSet.dataType == DataType.AGGREGATE_HEART_RATE_SUMMARY) {
                                val point = dataSet.dataPoints[0]
                                sessionInfo["hrAvg"] = point.getValue(Field.FIELD_AVERAGE).asFloat()
                                sessionInfo["hrMax"] = point.getValue(Field.FIELD_MAX).asFloat()
                            }
                        }
                    }

                    callback?.call(getKrollObject(), mySessions)

                }
                .addOnFailureListener {
                    if (callback != null) {
                        val dict = KrollDict()
                        dict["error"] = it.message
                        callback.call(getKrollObject(), dict)
                    }
                }

    }

    /**
     * Gets data from specified time range.
     *
     * Note that it's the caller's responsibility to specify the correct startDate including time.
     *
     * @param args: {startDate: Date, endDate: Date, dataTypes: array of dataType constants, timeFrame: TimeFrame constant}
     * @param callback: Called with object with "result" key.
     */
    @Kroll.method
    fun getData(args: KrollDict, callback: KrollFunction?) {

        // If you want buckets that are framed by full hours, make sure to send a Date
        // that has minute and second set to 0 and
        // If you want full days, make sure to set hour to 0 as well.
        val startDate = TiConvert.toDate(args["startDate"])
        val endDate = TiConvert.toDate(args["endDate"])
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate
        //startCalendar[Calendar.HOUR_OF_DAY] = 0
        val startTimeMS = startCalendar.timeInMillis
        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate
        //endCalendar[Calendar.HOUR_OF_DAY] = 24
        val endTimeMS = endCalendar.timeInMillis

        val timeFrame = args["timeFrame"]

        val dataTypesToRead = args.getStringArray("dataTypes")
        Utils.log("dataTypesToRead = " + dataTypesToRead.size)
        val fitnessOptions = getFitnessOptions(dataTypesToRead, false)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        Utils.log("Request fitness data from " + sdf.format(startCalendar.time).toString() + " up to " + sdf.format(endCalendar.time))
        val activity = TiApplication.getInstance().currentActivity
        val account = GoogleSignIn.getAccountForExtension(activity, fitnessOptions)
        val builder = DataReadRequest.Builder()
        for (name in dataTypesToRead) {
            if (name == TYPE_STEP_COUNT_DELTA) {
                builder.aggregate(dataSource, dataTypeHash[name]!!.aggregateDataType)
            } else {
                builder.aggregate(dataTypeHash[name]!!.dataType, dataTypeHash[name]!!.aggregateDataType)
            }
        }
        when (timeFrame) {
            TIME_FRAME_SECONDS -> builder.bucketByTime(1, TimeUnit.SECONDS)
            TIME_FRAME_MINUTE -> builder.bucketByTime(1, TimeUnit.MINUTES)
            TIME_FRAME_HOUR -> builder.bucketByTime(1, TimeUnit.HOURS)
            TIME_FRAME_DAY -> builder.bucketByTime(1, TimeUnit.DAYS)
            TIME_FRAME_WEEK -> builder.bucketByTime(7, TimeUnit.DAYS)
            //"monthly" -> builder.bucketByTime(1, TimeUnit.DAYS) // TODO: TimeUnit.MONTHS doesn't exist, so we have to manually compute this...
            //"yearly" -> builder.bucketByTime(1, TimeUnit.DAYS) // TODO: TimeUnit.YEARS doesn't exist, so we have to manually compute this...
        }
        val readRequest = builder
                .enableServerQueries()
                .setTimeRange(startTimeMS, endTimeMS, TimeUnit.MILLISECONDS)
                .build()
        Fitness.getHistoryClient(activity, account)
                .readData(readRequest)
                .addOnSuccessListener { dataReadResponse ->

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
                                    for (field in point.dataType.fields) {
                                        dataSetMap[field.name] = point.getValue(field).toString()
                                    }
                                }
                            }
                            result.add(bucketResult)
                        }
                    }

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

    @Kroll.method
    fun getDataFromSession(args: KrollDict, callback: KrollFunction?) {

        val startDate = TiConvert.toDate(args["startDate"])
        val endDate = TiConvert.toDate(args["endDate"])
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate
        val startTimeMS = startCalendar.timeInMillis
        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate
        val endTimeMS = endCalendar.timeInMillis

        val timeFrame = args["timeFrame"]

        val dataTypesToRead = args.getStringArray("dataTypes")
        Utils.log("dataTypesToRead = " + dataTypesToRead.size)
        val fitnessOptions = getFitnessOptions(dataTypesToRead, false)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        Utils.log("Request fitness data from " + sdf.format(startCalendar.time).toString() + " up to " + sdf.format(endCalendar.time))
        val activity = TiApplication.getInstance().currentActivity
        val account = GoogleSignIn.getAccountForExtension(activity, fitnessOptions)
        val builder = DataReadRequest.Builder()

        for (name in dataTypesToRead) {
            builder.read(dataTypeHash[name]!!.dataType)
        }

        when (timeFrame) {
            TIME_FRAME_SECONDS -> builder.bucketByTime(1, TimeUnit.SECONDS)
            TIME_FRAME_MINUTE -> builder.bucketByTime(1, TimeUnit.MINUTES) // je MOET MINUTE opgeven voor HR BPM! Ook als je deze per seconde wil uitlezen!
            TIME_FRAME_HOUR -> builder.bucketByTime(1, TimeUnit.HOURS)
            TIME_FRAME_DAY -> builder.bucketByTime(1, TimeUnit.DAYS)
            TIME_FRAME_WEEK -> builder.bucketByTime(7, TimeUnit.DAYS)
            //"monthly" -> builder.bucketByTime(1, TimeUnit.DAYS) // TODO: TimeUnit.MONTHS doesn't exist, so we have to manually compute this...
            //"yearly" -> builder.bucketByTime(1, TimeUnit.DAYS) // TODO: TimeUnit.YEARS doesn't exist, so we have to manually compute this...
        }
        val readRequest = builder
                //.bucketByTime(1, TimeUnit.MINUTES) // Dit moet ik zetten en ook op 1 MINUTE (ik denk omdat ik HR in BPM vraag?), nog uitzoeken...
                .enableServerQueries()
                .setTimeRange(startTimeMS, endTimeMS, TimeUnit.MILLISECONDS)
                .build()
        Fitness.getHistoryClient(activity, account)
                .readData(readRequest)
                .addOnSuccessListener { dataReadResponse ->

                    val result = ArrayList<Any>() // array van KrollDicts met  {start: DAte(), end: Date(), value: value} dus ik wil hier maar 1 dataset hebben!

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

                    if (dataReadResponse.buckets.size > 0) {

                        // Elke bucket representeert hier een minuut (als we bucketByTime op 1 MINUTE zetten)
                        for (bucket in dataReadResponse.buckets) {

                            // Elke bucket heeft 1 dataset als we ook maar 1 datatype opriepen, zoals HR BPM

                            val dataSets = bucket.dataSets

                            //var bucketResult = KrollDict()
                            //bucketResult["startDate"] = dateFormat.format(bucket.getStartTime(TimeUnit.MILLISECONDS))
                            //bucketResult["endDate"] = dateFormat.format(bucket.getEndTime(TimeUnit.MILLISECONDS))
                            //var dataSetsMap = KrollDict()
                            //bucketResult["dataSets"] = dataSetsMap

                            for (dataSet in dataSets) {

                                // Elke data set heeft 59 points die elk een seconde representeren
                                // Omdat we hier geen aggregate doen, krijg ik al deze punten hier.

                                //var dataSetMap = KrollDict()
                                //dataSetsMap[dataSet.dataType.name] = dataSetMap

                                if (dataSet.dataPoints.size == 0) {
                                    continue
                                }

                                for (point in dataSet.dataPoints) {

                                    var pointsMap = KrollDict()
                                    pointsMap["startDate"] = Date(point.getStartTime(TimeUnit.MILLISECONDS))
                                    pointsMap["endDate"] = Date(point.getEndTime(TimeUnit.MILLISECONDS))

                                    for (field in point.dataType.fields) {
                                        // bpm is naam van field.
                                        Utils.log("Field ${field.name}: value = ${point.getValue(field)}")
                                        pointsMap[field.name] = point.getValue(field).toString()
                                        //dataSetMap[field.name] = point.getValue(field).toString()
                                    }

                                    result.add(pointsMap)


                                }
                            }
                            //result.add(bucketResult)
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