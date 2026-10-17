import math
import subprocess
import os
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1080, 1920 # Vertical Reels/Shorts/TikTok/Telegram format
FPS = 30
DURATION_SEC = 16
TOTAL_FRAMES = FPS * DURATION_SEC

TMP_DIR = r"E:\AlarmSimple\promo_frames"
os.makedirs(TMP_DIR, exist_ok=True)

# Load fonts
try:
    font_title = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 68)
    font_subtitle = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 36)
    font_bold = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 38)
    font_clock = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 110)
    font_badge = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 26)
    font_small = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 28)
except Exception:
    font_title = font_subtitle = font_bold = font_clock = font_badge = font_small = ImageFont.load_default()

def draw_rounded_rect(draw, coords, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(coords, radius=radius, fill=fill, outline=outline, width=width)

def render_frame(frame_idx):
    t = frame_idx / FPS
    progress = frame_idx / TOTAL_FRAMES
    
    # Background: Dark cyber space with dynamic gradient
    img = Image.new("RGB", (WIDTH, HEIGHT), (8, 12, 24))
    draw = ImageDraw.Draw(img)
    
    # Background neon radial glow
    glow_x = int(WIDTH / 2 + math.sin(t * 1.5) * 120)
    glow_y = int(HEIGHT * 0.45 + math.cos(t * 1.2) * 80)
    glow_radius = 420
    
    # Neon glow backdrop
    for r in range(glow_radius, 50, -35):
        alpha = int((1 - r / glow_radius) * 45)
        # Deep indigo/purple glow
        color = (int(79 + math.sin(t)*30), int(40 + math.cos(t)*20), int(160 + math.sin(t*2)*40))
        draw.ellipse([glow_x - r, glow_y - r, glow_x + r, glow_y + r], fill=color)

    # Top Brand Header
    draw_rounded_rect(draw, [WIDTH//2 - 190, 80, WIDTH//2 + 190, 140], radius=30, fill=(30, 27, 75, 200), outline=(99, 102, 241), width=2)
    draw.text((WIDTH//2, 110), "⏰ AlarmSimple", font=font_subtitle, fill=(240, 245, 255), anchor="mm")

    # Determine Scenes:
    # 0 - 4s: Scene 1: Smart Wake & Soft phase
    # 4 - 8s: Scene 2: NFC Bank Card Quest
    # 8 - 12s: Scene 3: Unstoppable Siren & Volume Lock
    # 12 - 16s: Scene 4: "Баиньки" 1-click & Analytics Call to action
    
    scene = int(t // 4)
    local_t = t % 4
    
    # Phone Mockup coordinates
    phone_w = 640
    phone_h = 1080
    phone_x = (WIDTH - phone_w) // 2
    phone_float_y = int(math.sin(t * 2.5) * 15)
    phone_y = 520 + phone_float_y
    
    # Draw Phone Outer Body
    draw_rounded_rect(draw, [phone_x - 14, phone_y - 14, phone_x + phone_w + 14, phone_y + phone_h + 14], radius=68, fill=(30, 41, 59), outline=(71, 85, 105), width=3)
    # Phone Screen
    draw_rounded_rect(draw, [phone_x, phone_y, phone_x + phone_w, phone_y + phone_h], radius=56, fill=(15, 23, 42))
    
    # Dynamic Island
    notch_w = 180
    draw_rounded_rect(draw, [phone_x + (phone_w - notch_w)//2, phone_y + 16, phone_x + (phone_w + notch_w)//2, phone_y + 50], radius=18, fill=(0, 0, 0))
    draw.ellipse([phone_x + (phone_w - notch_w)//2 + 20, phone_y + 26, phone_x + (phone_w - notch_w)//2 + 36, phone_y + 42], fill=(15, 23, 42))
    
    # Phone Top Bar
    draw.text((phone_x + 60, phone_y + 34), "07:30", font=font_badge, fill=(203, 213, 225), anchor="mm")
    draw.text((phone_x + phone_w - 70, phone_y + 34), "5G 100%", font=font_badge, fill=(203, 213, 225), anchor="mm")

    if scene == 0:
        # Scene 1: Smart Wake
        headline = "МЯГКОЕ ПРОБУЖДЕНИЕ"
        sub = "Умный будильник будит в лёгкой фазе сна"
        badge_text = "ФАЗЫ СНА БЕЗ БРАСЛЕТА"
        
        # Phone Content:
        draw_rounded_rect(draw, [phone_x + 40, phone_y + 90, phone_x + phone_w - 40, phone_y + 250], radius=28, fill=(30, 27, 75), outline=(99, 102, 241), width=2)
        draw.text((phone_x + phone_w//2, phone_y + 145), "07:15", font=font_clock, fill=(255, 255, 255), anchor="mm")
        draw.text((phone_x + phone_w//2, phone_y + 215), "Лёгкая фаза сна обнаружена!", font=font_badge, fill=(165, 180, 252), anchor="mm")
        
        # Smart Window graphic
        draw_rounded_rect(draw, [phone_x + 40, phone_y + 280, phone_x + phone_w - 40, phone_y + 440], radius=24, fill=(24, 32, 56))
        draw.text((phone_x + 70, phone_y + 320), "Окно пробуждения: 30 минут", font=font_bold, fill=(255, 255, 255))
        draw.text((phone_x + 70, phone_y + 370), "Диапазон: 07:00 — 07:30", font=font_small, fill=(148, 163, 184))
        draw.text((phone_x + 70, phone_y + 405), "Подъём без головной боли и усталости", font=font_badge, fill=(52, 211, 153))

        # Sleep Graph wave inside screen
        graph_y = phone_y + 540
        draw.text((phone_x + 70, graph_y - 40), "Анализ дыхания и микро-движений", font=font_bold, fill=(241, 245, 249))
        for i in range(phone_w - 120):
            gx = phone_x + 60 + i
            gy = int(graph_y + math.sin(i * 0.05 + t * 4) * 35 + math.cos(i * 0.02) * 20)
            draw.line([gx, gy, gx, gy + 4], fill=(129, 140, 248), width=3)

    elif scene == 1:
        # Scene 2: NFC Bank Card Quest
        headline = "КВЕСТ ПО КАРТЕ"
        sub = "Привяжи обычную банковскую карту"
        badge_text = "NFC МЕТКА В ДРУГОЙ КОМНАТЕ"
        
        # Ringing banner
        draw_rounded_rect(draw, [phone_x + 40, phone_y + 85, phone_x + phone_w - 40, phone_y + 190], radius=24, fill=(159, 18, 57))
        draw.text((phone_x + phone_w//2, phone_y + 125), "БУДИЛЬНИК ЗВОНИТ!", font=font_bold, fill=(255, 255, 255), anchor="mm")
        draw.text((phone_x + phone_w//2, phone_y + 165), "Чтобы выключить — встань с кровати", font=font_badge, fill=(254, 205, 211), anchor="mm")
        
        # Card Mockup with radar waves
        center_x = phone_x + phone_w // 2
        center_y = phone_y + 440
        
        # Expanding pulse rings
        for ring in [70, 130, 190]:
            r_scale = (ring + int(t * 70)) % 220
            draw.ellipse([center_x - r_scale, center_y - r_scale, center_x + r_scale, center_y + r_scale], outline=(244, 63, 94), width=3)
            
        # Bank Card Graphic
        card_w, card_h = 360, 220
        draw_rounded_rect(draw, [center_x - card_w//2, center_y - card_h//2, center_x + card_w//2, center_y + card_h//2], radius=22, fill=(225, 29, 72), outline=(255, 255, 255), width=2)
        draw.text((center_x - card_w//2 + 30, center_y - card_h//2 + 40), "МИР / VISA / MASTERCARD", font=font_badge, fill=(255, 255, 255))
        draw.text((center_x, center_y + 20), "••••  ••••  ••••  9081", font=font_bold, fill=(255, 255, 255), anchor="mm")
        draw.text((center_x, center_y + 65), "ПРИЛОЖИТЕ КАРТУ ДЛЯ ОТБОЯ", font=font_badge, fill=(254, 226, 226), anchor="mm")

    elif scene == 2:
        # Scene 3: Unstoppable Siren & Volume Lock
        headline = "ЗАМОК ГРОМКОСТИ"
        sub = "Кнопки громкости отключены, не заглушить!"
        badge_text = "РЕЖИМ СИРЕНЫ 100%"
        
        draw_rounded_rect(draw, [phone_x + 40, phone_y + 90, phone_x + phone_w - 40, phone_y + 240], radius=26, fill=(136, 19, 55))
        draw.text((phone_x + phone_w//2, phone_y + 145), "🔒 ЗАБЛОКИРОВАНО", font=font_bold, fill=(255, 255, 255), anchor="mm")
        draw.text((phone_x + phone_w//2, phone_y + 195), "Громкость зафиксирована на максимум", font=font_badge, fill=(253, 164, 175), anchor="mm")

        # Siren speaker pulsing
        speaker_pulse = int(math.sin(t * 12) * 20)
        draw_rounded_rect(draw, [phone_x + 60, phone_y + 290, phone_x + phone_w - 60, phone_y + 530], radius=32, fill=(190, 18, 60))
        draw.text((phone_x + phone_w//2, phone_y + 370), "📢 СИРЕНА ВКЛЮЧЕНА", font=font_bold, fill=(255, 255, 255), anchor="mm")
        draw.text((phone_x + phone_w//2, phone_y + 440), "Квест обязателен:", font=font_badge, fill=(254, 205, 211), anchor="mm")
        draw.text((phone_x + phone_w//2, phone_y + 480), "NFC-карта, Штрихкод или Математика", font=font_bold, fill=(255, 255, 255), anchor="mm")

    else:
        # Scene 4: "Баиньки" & Final Call to action
        headline = "РЕЖИМ «БАИНЬКИ»"
        sub = "Трекинг сна в один клик без будильника"
        badge_text = "СКАЧАЙТЕ БЕСПЛАТНО"
        
        draw_rounded_rect(draw, [phone_x + 40, phone_y + 90, phone_x + phone_w - 40, phone_y + 280], radius=28, fill=(30, 27, 75), outline=(139, 92, 246), width=2)
        draw.text((phone_x + phone_w//2, phone_y + 145), "🌙 Идёте спать прямо сейчас?", font=font_bold, fill=(255, 255, 255), anchor="mm")
        draw.text((phone_x + phone_w//2, phone_y + 210), "Кнопка «Баиньки» активна", font=font_badge, fill=(196, 181, 253), anchor="mm")
        
        # Glowing big button
        draw_rounded_rect(draw, [phone_x + 80, phone_y + 330, phone_x + phone_w - 80, phone_y + 440], radius=24, fill=(99, 102, 241))
        draw.text((phone_x + phone_w//2, phone_y + 385), "Баиньки 💤 (Старт)", font=font_bold, fill=(255, 255, 255), anchor="mm")
        
        # Stats below
        draw_rounded_rect(draw, [phone_x + 40, phone_y + 480, phone_x + phone_w - 40, phone_y + 640], radius=24, fill=(24, 32, 56))
        draw.text((phone_x + 70, phone_y + 530), "Эффективность сна: 94%", font=font_bold, fill=(52, 211, 153))
        draw.text((phone_x + 70, phone_y + 580), "Время глубокого сна: 3ч 45м", font=font_small, fill=(148, 163, 184))

    # Top Scene Titles and Overlays
    draw_rounded_rect(draw, [WIDTH//2 - 240, 200, WIDTH//2 + 240, 255], radius=25, fill=(49, 46, 129, 200), outline=(129, 140, 248), width=1)
    draw.text((WIDTH//2, 227), badge_text, font=font_badge, fill=(199, 210, 254), anchor="mm")
    
    draw.text((WIDTH//2, 330), headline, font=font_title, fill=(255, 255, 255), anchor="mm")
    draw.text((WIDTH//2, 400), sub, font=font_subtitle, fill=(203, 213, 225), anchor="mm")

    # Bottom Call to action bar
    draw_rounded_rect(draw, [80, HEIGHT - 180, WIDTH - 80, HEIGHT - 70], radius=32, fill=(99, 102, 241))
    draw.text((WIDTH//2, HEIGHT - 125), "🔥 Скачать AlarmSimple (APK)", font=font_bold, fill=(255, 255, 255), anchor="mm")

    # Save frame
    frame_path = os.path.join(TMP_DIR, f"frame_{frame_idx:04d}.png")
    img.save(frame_path)

print(f"Generating {TOTAL_FRAMES} frames...")
for f in range(TOTAL_FRAMES):
    if f % 30 == 0:
        print(f"Rendering frame {f}/{TOTAL_FRAMES} ({f/TOTAL_FRAMES*100:.1f}%)")
    render_frame(f)

print("All frames rendered! Encoding to high-quality MP4 video with ffmpeg...")
output_mp4 = r"E:\AlarmSimple\AlarmSimple-Promo.mp4"

cmd = [
    "ffmpeg", "-y",
    "-framerate", str(FPS),
    "-i", os.path.join(TMP_DIR, "frame_%04d.png"),
    "-c:v", "libx264",
    "-pix_fmt", "yuv420p",
    "-preset", "fast",
    "-crf", "18",
    output_mp4
]

res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode == 0:
    print(f"SUCCESS: Video saved to {output_mp4}")
else:
    print(f"FFMPEG ERROR: {res.stderr}")
