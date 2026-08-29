from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLineEdit, QPushButton,
    QTextEdit, QLabel, QFrame
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal

class AgentWorker(QThread):
    response_received = pyqtSignal(str)

    def __init__(self, agent_service, message):
        super().__init__()
        self.agent = agent_service
        self.message = message

    def run(self):
        try:
            response = self.agent.chat(self.message)
            self.response_received.emit(response)
        except Exception as e:
            self.response_received.emit(f"Error: {e}")

class AgentOverlay(QFrame):
    def __init__(self, agent_service, parent=None):
        super().__init__(parent)
        self.agent = agent_service
        self.setFixedWidth(360)
        self.setFixedHeight(500)
        self.setObjectName("agentOverlay")
        self.setWindowFlags(Qt.WindowType.SubWindow)

        # Professional Styling
        self.setStyleSheet("""
            QFrame#agentOverlay {
                background: #1E293B;
                border: 1px solid #334155;
                border-radius: 12px;
            }
            QTextEdit {
                background: #0F172A;
                color: #F1F5F9;
                border: none;
                font-size: 13px;
                border-radius: 8px;
                padding: 10px;
            }
            QLineEdit {
                background: #334155;
                color: white;
                border: 1px solid #475569;
                border-radius: 8px;
                padding: 10px;
            }
            QPushButton {
                background: #2563EB;
                color: white;
                font-weight: bold;
                border-radius: 8px;
                padding: 10px 15px;
            }
            QPushButton:hover { background: #1D4ED8; }
            QLabel#agentTitle { color: #3B82F6; font-size: 15px; font-weight: 800; padding: 5px; }
        """)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        hdr_row = QHBoxLayout()
        hdr = QLabel("🤖 AI LIBRARIAN")
        hdr.setObjectName("agentTitle")
        hdr_row.addWidget(hdr)
        hdr_row.addStretch()

        close_btn = QPushButton("×")
        close_btn.setFixedSize(30, 30)
        close_btn.setStyleSheet("background: transparent; color: #94A3B8; font-size: 20px; padding: 0;")
        close_btn.clicked.connect(self.hide)
        hdr_row.addWidget(close_btn)
        layout.addLayout(hdr_row)

        self.chat_history = QTextEdit()
        self.chat_history.setReadOnly(True)
        layout.addWidget(self.chat_history)

        input_row = QHBoxLayout()
        self.input_field = QLineEdit()
        self.input_field.setPlaceholderText("Ask about books, stats, members...")
        self.input_field.returnPressed.connect(self.send_message)
        input_row.addWidget(self.input_field)

        self.send_btn = QPushButton("Send")
        self.send_btn.clicked.connect(self.send_message)
        input_row.addWidget(self.send_btn)

        layout.addLayout(input_row)

        self.chat_history.append("<span style='color:#3B82F6'><b>Agent:</b></span> Welcome! I'm your AI Librarian. Ask me to search books, analyze stats, or explain policies.")

    def send_message(self):
        text = self.input_field.text().strip()
        if not text:
            return

        self.chat_history.append(f"<br><b>You:</b> {text}")
        self.input_field.clear()
        self.chat_history.append("<i id='thinking'>Agent is thinking...</i>")
        self.chat_history.ensureCursorVisible()

        # Disable input while thinking
        self.input_field.setEnabled(False)
        self.send_btn.setEnabled(False)

        self.worker = AgentWorker(self.agent, text)
        self.worker.response_received.connect(self._on_response)
        self.worker.start()

    def _on_response(self, response):
        # Re-enable input
        self.input_field.setEnabled(True)
        self.send_btn.setEnabled(True)
        self.input_field.setFocus()

        # Remove "thinking" text manually using cursor
        cursor = self.chat_history.textCursor()
        cursor.movePosition(cursor.MoveOperation.End)
        cursor.select(cursor.SelectionType.LineUnderCursor)
        cursor.removeSelectedText()

        self.chat_history.append(f"<span style='color:#3B82F6'><b>Agent:</b></span> {response}")
        self.chat_history.ensureCursorVisible()
