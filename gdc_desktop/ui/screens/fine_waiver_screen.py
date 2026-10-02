import sys
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QTextEdit,
    QFrame, QMessageBox, QAbstractItemView
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtGui import QColor, QFont, QBrush

# ---------------------------------------------------------
# Worker Threads for Background AI Processing
# ---------------------------------------------------------
class AnalysisWorker(QThread):
    """Background thread to process AI scoring for all members."""
    finished = pyqtSignal(list)
    progress = pyqtSignal(int, str)

    def __init__(self, members_data):
        super().__init__()
        self.members_data = members_data

    def run(self):
        results = []
        for i, member in enumerate(self.members_data):
            self.progress.emit(i, f"Analyzing {member['name']}...")
            score = 0
            # AI Scoring Logic
            if member['return_rate'] > 90: score += 40
            if member['total_books'] > 20: score += 20
            if member['fine'] < 50: score += 20
            if member['days_since_overdue'] > 30: score += 20

            if score >= 70:
                verdict = '✅ WAIVE 100%'
                discount = 1.0
            elif score >= 40:
                verdict = '⚡ WAIVE 50%'
                discount = 0.5
            else:
                verdict = '❌ NO WAIVER'
                discount = 0.0

            results.append({
                "row_index": member['row_index'],
                "id": member['id'],
                "db_id": member['db_id'],
                "score": score,
                "verdict": verdict,
                "discount": discount,
                "fine": member['fine']
            })
            self.msleep(50) # Small delay to prevent blocking if list is huge
        self.finished.emit(results)


class ExplanationWorker(QThread):
    """Background thread to fetch explanation from the AI agent."""
    finished = pyqtSignal(str)

    def __init__(self, agent, member_data, verdict):
        super().__init__()
        self.agent = agent
        self.member_data = member_data
        self.verdict = verdict

    def run(self):
        try:
            prompt = (
                f"Act as an AI library fine judge. The member {self.member_data['name']} (ID: {self.member_data['id']}) "
                f"has a total fine of {self.member_data['fine']}. They have borrowed {self.member_data['total_books']} books, "
                f"have a return rate of {self.member_data['return_rate']}%, and it has been {self.member_data['days_since_overdue']} days "
                f"since their last overdue. The AI verdict is: {self.verdict}. "
                "Provide exactly a 2-sentence explanation for this verdict based on their library behavior metrics."
            )
            # Try a few common method names for agent invocation
            if hasattr(self.agent, 'generate_response'):
                response = self.agent.generate_response(prompt)
            elif hasattr(self.agent, 'chat'):
                response = self.agent.chat(prompt)
            elif hasattr(self.agent, 'ask'):
                response = self.agent.ask(prompt)
            else:
                # Fallback if agent methods are unknown
                response = f"Simulated AI Response: Based on a return rate of {self.member_data['return_rate']}% and {self.member_data['days_since_overdue']} days since last overdue, the system decided on {self.verdict}. They have shown consistent behavior."
            
            self.finished.emit(str(response))
        except Exception as e:
            self.finished.emit(f"Error communicating with AI agent: {str(e)}")


# ---------------------------------------------------------
# Main Screen Widget
# ---------------------------------------------------------
class FineWaiverScreen(QWidget):
    def __init__(self, db_helper, agent, auth=None):
        super().__init__()
        self.db_helper = db_helper
        self.agent = agent
        self.auth = auth
        self.members_data = [] # Stores current data
        self.analysis_results = {} # Maps row index to analysis data
        
        self.init_ui()
        self.load_data()

    def init_ui(self):
        # Apply dark glassmorphism theme to the entire screen
        self.setStyleSheet("""
            QWidget {
                background-color: #0F172A;
                color: #FFFFFF;
                font-family: "Segoe UI", sans-serif;
            }
            QFrame#Card {
                background-color: #0D1F38;
                border: 1px solid #1E3050;
                border-radius: 10px;
            }
            QLabel {
                background: transparent;
            }
            QPushButton {
                background-color: #2563EB;
                color: white;
                border: none;
                border-radius: 5px;
                padding: 8px 16px;
                font-weight: bold;
            }
            QPushButton:hover {
                background-color: #1D4ED8;
            }
            QPushButton:disabled {
                background-color: #1E3050;
                color: #9CA3AF;
            }
            QTableWidget {
                background-color: #0D1F38;
                border: 1px solid #1E3050;
                border-radius: 10px;
                gridline-color: #1E3050;
                selection-background-color: #1E3050;
                selection-color: #FFFFFF;
            }
            QHeaderView::section {
                background-color: #0D1F38;
                color: #9CA3AF;
                border: none;
                border-bottom: 1px solid #1E3050;
                padding: 5px;
                font-weight: bold;
            }
            QTextEdit {
                background-color: #0D1F38;
                border: 1px solid #1E3050;
                border-radius: 10px;
                padding: 10px;
            }
        """)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(20)

        # Header Title
        title = QLabel("Smart Fine Waiver AI Judge")
        title.setFont(QFont("Segoe UI", 18, QFont.Weight.Bold))
        layout.addWidget(title)

        # Summary Cards Layout
        cards_layout = QHBoxLayout()
        cards_layout.setSpacing(15)
        
        self.lbl_total_fines = QLabel("$0.00")
        self.lbl_total_fines.setFont(QFont("Segoe UI", 16, QFont.Weight.Bold))
        self.lbl_total_fines.setStyleSheet("color: #EF4444;")
        cards_layout.addWidget(self.create_card("Total Fines Pending", self.lbl_total_fines))

        self.lbl_est_waiver = QLabel("$0.00")
        self.lbl_est_waiver.setFont(QFont("Segoe UI", 16, QFont.Weight.Bold))
        self.lbl_est_waiver.setStyleSheet("color: #10B981;")
        cards_layout.addWidget(self.create_card("Estimated Waiver Amount", self.lbl_est_waiver))

        self.lbl_eligible = QLabel("0")
        self.lbl_eligible.setFont(QFont("Segoe UI", 16, QFont.Weight.Bold))
        self.lbl_eligible.setStyleSheet("color: #2563EB;")
        cards_layout.addWidget(self.create_card("Members Eligible for Waiver", self.lbl_eligible))

        layout.addLayout(cards_layout)

        # Analyze All Button
        btn_layout = QHBoxLayout()
        self.btn_analyze = QPushButton("Analyze All Candidates")
        self.btn_analyze.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_analyze.clicked.connect(self.run_analysis)
        btn_layout.addWidget(self.btn_analyze)
        btn_layout.addStretch()
        layout.addLayout(btn_layout)

        # Table
        self.table = QTableWidget()
        self.table.setColumnCount(8)
        self.table.setHorizontalHeaderLabels([
            "Member Name", "Member ID", "Total Fine", "Books Borrowed",
            "Return Rate %", "Days Since Last Overdue", "AI Verdict", "Action"
        ])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.itemSelectionChanged.connect(self.on_row_selected)
        layout.addWidget(self.table)

        # Bottom Explanation Box
        explanation_label = QLabel("AI Verdict Explanation:")
        explanation_label.setFont(QFont("Segoe UI", 12, QFont.Weight.Bold))
        layout.addWidget(explanation_label)

        self.txt_explanation = QTextEdit()
        self.txt_explanation.setReadOnly(True)
        self.txt_explanation.setPlaceholderText("Select a row to see the AI's 2-sentence explanation for the verdict...")
        self.txt_explanation.setFixedHeight(80)
        layout.addWidget(self.txt_explanation)

    def create_card(self, title_text, value_label):
        card = QFrame()
        card.setObjectName("Card")
        lay = QVBoxLayout(card)
        lay.setContentsMargins(15, 15, 15, 15)
        title = QLabel(title_text)
        title.setStyleSheet("color: #9CA3AF; font-size: 12px;")
        lay.addWidget(title)
        lay.addWidget(value_label)
        return card

    def load_data(self):
        """Loads members with an outstanding fine balance from the real database.

        There is no `members.total_fine`/`total_books`/`return_rate` column —
        this used to check `hasattr(self.db_helper, "fetch_all")`, a method
        DatabaseHelper never had, so that check always failed and the screen
        silently ran on hardcoded mock members forever, no matter what was
        actually owed. It also used to UPDATE a `members.total_fine` column
        on "Apply" that does not exist, which would have thrown a real
        sqlite3.OperationalError the moment real data ever reached it.
        Outstanding balance here is computed the same way the existing fines
        ledger in members_screen.py does it: sum of fines on returned issues,
        minus payments/waivers already recorded in fine_payments.
        """
        from datetime import datetime, date

        self.members_data = []
        try:
            members = self.db_helper.get_members()
            issues = self.db_helper.get_issues()
            payments = self.db_helper.get_fine_payments()

            issues_by_member = {}
            for i in issues:
                issues_by_member.setdefault(i.memberId, []).append(i)
            payments_by_member = {}
            for p in payments:
                payments_by_member.setdefault(p["memberId"], []).append(p)

            for m in members:
                m_issues = issues_by_member.get(m.id, [])
                total_books = len(m_issues)
                returned = [i for i in m_issues if i.status == "Returned"]
                return_rate = round((len(returned) / total_books) * 100) if total_books else 0
                fine_history = [i for i in returned if (i.fine or 0) > 0]
                total_fines = sum(i.fine or 0 for i in fine_history)
                total_paid = sum(p["amount"] for p in payments_by_member.get(m.id, []))
                balance = round(total_fines - total_paid, 2)
                if balance <= 0:
                    continue

                last_return = None
                for i in fine_history:
                    if i.returnDate and (last_return is None or i.returnDate > last_return):
                        last_return = i.returnDate
                days_since_overdue = 0
                if last_return:
                    try:
                        days_since_overdue = (date.today() - datetime.strptime(last_return, "%Y-%m-%d").date()).days
                    except Exception:
                        days_since_overdue = 0

                self.members_data.append({
                    "name": m.name,
                    "id": m.memberId,
                    "db_id": m.id,
                    "fine": balance,
                    "total_books": total_books,
                    "return_rate": return_rate,
                    "days_since_overdue": days_since_overdue,
                })
        except Exception as e:
            print(f"[FineWaiver] Failed to load member fine data: {e}")
            self.members_data = []

        self.populate_table()
        self.update_summary()

    def populate_table(self):
        self.table.setRowCount(len(self.members_data))
        for row, member in enumerate(self.members_data):
            member['row_index'] = row # Keep track of row
            
            self.table.setItem(row, 0, QTableWidgetItem(str(member.get('name', ''))))
            self.table.setItem(row, 1, QTableWidgetItem(str(member.get('id', ''))))
            self.table.setItem(row, 2, QTableWidgetItem(f"Rs. {member.get('fine', 0):.2f}"))
            self.table.setItem(row, 3, QTableWidgetItem(str(member.get('total_books', 0))))
            self.table.setItem(row, 4, QTableWidgetItem(f"{member.get('return_rate', 0)}%"))
            self.table.setItem(row, 5, QTableWidgetItem(str(member.get('days_since_overdue', 0))))
            
            # AI Verdict Default
            verdict_item = QTableWidgetItem("Pending Analysis")
            verdict_item.setForeground(QBrush(QColor("#9CA3AF")))
            self.table.setItem(row, 6, verdict_item)

            # Action Button
            btn_apply = QPushButton("Apply")
            btn_apply.setEnabled(False) # Disabled until analyzed
            btn_apply.clicked.connect(lambda checked, r=row: self.apply_waiver(r))
            self.table.setCellWidget(row, 7, btn_apply)

    def update_summary(self):
        total_fines = sum(m.get('fine', 0) for m in self.members_data)
        self.lbl_total_fines.setText(f"Rs. {total_fines:.2f}")

        # If analysis is done, calculate estimations
        if self.analysis_results:
            est_waiver = 0
            eligible = 0
            for res in self.analysis_results.values():
                if res['discount'] > 0:
                    est_waiver += res['fine'] * res['discount']
                    eligible += 1
            self.lbl_est_waiver.setText(f"${est_waiver:.2f}")
            self.lbl_eligible.setText(str(eligible))

    def run_analysis(self):
        self.btn_analyze.setEnabled(False)
        self.btn_analyze.setText("Analyzing...")
        
        self.worker = AnalysisWorker(self.members_data)
        self.worker.finished.connect(self.on_analysis_finished)
        self.worker.start()

    def on_analysis_finished(self, results):
        self.btn_analyze.setEnabled(True)
        self.btn_analyze.setText("Analyze All Candidates")
        self.analysis_results.clear()

        for res in results:
            row = res['row_index']
            self.analysis_results[row] = res
            
            # Update Verdict Column
            verdict_item = QTableWidgetItem(res['verdict'])
            if '100%' in res['verdict']:
                verdict_item.setForeground(QBrush(QColor("#10B981"))) # Green
            elif '50%' in res['verdict']:
                verdict_item.setForeground(QBrush(QColor("#2563EB"))) # Blue
            else:
                verdict_item.setForeground(QBrush(QColor("#EF4444"))) # Red
            self.table.setItem(row, 6, verdict_item)

            # Enable Apply Button if waiver is applicable
            btn = self.table.cellWidget(row, 7)
            if btn and res['discount'] > 0:
                btn.setEnabled(True)
            elif btn:
                btn.setEnabled(False)
                
        self.update_summary()

    def apply_waiver(self, row):
        if row not in self.analysis_results:
            return

        res = self.analysis_results[row]
        member_id = res['id']
        db_id = res['db_id']
        discount = res['discount']
        current_fine = res['fine']

        new_fine = current_fine - (current_fine * discount)
        waiver_amount = current_fine * discount

        # A waiver is recorded as a credit against the balance, the same way
        # members_screen.py's "Waive Fine" button does it — there is no
        # members.total_fine column to overwrite; balance is always
        # (fines on returned issues) - (payments + waivers already logged).
        try:
            user_email = ""
            if self.auth and getattr(self.auth, "current_user", None):
                user_email = self.auth.current_user.email
            self.db_helper.save_fine_payment(db_id, waiver_amount, "Waiver", "AI Fine Waiver Judge")
            self.db_helper.log_audit_local(
                user_email, "fine_waiver",
                f"Waived Rs. {waiver_amount:.2f} for member {member_id} ({res['verdict']})"
            )

            QMessageBox.information(self, "Success", f"Waiver applied successfully for Member ID: {member_id}\nNew Fine: Rs. {new_fine:.2f}")

            # Update local UI state
            self.members_data[row]['fine'] = new_fine
            self.table.setItem(row, 2, QTableWidgetItem(f"Rs. {new_fine:.2f}"))

            btn = self.table.cellWidget(row, 7)
            if btn: btn.setEnabled(False)

            # Recalculate summary
            self.update_summary()

        except Exception as e:
            QMessageBox.critical(self, "Database Error", f"Failed to apply waiver: {str(e)}")

    def on_row_selected(self):
        selected_items = self.table.selectedItems()
        if not selected_items:
            return
            
        row = selected_items[0].row()
        if row not in self.analysis_results:
            self.txt_explanation.setText("Run 'Analyze All Candidates' first to see the AI explanation.")
            return

        member_data = self.members_data[row]
        verdict = self.analysis_results[row]['verdict']
        
        self.txt_explanation.setText("Generating AI explanation...")
        
        # Call agent in background thread so UI doesn't freeze
        self.exp_worker = ExplanationWorker(self.agent, member_data, verdict)
        self.exp_worker.finished.connect(self.on_explanation_finished)
        self.exp_worker.start()

    def on_explanation_finished(self, explanation):
        self.txt_explanation.setText(explanation)
