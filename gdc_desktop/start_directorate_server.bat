@echo off
echo Starting GDC Library Network - Directorate Central Server...
cd directorate_server
pip install -r requirements.txt
python -m uvicorn main:app --reload --port 8000
pause
