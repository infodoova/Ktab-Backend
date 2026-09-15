#!/usr/bin/env python3
"""
Seed Script via REST API: Setup "Dar Hashem" Library, Staff & Books
Uses Ktab's versioned REST API (/api/v1/...) with JWT authentication.
Reads authentic PDFs from the docs/PDF directory and generates 1.6 aspect ratio covers.

Requirements:
    pip install requests
"""

import os
import sys
import json
import zlib
import struct
import requests
from pathlib import Path

# Ensure UTF-8 console output for Arabic titles on Windows
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

BASE_URL = os.environ.get("KTAB_BASE_URL", "http://localhost:8080/api/v1")

# PDF Folder Path (defaults to docs/PDF in the repository)
PDF_DIR = Path(os.environ.get(
    "PDF_DIR",
    Path(__file__).resolve().parent.parent / "docs" / "PDF"
))

# -----------------------------------------------------------------------------
# CONFIGURATION
# -----------------------------------------------------------------------------
ADMIN_EMAIL = os.environ.get("KTAB_ADMIN_EMAIL", "admin@ktab.com")
ADMIN_PASSWORD = os.environ.get("KTAB_ADMIN_PASSWORD", "Password123!")

LIBRARY_DATA = {
    "name": "Dar Hashem",
    "description": "دار هاشم للكتب والنشر هي دار نشر عربية مقرها بيروت، متخصصة في الكتب السياسية وشؤون الشرق الأوسط والسير والجيوسياسة والقضية الفلسطينية، وتهدف إلى تقديم محتوى معرفي وأبحاث وترجمات نوعية للقارئ العربي.",
    "city": "Beirut",
    "country": "Lebanon",
    "address": "Beirut, Lebanon",
    "website": "https://darhashem.com",
    "email": "books@darhashem.com",
    "phone": "+96170611220"
}

ADMIN_LIBRARIAN_DATA = {
    "email": "ali@darhashem.com",
    "firstName": "Ali",
    "lastName": "Hashem",
    "password": "Password123!",
    "role": "ADMIN_LIBRARIAN"
}

LIBRARIAN_STAFF_DATA = {
    "email": "zakariya@darhashem.com",
    "firstName": "Zakaria",
    "lastName": "Zakaria",
    "password": "Password123!",
    "role": "LIBRARIAN"
}

SAMPLE_BOOKS = [
    {
        "folder": "سيرة ملك",
        "title": "سيرة ملك: عبد الله الثاني... من واشنطن إلى عمّان",
        "description": "سيرة سياسية توثّق حياة الملك عبد الله الثاني ومسيرته في الحكم، وعلاقته بواشنطن، ومواجهته للأزمات الإقليمية والداخلية من حرب العراق ومكافحة الإرهاب إلى الربيع العربي والتحولات السياسية في الأردن.",
        "customAuthorName": "آرون ماجيد",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 360,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "صمود الدبلوماسية",
        "title": "صمود الدبلوماسية: مذكرات ثماني سنوات في وزارة الخارجية",
        "description": "مذكرات محمد جواد ظريف عن ثماني سنوات في وزارة الخارجية الإيرانية، تكشف كواليس صنع القرار والمفاوضات والعلاقات مع القوى الدولية والتحديات داخل مؤسسات الدولة الإيرانية.",
        "customAuthorName": "محمد جواد ظريف",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 616,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "عندما ينام العالم",
        "title": "عندما ينام العالم: قصص، كلمات، وجروح فلسطينية مفتوحة",
        "description": "كتاب يجمع قصصًا وشهادات وتأملات حول فلسطين من خلال أشخاص وتجارب تركت أثرًا في رؤية فرانشسكا ألبانيزي، جامعًا بين السرد الإنساني والتجربة الشخصية والدفاع عن الحقوق الفلسطينية.",
        "customAuthorName": "فرانشسكا ألبانيزي",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 232,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "عيون غزة",
        "title": "عيون غزة: يوميات صمود",
        "description": "يوميات وشهادة شخصية لبلستيا العقاد من قلب غزة، توثّق الحرب والنزوح والحياة اليومية والصمود الإنساني بعيون صحافية وكاتبة فلسطينية عاشت الأحداث ووثّقتها.",
        "customAuthorName": "بلستيا العقاد",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 168,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "قوة التفاوض",
        "title": "قوة التفاوض: مبادئ وقواعد المفاوضات السياسية والدبلوماسية",
        "description": "دليل عملي يستند إلى ثلاثة عقود من الخبرة الدبلوماسية لعباس عراقجي، ويعرض مبادئ وقواعد وأدوات التفاوض السياسي والدبلوماسي، من التحضير وبناء الثقة إلى إدارة المشاعر والمواقف والوصول إلى الاتفاق.",
        "customAuthorName": "عباس عراقجي",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 228,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "كنعان مكية",
        "title": "كنعان مكية: سيرة على تخوم العراق",
        "description": "سيرة فكرية وسياسية تتتبع مسار كنعان مكية بين العراق والمنفى، وتحولاته الفكرية والكتابة والهندسة ومواقفه من المعارضة العراقية ومشروع الذاكرة العراقية، مستندة إلى مواد وشهادات مترجمة ومحررة.",
        "customAuthorName": "فرج الحطاب",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 200,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "لظى",
        "title": "لظى: حكاية حرب لم تنته",
        "description": "توثيق سياسي وشهادة من داخل الأحداث حول الحرب وجبهة لبنان وتداعيات طوفان الأقصى، يكشف فيها الرئيس نبيه بري تفاصيل المرحلة ويرويها علي حسن خليل من موقعه في قلب الأحداث.",
        "customAuthorName": "نبيه بري وعلي حسن خليل",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 456,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "Zarif Eng",
        "title": "The Audacity of Resilience: An Insider’s View into the Making of Iranian Foreign Policy",
        "description": "An insider account of Iranian foreign-policy making, drawing on M. Javad Zarif’s decades of diplomatic experience to examine the Foreign Ministry, domestic power centers, neighboring states, major powers, and the structural constraints shaping Iran’s foreign-policy choices.",
        "customAuthorName": "M. Javad Zarif",
        "language": "en",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 536,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "استراتيجة ايران الكبرى",
        "title": "استراتيجية إيران الكبرى: تاريخ سياسي",
        "description": "دراسة في التاريخ السياسي والاستراتيجي لإيران تسعى إلى فهم حسابات الأمن القومي وخيارات السياسة الخارجية الإيرانية، وتبحث في عناصر الاستمرارية والتحول بين الدولة الملكية والجمهورية الإسلامية.",
        "customAuthorName": "ولي نصر",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 438,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "الرسائل المصرية",
        "title": "الرسائل المصرية: 24 كاتبًا عربيًا يروون أدوار «المحروسة» في تأهيل عرب القرن العشرين",
        "description": "كتاب جماعي يضم شهادات وتجارب 24 كاتبًا وصحافيًا وأكاديميًا وفنانًا عربيًا عن مصر ومدنها وثقافتها وسياساتها وتاريخها وأثرها في تكوين أجيال من العرب خلال القرن العشرين.",
        "customAuthorName": "24 كاتبًا عربيًا – إشراف فيصل جلول وسامي كليب",
        "language": "ar",
        "ageRangeMin": 14,
        "ageRangeMax": 99,
        "pageCount": 352,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "الزلزال",
        "title": "الزلزال: النظام العالمي بين الشعبوية والديمقراطية",
        "description": "تحليل لتحولات النظام العالمي تحت ضغط الشعبوية والاستقطاب والعولمة، يستخدم مجاز «الزلزال» لشرح الاهتزازات الجيوسياسية والأمنية والاقتصادية والبنيوية التي تواجه الدولة الوطنية والديمقراطية.",
        "customAuthorName": "أحمد داود أوغلو",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 464,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "الصين والولايات المتحدة",
        "title": "الصين والولايات المتحدة: حتمية الحرب الاقتصادية",
        "description": "قراءة في الصراع البنيوي بين الولايات المتحدة والصين، تربط الجيوبوليتيك بالاقتصاد والتكنولوجيا والطاقة والعملة، وتتتبع صعود الصين وأدوات الهيمنة الأميركية والتنافس على الأسواق والنفوذ العالمي.",
        "customAuthorName": "عدنان منصور",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 256,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "امريكا وايران الراسائل والمدافع",
        "title": "أميركا وإيران: الرسائل والمدافع – 300 عام من العلاقات المعقّدة",
        "description": "تاريخ موسّع للعلاقات الأميركية الإيرانية على مدى ثلاثة قرون، يتتبع مراحل الاتصال والتقارب والصراع والثورات والحروب والمفاوضات والأزمات التي شكّلت العلاقة بين البلدين حتى العصر الحديث.",
        "customAuthorName": "جان قزوينيان",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 800,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "بعض الصوت",
        "title": "بعض الصوت",
        "description": "رواية تركية مترجمة تستكشف الوحدة والذاكرة والحب والعلاقات العائلية والضغوط الاجتماعية والاقتصادية، من خلال شخصيات تتقاطع حيواتهم بين الماضي والحاضر ومحاولات البحث عن معنى وانتماء.",
        "customAuthorName": "بيلغهان أوتشاك",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 256,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    },
    {
        "folder": "ثورة دونالد ترامب",
        "title": "ثورة دونالد ترامب: قواعد القوى العظمى",
        "description": "مجموعة مقالات وحوارات سياسية لألكسندر دوغين حول عودة دونالد ترامب وصعود الترامبية وتحولات السياسة الأميركية، وعلاقتها بنشوء نظام دولي متعدد الأقطاب وقواعد جديدة للتنافس بين القوى العظمى.",
        "customAuthorName": "ألكسندر دوغين",
        "language": "ar",
        "ageRangeMin": 16,
        "ageRangeMax": 99,
        "pageCount": 280,
        "hasAudio": False,
        "status": "PUBLISHED",
        "mainGenreId": None,
        "subGenreId": None
    }
]

# -----------------------------------------------------------------------------
# HELPER FUNCTIONS
# -----------------------------------------------------------------------------
def generate_cover_png(width=500, height=800, r=26, g=54, b=93):
    """
    Generates a valid 500x800 PNG byte stream (exact ratio 1.6)
    that complies with ImageValidator (COVER_RATIO = 1.6, TOLERANCE = 0.1).
    """
    png = b"\x89PNG\r\n\x1a\n"
    ihdr_data = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    ihdr_crc = zlib.crc32(b"IHDR" + ihdr_data)
    png += struct.pack(">I", len(ihdr_data)) + b"IHDR" + ihdr_data + struct.pack(">I", ihdr_crc)

    scanlines = bytearray()
    for y in range(height):
        factor = y / float(height)
        cr = int(r * (1.0 - 0.4 * factor))
        cg = int(g * (1.0 - 0.4 * factor))
        cb = int(b * (1.0 - 0.2 * factor))
        scanlines.append(0)
        for _ in range(width):
            scanlines.extend((cr, cg, cb))

    compressed = zlib.compress(bytes(scanlines), 9)
    idat_crc = zlib.crc32(b"IDAT" + compressed)
    png += struct.pack(">I", len(compressed)) + b"IDAT" + compressed + struct.pack(">I", idat_crc)

    iend_crc = zlib.crc32(b"IEND")
    png += struct.pack(">I", 0) + b"IEND" + struct.pack(">I", iend_crc)
    return bytes(png)

def find_pdf_in_folder(base_dir, folder_name):
    """Locates the PDF file inside the given folder under PDF_DIR."""
    if not folder_name:
        return None
    target_dir = base_dir / folder_name
    if target_dir.is_dir():
        pdfs = list(target_dir.glob("*.pdf"))
        if pdfs:
            return pdfs[0]
    return None

def login(email, password):
    """Attempts login using email, with fallbacks to alternate admin emails if needed."""
    candidate_emails = [email]
    if email == "admin@ktab.com":
        candidate_emails.append("admin@ktab.ai")
    elif email == "admin@ktab.ai":
        candidate_emails.append("admin@ktab.com")

    last_resp = None
    for try_email in candidate_emails:
        last_resp = requests.post(
            f"{BASE_URL}/auth/login",
            json={"email": try_email, "password": password},
            headers={"X-Forwarded-For": "127.0.0.10"}
        )
        if last_resp.status_code == 200:
            data = last_resp.json().get("data")
            return data.get("accessToken") if isinstance(data, dict) else data

    raise Exception(f"Login failed for {candidate_emails}: {last_resp.status_code} {last_resp.text}")

def fetch_default_genres(token):
    """Fetches valid mainGenreId and subGenreId from GET /api/v1/genres."""
    headers = {"Authorization": f"Bearer {token}"}
    try:
        resp = requests.get(f"{BASE_URL}/genres", headers=headers)
        if resp.status_code == 200:
            data = resp.json().get("data", [])
            if data:
                main_id = data[0]["id"]
                sub_genres = data[0].get("subGenres", [])
                sub_id = sub_genres[0]["id"] if sub_genres else 1
                return main_id, sub_id
    except Exception as e:
        print(f"  ⚠️ Could not fetch genres from API ({e}), using default fallback 1, 1.")
    return 1, 1

# -----------------------------------------------------------------------------
# MAIN PIPELINE
# -----------------------------------------------------------------------------
def main():
    print("=" * 70)
    print("   DAR HASHEM (دار هاشم - بيروت) SEEDING & LOCAL BOOKS UPLOAD")
    print("=" * 70)
    print(f"Target Server : {BASE_URL}")
    print(f"PDF Directory : {PDF_DIR.resolve()}")
    print("-" * 70)

    if not PDF_DIR.exists():
        print(f"❌ Error: PDF directory does not exist at: {PDF_DIR.resolve()}")
        sys.exit(1)

    print("\n=== 1. Logging in as Super Admin ===")
    admin_token = login(ADMIN_EMAIL, ADMIN_PASSWORD)
    headers = {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}
    print("  ✓ Admin authenticated successfully.")

    print("\n=== 2. Setting up Library Organization: Dar Hashem ===")
    org_id = None
    get_resp = requests.get(f"{BASE_URL}/reader/organizations?search=Dar+Hashem", headers=headers)
    if get_resp.status_code == 200:
        data = get_resp.json().get("data", {})
        content = data.get("content", []) if isinstance(data, dict) else []
        for org in content:
            if org.get("slug") == "dar-hashem" or org.get("name") == LIBRARY_DATA["name"]:
                org_id = org["id"]
                print(f"  ✓ Found existing Library Organization ID: {org_id} (slug: {org.get('slug')})")
                break

    if not org_id:
        org_resp = requests.post(f"{BASE_URL}/admin/libraries", json=LIBRARY_DATA, headers=headers)
        if org_resp.status_code in [200, 201]:
            org_id = org_resp.json()["data"]["id"]
            print(f"  ✓ Library created successfully (ID: {org_id})")
        else:
            org_id = 1
            print(f"  ✓ Using fallback Library Organization ID: {org_id}")

    print("\n=== 3. Assigning Admin Librarian ===")
    assign_admin_resp = requests.post(
        f"{BASE_URL}/admin/libraries/{org_id}/librarians",
        json=ADMIN_LIBRARIAN_DATA,
        headers=headers
    )
    print(f"  ✓ Admin Librarian status: HTTP {assign_admin_resp.status_code}")

    print("\n=== 4. Logging in as Admin Librarian to Add Librarian Staff ===")
    try:
        admin_lib_token = login(ADMIN_LIBRARIAN_DATA["email"], ADMIN_LIBRARIAN_DATA["password"])
        admin_lib_headers = {"Authorization": f"Bearer {admin_lib_token}", "Content-Type": "application/json"}

        assign_staff_resp = requests.post(
            f"{BASE_URL}/library-admin/staff",
            json=LIBRARIAN_STAFF_DATA,
            headers=admin_lib_headers
        )
        print(f"  ✓ Librarian Staff assignment status: HTTP {assign_staff_resp.status_code}")
    except Exception as e:
        print(f"  ℹ Staff notice: {e}")

    print("\n=== 5. Logging in as Librarian Staff to Upload Books ===")
    try:
        librarian_token = login(LIBRARIAN_STAFF_DATA["email"], LIBRARIAN_STAFF_DATA["password"])
        uploader_role = "Librarian Staff"
    except Exception:
        print("  ℹ Falling back to Admin Librarian credentials for uploads...")
        librarian_token = login(ADMIN_LIBRARIAN_DATA["email"], ADMIN_LIBRARIAN_DATA["password"])
        uploader_role = "Admin Librarian"

    librarian_headers = {"Authorization": f"Bearer {librarian_token}"}
    default_main_genre, default_sub_genre = fetch_default_genres(librarian_token)
    print(f"  ✓ Authenticated as {uploader_role}")
    print(f"  ✓ Active Genres: Main ID = {default_main_genre}, Sub ID = {default_sub_genre}")

    total_books = len(SAMPLE_BOOKS)
    print(f"\n=== 6. Uploading {total_books} Books from {PDF_DIR.name} ===")

    success_count = 0
    fail_count = 0

    for idx, b in enumerate(SAMPLE_BOOKS, start=1):
        title = b["title"]
        folder_name = b.get("folder")
        pdf_path = find_pdf_in_folder(PDF_DIR, folder_name)

        if not pdf_path:
            print(f"\n  [{idx}/{total_books}] ⚠️ Skipping '{title}': No PDF found in '{folder_name}'")
            fail_count += 1
            continue

        file_size_mb = pdf_path.stat().st_size / (1024 * 1024)
        print(f"\n  [{idx}/{total_books}] Uploading: '{title}'")
        print(f"      File: {pdf_path.name} ({file_size_mb:.2f} MB)")

        # Prepare DTO
        book_dto = {
            "title": title,
            "description": b.get("description", ""),
            "customAuthorName": b.get("customAuthorName", "دار هاشم"),
            "language": b.get("language", "ar"),
            "ageRangeMin": b.get("ageRangeMin", 12),
            "ageRangeMax": b.get("ageRangeMax", 99),
            "pageCount": b.get("pageCount", 200),
            "hasAudio": b.get("hasAudio", False),
            "status": b.get("status", "PUBLISHED"),
            "mainGenreId": b.get("mainGenreId") or default_main_genre,
            "subGenreId": b.get("subGenreId") or default_sub_genre
        }

        # Check for cover image in the same directory, or generate 1.6 compliant PNG
        cover_files = list(pdf_path.parent.glob("*.jpg")) + list(pdf_path.parent.glob("*.png"))
        if cover_files:
            with open(cover_files[0], "rb") as cf:
                cover_bytes = cf.read()
            cover_name = cover_files[0].name
            cover_mime = "image/png" if cover_name.lower().endswith(".png") else "image/jpeg"
        else:
            cover_bytes = generate_cover_png(width=500, height=800)
            cover_name = "cover.png"
            cover_mime = "image/png"

        # Read PDF binary
        with open(pdf_path, "rb") as pf:
            pdf_bytes = pf.read()

        files = {
            "bookDto": (None, json.dumps(book_dto), "application/json"),
            "coverImage": (cover_name, cover_bytes, cover_mime),
            "pdfFile": (pdf_path.name, pdf_bytes, "application/pdf")
        }

        try:
            book_resp = requests.post(
                f"{BASE_URL}/librarians/books",
                headers=librarian_headers,
                files=files,
                timeout=120
            )

            if book_resp.status_code in [200, 201]:
                res_data = book_resp.json().get("data", {})
                created_id = res_data.get("id")
                print(f"      ✓ Uploaded successfully! Book ID: {created_id}")
                success_count += 1
            else:
                print(f"      ✗ Upload failed (HTTP {book_resp.status_code}): {book_resp.text}")
                fail_count += 1
        except Exception as e:
            print(f"      ✗ Upload exception: {e}")
            fail_count += 1

    print("\n" + "=" * 70)
    print("                     SEEDING COMPLETE SUMMARY                       ")
    print("=" * 70)
    print(f"  • Library Organization : Dar Hashem (Beirut, Lebanon)")
    print(f"  • Admin Librarian      : {ADMIN_LIBRARIAN_DATA['email']}")
    print(f"  • Librarian Staff      : {LIBRARIAN_STAFF_DATA['email']}")
    print(f"  • Books Upload Result  : {success_count} succeeded, {fail_count} failed out of {total_books}")
    print("=" * 70)


if __name__ == "__main__":
    main()
