\## Build your Own



\###Prerequisites



\### 1. Required tools



This project is intended to be built with Android Studio.



Recommended environment:



\- \*\*Android Studio:\*\* Current stable version

\- \*\*JDK:\*\* Android Studio embedded JDK / JetBrains Runtime

\- \*\*Gradle:\*\* Use the included Gradle wrapper

\- \*\*Android SDK:\*\* API level compatible with the project configuration

\- \*\*Device target:\*\* Android 10 or newer



\### 2. Gradle JDK



In Android Studio, set the Gradle JDK to the embedded runtime:



```text

File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK

```



Recommended selection:



```text

GRADLE\_LOCAL\_JAVA\_HOME

```



or, if shown by your Android Studio version:



```text

Embedded JDK

```



The project keeps Java and Kotlin compile targets aligned to avoid JVM target mismatch errors.



\---



\## Final Build



Open the project root folder in Android Studio. The correct folder is the one containing:



```text

settings.gradle

build.gradle

gradle.properties

gradlew.bat

app/

```



Then run:



```text

Build → Rebuild Project

```



To build a debug APK from the terminal inside Android Studio:



```powershell

.\\gradlew.bat --stop

.\\gradlew.bat clean

.\\gradlew.bat :app:assembleDebug --stacktrace

```



The generated debug APK is created here:



```text

app\\build\\outputs\\apk\\debug\\app-debug.apk

```



\---

