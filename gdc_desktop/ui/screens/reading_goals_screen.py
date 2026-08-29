import os
import datetime
from PyQt6.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout, QPushButton,
                             QLabel, QTableWidget, QTableWidgetItem, QProgressBar,
                             QHeaderView, QDialog, QFormLayout, QComboBox, QSpinBox, QMessageBox)
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QFont

class ReadingGoalsScreen(QWidget):
    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper
        self.init_db()
        self.init_ui()

    def init_db(self):
        try:
            cursor = self.db.conn.cursor()
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS reading_goals (
                    member_id TEXT,
                    year INTEGER,
                    target_books INTEGER,
                    current_books INTEGER DEFAULT 0,
                    streak_days INTEGER DEFAULT 0,
                    last_read_date TEXT,
                    PRIMARY KEY (member_id, year)
                )
            ''')
            self.db.conn.commit()
        except Exception as e:
            print(f"Error initializing reading_goals table: {e}")

    def init_ui(self):
        self.setStyleSheet("""
            QWidget {
                background-color: #121212;
                color: #ffffff;
                font-family: 'Segoe UI', sans-serif;
            }
            QLabel {
                font-size: 16px;
            }
            QPushButton {
                background: rgba(255, 255, 255, 0.1);
                border: 1px solid rgba(255, 255, 255, 0.2);
                border-radius: 8px;
                padding: 8px 16px;
                color: #ffffff;
                font-weight: bold;
            }
            QPushButton:hover {
                background: rgba(255, 255, 255, 0.2);
            }
            QTableWidget {
                background: rgba(255, 255, 255, 0.05);
                border: 1px solid rgba(255, 255, 255, 0.1);
                border-radius: 10px;
                gridline-color: rgba(255, 255, 255, 0.1);
            }
            QHeaderView::section {
                background-color: rgba(255, 255, 255, 0.1);
                color: white;
                padding: 4px;
                border: 1px solid rgba(255, 255, 255, 0.1);
            }
            QProgressBar {
                border: 1px solid rgba(255, 255, 255, 0.2);
                border-radius: 5px;
                text-align: center;
                background-color: rgba(255, 255, 255, 0.05);
                color: white;
            }
            QProgressBar::chunk {
                background-color: #4CAF50;
                border-radius: 4px;
            }
        """)
        
        layout = QVBoxLayout(self)
        
        header_layout = QHBoxLayout()
        title_label = QLabel("📚 Reading Goals & Streaks")
        title_label.setFont(QFont("Segoe UI", 24, QFont.Weight.Bold))
        header_layout.addWidget(title_label)
        header_layout.addStretch()
        
        set_goal_btn = QPushButton("Set Goal")
        set_goal_btn.clicked.connect(self.show_set_goal_dialog)
        header_layout.addWidget(set_goal_btn)
        
        update_btn = QPushButton("Update All")
        update_btn.clicked.connect(self.update_all_progress)
        header_layout.addWidget(update_btn)
        
        layout.addLayout(header_layout)
        
        self.table = QTableWidget()
        self.table.setColumnCount(6)
        self.table.setHorizontalHeaderLabels([
            "Member Name", "Goal", "Progress", "Progress Bar", "Streak", "Status"
        ])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.verticalHeader().setVisible(False)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        
        layout.addWidget(self.table)
        self.load_data()

    def get_badge(self, percentage):
        if percentage >= 100: return "🏆 Platinum"
        elif percentage >= 75: return "🥇 Gold"
        elif percentage >= 50: return "🥈 Silver"
        elif percentage >= 25: return "🥉 Bronze"
        else: return "🌱 Starter"

    def load_data(self):
        try:
            cursor = self.db.conn.cursor()
            current_year = datetime.datetime.now().year
            
            # Check if members table exists to get the actual name
            cursor.execute("SELECT count(name) FROM sqlite_master WHERE type='table' AND name='members'")
            has_members = cursor.fetchone()[0] == 1
            
            if has_members:
                cursor.execute("""
                    SELECT m.name, r.target_books, r.current_books, r.streak_days, r.member_id
                    FROM reading_goals r
                    JOIN members m ON r.member_id = m.member_id
                    WHERE r.year = ?
                """, (current_year,))
            else:
                cursor.execute("""
                    SELECT member_id, target_books, current_books, streak_days, member_id
                    FROM reading_goals
                    WHERE year = ?
                """, (current_year,))
                
            rows = cursor.fetchall()
            self.table.setRowCount(len(rows))
            
            for row_idx, row in enumerate(rows):
                name = row[0]
                target = row[1]
                current = row[2]
                streak = row[3]
                
                percentage = int((current / target) * 100) if target > 0 else 0
                badge = self.get_badge(percentage)
                
                self.table.setItem(row_idx, 0, QTableWidgetItem(str(name)))
                self.table.setItem(row_idx, 1, QTableWidgetItem(f"{target} books"))
                self.table.setItem(row_idx, 2, QTableWidgetItem(f"{current}/{target}"))
                
                progress_bar = QProgressBar()
                progress_bar.setRange(0, target)
                progress_bar.setValue(current)
                self.table.setCellWidget(row_idx, 3, progress_bar)
                
                self.table.setItem(row_idx, 4, QTableWidgetItem(f"🔥 {streak} days"))
                self.table.setItem(row_idx, 5, QTableWidgetItem(badge))
                
        except Exception as e:
            print(f"Error loading goals: {e}")

    def show_set_goal_dialog(self):
        dialog = QDialog(self)
        dialog.setWindowTitle("Set Reading Goal")
        dialog.setStyleSheet(self.styleSheet())
        layout = QFormLayout(dialog)
        
        member_combo = QComboBox()
        try:
            cursor = self.db.conn.cursor()
            cursor.execute("SELECT count(name) FROM sqlite_master WHERE type='table' AND name='members'")
            if cursor.fetchone()[0] == 1:
                cursor.execute("SELECT member_id, name FROM members")
                for row in cursor.fetchall():
                    member_combo.addItem(f"{row[0]} - {row[1]}", row[0])
            else:
                member_combo.addItem("M001 - Dummy Member", "M001")
        except Exception:
            member_combo.addItem("M001 - Dummy Member", "M001")
            
        layout.addRow("Member:", member_combo)
        
        target_spin = QSpinBox()
        target_spin.setRange(1, 1000)
        target_spin.setValue(20)
        layout.addRow("Annual Target:", target_spin)
        
        btn_layout = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(lambda: self.save_goal(member_combo.currentData(), target_spin.value(), dialog))
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(dialog.reject)
        
        btn_layout.addWidget(save_btn)
        btn_layout.addWidget(cancel_btn)
        layout.addRow(btn_layout)
        
        dialog.exec()

    def save_goal(self, member_id, target, dialog):
        if not member_id:
            QMessageBox.warning(self, "Error", "No member selected")
            return
            
        current_year = datetime.datetime.now().year
        try:
            cursor = self.db.conn.cursor()
            cursor.execute("""
                INSERT INTO reading_goals (member_id, year, target_books)
                VALUES (?, ?, ?)
                ON CONFLICT(member_id, year) DO UPDATE SET target_books=excluded.target_books
            """, (member_id, current_year, target))
            self.db.conn.commit()
            dialog.accept()
            self.load_data()
        except Exception as e:
            QMessageBox.critical(self, "Error", f"Failed to save goal: {e}")

    def update_all_progress(self):
        try:
            cursor = self.db.conn.cursor()
            current_year = datetime.datetime.now().year
            
            cursor.execute("SELECT count(name) FROM sqlite_master WHERE type='table' AND name='book_issues'")
            if cursor.fetchone()[0] == 1:
                start_date = f"{current_year}-01-01"
                end_date = f"{current_year}-12-31"
                cursor.execute("""
                    UPDATE reading_goals
                    SET current_books = (
                        SELECT COUNT(*) FROM book_issues 
                        WHERE book_issues.member_id = reading_goals.member_id 
                        AND issue_date BETWEEN ? AND ?
                    )
                    WHERE year = ?
                """, (start_date, end_date, current_year))
                self.db.conn.commit()
                QMessageBox.information(self, "Success", "Progress updated successfully.")
            else:
                QMessageBox.warning(self, "Warning", "book_issues table not found.")
            
            self.load_data()
        except Exception as e:
            QMessageBox.warning(self, "Error", f"Could not update progress: {e}")
