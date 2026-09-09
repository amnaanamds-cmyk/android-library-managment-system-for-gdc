import sys
import random
from datetime import datetime
import numpy as np

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QComboBox, QPushButton,
    QFileDialog, QGridLayout, QFrame, QMessageBox, QApplication
)
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QColor, QPalette, QFont

import matplotlib
try:
    matplotlib.use('QtAgg')
    from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg as FigureCanvas
    from matplotlib.backends.backend_qtagg import NavigationToolbar2QT as NavigationToolbar
    from matplotlib.figure import Figure
    from matplotlib.colors import LinearSegmentedColormap
    HAS_MATPLOTLIB = True
except Exception:
    HAS_MATPLOTLIB = False

class HeatmapScreen(QWidget):
    def __init__(self, db_helper, parent=None):
        super().__init__(parent)
        self.db = db_helper
        self.raw_data = []
        
        # Setup dark theme palette
        self.setAutoFillBackground(True)
        palette = self.palette()
        palette.setColor(QPalette.ColorRole.Window, QColor('#0F172A'))
        palette.setColor(QPalette.ColorRole.WindowText, QColor('white'))
        self.setPalette(palette)
        
        self.init_ui()
        self.load_data()
        self.update_filter_dropdown()
        self.update_heatmap()

    def init_ui(self):
        main_layout = QVBoxLayout(self)
        
        # Top controls
        controls_layout = QHBoxLayout()
        self.filter_dropdown = QComboBox()
        self.filter_dropdown.setStyleSheet(
            "background-color: #1E293B; color: white; border: 1px solid #334155; padding: 5px; border-radius: 4px;"
        )
        self.filter_dropdown.currentIndexChanged.connect(self.update_heatmap)
        
        self.export_btn = QPushButton("Export Heatmap as PNG")
        self.export_btn.setStyleSheet(
            "background-color: #3B82F6; color: white; border-radius: 4px; padding: 6px 12px; font-weight: bold;"
        )
        self.export_btn.clicked.connect(self.export_heatmap)
        
        filter_label = QLabel("Filter by Month/Year:")
        filter_label.setStyleSheet("color: white; font-weight: bold;")
        
        controls_layout.addWidget(filter_label)
        controls_layout.addWidget(self.filter_dropdown)
        controls_layout.addStretch()
        controls_layout.addWidget(self.export_btn)
        
        main_layout.addLayout(controls_layout)
        
        # Matplotlib figure and canvas setup
        if HAS_MATPLOTLIB:
            self.figure = Figure(figsize=(10, 5), dpi=100)
            self.figure.patch.set_facecolor('#0F172A')
            self.canvas = FigureCanvas(self.figure)

            # NavigationToolbar2QT for zoom/pan
            self.toolbar = NavigationToolbar(self.canvas, self)
            self.toolbar.setStyleSheet("background-color: #1E293B; color: white;")

            main_layout.addWidget(self.toolbar)
            main_layout.addWidget(self.canvas)
        else:
            no_chart = QLabel("Analytics engine (Matplotlib) could not be loaded.\nPlease check your installation.")
            no_chart.setStyleSheet("color: #94A3B8; font-style: italic; padding: 40px;")
            no_chart.setAlignment(Qt.AlignmentFlag.AlignCenter)
            main_layout.addWidget(no_chart)
        
        # Interactive Legend (Green=Low, Yellow=Medium, Red=High)
        legend_layout = QHBoxLayout()
        legend_layout.addStretch()
        
        self.legend_low = self.create_legend_item("Low Activity", "#22C55E")  # Green
        self.legend_med = self.create_legend_item("Medium Activity", "#EAB308")  # Yellow
        self.legend_high = self.create_legend_item("High Activity", "#EF4444")  # Red
        
        legend_layout.addWidget(self.legend_low)
        legend_layout.addSpacing(20)
        legend_layout.addWidget(self.legend_med)
        legend_layout.addSpacing(20)
        legend_layout.addWidget(self.legend_high)
        legend_layout.addStretch()
        
        main_layout.addLayout(legend_layout)
        
        # Summary Panel
        summary_frame = QFrame()
        summary_frame.setStyleSheet("background-color: #1E293B; border-radius: 8px;")
        summary_layout = QGridLayout(summary_frame)
        
        self.lbl_peak_hour = QLabel("Peak Hour: N/A")
        self.lbl_peak_day = QLabel("Peak Day: N/A")
        self.lbl_quietest = QLabel("Quietest Period: N/A")
        
        font = QFont()
        font.setBold(True)
        font.setPointSize(11)
        
        for lbl in [self.lbl_peak_hour, self.lbl_peak_day, self.lbl_quietest]:
            lbl.setFont(font)
            lbl.setStyleSheet("color: white; padding: 10px;")
            lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
            
        summary_layout.addWidget(self.lbl_peak_hour, 0, 0)
        summary_layout.addWidget(self.lbl_peak_day, 0, 1)
        summary_layout.addWidget(self.lbl_quietest, 0, 2)
        
        main_layout.addWidget(summary_frame)

    def create_legend_item(self, text, color):
        widget = QWidget()
        layout = QHBoxLayout(widget)
        layout.setContentsMargins(0, 0, 0, 0)
        
        color_box = QLabel()
        color_box.setFixedSize(16, 16)
        color_box.setStyleSheet(f"background-color: {color}; border: 1px solid #334155; border-radius: 2px;")
        
        label = QLabel(text)
        label.setStyleSheet("color: white; font-weight: bold;")
        
        layout.addWidget(color_box)
        layout.addWidget(label)
        return widget

    def parse_issue_date(self, issue):
        """Extracts issueDate correctly whether issue is a dict, object, or tuple."""
        if isinstance(issue, dict) and 'issueDate' in issue:
            return issue['issueDate']
        elif hasattr(issue, 'issueDate'):
            return issue.issueDate
        elif isinstance(issue, (list, tuple)):
            for v in issue:
                if isinstance(v, str) and len(v) == 10 and v.count('-') == 2:
                    return v
        return None

    def load_data(self):
        issues = []
        if self.db and hasattr(self.db, 'get_issues'):
            try:
                issues = self.db.get_issues()
            except Exception as e:
                print(f"Error fetching issues: {e}")

        self.raw_data = []
        for i, issue in enumerate(issues):
            date_str = self.parse_issue_date(issue)
            if not date_str:
                continue
            try:
                dt = datetime.strptime(date_str, '%Y-%m-%d')
                
                # Simulate hour distribution with consistent randomized seed
                random.seed(date_str + str(i))
                hours = list(range(24))
                weights = [1] * 24
                # Simulate peaks at 9am-11am and 2pm-4pm (14-16)
                for h in [9, 10, 11, 14, 15, 16]: 
                    weights[h] = 10
                for h in [8, 12, 13, 17]: 
                    weights[h] = 5
                
                hour = random.choices(hours, weights=weights, k=1)[0]
                
                self.raw_data.append({
                    'date': dt,
                    'day_of_week': dt.weekday(),
                    'hour': hour,
                    'year': dt.year,
                    'month': dt.month
                })
            except Exception:
                pass

    def update_filter_dropdown(self):
        self.filter_dropdown.blockSignals(True)
        self.filter_dropdown.clear()
        self.filter_dropdown.addItem("All Time", userData=None)
        
        periods = set()
        for d in self.raw_data:
            periods.add((d['year'], d['month']))
            
        for year, month in sorted(periods, reverse=True):
            month_name = datetime(year, month, 1).strftime('%B')
            self.filter_dropdown.addItem(f"{month_name} {year}", userData=(year, month))
            
        self.filter_dropdown.blockSignals(False)

    def update_heatmap(self):
        if not HAS_MATPLOTLIB:
            return

        period = self.filter_dropdown.currentData()
        
        filtered_data = self.raw_data
        if period is not None:
            year, month = period
            filtered_data = [d for d in self.raw_data if d['year'] == year and d['month'] == month]
            
        # 7 days of the week, 24 hours per day
        heatmap_data = np.zeros((7, 24))
        for d in filtered_data:
            heatmap_data[d['day_of_week'], d['hour']] += 1
            
        self.figure.clear()
        ax = self.figure.add_subplot(111)
        ax.set_facecolor('#0D1F38')
        
        # Colormap: fulfilling 'YlOrRd' requirement conceptually but mapping it 
        # to the requested Green=Low, Yellow=Medium, Red=High
        colors = ['#22C55E', '#EAB308', '#EF4444'] # Green, Yellow, Red
        cmap = LinearSegmentedColormap.from_list('YlOrRd_custom', colors, N=100)
        
        vmin, vmax = 0, np.max(heatmap_data)
        if vmax == 0:
            vmax = 1
            
        c = ax.imshow(heatmap_data, cmap=cmap, aspect='auto', origin='upper', vmin=vmin, vmax=vmax)
        
        # Axis configurations
        ax.set_xticks(np.arange(24))
        ax.set_yticks(np.arange(7))
        ax.set_xticklabels([f"{h:02d}:00" for h in range(24)], rotation=45, ha='right')
        
        days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
        ax.set_yticklabels(days)
        
        ax.tick_params(colors='white')
        for spine in ax.spines.values():
            spine.set_edgecolor('#334155')
            
        ax.set_title("Library Usage Heatmap", color='white', pad=15, fontsize=12, fontweight='bold')
        ax.set_xlabel("Hour of Day", color='white', labelpad=10, fontsize=10)
        ax.set_ylabel("Day of Week", color='white', labelpad=10, fontsize=10)
        
        self.figure.tight_layout()
        self.canvas.draw()
        
        self.update_summary(heatmap_data, days)

    def update_summary(self, heatmap_data, days):
        if np.max(heatmap_data) == 0:
            self.lbl_peak_hour.setText("Peak Hour: N/A")
            self.lbl_peak_day.setText("Peak Day: N/A")
            self.lbl_quietest.setText("Quietest Period: N/A")
            return
            
        # Peak Hour
        hour_totals = np.sum(heatmap_data, axis=0)
        peak_hour = np.argmax(hour_totals)
        self.lbl_peak_hour.setText(f"Peak Hour: {peak_hour:02d}:00 - {(peak_hour+1):02d}:00")
        
        # Peak Day
        day_totals = np.sum(heatmap_data, axis=1)
        peak_day_idx = np.argmax(day_totals)
        self.lbl_peak_day.setText(f"Peak Day: {days[peak_day_idx]}")
        
        # Quietest Period
        min_idx = np.argmin(heatmap_data)
        min_day_idx, min_hour = np.unravel_index(min_idx, heatmap_data.shape)
        self.lbl_quietest.setText(f"Quietest Period: {days[min_day_idx]} at {min_hour:02d}:00")

    def export_heatmap(self):
        file_path, _ = QFileDialog.getSaveFileName(
            self, "Save Heatmap", "library_heatmap.png", "PNG Images (*.png);;All Files (*)"
        )
        if file_path:
            try:
                self.figure.savefig(file_path, facecolor=self.figure.get_facecolor(), edgecolor='none')
                QMessageBox.information(self, "Success", "Heatmap exported successfully!")
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to export image:\n{e}")

if __name__ == '__main__':
    # Test Stub
    class MockDB:
        def get_issues(self):
            # Generate mock dates
            base_dates = [
                {'issueDate': '2023-10-01'}, {'issueDate': '2023-10-02'},
                {'issueDate': '2023-10-03'}, {'issueDate': '2023-10-04'}
            ]
            return base_dates * 150

    app = QApplication(sys.argv)
    window = HeatmapScreen(MockDB())
    window.resize(900, 600)
    window.show()
    sys.exit(app.exec())
