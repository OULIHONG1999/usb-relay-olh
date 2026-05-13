
@echo off
echo Starting USB Relay Client GUI...
python "%~dp0win_gui.py"
if errorlevel 1 (
    echo.
    echo Failed to start. Make sure Python is installed!
    echo.
    pause
)
