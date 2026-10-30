cd /d C:\Users\matga\Downloads\Project-Atmosphere
call "C:\Users\matga\Downloads\Project-Atmosphere\gradlew.bat" runClient --offline -PsimpleCloudsMandatory=false -PpaProdCapture=s3 "-PpaProdCaptureWorld=pa-t132-autogen-restored" > runClient-s3.out.log 2> runClient-s3.err.log
