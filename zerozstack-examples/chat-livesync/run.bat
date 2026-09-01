@echo off
setlocal
cd /d "%~dp0chat-livesync-server"

rem Each example has a port of its own, so several can run at the same time.
rem To use a different one, pass it as the first argument:  run.bat 9000
set PORT=8086
if not "%~1"=="" set PORT=%~1

if not exist "target\classes" goto :needbuild
if not exist "target\libs" goto :needbuild

echo.
echo Starting chat-livesync on http://localhost:%PORT%   (Ctrl+C to stop)
echo.
rem --dev-login switches on the built-in demo accounts (demo/demo, admin/admin).
rem This example needs a sign-in; drop the flag and nothing can log in.
java -cp "target\classes;target\libs\*" com.zeroz4j.example.server.ExampleServer --port %PORT% --dev-login
exit /b %errorlevel%

:needbuild
echo chat-livesync is not built yet. Run "mvn install" once from the repository root, then re-run this script.
exit /b 1
