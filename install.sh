#!/bin/bash

rm -rf ~/.m2/repository/top

sh gradlew :runtime:generateRuntimeDexForRelease
sh gradlew install