const moment = require("/alloy/moment");

const googlefitness = require("mve.googlefit");
googlefitness.setDataTypes([googlefitness.TYPE_STEP_COUNT_DELTA, googlefitness.TYPE_MOVE_MINUTES, googlefitness.TYPE_HEART_POINTS], true);

const typeMapper = {
    steps: {
        name: googlefitness.TYPE_STEP_COUNT_DELTA,
        key: "steps"
    },
    minutes: {
        name: googlefitness.TYPE_MOVE_MINUTES,
        key: "duration"
    }
};

function doClick(e) {
	requestPermission(function(arg) {
		
		if (arg.error) {
			alert(arg.error);
			return;
		}

		getData({
			type: "steps",
			startDate: moment().subtract(7, 'd').toDate(),
			endDate: new Date()
		}, function(response) {
			console.log(response);
		});

	});
}

function errorPermissionCallback(callback, error) {
    callback({error: error, isPermissionError: true});
}

function subscribe(callback) {
    googlefitness.subscribe(function(argSubscribe) {
        if (argSubscribe.error) {
            errorPermissionCallback(callback, argSubscribe.error);
        } else {
            callback({success: true});
        }
    });
}

function requestPermission(callback) {
	if (Ti.Platform.Android.API_LEVEL >= 29 && !Ti.Android.hasPermission("android.permission.ACTIVITY_RECOGNITION")) {
		Ti.Android.requestPermissions("android.permission.ACTIVITY_RECOGNITION", function (response) {
			if (response.success) {
				requestPermission();
			} else {
				errorPermissionCallback(callback, response.error);
			}
		});
		return;
	}
	if (googlefitness.hasPermission()) {
		// You can still have permissions, but the subscriptions are gone.
		subscribe(callback);
	} else {
		googlefitness.requestPermission(function (argRequestPermission) {
			if (argRequestPermission.error) {
				errorPermissionCallback(callback, argRequestPermission.error);
			} else {
				subscribe(callback);
			}
		});
	}
}

/**
 * 
 * @param {*} arg {type: "steps"|"minutes", startDate: Date, endDate: Date}
 * @param {*} callback  arg: {success: true, data: Object} OR {error: String} IF permission error arg: {error: String, isPermissionError: true}
 */
function getData(arg, callback) {
	requestPermission(function(ePermission) {
		if (ePermission.error) {
			errorPermissionCallback(callback, ePermission.error);
		} else {
			googlefitness.getData({
				start_date: arg.startDate,
				end_date: arg.endDate
			}, function(eData) {
				console.log(eData);

				eData.result.forEach(function(bucket) {
					console.log(bucket.startDate + " - " + bucket.endDate);
					Object.keys(bucket.dataSets).forEach(function(dataType) {
						console.log("- Datatype: " + dataType);
						Object.keys(bucket.dataSets[dataType]).forEach(function(fieldName) {
							console.log("-- Value for " + fieldName + " = " + bucket.dataSets[dataType][fieldName]);
						});
					});
				});

				// if (eData.error) {
				// 	callback({error: eData.error});
				// } else {
				// 	const mappedType = typeMapper[arg.type];
				// 	let dateValue = {};
				// 	Object.keys(eData).forEach(function (date) {
				// 		if ((mappedType.name in eData[date]) && (mappedType.key in eData[date][mappedType.name])) {
				// 			dateValue[date] = parseInt(eData[date][mappedType.name][mappedType.key], 10);
				// 		}
				// 	});
				// 	callback({
				// 		success: true,
				// 		data: dateValue
				// 	});

				// }
			});
		}
	});
}

$.index.open();
