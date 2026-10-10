@echo off
rem Starts Popcorn Journal: backend first, then the frontend. Ctrl+C stops both.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1"
