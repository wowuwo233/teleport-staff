@echo off
REM Wrapper around dev.ps1 so the build works even when the PowerShell execution
REM policy forbids running .ps1 files (the default on Windows clients).
REM
REM Usage:  dev.cmd build | dev.cmd runClient | dev.cmd runServer | dev.cmd runData | ...
if "%~1"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1" build
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1" %*
)
exit /b %ERRORLEVEL%
