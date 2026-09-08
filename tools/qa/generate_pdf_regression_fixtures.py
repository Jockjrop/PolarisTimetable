# -*- coding: utf-8 -*-
"""Generate the anonymized PDF fixtures used by PdfRegressionTest.

The fixtures intentionally keep the three timetable layout families small and
readable while containing no real student, teacher, class or account data.
They are generated from a locally installed CJK font and checked into the
Android test assets as the parser's stable text-extraction contract.
"""

from pathlib import Path

from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4, landscape


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "app" / "src" / "androidTest" / "assets" / "pdfregression"
FONT_CANDIDATES = (
    Path("C:/Windows/Fonts/SourceHanSansCN-Normal.ttf"),
    Path("C:/Windows/Fonts/simhei.ttf"),
    Path("C:/Windows/Fonts/Noto Sans SC (TrueType).otf"),
    Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
)


def font_path() -> Path:
    for candidate in FONT_CANDIDATES:
        if candidate.exists():
            return candidate
    raise RuntimeError("No CJK font found; set FONT_CANDIDATES for fixture generation")


def make_pdf(filename: str, days: int, courses: list[dict[str, object]], parser: str) -> None:
    page_size = landscape(A4) if days == 7 and parser == "xaut" else A4
    width, height = page_size
    pdf = canvas.Canvas(str(OUTPUT / filename), pagesize=page_size, pageCompression=1)
    font_name = "PolarisCJK"
    if font_name not in pdfmetrics.getRegisteredFontNames():
        pdfmetrics.registerFont(TTFont(font_name, str(font_path())))

    def text(x: float, top_y: float, value: str, size: float) -> None:
        pdf.setFont(font_name, size)
        pdf.drawString(x, height - top_y, value)

    def line(x1: float, top_y1: float, x2: float, top_y2: float) -> None:
        pdf.line(x1, height - top_y1, x2, height - top_y2)

    def rect(x: float, top_y: float, rect_width: float, rect_height: float) -> None:
        pdf.rect(x, height - top_y - rect_height, rect_width, rect_height)

    left = 24
    right = width - 24
    top = 76
    header_bottom = 101
    bottom = height - 52
    time_width = 54
    day_left = left + time_width
    day_width = (right - day_left) / days
    row_height = (bottom - header_bottom) / 6

    pdf.setStrokeColorRGB(55 / 255, 65 / 255, 81 / 255)
    pdf.setLineWidth(0.65)
    rect(left, top, right - left, bottom - top)
    line(left, header_bottom, right, header_bottom)
    line(day_left, top, day_left, bottom)
    for index in range(1, days):
        x = day_left + day_width * index
        line(x, top, x, bottom)
    for index in range(1, 6):
        y = header_bottom + row_height * index
        line(left, y, right, y)

    pdf.setFillColorRGB(17 / 255, 24 / 255, 39 / 255)
    text(left + 12, 33, "Polaris 课程表回归样例", 16)
    text(left + 12, 53, "匿名学期：2026-2027-1    样例编号：ANON-0000", 7)
    text(right - 174, 53, f"解析模型：{parser.upper()}", 7)

    text(left + 13, top + 17, "时间段", 8)
    for index, label in enumerate(("星期一", "星期二", "星期三", "星期四",
                                   "星期五", "星期六", "星期日")[:days]):
        x = day_left + day_width * index + 5
        text(x, top + 17, label, 8)

    for index, label in enumerate(("1-2节", "3-4节", "5-6节", "7-8节", "9-10节", "11-12节")):
        y = header_bottom + row_height * index + 18
        text(left + 11, y, label, 7)

    for course in courses:
        day = int(course["day"])
        start = int(course["start"])
        end = int(course["end"])
        x = day_left + day_width * day + 4
        row = max(0, min(5, (start - 1) // 2))
        y = header_bottom + row_height * row + 30
        name = str(course["name"])
        weeks = str(course["weeks"])
        room = str(course["room"])
        teacher = str(course["teacher"])

        if parser == "xaut":
            text(x, y, name, 6.8)
            text(x, y + 12, teacher, 6.8)
            text(x, y + 24, f"{weeks}([周])[{start:02d}-{end:02d}节]", 6.8)
            text(x, y + 36, room, 6.8)
        else:
            text(x, y, f"{name}({start}-{end}节){weeks}", 6.8)
            if parser == "hdu":
                text(x, y + 12, "校区：下沙", 5.8)
                text(x, y + 24, "场地：" + room, 5.8)
                text(x, y + 36, "教师：" + teacher, 5.8)
            else:
                text(x, y + 12, "地点：" + room, 6.1)
                text(x, y + 24, "教师：" + teacher, 6.1)

    text(left + 4, bottom + 17,
         "本文件为匿名固定版式样例，仅用于解析回归；不包含真实姓名、学号、教师或教学班信息。", 6.5)
    pdf.showPage()
    pdf.save()


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    make_pdf(
        "xupt_anonymous.pdf", 5,
        [
            {"day": 0, "start": 1, "end": 2, "name": "高等数学", "weeks": "1-16周",
             "room": "A-101", "teacher": "李老师"},
            {"day": 2, "start": 3, "end": 4, "name": "数据结构", "weeks": "2-8周",
             "room": "B-202", "teacher": "王老师"},
            {"day": 4, "start": 5, "end": 6, "name": "大学英语", "weeks": "1-17周",
             "room": "C-303", "teacher": "赵老师"},
        ],
        "xupt",
    )
    make_pdf(
        "xaut_anonymous.pdf", 7,
        [
            {"day": 0, "start": 1, "end": 2, "name": "电路分析", "weeks": "1-16",
             "room": "曲江1-101", "teacher": "王老师"},
            {"day": 2, "start": 3, "end": 4, "name": "信号与系统", "weeks": "2-14",
             "room": "工程训练基地", "teacher": "赵老师"},
            {"day": 4, "start": 7, "end": 8, "name": "自动控制原理", "weeks": "3-15",
             "room": "三电实验中心教1-201", "teacher": "周老师"},
        ],
        "xaut",
    )
    make_pdf(
        "hdu_anonymous.pdf", 7,
        [
            {"day": 1, "start": 1, "end": 2, "name": "操作系统", "weeks": "1-17周",
             "room": "教学楼101", "teacher": "陈老师"},
            {"day": 3, "start": 3, "end": 4, "name": "计算机网络", "weeks": "1-16周",
             "room": "教学楼202", "teacher": "刘老师"},
            {"day": 5, "start": 5, "end": 6, "name": "算法设计", "weeks": "2-16周",
             "room": "实验楼303", "teacher": "孙老师"},
        ],
        "hdu",
    )


if __name__ == "__main__":
    main()
