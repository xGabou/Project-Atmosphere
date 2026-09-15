@echo off
cd /d C:\Users\matga\Downloads\Project-Atmosphere
call "C:\Users\matga\Downloads\Project-Atmosphere\gradlew.bat" runClient --offline -PsimpleCloudsMandatory=false > runClient-t202.out.log 2> runClient-t202.err.log

