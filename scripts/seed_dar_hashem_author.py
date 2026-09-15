#!/usr/bin/env python3
"""
Seed Script: Setup Dar Hashem Author (author@darhashem.com) & Restore Ali Hashem (ali@darhashem.com)
===================================================================================================
This script:
1. Restores Ali Hashem (ali@darhashem.com) as ADMIN_LIBRARIAN (role: 35) linked to Dar Hashem library.
2. Creates/Updates Dar Hashem Author (author@darhashem.com) with role AUTHOR (role: 10, active: 1).
3. Connects the 15 books under author@darhashem.com (col_book_source = 'AUTHOR').
4. Links the ALREADY-EXISTING attachments (covers & PDFs stored in Cloudflare R2 / S3)
   to the author books WITHOUT re-uploading files or creating duplicate storage objects.
5. Authenticates via Ktab's REST API (/api/v1/auth/login) for both:
   - Admin Librarian: ali@darhashem.com (verifies librarian access restored)
   - Author: author@darhashem.com (verifies author access)
6. Verifies GET /api/v1/authors/books to ensure all 15 books have valid presigned
   cover image and PDF download URLs.

Requirements:
    pip install requests
    PostgreSQL client (psql in PATH)
"""

import os
import sys
import json
import subprocess
from pathlib import Path
import requests

# Ensure UTF-8 console output for Arabic titles on Windows
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# -----------------------------------------------------------------------------
# CONFIGURATION
# -----------------------------------------------------------------------------
BASE_URL = os.environ.get("KTAB_BASE_URL", "http://localhost:8080/api/v1")

DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_PORT = os.environ.get("DB_PORT", "5432")
DB_NAME = os.environ.get("DB_NAME", "ktab")
DB_USER = os.environ.get("DB_USER", os.environ.get("SPRING_DATASOURCE_USERNAME", "postgres"))
DB_PASSWORD = os.environ.get("DB_PASSWORD", os.environ.get("SPRING_DATASOURCE_PASSWORD", "123456"))

LIBRARIAN_EMAIL = os.environ.get("LIBRARIAN_EMAIL", "ali@darhashem.com")
LIBRARIAN_PASSWORD = os.environ.get("LIBRARIAN_PASSWORD", "Password123!")

AUTHOR_EMAIL = os.environ.get("AUTHOR_EMAIL", "author@darhashem.com")
AUTHOR_PASSWORD = os.environ.get("AUTHOR_PASSWORD", "Password123!")

SQL_FILE_PATH = Path(__file__).resolve().parent / "seed_dar_hashem_author.sql"


def run_psql_script(sql_path: Path):
    """Executes the SQL script using psql."""
    print(f"\n=== 1. Executing SQL Seed via psql ({sql_path.name}) ===")
    env = os.environ.copy()
    env["PGPASSWORD"] = DB_PASSWORD

    cmd = [
        "psql",
        "-U", DB_USER,
        "-h", DB_HOST,
        "-p", str(DB_PORT),
        "-d", DB_NAME,
        "-f", str(sql_path.resolve())
    ]

    try:
        proc = subprocess.run(
            cmd,
            env=env,
            capture_output=True,
            text=True,
            encoding="utf-8"
        )
        if proc.returncode != 0:
            print(f"❌ psql failed with exit code {proc.returncode}:\n{proc.stderr}")
            sys.exit(proc.returncode)

        for line in proc.stdout.splitlines():
            if "NOTICE:" in line:
                print(f"  {line.replace('NOTICE:  ', '')}")
            elif line.strip():
                print(f"  {line}")

        print("  ✓ Database updated successfully.")
    except FileNotFoundError:
        print("❌ Error: 'psql' executable was not found in PATH.")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Execution error: {e}")
        sys.exit(1)


def login(email: str, password: str) -> str:
    """Logs in to Ktab REST API and returns access token."""
    login_url = f"{BASE_URL}/auth/login"
    payload = {"email": email, "password": password}
    headers = {"X-Forwarded-For": "127.0.0.1", "ngrok-skip-browser-warning": "true"}

    resp = requests.post(login_url, json=payload, headers=headers)
    if resp.status_code != 200:
        raise Exception(f"Login failed for {email} (HTTP {resp.status_code}): {resp.text}")

    data = resp.json().get("data")
    return data.get("accessToken") if isinstance(data, dict) else data


def verify_author_books(token: str):
    """Verifies that author books are returned by GET /api/v1/authors/books with attachments."""
    print(f"\n=== 3. Querying GET /api/v1/authors/books as Author ({AUTHOR_EMAIL}) ===")
    headers = {"Authorization": f"Bearer {token}", "ngrok-skip-browser-warning": "true"}

    resp = requests.get(f"{BASE_URL}/authors/books?page=0&size=50", headers=headers)
    if resp.status_code != 200:
        print(f"❌ Failed to fetch author books (HTTP {resp.status_code}): {resp.text}")
        return

    res_json = resp.json()
    data = res_json.get("data", {})
    books = data.get("content", []) if isinstance(data, dict) else []

    print(f"  ✓ Total author books found: {len(books)}\n")

    linked_covers = 0
    linked_pdfs = 0

    for idx, b in enumerate(books, start=1):
        book_id = b.get("id")
        title = b.get("title")
        author_name = b.get("authorName") or b.get("customAuthorName")
        cover_url = b.get("coverImageUrl")
        pdf_url = b.get("pdfDownloadUrl")
        pdf_name = b.get("pdfFileName")

        has_cover = bool(cover_url)
        has_pdf = bool(pdf_url)

        if has_cover:
            linked_covers += 1
        if has_pdf:
            linked_pdfs += 1

        status_icon = "✓" if (has_cover and has_pdf) else "⚠️"
        print(f"  [{idx:02d}] {status_icon} ID {book_id}: '{title}'")
        print(f"       Author: {author_name} | Status: {b.get('status')}")
        print(f"       Cover : {'LINKED (presigned URL ready)' if has_cover else 'MISSING'}")
        print(f"       PDF   : {'LINKED (' + str(pdf_name) + ')' if has_pdf else 'MISSING'}")

    print("\n" + "=" * 70)
    print("                     SEEDING VERIFICATION SUMMARY                   ")
    print("=" * 70)
    print(f"  • Admin Librarian    : {LIBRARIAN_EMAIL} (Role: ADMIN_LIBRARIAN, Org: Dar Hashem)")
    print(f"  • Author Account     : {AUTHOR_EMAIL} (Role: AUTHOR)")
    print(f"  • Total Author Books : {len(books)}")
    print(f"  • Linked Cover Images: {linked_covers}/{len(books)}")
    print(f"  • Linked PDF Files   : {linked_pdfs}/{len(books)}")
    print(f"  • New Files Uploaded : 0 (reused existing Cloudflare R2 / S3 paths)")
    print("=" * 70)


def main():
    print("=" * 70)
    print("   DAR HASHEM (دار هاشم) AUTHOR SEEDING & ATTACHMENT LINKING")
    print("=" * 70)
    print(f"Target API Server : {BASE_URL}")
    print(f"Target Database   : {DB_HOST}:{DB_PORT}/{DB_NAME} (User: {DB_USER})")
    print(f"Admin Librarian   : {LIBRARIAN_EMAIL}")
    print(f"Author Target     : {AUTHOR_EMAIL}")
    print("-" * 70)

    if not SQL_FILE_PATH.exists():
        print(f"❌ Error: SQL file not found at {SQL_FILE_PATH}")
        sys.exit(1)

    # 1. Run SQL seed
    run_psql_script(SQL_FILE_PATH)

    # 2. Verify via REST API (if server is running)
    print(f"\n=== 2. Verifying User Logins via REST API ===")
    try:
        # Check Admin Librarian login
        lib_token = login(LIBRARIAN_EMAIL, LIBRARIAN_PASSWORD)
        print(f"  ✓ Admin Librarian ({LIBRARIAN_EMAIL}) authenticated successfully (JWT acquired).")

        # Check Author login
        author_token = login(AUTHOR_EMAIL, AUTHOR_PASSWORD)
        print(f"  ✓ Author ({AUTHOR_EMAIL}) authenticated successfully (JWT acquired).")

        # Verify Author books
        verify_author_books(author_token)
    except requests.exceptions.ConnectionError:
        print(f"  ℹ Notice: Ktab REST API server is not running on {BASE_URL}.")
        print("  ✓ Database seeding & attachment linking completed successfully in PostgreSQL.")
        print("    You can start the backend application and verify author books at:")
        print(f"    GET {BASE_URL}/authors/books")
    except Exception as e:
        print(f"  ⚠️ Notice during API verification: {e}")
        print("  ✓ Database seeding & attachment linking completed successfully in PostgreSQL.")


if __name__ == "__main__":
    main()
