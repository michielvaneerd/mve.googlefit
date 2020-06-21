#!/bin/bash

# mve.googlefit-android-1.0.0.zip
ti build -p android --build-only
unzip -o dist/mve.googlefit-android-*.zip -d ../example_not_included/

