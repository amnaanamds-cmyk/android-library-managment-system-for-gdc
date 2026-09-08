# -*- mode: python ; coding: utf-8 -*-
"""
PyInstaller build for the NEXLIB desktop client.

    cd gdc_desktop
    pip install -r requirements.txt pyinstaller
    python -m PyInstaller --noconfirm --clean gdc_desktop.spec

Produces dist/NEXLIB/ (a folder build). build_exe.bat referenced this file but
it did not exist, so the desktop client had no repeatable build at all.

Folder build rather than --onefile deliberately: a one-file bundle unpacks
itself to a temp directory on every launch, which adds seconds to startup and
is routinely quarantined by antivirus on the shared machines these colleges
run. The folder can also be patched in place for an update.

WHAT MUST NOT BE BUNDLED
------------------------
serviceAccountKey.json and .env are excluded on purpose. The service account
key grants Admin SDK access, which BYPASSES firestore.rules entirely — a copy
inside a binary shipped to 300 colleges is a credential that reads and writes
every college's data, extractable by anyone holding the file. Both are
delivered to each installation separately. See DEPLOYMENT.md.
"""

import os
from PyInstaller.utils.hooks import collect_data_files, collect_submodules

block_cipher = None
HERE = os.path.abspath(os.getcwd())

# Assets the app loads at runtime by relative path.
datas = [
    ("assets/style.qss", "assets"),
    ("assets/gdc_library.ico", "assets"),
]

# Firebase and Google client libraries load service definitions from package
# data and resolve several modules dynamically, so PyInstaller's static
# analysis misses them. Without these the build succeeds and then fails at
# runtime with an opaque import error the moment sync starts.
for package in ("google.cloud.firestore_v1", "google.api_core", "grpc", "certifi"):
    try:
        datas += collect_data_files(package)
    except Exception:
        pass

hiddenimports = [
    "firebase_admin",
    "firebase_admin.credentials",
    "firebase_admin.firestore",
    "firebase_admin.storage",
    "google.cloud.firestore",
    "google.cloud.firestore_v1",
    "google.auth.transport.requests",
    "grpc",
    "keyring.backends.Windows",
    "keyring.backends.SecretService",
    "keyring.backends.macOS",
    "pymarc",
    "qrcode",
    "barcode",
    "reportlab.graphics.barcode.code128",
    "openpyxl",
    "matplotlib.backends.backend_qtagg",
]
for package in ("PyQt6", "reportlab", "pymarc"):
    try:
        hiddenimports += collect_submodules(package)
    except Exception:
        pass

a = Analysis(
    ["main.py"],
    pathex=[HERE],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    # Qt5/Tk alongside PyQt6 pull in a second GUI toolkit and can win the
    # plugin lookup at runtime, producing a blank window.
    excludes=["PyQt5", "PySide2", "PySide6", "tkinter", "test", "unittest"],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="NEXLIB",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,  # UPX-packed binaries are a common false positive for antivirus.
    console=False,  # GUI app: no console window behind it.
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon="assets/gdc_library.ico",
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="NEXLIB",
)
