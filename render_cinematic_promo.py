import math
import subprocess
import os
import shutil
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1080, 1920
FPS = 30
DURATION_SEC = 16
TOTAL_FRAMES = FPS * DURATION_SEC

TMP_DIR = r"E:\AlarmSimple\promo_cinematic_frames"
os.makedirs(TMP_DIR, exist_ok=True)

# Colors from AlarmSimple Theme.kt
COLOR_BG = (9, 13, 26)         # 0xFF090D1A
COLOR_SURFACE = (17, 23, 43)    # 0xFF11172B
COLOR_CARD_DARK = (20, 25, 43)  # 0xFF14192B
COLOR_PRIMARY = (99, 102, 241)  # 0xFF6366F1
COLOR_PRIMARY_LIGHT = (129, 140, 248) # 0xFF818CF8
COLOR_SECONDARY = (16, 185, 129)# 0xFF10B981
COLOR_ROSE = (244, 63, 94)      # 0xFFF43F5E
COLOR_TEXT_WHITE = (241, 245, 249)
COLOR_TEXT_MUTED = (148, 163, 184)
COLOR_BORDER = (51, 65, 85)

# Fonts
try:
    font_promo_title = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 62)
    font_promo_sub = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 34)
    font_clock_time = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 78)
    font_alarm_bold = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 34)
    font_body = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 26)
    font_small = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 22)
    font_badge = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 24)
except Exception:
    font_promo_title = font_promo_sub = font_clock_time = font_alarm_bold = font_body = font_small = font_badge = ImageFont.load_default()

def draw_round_rect(draw, bbox, r, fill=None, outline=None, width=1):
    draw.rounded_rectangle(bbox, radius=r, fill=fill, outline=outline, width=width)

def render_cinematic_frame(frame_idx):
    t = frame_idx / FPS
    img = Image.new("RGB", (WIDTH, HEIGHT), COLOR_BG)
    draw = ImageDraw.Draw(img)

    # 1. Background studio lighting & ambient gradients
    light_x = int(WIDTH/2 + math.sin(t * 1.2) * 160)
    light_y = int(HEIGHT * 0.48 + math.cos(t * 1.5) * 100)
    
    # 4 Scenes:
    # 0 - 4s: Real AlarmListScreen with QuickSleepCard & Coach marks
    # 4 - 8s: Alarm Firing with NFC Bank Card Tap Quest & Volume Lock
    # 8 - 12s: AlarmSetup with Smart Window (15/30/45/60 min)
    # 12 - 16s: Sleep Stats & Night Hypnogram
    scene = int(t // 4)
    local_t = t % 4

    glow_colors = [
        (99, 102, 241),  # Indigo
        (244, 63, 94),   # Rose / Alert
        (139, 92, 246),  # Purple / Smart
        (16, 185, 129)   # Mint / Healthy sleep
    ]
    gc = glow_colors[scene]

    for radius in range(500, 80, -40):
        intensity = (1 - radius / 500) ** 1.8
        col = (int(gc[0] * intensity * 0.35), int(gc[1] * intensity * 0.35), int(gc[2] * intensity * 0.35))
        draw.ellipse([light_x - radius, light_y - radius, light_x + radius, light_y + radius], fill=col)

    # 2. Studio Brand Header
    draw_round_rect(draw, [WIDTH//2 - 200, 65, WIDTH//2 + 200, 125], 30, fill=(17, 23, 43, 220), outline=COLOR_PRIMARY, width=2)
    draw.text((WIDTH//2, 95), "⏰ AlarmSimple", font=font_alarm_bold, fill=COLOR_TEXT_WHITE, anchor="mm")

    # Titles & Narration text
    scene_texts = [
        ("РЕЖИМ «БАИНЬКИ»", "Сон в 1 клик без будильника + подсказки", "ФИЧА 1 • МОНИТОРИНГ СНА"),
        ("NFC КАРТА МИР", "Привяжи банковскую карту для отбоя!", "ФИЧА 2 • НЕОТКЛЮЧАЕМЫЙ КВЕСТ"),
        ("УМНОЕ ПРОБУЖДЕНИЕ", "Окно лёгкого сна: 15 / 30 / 45 / 60 мин", "ФИЧА 3 • БОДРОЕ УТРО"),
        ("АНАЛИТИКА НОЧИ", "Локальная гипнограмма каждой ночи", "ФИЧА 4 • СТАТИСТИКА 0% ТРАТЫ")
    ]
    title, subtitle, badge = scene_texts[scene]

    # Category Pill
    draw_round_rect(draw, [WIDTH//2 - 230, 160, WIDTH//2 + 230, 210], 25, fill=(30, 41, 59), outline=COLOR_PRIMARY_LIGHT, width=1)
    draw.text((WIDTH//2, 185), badge, font=font_badge, fill=COLOR_PRIMARY_LIGHT, anchor="mm")

    # Headline
    draw.text((WIDTH//2, 270), title, font=font_promo_title, fill=COLOR_TEXT_WHITE, anchor="mm")
    draw.text((WIDTH//2, 335), subtitle, font=font_promo_sub, fill=COLOR_TEXT_MUTED, anchor="mm")

    # 3. Precision Android Flagship Device (3D Floating)
    pw, ph = 700, 1240
    px = (WIDTH - pw) // 2
    py = 430 + int(math.sin(t * 2.2) * 14)

    # Outer phone titanium frame
    draw_round_rect(draw, [px - 14, py - 14, px + pw + 14, py + ph + 14], 72, fill=(30, 36, 56), outline=(71, 85, 105), width=3)
    # Glass Screen
    draw_round_rect(draw, [px, py, px + pw, py + ph], 60, fill=COLOR_BG)

    # Dynamic Island / Camera
    draw_round_rect(draw, [px + (pw - 200)//2, py + 16, px + (pw + 200)//2, py + 52], 18, fill=(0, 0, 0))
    # Green mic/sensor indicator
    draw.ellipse([px + (pw + 130)//2, py + 30, px + (pw + 146)//2, py + 38], fill=(16, 185, 129))

    # Top Android Bar
    draw.text((px + 70, py + 35), "07:30", font=font_badge, fill=COLOR_TEXT_WHITE, anchor="mm")
    draw.text((px + pw - 80, py + 35), "VoLTE 100%", font=font_small, fill=COLOR_TEXT_WHITE, anchor="mm")

    # App Screen Interior:
    if scene == 0:
        # SCREEN 1: AlarmListScreen with QuickSleepCard & Coach mark overlay
        # App Top Bar inside
        draw.text((px + 60, py + 95), "AlarmSimple", font=font_alarm_bold, fill=COLOR_TEXT_WHITE)
        draw.text((px + pw - 60, py + 95), "❓  ⚙️", font=font_alarm_bold, fill=COLOR_TEXT_MUTED, anchor="rm")

        # QuickSleepCard («Баиньки»)
        draw_round_rect(draw, [px + 40, py + 140, px + pw - 40, py + 300], 28, fill=(30, 27, 75), outline=COLOR_PRIMARY, width=2)
        draw.text((px + 65, py + 180), "🌙 Идёте спать прямо сейчас?", font=font_alarm_bold, fill=COLOR_TEXT_WHITE)
        draw.text((px + 65, py + 225), "Нажмите «Баиньки», чтобы начать трекинг", font=font_body, fill=COLOR_TEXT_MUTED)
        
        # Pill button
        draw_round_rect(draw, [px + pw - 210, py + 230, px + pw - 60, py + 285], 16, fill=(255, 255, 255))
        draw.text((px + pw - 135, py + 257), "Баиньки 💤", font=font_badge, fill=(15, 23, 42), anchor="mm")

        # AlarmItemCard
        draw_round_rect(draw, [px + 40, py + 330, px + pw - 40, py + 540], 28, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=2)
        draw.text((px + 65, py + 370), "Будни (Работа)", font=font_badge, fill=COLOR_PRIMARY_LIGHT)
        draw.text((px + 65, py + 435), "07:30", font=font_clock_time, fill=COLOR_TEXT_WHITE)
        
        # Switch toggle
        draw_round_rect(draw, [px + pw - 150, py + 390, px + pw - 65, py + 440], 25, fill=COLOR_PRIMARY)
        draw.ellipse([px + pw - 110, py + 395, px + pw - 70, py + 435], fill=(255, 255, 255))

        draw.text((px + 65, py + 500), "💳 NFC: Карта МИР   •   Окно: 30 мин", font=font_body, fill=COLOR_PRIMARY_LIGHT)

        # Real CoachMarksOverlay Card
        draw_round_rect(draw, [px + 40, py + 570, px + pw - 40, py + 780], 26, fill=(25, 34, 61), outline=COLOR_PRIMARY_LIGHT, width=2)
        draw.text((px + 65, py + 610), "💡 Режим «Баиньки» (Подсказка 1 из 3)", font=font_alarm_bold, fill=COLOR_TEXT_WHITE)
        draw.text((px + 65, py + 665), "Запустите анализ сна в один клик без звонка.", font=font_body, fill=COLOR_TEXT_MUTED)
        draw.text((px + 65, py + 700), "Алгоритм запишет фазы и микро-движения.", font=font_body, fill=COLOR_TEXT_MUTED)

        # Bottom FAB button inside screen
        draw_round_rect(draw, [px + pw - 270, py + ph - 110, px + pw - 40, py + ph - 40], 24, fill=COLOR_PRIMARY)
        draw.text((px + pw - 155, py + ph - 75), "+ Будильник", font=font_alarm_bold, fill=COLOR_TEXT_WHITE, anchor="mm")

    elif scene == 1:
        # SCREEN 2: Alarm Firing with NFC Bank Card & Volume Lock
        draw_round_rect(draw, [px + 40, py + 90, px + pw - 40, py + 220], 24, fill=(159, 18, 57))
        draw.text((px + pw//2, py + 135), "БУДИЛЬНИК ЗВОНИТ!", font=font_alarm_bold, fill=COLOR_TEXT_WHITE, anchor="mm")
        draw.text((px + pw//2, py + 185), "Лёгкая фаза сна • 07:18", font=font_body, fill=(254, 205, 211), anchor="mm")

        # Bank Card + Pulse radar
        cx, cy = px + pw//2, py + 470
        for r_pulse in [90, 160, 230]:
            rp = (r_pulse + int(t * 80)) % 260
            draw.ellipse([cx - rp, cy - rp, cx + rp, cy + rp], outline=(244, 63, 94), width=3)

        # Card Graphic
        cw, ch = 440, 260
        draw_round_rect(draw, [cx - cw//2, cy - ch//2, cx + cw//2, cy + ch//2], 26, fill=(225, 29, 72), outline=(255, 255, 255), width=2)
        draw.text((cx - cw//2 + 35, cy - ch//2 + 45), "КАРТА МИР (NFC)", font=font_badge, fill=COLOR_TEXT_WHITE)
        draw.text((cx, cy + 15), "••••  ••••  ••••  8842", font=font_alarm_bold, fill=COLOR_TEXT_WHITE, anchor="mm")
        draw.text((cx, cy + 80), "Приложите карту для отбоя", font=font_body, fill=(254, 226, 226), anchor="mm")

        # Volume Lock Banner
        draw_round_rect(draw, [px + 40, py + 720, px + pw - 40, py + 860], 24, fill=COLOR_SURFACE, outline=COLOR_ROSE, width=2)
        draw.text((px + 65, py + 760), "🔒 Громкость заблокирована на максимум", font=font_alarm_bold, fill=COLOR_ROSE)
        draw.text((px + 65, py + 810), "Кнопки смартфона отключены до выполнения квеста", font=font_body, fill=COLOR_TEXT_MUTED)

    elif scene == 2:
        # SCREEN 3: AlarmSetup with Smart Window
        draw.text((px + 60, py + 95), "← Настройка умного будильника", font=font_alarm_bold, fill=COLOR_TEXT_WHITE)

        # Target Time
        draw_round_rect(draw, [px + 40, py + 140, px + pw - 40, py + 260], 26, fill=COLOR_SURFACE, outline=COLOR_BORDER)
        draw.text((px + 65, py + 200), "Время пробуждения:  07:00", font=font_alarm_bold, fill=COLOR_PRIMARY_LIGHT)

        # Smart Window Box
        draw_round_rect(draw, [px + 40, py + 290, px + pw - 40, py + 520], 28, fill=COLOR_SURFACE, outline=COLOR_PRIMARY, width=2)
        draw.text((px + 65, py + 335), "Умное окно пробуждения", font=font_alarm_bold, fill=COLOR_TEXT_WHITE)
        draw.text((px + 65, py + 380), "Будит в лёгкой фазе сна перед звонком", font=font_body, fill=COLOR_TEXT_MUTED)

        # 15 / 30 / 45 / 60 min buttons
        btn_y = py + 430
        draw_round_rect(draw, [px + 60, btn_y, px + 185, btn_y + 60], 16, fill=(30, 41, 59))
        draw.text((px + 122, btn_y + 30), "15м", font=font_badge, fill=COLOR_TEXT_MUTED, anchor="mm")

        draw_round_rect(draw, [px + 205, btn_y, px + 330, btn_y + 60], 16, fill=COLOR_PRIMARY)
        draw.text((px + 267, btn_y + 30), "30м ✓", font=font_badge, fill=COLOR_TEXT_WHITE, anchor="mm")

        draw_round_rect(draw, [px + 350, btn_y, px + 475, btn_y + 60], 16, fill=(30, 41, 59))
        draw.text((px + 412, btn_y + 30), "45м", font=font_badge, fill=COLOR_TEXT_MUTED, anchor="mm")

        draw_round_rect(draw, [px + 495, btn_y, px + 620, btn_y + 60], 16, fill=(30, 41, 59))
        draw.text((px + 557, btn_y + 30), "60м", font=font_badge, fill=COLOR_TEXT_MUTED, anchor="mm")

        # Quests list
        draw_round_rect(draw, [px + 40, py + 550, px + pw - 40, py + 740], 26, fill=COLOR_SURFACE, outline=COLOR_BORDER)
        draw.text((px + 65, py + 595), "Квест на выключение:", font=font_alarm_bold, fill=COLOR_TEXT_WHITE)
        draw.text((px + 65, py + 645), "• 💳 Банковская карта (NFC)", font=font_body, fill=COLOR_PRIMARY_LIGHT)
        draw.text((px + 65, py + 685), "• 📷 Штрихкод в ванной комнате", font=font_body, fill=COLOR_TEXT_WHITE)

    else:
        # SCREEN 4: Sleep Stats Screen with Real Hypnogram
        draw.text((px + 60, py + 95), "Статистика сна • 18 Октября", font=font_alarm_bold, fill=COLOR_TEXT_WHITE)

        # Graph Box
        draw_round_rect(draw, [px + 40, py + 140, px + pw - 40, py + 460], 28, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=2)
        draw.text((px + 65, py + 180), "Гипнограмма ночи", font=font_alarm_bold, fill=COLOR_PRIMARY_LIGHT)
        draw.text((px + pw - 70, py + 180), "94% КПД", font=font_alarm_bold, fill=COLOR_SECONDARY, anchor="rm")

        # Draw smooth wave graph
        gw = pw - 150
        base_y = py + 360
        for i in range(gw):
            gx = px + 75 + i
            # Sleep phases curve
            phase_h = math.sin(i * 0.04) * 45 + math.cos(i * 0.015) * 35
            gy = int(base_y + phase_h)
            draw.line([gx, gy, gx, base_y + 55], fill=(30, 41, 75), width=2)
            draw.point((gx, gy), fill=COLOR_PRIMARY_LIGHT)

        draw.text((px + 75, py + 430), "23:45 (Засыпание)           03:15 (Глубокий)           07:18 (Подъём)", font=font_small, fill=COLOR_TEXT_MUTED)

        # Metrics cards
        draw_round_rect(draw, [px + 40, py + 490, px + pw//2 - 15, py + 640], 24, fill=COLOR_SURFACE)
        draw.text((px + 65, py + 530), "Общий сон", font=font_body, fill=COLOR_TEXT_MUTED)
        draw.text((px + 65, py + 580), "7ч 33м", font=font_alarm_bold, fill=COLOR_TEXT_WHITE)

        draw_round_rect(draw, [px + pw//2 + 15, py + 490, px + pw - 40, py + 640], 24, fill=COLOR_SURFACE)
        draw.text((px + pw//2 + 40, py + 530), "Пробуждений", font=font_body, fill=COLOR_TEXT_MUTED)
        draw.text((px + pw//2 + 40, py + 580), "0 раз", font=font_alarm_bold, fill=COLOR_SECONDARY)

        # Call to action inside phone
        draw_round_rect(draw, [px + 40, py + 680, px + pw - 40, py + 780], 24, fill=COLOR_PRIMARY)
        draw.text((px + pw//2, py + 730), "Просыпайся легко с AlarmSimple!", font=font_alarm_bold, fill=COLOR_TEXT_WHITE, anchor="mm")

    # Bottom Call To Action in Video
    draw_round_rect(draw, [70, HEIGHT - 180, WIDTH - 70, HEIGHT - 70], 36, fill=COLOR_PRIMARY)
    draw.text((WIDTH//2, HEIGHT - 125), "🔥 Скачать APK бесплатно: AlarmSimple", font=font_promo_sub, fill=COLOR_TEXT_WHITE, anchor="mm")

    # Save frame
    frame_path = os.path.join(TMP_DIR, f"cinematic_{frame_idx:04d}.png")
    img.save(frame_path)

print(f"Rendering {TOTAL_FRAMES} high-precision cinematic frames...")
for f in range(TOTAL_FRAMES):
    if f % 30 == 0:
        print(f"Frame {f}/{TOTAL_FRAMES} ({f/TOTAL_FRAMES*100:.1f}%)")
    render_cinematic_frame(f)

print("Frames rendered! Generating audio track and final MP4 video...")

# Generate soundtrack via ffmpeg synth:
# Ambient drone + high-tech swooshes at 4s, 8s, 12s + alarm beep at 4.5s
audio_mp3 = os.path.join(TMP_DIR, "soundtrack.mp3")

# We synthesize a rich audio bed:
synth_audio_cmd = [
    "ffmpeg", "-y",
    "-f", "lavfi", "-i", "sine=frequency=110:duration=16",
    "-f", "lavfi", "-i", "sine=frequency=440:duration=0.2",
    "-f", "lavfi", "-i", "sine=frequency=880:duration=0.25",
    "-filter_complex", 
    "[0:a]volume=0.25[amb];"
    "[1:a]adelay=4000|4000,volume=0.5[sw1];"
    "[2:a]adelay=4400|4400,volume=0.6[al1];"
    "[1:a]adelay=8000|8000,volume=0.5[sw2];"
    "[1:a]adelay=12000|12000,volume=0.5[sw3];"
    "[amb][sw1][al1][sw2][sw3]amix=inputs=5:duration=longest[outa]",
    "-map", "[outa]",
    audio_mp3
]
subprocess.run(synth_audio_cmd, capture_output=True)

output_mp4 = r"E:\AlarmSimple\AlarmSimple-Cinematic-Promo.mp4"
video_cmd = [
    "ffmpeg", "-y",
    "-framerate", str(FPS),
    "-i", os.path.join(TMP_DIR, "cinematic_%04d.png"),
    "-i", audio_mp3,
    "-c:v", "libx264",
    "-pix_fmt", "yuv420p",
    "-preset", "fast",
    "-crf", "17",
    "-c:a", "aac",
    "-b:a", "192k",
    "-shortest",
    output_mp4
]

res = subprocess.run(video_cmd, capture_output=True, text=True)
if res.returncode == 0:
    print(f"SUCCESS: Cinematic Video created: {output_mp4}")
    # Cleanup frames
    shutil.rmtree(TMP_DIR, ignore_errors=True)
else:
    print(f"FFMPEG ERROR: {res.stderr}")
