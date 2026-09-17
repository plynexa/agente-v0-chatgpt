@rem Gradle wrapper bootstrap for Windows
@echo off
set DIRNAME=%~dp0
set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
  echo gradle-wrapper.jar is missing. Install Gradle 8.10.2 and run: gradle wrapper --gradle-version 8.10.2
  exit /b 1
)
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
