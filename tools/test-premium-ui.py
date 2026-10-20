"""Physical-receiver smoke test. Does not change subscriptions or the default HOME.

Run: python tools/test-premium-ui.py --device HOST:5555
Requires an already installed APK and authorized ADB. Evidence is saved under shots/.
"""
import argparse
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--device", required=True)
parser.add_argument("--output", default="shots/premium-validation")
args = parser.parse_args()
sys.stdout.reconfigure(encoding="utf-8")
out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)
checks = []


def adb(*words, binary=False):
    result = subprocess.run(["adb", "-s", args.device, *words], capture_output=True, timeout=40)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", "replace"))
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def key(code):
    adb("shell", "input", "keyevent", str(code))
    time.sleep(0.2)


def snapshot(name, photo=True):
    remote = "/sdcard/tvhome-validation-" + name + ".xml"
    for attempt in range(5):
        words = ["shell", "uiautomator", "dump"]
        if attempt >= 3:
            words.append("--compressed")
        result = adb(*words, remote)
        if "dumped to:" in result:
            break
        if attempt == 4:
            raise RuntimeError("Could not capture a fresh UI hierarchy: " + result)
        time.sleep(1)
    xml = adb("exec-out", "cat", remote, binary=True)
    (out / (name + ".xml")).write_bytes(xml)
    if photo:
        png = adb("exec-out", "screencap", "-p", binary=True)
        assert png[:8] == b"\x89PNG\r\n\x1a\n", "Invalid screenshot"
        assert struct.unpack(">II", png[16:24]) == (1920, 1080), "Unexpected receiver resolution"
        (out / (name + ".png")).write_bytes(png)
    return ET.fromstring(xml)


def check(condition, description):
    assert condition, description
    checks.append(description)
    print("PASS " + description, flush=True)


def node(root, label):
    return next(n for n in root.iter("node") if n.get("text") == label or n.get("content-desc") == label)


def bounds(n):
    return list(map(int, re.findall(r"\d+", n.get("bounds"))))


def tap(root, label):
    x1, y1, x2, y2 = bounds(node(root, label))
    assert x2 > x1 and y2 > y1, "Target is outside the visible screen"
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.4)


def focus(root):
    return next(n for n in root.iter("node") if n.get("focused") == "true")


adb("shell", "am", "start", "-n", "com.mihir.tvhome/.MainActivity")
time.sleep(1)
for _ in range(8):
    key(19)
for _ in range(6):
    key(22)
home = snapshot("01-home-ar")
check(focus(home).get("text") == "الرئيسية", "Right edge of RTL navigation is Home")
check(bounds(node(home, "الرئيسية"))[0] > bounds(node(home, "التطبيقات"))[0], "Arabic navigation is arranged right to left")
check(all(n.get("text") != "متابعة المشاهدة" for n in home.iter("node")), "No unsupported Continue Watching shelf")
cards = [n for n in home.iter("node") if n.get("content-desc") and 400 <= bounds(n)[1] < 600]
check(len(cards) >= 5 and len({bounds(n)[2] - bounds(n)[0] for n in cards}) == 1,
      "Favorites use equal fixed widths")
key(20)
# A remembered favorite can be the leftmost Add App tile. Normalize to the
# right edge before checking motion; Left correctly stays put at the far edge.
for _ in cards:
    key(22)
dock = snapshot("02-dock-focus", photo=False)
first = focus(dock).get("content-desc")
check(first is not None, "Down from navigation returns to a labeled favorite")
key(21)
left = snapshot("03-left-focus", photo=False)
check(bounds(focus(left))[0] < bounds(focus(dock))[0], "Left on the remote moves physically left through Favorites")
key(22)
right = snapshot("04-right-focus", photo=False)
check(focus(right).get("content-desc") == first, "Right returns to the previous favorite")

# Test the favorite menu without saving a reorder/removal.
key(21)
key(82)
menu = snapshot("05-favorite-menu")
check(node(menu, "نقل لليسار") is not None and node(menu, "إزالة من المفضلة") is not None,
      "MENU opens Arabic favorite controls")
check(all(n.get("text") != "واجهة ستارنت الأصلية" for n in menu.iter("node")),
      "Favorite MENU does not also open the quick menu")
key(4)

key(19)
key(19)
key(21)
discover = snapshot("06-discover-ar")
check(node(discover, "اختر ما تشاهده اليوم") is not None, "Discover has Arabic hero copy")
key(21)
apps = snapshot("07-apps-ar")
check(node(apps, "كل التطبيقات") is not None, "All Apps is reachable by remote")
check(any(n.get("content-desc") == "NOVA V2 AC" for n in apps.iter("node")), "Nova remains in the complete Apps inventory")
key(21)
key(23)
quick = snapshot("08-quick-menu")
check(node(quick, "واجهة ستارنت الأصلية") is not None and node(quick, "المشغّل الافتراضي") is not None,
      "Quick menu exposes Glide and launcher switching")
key(4)
key(21)
settings = snapshot("09-settings-ar")
check(node(settings, "الإعدادات والتخصيص") is not None, "Settings is localized")
key(20)
for _ in range(4):
    key(20)
saver_settings = snapshot("10-screensaver-settings")
check(node(saver_settings, "معاينة شاشة التوقف") is not None, "Screensaver controls scroll into view with the remote")
key(23)
time.sleep(4)
# This firmware does not consistently expose a UI hierarchy over fullscreen photos.
png = adb("exec-out", "screencap", "-p", binary=True)
check(png[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack(">II", png[16:24]) == (1920, 1080),
      "Screensaver produces a Full HD screenshot")
(out / "11-screensaver-preview.png").write_bytes(png)
pid = adb("shell", "pidof", "com.mihir.tvhome").strip()
check("Displaying " in adb("logcat", "-d", "--pid=" + pid, "-s", "PhotoScreensaver:I", "*:S"),
      "Photo decoder successfully displays a screensaver image")
key(23)
awake = snapshot("12-screensaver-wake")
check(node(awake, "معاينة شاشة التوقف") is not None, "First OK wakes the screensaver without relaunching it")
check("com.mihir.tvhome/.MainActivity" in adb("shell", "cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"),
      "Launcher is registered as a selectable Android HOME")
adb("shell", "input", "keyevent", "4")
(out / "results.json").write_text(json.dumps({"checks": checks}, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"passed": len(checks), "evidence": str(out.resolve())}, ensure_ascii=False), flush=True)
