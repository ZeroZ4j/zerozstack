@echo off
setlocal
cd /d "%~dp0inventory-crud-server"

if not exist "target\classes" goto :needbuild
if not exist "target\libs" goto :needbuild

echo.
echo Starting inventory-crud on http://localhost:8080   (Ctrl+C to stop; run one example at a time)
echo.
java -cp "target\classes;target\libs\*" com.zeroz4j.example.server.ExampleServer
exit /b %errorlevel%

:needbuild
echo inventory-crud is not built yet. Run "mvn install" once from the repository root, then re-run this script.
exit /b 1
