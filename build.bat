@echo off
REM 本机编译脚本：固定使用 Android Studio 自带 JDK + 本地 Gradle 9.8.2
REM 用法：build.bat :app:assembleDebug
setlocal
set "JAVA_HOME=D:\no game\Android\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "C:\Users\ZhengY\gradle-dist\gradle-9.8.2\bin\gradle.bat" -p "D:\xiangmu" %* --console=plain
endlocal
