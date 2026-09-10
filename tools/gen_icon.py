# -*- coding: utf-8 -*-
"""
터치노치 런처 아이콘 생성기.

안드로이드 vector drawable 과 브라우저 미리보기 SVG 를 같은 좌표 정의에서 만들어
둘이 어긋나지 않게 한다. 아이콘을 손보려면 아래 '형태 정의'만 고치고 다시 실행:

    python tools/gen_icon.py
    # tools/icon-preview.html 을 브라우저로 열어 확인

좌표계는 adaptive icon 기준 108x108 이다. 주의할 점:
  - 가운데 72dp(18~90) 만 보인다.
  - 원형 마스크에서는 지름 66dp(중심 54, 반지름 33) 까지 잘린다.
그래서 폰 실루엣처럼 큰 형태는 잘려나가 못 쓴다. 노치 자체를 크게 그리고
아래로 퍼지는 파동으로 "터치하면 반응한다"는 뜻을 담았다.
"""
import io
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
PREVIEW = os.path.join(ROOT, "tools", "icon-preview.html")

# ---------------- 형태 정의 ----------------

PILL_CX, PILL_CY = 54.0, 40.0
PILL_W, PILL_H = 34.0, 12.0

# 파동이 아래로 퍼지는 각도 (정중앙 아래에서 좌우로 벌어지는 반각)
WAVE_SPREAD_DEG = 52.0
# (반지름, 알파, 선 굵기) — 안쪽부터
WAVES = [(15.0, 0.55, 2.4), (21.0, 0.33, 2.2), (27.0, 0.18, 2.0)]

GRADIENT = [(0.0, "#FF4FC3F7"), (0.5, "#FF2081E2"), (1.0, "#FF16308C")]


def num(v):
    """소수점 불필요한 0 제거"""
    return f"{v:g}"


def pill_path(cx=PILL_CX, cy=PILL_CY, w=PILL_W, h=PILL_H):
    """양끝이 완전히 둥근 pill 경로"""
    r = h / 2.0
    left, right = cx - w / 2.0, cx + w / 2.0
    top, bottom = cy - r, cy + r
    return (
        f"M{num(left + r)},{num(top)} H{num(right - r)} "
        f"A{num(r)},{num(r)} 0 0 1 {num(right - r)},{num(bottom)} "
        f"H{num(left + r)} A{num(r)},{num(r)} 0 0 1 {num(left + r)},{num(top)} Z"
    )


def wave_path(r, cx=PILL_CX, cy=PILL_CY, spread_deg=WAVE_SPREAD_DEG):
    """노치 아래로 퍼지는 호. sweep=0 이 아래쪽으로 부푼다."""
    t = math.radians(spread_deg)
    dx, dy = r * math.sin(t), r * math.cos(t)
    return (
        f"M{num(round(cx - dx, 2))},{num(round(cy + dy, 2))} "
        f"A{num(r)},{num(r)} 0 0 0 {num(round(cx + dx, 2))},{num(round(cy + dy, 2))}"
    )


# ---------------- 안드로이드 vector drawable ----------------

VECTOR_HEAD = '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
'''


def android_waves(alpha_boost=0.0, width_boost=0.0):
    return "\n".join(
        f'''    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeAlpha="{min(a + alpha_boost, 1.0):.2f}"
        android:strokeWidth="{w + width_boost:g}"
        android:strokeLineCap="round"
        android:pathData="{wave_path(r)}" />'''
        for r, a, w in WAVES
    )


def android_foreground():
    return (
        VECTOR_HEAD
        + '''
    <!--
      가운데 72dp 만 보이고 원형 마스크에서는 지름 66dp 까지 잘린다.
      큰 형태는 못 쓰므로 노치를 주인공으로 두고 아래로 파동을 퍼뜨렸다.
      모양을 고치려면 tools/gen_icon.py 를 수정해 다시 생성한다.
    -->

'''
        + android_waves()
        + f'''

    <!-- 노치 -->
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{pill_path()}" />
</vector>
'''
    )


def android_monochrome():
    """테마 아이콘용 — 단색으로 칠해지므로 대비를 조금 더 준다"""
    return (
        VECTOR_HEAD
        + android_waves(alpha_boost=0.25, width_boost=0.3)
        + f'''
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{pill_path()}" />
</vector>
'''
    )


def android_background():
    stops = "\n".join(
        f'                <item android:offset="{o:g}" android:color="{c}" />'
        for o, c in GRADIENT
    )
    return f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="4"
                android:startY="0"
                android:endX="104"
                android:endY="108">
{stops}
            </gradient>
        </aapt:attr>
    </path>
    <!-- 왼쪽 위 은은한 광택 -->
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="30"
                android:centerY="22"
                android:gradientRadius="62">
                <item android:offset="0" android:color="#33FFFFFF" />
                <item android:offset="1" android:color="#00FFFFFF" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''


def android_notification_glyph():
    """알림용 24dp 글리프 — 같은 모티프를 단순화 (흰 실루엣만 쓰인다)"""
    return '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M9,5 H15 A3,3 0 0 1 15,11 H9 A3,3 0 0 1 9,5 Z" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="@android:color/white"
        android:strokeAlpha="0.6"
        android:strokeWidth="1.7"
        android:strokeLineCap="round"
        android:pathData="M8.3,14.4 A5,5 0 0 0 15.7,14.4" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="@android:color/white"
        android:strokeAlpha="0.35"
        android:strokeWidth="1.6"
        android:strokeLineCap="round"
        android:pathData="M5.9,17.3 A8.5,8.5 0 0 0 18.1,17.3" />
</vector>
'''


# ---------------- 미리보기 ----------------

def preview_html():
    waves = "\n".join(
        f'      <path fill="none" stroke="#fff" stroke-opacity="{a}" stroke-width="{w:g}" '
        f'stroke-linecap="round" d="{wave_path(r)}"/>'
        for r, a, w in WAVES
    )
    mono_waves = "\n".join(
        f'      <path fill="none" stroke="#fff" stroke-opacity="{min(a + 0.25, 1.0):.2f}" '
        f'stroke-width="{w + 0.3:g}" stroke-linecap="round" d="{wave_path(r)}"/>'
        for r, a, w in WAVES
    )
    stops = "\n".join(
        f'      <stop offset="{o:g}" stop-color="#{c[3:]}"/>' for o, c in GRADIENT
    )
    pill = pill_path()
    sizes = [(192, "squircle"), (128, "circle"), (96, "squircle"),
             (72, "circle"), (48, "squircle"), (36, "circle"), (24, "circle")]
    tiles = "\n".join(
        f'  <div class="item"><svg width="{s}" height="{s}" viewBox="18 18 72 72">'
        f'<g clip-path="url(#{m})"><use href="#icon"/></g></svg>'
        f'<div class="cap">{s}px</div></div>'
        for s, m in sizes
    )
    return f'''<title>터치노치 아이콘 미리보기</title>
<!-- tools/gen_icon.py 가 생성한다. 직접 고치지 말 것 -->
<style>
  body {{ background:#e8eaed; font-family:system-ui,sans-serif; padding:20px; margin:0; }}
  h2 {{ font-size:13px; color:#3c4043; margin:20px 0 8px; }}
  .row {{ display:flex; align-items:flex-end; gap:20px; flex-wrap:wrap; }}
  .item {{ text-align:center; }}
  .cap {{ font-size:11px; color:#5f6368; margin-top:5px; }}
  .dark {{ background:#111418; padding:16px; border-radius:12px; }}
</style>
<svg width="0" height="0" style="position:absolute"><defs>
  <linearGradient id="bg" x1="4" y1="0" x2="104" y2="108" gradientUnits="userSpaceOnUse">
{stops}
  </linearGradient>
  <radialGradient id="gloss" cx="30" cy="22" r="62" gradientUnits="userSpaceOnUse">
    <stop offset="0" stop-color="#fff" stop-opacity="0.2"/><stop offset="1" stop-color="#fff" stop-opacity="0"/>
  </radialGradient>
  <clipPath id="circle"><circle cx="54" cy="54" r="36"/></clipPath>
  <clipPath id="squircle"><path d="M54,18 C74,18 90,34 90,54 C90,74 74,90 54,90 C34,90 18,74 18,54 C18,34 34,18 54,18 Z"/></clipPath>
  <g id="icon">
    <rect x="0" y="0" width="108" height="108" fill="url(#bg)"/>
    <circle cx="54" cy="54" r="54" fill="url(#gloss)"/>
{waves}
    <path fill="#fff" d="{pill}"/>
  </g>
  <g id="mono">
{mono_waves}
    <path fill="#fff" d="{pill}"/>
  </g>
</defs></svg>

<h2>런처 아이콘 (마스크: 스퀴클 / 원형 번갈아)</h2>
<div class="row">
{tiles}
</div>

<h2>안전영역 확인 — 회색이 잘려나가는 부분</h2>
<div class="row">
  <div class="item">
    <svg width="200" height="200" viewBox="0 0 108 108">
      <use href="#icon"/>
      <rect x="0" y="0" width="108" height="108" fill="#000" opacity="0.45"/>
      <g clip-path="url(#circle)"><use href="#icon"/></g>
      <circle cx="54" cy="54" r="33" fill="none" stroke="#ff5252" stroke-width="0.7" stroke-dasharray="3 2"/>
      <rect x="18" y="18" width="72" height="72" fill="none" stroke="#ffeb3b" stroke-width="0.7" stroke-dasharray="3 2"/>
    </svg>
    <div class="cap">노란선 72dp / 빨간선 원형 마스크 66dp</div>
  </div>
</div>

<h2>테마 아이콘 (monochrome)</h2>
<div class="row">
  <div class="item dark"><svg width="120" height="120" viewBox="18 18 72 72"><use href="#mono"/></svg>
    <div class="cap" style="color:#9aa0a6">120px</div></div>
  <div class="item dark"><svg width="56" height="56" viewBox="18 18 72 72"><use href="#mono"/></svg>
    <div class="cap" style="color:#9aa0a6">56px</div></div>
</div>
'''


def write(path, content):
    io.open(path, "w", encoding="utf-8", newline="\n").write(content)
    print("wrote", os.path.relpath(path, ROOT))


write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), android_foreground())
write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), android_monochrome())
write(os.path.join(RES, "drawable", "ic_launcher_background.xml"), android_background())
write(os.path.join(RES, "drawable", "ic_notch.xml"), android_notification_glyph())
write(PREVIEW, preview_html())
