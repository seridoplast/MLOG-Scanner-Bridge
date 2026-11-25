#!/usr/bin/env python3
"""Generates the public (non-secret) GitHub Releases update catalog.

Uses the Actions token SERVER SIDE only. Never bundle a GitHub token in an APK.
The installed APK is independently checked for SHA-256 and pinned signature.
"""
import json
import hashlib
import os
from pathlib import Path
import re
import time
from urllib.request import Request, urlopen
from urllib.error import HTTPError

REPO = os.environ.get('GITHUB_REPOSITORY', 'seridoplast/MLOG-Scanner-Bridge')
TOKEN = os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN')
if not TOKEN:
    raise SystemExit('GitHub Actions token required for generating the catalog')
if REPO != 'seridoplast/MLOG-Scanner-Bridge':
    raise SystemExit('Unexpected repository: ' + REPO)


def api(path):
    url = 'https://api.github.com/repos/' + REPO + path
    request = Request(url, headers={
        'Accept': 'application/vnd.github+json',
        'Authorization': 'Bearer ' + TOKEN,
        'User-Agent': 'Seridoplast-Bridge-ReleaseCatalog'
    })
    with urlopen(request, timeout=25) as stream:
        return json.load(stream)


def to_record(rel):
    if rel.get('draft') or not re.fullmatch(r'v\d+\.\d+\.\d+', rel.get('tag_name', '')):
        return None
    tag = rel['tag_name']
    name = f'MLOG-Scanner-Bridge-{tag}.apk'
    apk = next((a for a in rel.get('assets', []) if a.get('name') == name), None)
    if apk is None:
        return None
    url = apk.get('browser_download_url')
    if url != f'https://github.com/{REPO}/releases/download/{tag}/{name}':
        raise ValueError(f'Unexpected asset URL in {tag}')
    digest = apk.get('digest') or ''
    if not re.fullmatch('sha256:[a-fA-F0-9]{64}', digest):
        # Older GitHub releases can expose a null digest. Hash the real
        # public asset server-side. Devices still verify this and the signer.
        print('GitHub omitted SHA-256 for', tag, '- calculating on runner')
        sha = hashlib.sha256()
        total = 0
        with urlopen(Request(url, headers={'User-Agent': 'Seridoplast-Bridge-Catalog'}), timeout=60) as stream:
            while True:
                chunk = stream.read(128*1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > 120 * 1024 * 1024:
                    raise ValueError(f'APK too large in {tag}')
                sha.update(chunk)
        if total < 50 * 1024:
            raise ValueError(f'Invalid APK downloaded from {tag}')
        digest = 'sha256:' + sha.hexdigest()
    return {
        'tag_name': tag,
        'name': rel.get('name') or tag,
        'body': rel.get('body') or '',
        'prerelease': bool(rel.get('prerelease')),
        'draft': False,
        'assets': [{
            'name': name,
            'browser_download_url': apk['browser_download_url'],
            'digest': digest,
        }],
    }


def generate():
    releases = api('/releases?per_page=25')
    latest = None
    try:
        latest = api('/releases/latest')
    except HTTPError as exc:
        # Only 404 means no stable release. Preserve the old feed on outages.
        if exc.code != 404:
            raise
    ordered = []
    seen = set()
    for rel in releases:
        tag = rel.get('tag_name')
        if tag not in seen:
            seen.add(tag)
            ordered.append(rel)
    if latest and latest.get('tag_name') not in seen:
        ordered.append(latest)
    # Sort by semantic version, not release creation order.
    ordered.sort(key=lambda r: tuple(int(x) for x in r["tag_name"][1:].split("."))
                 if re.fullmatch(r"v\d+\.\d+\.\d+", r.get("tag_name", "")) else (-1,), reverse=True)
    # Four newest releases including tests. Preserve latest approved independently.
    newest = []
    stable = []
    for rel in ordered:
        if len(newest)>=4 and (len(stable)>=4 or rel.get('prerelease',False)):
            continue
        rec = to_record(rel)
        if rec:
            if len(newest)<4:
                newest.append(rec)
            if not rec['prerelease'] and len(stable)<4:
                stable.append(rec)
    approved = to_record(latest) if latest else None
    if approved and approved['prerelease']:
        raise ValueError('GitHub /latest returned a prerelease, refusing feed')
    if not approved and latest:
        raise ValueError('Latest stable release has no valid APK')
    output = {
        'schema': 1,
        'repository': REPO,
        'generated_at': int(time.time()),
        'latest': approved,
        'releases': newest,
        'stable_releases': stable,
    }
    folder = Path('site')
    folder.mkdir(exist_ok=True)
    (folder / 'releases.json').write_text(json.dumps(output, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    (folder / 'index.html').write_text('<!doctype html><meta charset="utf-8"><title>MLOG Bridge Updates</title><p>Catálogo de atualizações MLOG Bridge.</p>', encoding='utf-8')
    print('Catalog generated:', len(newest), 'releases; latest:', approved['tag_name'] if approved else 'none')


if __name__ == '__main__':
    generate()
