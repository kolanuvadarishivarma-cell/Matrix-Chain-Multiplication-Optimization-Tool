@echo off
REM Matrix Chain Multiplication Optimizer - launcher (Windows)
cd /d "%~dp0"
if not exist out mkdir out
if not exist out\Main.class (
  echo Compiling...
  javac -d out src\*.java
  if errorlevel 1 goto :err
)
java -cp out Main %*
goto :eof

:err
echo.
echo Compilation failed. Make sure the JDK (not just the JRE^) is installed
echo and that "javac" works from this Command Prompt.
pause
