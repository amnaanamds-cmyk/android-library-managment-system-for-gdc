@echo off
echo Starting GDC Library Network - Directorate Central Server...
cd directorate_server

if "%JWT_SECRET%"=="" (
    echo.
    echo ERROR: JWT_SECRET is not set. This server refuses to start without it —
    echo a missing or guessable signing secret lets anyone forge admin access.
    echo Set it in your environment before running this script, e.g.:
    echo     set JWT_SECRET=some-long-random-value
    echo.
    pause
    exit /b 1
)

pip install -r requirements.txt

if not exist central_directorate.db (
    echo No directorate database found — run create_admin.py first to set up the first account.
    python create_admin.py
)

rem --reload is a development convenience that restarts the server on every
rem file change; it has no place in what a directorate actually runs day to
rem day. If exposed beyond localhost, this also needs a reverse proxy
rem terminating HTTPS in front of it — plain HTTP means every API key and
rem password crosses the network in clear text.
python -m uvicorn main:app --port 8000
pause
