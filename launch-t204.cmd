@echo off
cd /d C:\Users\matga\Downloads\Project-Atmosphere
call "C:\Users\matga\Downloads\Project-Atmosphere\gradlew.bat" runClient --offline -PsimpleCloudsMandatory=false > runClient-t204.out.log 2> runClient-t204.err.log
