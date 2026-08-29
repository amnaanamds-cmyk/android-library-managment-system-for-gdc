import os
import collections
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, 
    QPushButton, QScrollArea, QGridLayout, QFrame, QSizePolicy,
    QComboBox, QMessageBox
)
from PyQt6.QtCore import Qt, pyqtSignal, QThread
from PyQt6.QtGui import QFont, QCursor

try:
    import pandas as pd
except ImportError:
    pd = None

class RecommendationWorker(QThread):
    finished = pyqtSignal(list)
    error = pyqtSignal(str)

    def __init__(self, db_helper, member_id, category_filter):
        super().__init__()
        self.db = db_helper
        self.member_id = member_id.strip()
        self.category_filter = category_filter.strip().lower()

    def run(self):
        try:
            # Fetch data using the provided db helper
            # Adjust if actual methods are named differently
            books_raw = self.db.get_books() if hasattr(self.db, 'get_books') else []
            issues_raw = self.db.get_issues() if hasattr(self.db, 'get_issues') else []

            recommendations = self.generate_recommendations(books_raw, issues_raw)
            self.finished.emit(recommendations)
        except Exception as e:
            self.error.emit(str(e))

    def _parse_book(self, b):
        if isinstance(b, dict):
            return {
                'id': str(b.get('book_id', b.get('id', ''))),
                'title': b.get('title', 'Unknown Title'),
                'author': b.get('author', 'Unknown Author'),
                'category': b.get('category', 'General')
            }
        else:
            # Assuming tuple format: (id, title, author, category, ...)
            try:
                return {
                    'id': str(b[0]),
                    'title': str(b[1]) if len(b) > 1 else 'Unknown',
                    'author': str(b[2]) if len(b) > 2 else 'Unknown',
                    'category': str(b[3]) if len(b) > 3 else 'General'
                }
            except Exception:
                return {'id': '', 'title': 'Unknown', 'author': 'Unknown', 'category': 'General'}

    def _parse_issue(self, i):
        if isinstance(i, dict):
            return {
                'member_id': str(i.get('member_id', '')),
                'book_id': str(i.get('book_id', ''))
            }
        else:
            # Assuming tuple format: (issue_id, book_id, member_id, ...)
            try:
                return {
                    'member_id': str(i[2]) if len(i) > 2 else '',
                    'book_id': str(i[1]) if len(i) > 1 else ''
                }
            except Exception:
                return {'member_id': '', 'book_id': ''}

    def generate_recommendations(self, books_raw, issues_raw):
        # 1. Parse Data
        books = {}
        for b in books_raw:
            pb = self._parse_book(b)
            if pb['id']:
                books[pb['id']] = pb

        issues = [self._parse_issue(i) for i in issues_raw if self._parse_issue(i)['book_id']]
        
        # 2. Build User-Item Interaction
        user_books = collections.defaultdict(set)
        book_popularity = collections.Counter()
        
        for issue in issues:
            mid = issue['member_id']
            bid = issue['book_id']
            if mid and bid:
                user_books[mid].add(bid)
                book_popularity[bid] += 1
                
        results = []
        
        # Helper to convert score to stars (0.0 to 5.0)
        def get_stars(score, max_score):
            if max_score == 0: return 0.0
            return round((score / max_score) * 5.0, 1)

        # 3. Collaborative Filtering logic
        if self.member_id and self.member_id in user_books:
            target_books = user_books[self.member_id]
            similar_users = collections.defaultdict(int)
            
            # Calculate similarity
            for mid, b_set in user_books.items():
                if mid != self.member_id:
                    common = len(target_books.intersection(b_set))
                    if common > 0:
                        similar_users[mid] = common
                        
            # Score books based on similar users
            book_scores = collections.defaultdict(int)
            for mid, sim_score in similar_users.items():
                for bid in user_books[mid]:
                    if bid not in target_books:
                        book_scores[bid] += sim_score
                        
            if book_scores:
                max_score = max(book_scores.values())
                # Sort and pick top
                sorted_books = sorted(book_scores.items(), key=lambda x: x[1], reverse=True)
                for bid, score in sorted_books:
                    if bid in books:
                        b = books[bid]
                        if self.category_filter and self.category_filter not in b['category'].lower() and self.category_filter != 'all':
                            continue
                        results.append({
                            'title': b['title'],
                            'author': b['author'],
                            'category': b['category'],
                            'stars': get_stars(score, max_score),
                            'reason': 'Popular among similar members'
                        })
            else:
                # Fallback to popular books if no similar users found for unread books
                results = self._get_popular_books(book_popularity, books, target_books, "Recommended for you")
                
        else:
            # Trending / Popular books overall
            target_books = set() if not self.member_id else user_books.get(self.member_id, set())
            results = self._get_popular_books(book_popularity, books, target_books, "Trending this month")

        # Limit to top 20 recommendations
        return results[:20]

    def _get_popular_books(self, book_popularity, books, excluded_books, reason_text):
        results = []
        if not book_popularity:
            # If no issues at all, just return some random books
            for i, (bid, b) in enumerate(books.items()):
                if self.category_filter and self.category_filter not in b['category'].lower() and self.category_filter != 'all':
                    continue
                results.append({
                    'title': b['title'],
                    'author': b['author'],
                    'category': b['category'],
                    'stars': 3.0,
                    'reason': 'New Arrival'
                })
                if len(results) >= 10: break
            return results

        max_pop = max(book_popularity.values())
        sorted_pop = book_popularity.most_common()
        for bid, pop in sorted_pop:
            if bid not in excluded_books and bid in books:
                b = books[bid]
                if self.category_filter and self.category_filter not in b['category'].lower() and self.category_filter != 'all':
                    continue
                results.append({
                    'title': b['title'],
                    'author': b['author'],
                    'category': b['category'],
                    'stars': round((pop / max_pop) * 5.0, 1),
                    'reason': reason_text
                })
        return results


class RecommendationCard(QFrame):
    def __init__(self, data):
        super().__init__()
        self.setObjectName("recommendationCard")
        self.setFrameShape(QFrame.Shape.StyledPanel)
        
        # Styles for the card
        self.setStyleSheet("""
            QFrame#recommendationCard {
                background-color: #0D1F38;
                border: 1px solid #1E3050;
                border-radius: 12px;
            }
            QFrame#recommendationCard:hover {
                border: 1px solid #2563EB;
            }
        """)
        
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(8)
        
        # Title
        title_lbl = QLabel(data.get('title', ''))
        title_lbl.setStyleSheet("color: #E8EEF8; font-weight: bold; font-size: 16px; border: none; background: transparent;")
        title_lbl.setWordWrap(True)
        
        # Author & Category
        author_lbl = QLabel(f"{data.get('author', '')} • {data.get('category', '')}")
        author_lbl.setStyleSheet("color: #94A3B8; font-size: 13px; border: none; background: transparent;")
        
        # Rating & Reason Layout
        bottom_layout = QHBoxLayout()
        bottom_layout.setContentsMargins(0, 8, 0, 0)
        
        stars = data.get('stars', 0.0)
        stars_text = "★" * int(stars) + "☆" * (5 - int(stars))
        stars_lbl = QLabel(f"{stars_text} {stars}")
        stars_lbl.setStyleSheet("color: #C8A84B; font-weight: bold; font-size: 14px; border: none; background: transparent;")
        
        reason_lbl = QLabel(data.get('reason', ''))
        reason_lbl.setStyleSheet("""
            color: #2563EB; 
            background-color: rgba(37, 99, 235, 0.1); 
            padding: 4px 8px; 
            border-radius: 6px; 
            font-size: 12px; 
            border: none;
        """)
        reason_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        
        bottom_layout.addWidget(stars_lbl)
        bottom_layout.addStretch()
        bottom_layout.addWidget(reason_lbl)
        
        layout.addWidget(title_lbl)
        layout.addWidget(author_lbl)
        layout.addLayout(bottom_layout)
        
        self.setMinimumHeight(130)


class RecommendationScreen(QWidget):
    def __init__(self, db_helper):
        super().__init__()
        self.db_helper = db_helper
        self.worker = None
        self.init_ui()
        
    def init_ui(self):
        # Base Screen Style
        self.setStyleSheet("""
            QWidget {
                background-color: #0F172A;
                color: #E8EEF8;
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            }
            QLineEdit, QComboBox {
                background-color: #0D1F38;
                border: 1px solid #1E3050;
                border-radius: 8px;
                padding: 8px 12px;
                color: #E8EEF8;
                font-size: 14px;
            }
            QLineEdit:focus, QComboBox:focus {
                border: 1px solid #2563EB;
            }
            QPushButton {
                background-color: #2563EB;
                color: white;
                border: none;
                border-radius: 8px;
                padding: 8px 16px;
                font-weight: bold;
                font-size: 14px;
            }
            QPushButton:hover {
                background-color: #1D4ED8;
            }
            QScrollArea {
                border: none;
                background-color: transparent;
            }
            QScrollBar:vertical {
                border: none;
                background: #0F172A;
                width: 10px;
                margin: 0px 0px 0px 0px;
            }
            QScrollBar::handle:vertical {
                background: #1E3050;
                border-radius: 5px;
                min-height: 20px;
            }
            QScrollBar::handle:vertical:hover {
                background: #2563EB;
            }
        """)
        
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(24, 24, 24, 24)
        main_layout.setSpacing(20)
        
        # --- Header ---
        header_lbl = QLabel("AI Smart Book Recommender")
        header_lbl.setFont(QFont("Segoe UI", 24, QFont.Weight.Bold))
        header_lbl.setStyleSheet("color: #E8EEF8; margin-bottom: 8px;")
        main_layout.addWidget(header_lbl)
        
        # --- Control Bar ---
        control_layout = QHBoxLayout()
        control_layout.setSpacing(12)
        
        self.member_input = QLineEdit()
        self.member_input.setPlaceholderText("Enter Member ID for personalized picks...")
        self.member_input.setMinimumWidth(250)
        
        self.category_combo = QComboBox()
        self.category_combo.addItems(["All", "Fiction", "Science", "History", "Technology", "Art", "Literature"])
        self.category_combo.setMinimumWidth(150)
        
        self.refresh_btn = QPushButton("Refresh")
        self.refresh_btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.refresh_btn.clicked.connect(self.load_recommendations)
        
        control_layout.addWidget(self.member_input)
        control_layout.addWidget(self.category_combo)
        control_layout.addWidget(self.refresh_btn)
        control_layout.addStretch()
        
        main_layout.addLayout(control_layout)
        
        # --- Grid Area ---
        self.scroll_area = QScrollArea()
        self.scroll_area.setWidgetResizable(True)
        
        self.grid_widget = QWidget()
        self.grid_widget.setStyleSheet("background-color: transparent;")
        self.grid_layout = QGridLayout(self.grid_widget)
        self.grid_layout.setSpacing(16)
        self.grid_layout.setContentsMargins(0, 0, 0, 0)
        self.grid_layout.setAlignment(Qt.AlignmentFlag.AlignTop)
        
        self.scroll_area.setWidget(self.grid_widget)
        main_layout.addWidget(self.scroll_area)
        
        # Loading Indicator
        self.status_lbl = QLabel("Enter details and click Refresh to get recommendations.")
        self.status_lbl.setStyleSheet("color: #94A3B8; font-size: 14px; font-style: italic;")
        self.status_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        main_layout.addWidget(self.status_lbl)

        # Initial load
        self.load_recommendations()

    def load_recommendations(self):
        self.refresh_btn.setEnabled(False)
        self.refresh_btn.setText("Loading...")
        self.status_lbl.show()
        self.status_lbl.setText("Analyzing borrowing patterns...")
        self.scroll_area.hide()
        
        # Clear existing grid
        while self.grid_layout.count():
            item = self.grid_layout.takeAt(0)
            widget = item.widget()
            if widget:
                widget.deleteLater()
                
        # Start background worker
        member_id = self.member_input.text()
        category = self.category_combo.currentText()
        
        self.worker = RecommendationWorker(self.db_helper, member_id, category)
        self.worker.finished.connect(self.on_recommendations_ready)
        self.worker.error.connect(self.on_recommendation_error)
        self.worker.start()

    def on_recommendations_ready(self, recommendations):
        self.refresh_btn.setEnabled(True)
        self.refresh_btn.setText("Refresh")
        
        if not recommendations:
            self.status_lbl.setText("No recommendations found for this criteria.")
            self.status_lbl.show()
            self.scroll_area.hide()
            return
            
        self.status_lbl.hide()
        self.scroll_area.show()
        
        # Populate grid (2 columns)
        col_count = 2
        for i, rec in enumerate(recommendations):
            row = i // col_count
            col = i % col_count
            card = RecommendationCard(rec)
            self.grid_layout.addWidget(card, row, col)

    def on_recommendation_error(self, error_msg):
        self.refresh_btn.setEnabled(True)
        self.refresh_btn.setText("Refresh")
        self.status_lbl.setText("Error generating recommendations.")
        self.status_lbl.show()
        QMessageBox.critical(self, "Recommendation Error", f"An error occurred: {error_msg}")
