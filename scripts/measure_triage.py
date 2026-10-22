#!/usr/bin/env python3
"""Measures AI triage accuracy against a labelled set of tickets.

Usage:  python3 scripts/measure_triage.py [base-url]

Triage runs in the background by default, so a ticket is created immediately with
the deterministic classification and refined by the AI a moment later. This
script waits for that refinement and measures the *final* answer.

Set TRIAGE_ASYNC=false on the server (app.ai.triage-async=false) to measure the
synchronous path instead.
"""
import json
import os
import sys
import time
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/v1"
WAIT_SECONDS = int(os.environ.get("WAIT_SECONDS", "90"))

CASES = [
    ("Royalty payment not received for 6 months",
     "I published my book in June 2023 and my dashboard still shows 3570 rupees pending. It has now been 6 months and I have not received a single royalty payout.",
     "ROYALTY_PAYMENTS", "CRITICAL"),
    ("Royalty amount too low",
     "My royalty for 200 copies was only 3000 rupees which seems too low. Could you share a line by line breakdown of the calculation?",
     "ROYALTY_PAYMENTS", "MEDIUM"),
    ("Wrong ISBN on Amazon",
     "My book shows a different ISBN on Amazon than the one printed on the paperback cover. Which one is correct?",
     "ISBN_METADATA", "HIGH"),
    ("Terrible print quality",
     "My author copies arrived with blurry images and the pages are misaligned near the middle. The binding is also broken.",
     "PRINTING_QUALITY", "HIGH"),
    ("Book unavailable on Amazon",
     "My book is published and live on the BookLeaf Store but Amazon says Currently Unavailable and I cannot buy it there.",
     # "I cannot buy it there" is an explicit escalation trigger in the rubric.
     "DISTRIBUTION_AVAILABILITY", "HIGH"),
    ("Still in typesetting after 3 weeks",
     "It has been three weeks and my book is still in typesetting. When exactly will it be published?",
     "PRODUCTION_UPDATES", "HIGH"),
    ("Update author bio",
     "Can I update my author bio on the store listings after my book is already published?",
     "GENERAL_INQUIRY", "LOW"),
    ("ISBN reported wrongly on two platforms",
     "The ISBN is registered to the wrong title on both Amazon and Flipkart. My retailer has flagged it as a metadata error.",
     "ISBN_METADATA", "HIGH"),
    ("Print quality issue with blurry images",
     "The photos in my book are blurry and the pages are misaligned. This is a printing defect, not a digital issue.",
     "PRINTING_QUALITY", "HIGH"),
    ("Royalty not credited for two quarters",
     "My royalty has not been credited for two quarters now and the dashboard still shows it as pending.",
     "ROYALTY_PAYMENTS", "CRITICAL"),
]


def call(method, path, token=None, payload=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(payload).encode() if payload is not None else None
    with urllib.request.urlopen(req, data, timeout=180) as response:
        return json.loads(response.read())


def wait_for_refinement(token, ticket_id, deadline):
    """Polls until the background AI refinement has landed (or we give up)."""
    while time.time() < deadline:
        detail = call("GET", f"/author/tickets/{ticket_id}", token)
        if detail["ticket"]["categorySource"] == "AI":
            return detail["ticket"]
        time.sleep(1.5)
    return None


def main():
    auth = call("POST", "/auth/login", payload={"email": "ananya.reddy@email.com", "password": "Author@1234"})
    token = auth["accessToken"]
    meta = call("GET", "/meta")
    print(f"Provider in use: {meta['aiProvider']} / {meta.get('aiModel', 'n/a')} "
          f"(ai enabled: {meta['aiEnabled']})\n")

    category_hits = priority_hits = 0
    refined = 0
    started = time.time()
    for subject, description, expected_category, expected_priority in CASES:
        created = call("POST", "/author/tickets", token, {
            "subject": subject, "description": description,
        })
        # The ticket already carries the deterministic classification; wait for
        # the AI to refine it.
        final = wait_for_refinement(token, created["id"], time.time() + WAIT_SECONDS)
        if final is None:
            final = created
        else:
            refined += 1

        category_ok = final["category"] == expected_category
        priority_ok = final["priority"] == expected_priority
        category_hits += category_ok
        priority_hits += priority_ok
        mark = lambda ok: "ok  " if ok else "MISS"
        print(f"  {mark(category_ok)} {mark(priority_ok)} "
              f"expected {expected_category}/{expected_priority}  "
              f"got {final['category']}/{final['priority']} ({final['categorySource']})")

    total = len(CASES)
    print(f"\n  category accuracy : {category_hits}/{total} ({category_hits * 100 // total}%)")
    print(f"  priority accuracy : {priority_hits}/{total} ({priority_hits * 100 // total}%)")
    print(f"  refined by the AI : {refined}/{total}")
    print(f"  elapsed           : {time.time() - started:.1f}s")


if __name__ == "__main__":
    main()
