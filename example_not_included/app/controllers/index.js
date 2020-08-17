const moment = require("/alloy/moment");
const googlefitness = require("mve.googlefit");

let mySessions = null;

// The datatypes we want to read.
const fitOptions = {
	dataTypes: [googlefitness.TYPE_STEP_COUNT_DELTA, googlefitness.TYPE_MOVE_MINUTES, googlefitness.TYPE_CALORIES_EXPENDED,
		googlefitness.TYPE_HEART_POINTS, googlefitness.TYPE_HEART_RATE_BPM],
	write: false
}

var permissionChecked = false;

function getSessionClick() {
	
	const sessionId = $.sessionId.value;

	if (!sessionId) {
		alert("Enter session id");
	}

	if (mySessions === null || Object.keys(mySessions).length === 0 || !(sessionId in mySessions)) {
		alert("No sessions");
		return;
	}

	const session = mySessions[sessionId];

	googlefitness.getDataFromSession({
		startDate: session.startTime,
		endDate: session.endTime,
		dataTypes: [googlefitness.TYPE_HEART_RATE_BPM],
		timeFrame: googlefitness.TIME_FRAME_MINUTE, // necessary for HR BPM even if you want them in seconds.
		//aggregate: false
	}, function(eData) {
		//console.log(eData);
		eData.result.forEach(function(point) {
			console.log(point.startDate + ": " + point.bpm);
		})
	});
}

function getFitData() {

	permissionChecked = true;

	googlefitness.getData({
		// Make sure to set startDate to 00:00:00h otherwise you get incorrect buckets.
		startDate: moment().subtract(7, 'd').hours(0).minutes(0).seconds(0).toDate(),
		timeFrame: googlefitness.TIME_FRAME_DAY,
		endDate: new Date(),
		dataTypes: fitOptions.dataTypes
	}, function(eData) {

		console.log(eData);

		eData.result.forEach(function(bucket) {
			console.log("Bucket: " + bucket.startDate + " - " + bucket.endDate);
			Object.keys(bucket.dataSets).forEach(function(dataType) {
				console.log("- Datatype: " + dataType);
				Object.keys(bucket.dataSets[dataType]).forEach(function(fieldName) {
					console.log("-- Value for " + fieldName + " = " + bucket.dataSets[dataType][fieldName]);
				});
			});
		});
	});

}

function getSessionsClick() {
	requestPermission(fitOptions.dataTypes, fitOptions.write, function() {
		googlefitness.getSessions({
			packageName: "fi.polar.beat",
			startDate: moment().subtract(7, 'd').hours(0).minutes(0).seconds(0).toDate(),
			endDate: new Date(),
		}, function(eSessions) {
			mySessions = eSessions;
			console.log(mySessions);
		});
	});
}

function doClick(e) {
	if (!permissionChecked) {
		// Always request the permissions first.
		// You can do this always, but if you request data many times, it may have negative impact on performance.
		requestPermission(fitOptions.dataTypes, fitOptions.write, getFitData);
	} else {
		getFitData();
	}
}

function errorPermissionCallback(callback, error) {
    callback({error: error, isPermissionError: true});
}

// Subscribe so Google Fitness tracks the data types.
function subscribe(dataTypes, callback) {
    googlefitness.subscribe(dataTypes, function(argSubscribe) {
        if (argSubscribe.error) {
            errorPermissionCallback(callback, argSubscribe.error);
        } else {
            callback({success: true});
        }
    });
}

// Start permission requests + subscriptions.
function requestPermission(dataTypes, write, callback) {
	if (Ti.Platform.Android.API_LEVEL >= 29 && (!Ti.Android.hasPermission("android.permission.ACTIVITY_RECOGNITION") || !Ti.Android.hasPermission("android.permission.ACCESS_FINE_LOCATION"))) {
		Ti.Android.requestPermissions(["android.permission.ACTIVITY_RECOGNITION", "android.permission.ACCESS_FINE_LOCATION"], function (response) {
			if (response.success) {
				requestPermission(dataTypes, write, callback);
			} else {
				errorPermissionCallback(callback, response.error);
			}
		});
		return;
	}
	if (googlefitness.hasPermissions(dataTypes, write)) {
		// You can still have permissions, but the subscriptions are gone.
		subscribe(dataTypes, callback);
	} else {
		googlefitness.requestPermissions(dataTypes, write, function (argRequestPermission) {
			if (argRequestPermission.error) {
				errorPermissionCallback(callback, argRequestPermission.error);
			} else {
				subscribe(dataTypes, callback);
			}
		});
	}
}

$.index.open();
