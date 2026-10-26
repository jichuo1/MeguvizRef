@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  gradlew startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables, and ensure extensions are enabled
setlocal EnableExtensions

@rem Catch executions from older scripts and ensure they exit cleanly.
@rem This can be removed once we can be reasonably confident that few people
@rem will be migrating directly to this new wrapper.
goto afterSafetyNet
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
goto exitWithErrorLevel
:afterSafetyNet

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
@rem Keep the Windows-only writable temp path in this launcher so Unix CI never
@rem receives a Windows absolute path from the shared gradle.properties file.
set "MEGUVIZ_GRADLE_TMP=%SystemRoot%\Temp"
if not defined SystemRoot set "MEGUVIZ_GRADLE_TMP=%TEMP%"
if not exist "%MEGUVIZ_GRADLE_TMP%" set "MEGUVIZ_GRADLE_TMP=%TEMP%"
for %%i in ("%MEGUVIZ_GRADLE_TMP%") do set "MEGUVIZ_GRADLE_TMP=%%~fsi"
@rem Gradle Worker processes create their communication channel before task-level
@rem JVM arguments apply. Propagate the short path through the process environment
@rem so the daemon, worker launcher, test JVM and compiler workers all agree.
set "TEMP=%MEGUVIZ_GRADLE_TMP%"
set "TMP=%MEGUVIZ_GRADLE_TMP%"
@rem Keep the Windows local build heap aligned with shared gradle.properties
@rem to avoid reserving an unnecessarily large heap for local JVMs.
@rem Match org.gradle.jvmargs so --no-daemon can reuse this JVM instead of
@rem forking a single-use Daemon and opening a local selector/AF_UNIX pipe.
set "MEGUVIZ_GRADLE_HEAP=4096m"
@rem Keep Gradle's standard JPMS openings on in-process Windows builds too.
@rem Only portable JDK 17+ flags are used; JVM-version-specific native/Unsafe flags stay with Gradle.
set "MEGUVIZ_JVM_OPENS=--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED"
set "MEGUVIZ_JVM_OPENS=%MEGUVIZ_JVM_OPENS% --add-opens=java.base/java.nio.charset=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED"
set "MEGUVIZ_JVM_OPENS=%MEGUVIZ_JVM_OPENS% --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
@rem Match launcher and requested build JVM options so --no-daemon does not fork.
set "MEGUVIZ_JVM_TUNING=-XX:MaxMetaspaceSize=1024m -XX:ReservedCodeCacheSize=512m -XX:+UseCodeCacheFlushing %MEGUVIZ_JVM_OPENS%"
set "JDK_JAVA_OPTIONS=%MEGUVIZ_JVM_TUNING%"
set DEFAULT_JVM_OPTS="-Xmx%MEGUVIZ_GRADLE_HEAP%" "-Dfile.encoding=UTF-8" "-Djava.io.tmpdir=%MEGUVIZ_GRADLE_TMP%"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

1>&2 echo.
1>&2 echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
1>&2 echo.
1>&2 echo Please set the JAVA_HOME variable in your environment to match the
1>&2 echo location of your Java installation.

"%COMSPEC%" /c exit 1
goto exitWithErrorLevel

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

1>&2 echo.
1>&2 echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
1>&2 echo.
1>&2 echo Please set the JAVA_HOME variable in your environment to match the
1>&2 echo location of your Java installation.

"%COMSPEC%" /c exit 1
goto exitWithErrorLevel

:execute
@rem Setup the command line



@rem Execute gradlew
@rem Keep short TEMP/TMP inherited by Gradle and its workers until Java exits.
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" "-Dorg.gradle.jvmargs=-Xmx%MEGUVIZ_GRADLE_HEAP% %MEGUVIZ_JVM_TUNING% -Dfile.encoding=UTF-8" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
endlocal & "%COMSPEC%" /c exit %ERRORLEVEL%
goto exitWithErrorLevel

@rem This label must not be changed. We rely on old scripts being able to jump to this point.
:exitWithErrorLevel
@rem Use "%COMSPEC%" /c exit to allow operators to work properly in scripts
"%COMSPEC%" /c exit %ERRORLEVEL%
