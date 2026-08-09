from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "output" / "pdf" / "polaris_recent_test_schedule_2026_summer.pdf"
FONT_REGULAR = r"C:\Windows\Fonts\simhei.ttf"
FONT_BOLD = r"C:\Windows\Fonts\Dengb.ttf"

pdfmetrics.registerFont(TTFont("PolarisCN", FONT_REGULAR))
pdfmetrics.registerFont(TTFont("PolarisCNBold", FONT_BOLD))

PAGE_W, PAGE_H = landscape(A4)

COURSES = [
    # day, row, name, marker, sections, weeks, location, teacher, color
    (0, 0, "移动应用开发", "◆", "1-2", "1-4周", "A101", "陈晨", "EAF2FF"),
    (1, 1, "数据结构", "◆", "3-4", "1-4周", "B204", "李明", "EAF8F1"),
    (2, 2, "大学英语", "◆", "5-6", "1-4周", "语音室2", "王悦", "FFF3D9"),
    (3, 3, "计算机网络", "◆", "7-8", "1-4周", "C306", "赵宁", "F1EAFE"),
    (4, 0, "人工智能导论", "○", "1-2", "1-4周(单)", "线上课堂", "周航", "EAF2FF"),
    (5, 0, "UI设计实践", "◇", "1-2", "1-4周", "A305", "孙琳", "FFEDEE"),
    (5, 2, "软件工程", "◆", "5-6", "1-4周", "B201", "郭峰", "EAF8F1"),
    (6, 1, "数据库原理", "◆", "3-4", "1-4周", "C403", "吴桐", "FFF3D9"),
    (6, 3, "创新创业训练", "●", "7-8", "1-4周", "创客空间", "郑博", "F1EAFE"),
]


def hex_color(value):
    return colors.HexColor("#" + value)


def draw_centered(c, text, x, y, font="PolarisCN", size=7.2, color=colors.HexColor("#1C2A3A")):
    c.setFont(font, size)
    c.setFillColor(color)
    c.drawCentredString(x, y, text)


def build_pdf():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(OUTPUT), pagesize=(PAGE_W, PAGE_H), pageCompression=1)
    c.setTitle("Polaris近期测试课表-2026暑期")
    c.setAuthor("Polaris课程表测试数据")
    c.setSubject("用于PDF导入和桌面小组件测试")

    margin = 28
    title_y = PAGE_H - 34
    c.setFillColor(colors.HexColor("#123B63"))
    c.setFont("PolarisCNBold", 18)
    c.drawString(margin, title_y, "Polaris 近期测试课表")
    c.setFont("PolarisCN", 9)
    c.setFillColor(colors.HexColor("#4B647C"))
    c.drawRightString(PAGE_W - margin, title_y + 2, "2025-2026学年第3学期")
    c.drawString(margin, title_y - 18, "测试周期：2026/8/3 - 2026/8/30  ·  导入模型：西安邮电大学")
    c.drawRightString(PAGE_W - margin, title_y - 18, "导入后第一周日期请选择 2026/8/3")

    grid_left = margin
    grid_right = PAGE_W - margin
    grid_top = title_y - 42
    grid_bottom = 92
    time_col_w = 62
    day_w = (grid_right - grid_left - time_col_w) / 7
    header_h = 30
    row_h = (grid_top - grid_bottom - header_h) / 6
    weekdays = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
    row_labels = ["1-2节", "3-4节", "5-6节", "7-8节", "9-10节", "11-12节"]

    c.setFillColor(colors.HexColor("#E8F2FC"))
    c.roundRect(grid_left, grid_bottom, grid_right - grid_left, grid_top - grid_bottom, 10, fill=1, stroke=0)
    c.setFillColor(colors.HexColor("#D4E6F7"))
    c.rect(grid_left, grid_top - header_h, grid_right - grid_left, header_h, fill=1, stroke=0)
    c.setStrokeColor(colors.HexColor("#AEC7DD"))
    c.setLineWidth(0.65)
    c.roundRect(grid_left, grid_bottom, grid_right - grid_left, grid_top - grid_bottom, 10, fill=0, stroke=1)
    c.line(grid_left + time_col_w, grid_bottom, grid_left + time_col_w, grid_top)
    for day in range(1, 7):
        x = grid_left + time_col_w + day * day_w
        c.line(x, grid_bottom, x, grid_top)
    for row in range(7):
        y = grid_top - header_h - row * row_h
        c.line(grid_left, y, grid_right, y)

    draw_centered(c, "节次", grid_left + time_col_w / 2, grid_top - 19, "PolarisCNBold", 9)
    for day, label in enumerate(weekdays):
        center = grid_left + time_col_w + day_w * (day + 0.5)
        draw_centered(c, label, center, grid_top - 19, "PolarisCNBold", 9)
    for row, label in enumerate(row_labels):
        center_y = grid_top - header_h - row_h * (row + 0.5)
        draw_centered(c, label, grid_left + time_col_w / 2, center_y + 3, "PolarisCNBold", 8)

    for day, row, name, marker, sections, weeks, location, teacher, fill in COURSES:
        left = grid_left + time_col_w + day * day_w + 4
        right = left + day_w - 8
        top = grid_top - header_h - row * row_h - 4
        bottom = top - row_h + 8
        c.setFillColor(hex_color(fill))
        c.roundRect(left, bottom, right - left, top - bottom, 7, fill=1, stroke=0)
        center = (left + right) / 2
        draw_centered(c, name + marker, center, top - 13, "PolarisCNBold", 7.5)
        draw_centered(c, f"({sections}节){weeks}", center, top - 27, "PolarisCN", 6.8)
        draw_centered(c, f"场地:{location}", center, top - 40, "PolarisCN", 6.6, colors.HexColor("#40566D"))
        draw_centered(c, f"教师:{teacher}", center, top - 52, "PolarisCN", 6.6, colors.HexColor("#40566D"))

    footer_y = 66
    c.setFillColor(colors.HexColor("#F4F8FC"))
    c.roundRect(margin, 24, PAGE_W - margin * 2, 52, 8, fill=1, stroke=0)
    c.setFillColor(colors.HexColor("#1E466A"))
    c.setFont("PolarisCNBold", 8)
    c.drawString(margin + 10, footer_y - 4, "实践课程：")
    c.setFont("PolarisCN", 7.6)
    c.drawString(margin + 70, footer_y - 4, "暑期项目实训●刘老师(共4周)/1-4周;")
    c.drawString(margin + 10, footer_y - 22, "○: 网络  ◆: 讲课  ◇: 实验  ●: 实践")
    c.drawRightString(PAGE_W - margin - 10, footer_y - 22, "打印时间:2026-08-08")
    c.setFillColor(colors.HexColor("#6A7E91"))
    c.setFont("PolarisCN", 6.8)
    c.drawRightString(PAGE_W - margin - 10, 30, "仅用于 Polaris PDF 导入与桌面小组件测试")

    c.showPage()
    c.save()
    print(OUTPUT)


if __name__ == "__main__":
    build_pdf()
