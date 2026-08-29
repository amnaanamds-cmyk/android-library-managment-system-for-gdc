import runpy
import sys
from pathlib import Path

root_dir = Path(__file__).resolve().parent
app_dir = root_dir / "gdc_desktop"

if str(app_dir) not in sys.path:
    sys.path.insert(0, str(app_dir))

runpy.run_path(str(app_dir / "main.py"), run_name="__main__")
