"""
ui/widgets/background_task.py - run slow work off the UI thread, with progress.

Qt runs every widget on one thread. Anything slow called straight from a
button handler blocks that thread, so the window stops repainting and Windows
paints "Not Responding" over it. That is what made Reset Database look broken:
it streams and deletes every document in six Firestore collections - hundreds
of round trips on a college with a real catalogue - from inside the click
handler, with no feedback at all until it finished.

Nothing here is Firestore-specific. Any long call can use it:

    run_with_progress(
        self, "Resetting database",
        lambda report: do_slow_thing(progress=report),
        on_done=lambda result: ...,
    )

The callable is handed a `report(message, done=None, total=None)` function. It
runs on a worker thread, so it must not touch widgets - report() is the only
thing it may call, and that is delivered back to the UI thread as a signal.
"""
from PyQt6 import QtCore, QtWidgets


class _TaskThread(QtCore.QThread):
    progress = QtCore.pyqtSignal(str, int, int)   # message, done, total
    succeeded = QtCore.pyqtSignal(object)
    failed = QtCore.pyqtSignal(str)

    def __init__(self, fn, parent=None):
        super().__init__(parent)
        self._fn = fn

    def run(self):
        def report(message, done=-1, total=-1):
            self.progress.emit(str(message), int(done), int(total))
        try:
            self.succeeded.emit(self._fn(report))
        except Exception as exc:               # noqa: BLE001 - surfaced to the user
            self.failed.emit(f"{type(exc).__name__}: {exc}")


def run_with_progress(parent, title, fn, on_done=None, on_error=None,
                      cancellable=False):
    """Run `fn(report)` on a worker thread behind a modal progress dialog.

    Returns the thread so a caller can keep a reference; the dialog closes and
    the thread is cleaned up automatically when the work ends.

    on_done receives the callable's return value, on_error the message. Both
    run on the UI thread, so they may touch widgets.
    """
    dlg = QtWidgets.QProgressDialog(title + "…", "Cancel" if cancellable else "",
                                    0, 0, parent)
    dlg.setWindowTitle(title)
    dlg.setWindowModality(QtCore.Qt.WindowModality.WindowModal)
    dlg.setMinimumWidth(380)
    dlg.setAutoClose(False)
    dlg.setAutoReset(False)
    if not cancellable:
        dlg.setCancelButton(None)
    # Show immediately. The default 4-second grace period is exactly the window
    # in which a user decides the app has hung.
    dlg.setMinimumDuration(0)

    thread = _TaskThread(fn, parent)

    def on_progress(message, done, total):
        dlg.setLabelText(message)
        if total > 0:
            dlg.setRange(0, total)
            dlg.setValue(done)
        else:
            dlg.setRange(0, 0)          # back to indeterminate

    def finish():
        dlg.close()
        thread.deleteLater()

    thread.progress.connect(on_progress)
    if on_done is not None:
        thread.succeeded.connect(on_done)
    thread.succeeded.connect(lambda _r: finish())
    if on_error is not None:
        thread.failed.connect(on_error)
    else:
        thread.failed.connect(
            lambda msg: QtWidgets.QMessageBox.warning(parent, title + " failed", msg))
    thread.failed.connect(lambda _m: finish())

    dlg.show()
    thread.start()
    return thread
