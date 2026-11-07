@echo off
REM 便捷构建脚本，等价于 ./gradlew，只是帮你把 JAVA_HOME 找出来。
REM 用法：build.bat :app:assembleDebug
setlocal

REM 1) 已经有 JAVA_HOME 就直接用；
REM 2) 否则回退到 Android Studio 自带的 JBR（本机路径，别人 clone 后自己改）。
if not defined JAVA_HOME (
    if exist "D:\no game\Android\jbr" (
        set "JAVA_HOME=D:\no game\Android\jbr"
    )
)

if not defined JAVA_HOME (
    echo [build.bat] 没找到 JAVA_HOME。请先设置：
    echo     set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0gradlew.bat" %* --console=plain
endlocal
