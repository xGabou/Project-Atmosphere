@echo off
cd /d C:\Users\matga\Downloads\Project-Atmosphere
call "C:\Users\matga\Downloads\Project-Atmosphere\gradlew.bat" runClient --offline -PsimpleCloudsMandatory=false > runClient-t206.out.log 2> runClient-t206.err.log
