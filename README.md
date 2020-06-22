# mve.googlefit

This Titanium module makes it possible to read data from Google Fitness.

## Getting Started

1. Build this project or download the module from the `dist` directory.
2. Require the module.
3. [Get an OAuth 2.0 Client ID](https://developers.google.com/fit/android/get-api-key)
4. Add the ACTIVITY_RECOGNITION permission to the [tiapp.xml](example_not_included/tiapp.xml) file.
5. Use the module.

## API

### hasPermissions(dataTypes, write)

Checks whether you have permissions for the dataTypes parameters.

* Required Array of strings `dataTypes` - Specify all the data types you are interested in. See below for all types.
* Required Boolan `write` - Currently writing data is not possible yet, so use always `false`.

### requestPermissions(dataTypes, write, callback)

* Required Array of strings `dataTypes` - Specify all the data types you are interested in. See below for all types.
* Required Boolan `write` - Currently writing data is not possible yet, so use always `false`.
* Required Function `callback` - Will be called after success or error.

### disable()

Disables the Google Fit connection and removes all subsciptions.

### unsubscribe(callback)

Removes all subscriptions.

* Optional Function `callback` - Will be called after success or error.

### getSubscriptions(callback)

Retrieves all subscriptions.

* Optional Function `callback` - Will be called after success or error.

### subscribe(dataTypes, callback)

Subscribes to the specified data types.

* Required Array of strings `dataTypes` - Specify all the data types you are interested in. See below for all types.
* Optional Function `callback` - Will be called after success or error.

### getData(args, callback)

Retrieves data.

* Required Object `arg` - Available keys:
  * Required Date `startDate` - The start date for the data. Be aware that this date determines how the buckets (the time slots) will be. For example if you use the current time but 7 days ago and sets the `timeFrame` to `day`, then you get buckets that exactly start at the specified time. If the time is 15:35h, then you get buckets from 12/06/2020 15:35:00 - 13/06/2020 15:35:00, 13/06/2020 15:35:00 - 14/06/2020 15:35:00 etc. So make sure you set the `startDate` to exactly the time you want the buckets to start.
  * Required Date `endDate` - The end date for the data.
  * Required Array of strings `dataTypes` - Specify all the data types you are interested in. See below for all types.
  * Required String `timeFrame` - A string indicating the bucket time. Possible values: `minute`, `hour`, `day`, `week`.
* Required Function `callback` - Will be called after success or error. The argument of this callback has a `result` key which is an array with objects. Each object has:
  * `startDate` - ISO8601 date string
  * `endDate` - ISO8601 date string
  * `dataSets` - Object with key = name of data type and value = Object of:
      * key = name of field (for example `steps` or `duration` - this depends on the date type), value = value of field

## When to call `requestPermissions`

You can call this everytime before you call `getData` if you wish. If we already have the necessary permissions, nothing happens so it won't crash your app. But it can have some performance consequences. Maybe a better approach is to call `requestPermissions` only once in a session.

## Data types

You can specify the data types by using the module's constants, for example:

```
const googlefit = require("mve.googlefit");
console.log(googlefit.TYPE_STEP_COUNT_DELTA);
```

* TYPE_STEP_COUNT_DELTA
* TYPE_MOVE_MINUTES
* TYPE_CALORIES_EXPENDED
* TYPE_HEART_POINTS
* TYPE_HEART_RATE_BPM

## Building this module

Clone this project. Go inside the android directory and do:

```
ti build -p android --build-only
```

## License

This project is licensed under the GNU GPLv3 License - see the [LICENSE](LICENSE) file for details.
