#!/usr/bin/env python3
"""Reconcile before retrying release writes. Never overwrite an uploaded APK."""
import json
import os
from pathlib import Path
import re
import subprocess
import time


class GhError(RuntimeError):
    def __init__(self, message):
        super().__init__(message)
        match = re.search(r'HTTP\s+(\d{3})\b', message, re.I)
        self.status = int(match.group(1)) if match else None

    @property
    def retryable(self):
        return self.status in (408, 429, 500, 502, 503, 504) or (
            self.status is None and bool(re.search(r'timeout|timed out|connection reset|unexpected EOF|temporary failure|connection refused|TLS handshake|network is unreachable', str(self), re.I)))


def gh(*args):
    try:
        result = subprocess.run(['gh', *args], text=True, capture_output=True, timeout=180)
    except subprocess.TimeoutExpired as exc:
        raise GhError('GitHub request timed out') from exc
    if result.returncode:
        raise GhError((result.stderr or result.stdout).strip())
    return result.stdout


def reconcile(repo, tag, asset, sha, stable, rollout, run=gh):
    name = asset.name
    title = 'MLOG Scanner Bridge ' + tag
    notes = 'rollout=' + rollout
    def read():
        try:
            return json.loads(run('api', f'repos/{repo}/releases/tags/{tag}'))
        except GhError as exc:
            if exc.status == 404:
                return None
            raise
    def asset_present(release):
        match = next((a for a in release.get('assets', []) if a['name'] == name), None)
        if match is None:
            return False
        if match.get('state') != 'uploaded' or not match.get('size', 0):
            raise RuntimeError('APK remoto incompleto: não substituir automaticamente. Confira a Release.')
        return True
    release = read()
    if release is None:
        # Each retry starts with read(), even if POST returned 500 after success.
        run('release', 'create', tag, str(asset), '--repo', repo, '--target', sha,
            '--title', title, '--notes', notes, '--latest' if stable else '--prerelease')
        release = read()
        if release is None:
            raise GhError('GitHub request timed out: release not visible yet')
    if not asset_present(release):
        # If GitHub created the tag but failed to upload the asset, recover only
        # when this APK was built from that very commit. Never mix sources.
        target = json.loads(run('api', f'repos/{repo}/commits/{tag}'))['sha']
        if target != sha:
            raise RuntimeError('Tag existente aponta para outro commit; gere uma nova versão.')
        run('release', 'upload', tag, str(asset), '--repo', repo)
        release = read()
        if release is None or not asset_present(release):
            raise GhError('GitHub request timed out: uploaded asset not visible yet')
    if stable:
        run('release', 'edit', tag, '--repo', repo, '--prerelease=false', '--draft=false', '--latest', '--title', title, '--notes', notes)
    elif release.get('draft'):
        # Complete a partially published pre-release, but not an unrelated draft.
        target = json.loads(run('api', f'repos/{repo}/commits/{tag}'))['sha']
        if target != sha:
            raise RuntimeError('Draft existente de outro commit; confira a Release.')
        run('release', 'edit', tag, '--repo', repo, '--draft=false', '--prerelease=true', '--title', title, '--notes', notes)
    print(f'APK publicado/verificado: {tag}; sem sobrescrever assets existentes.')


def publish(repo, tag, asset, sha, stable, rollout, run=gh, sleep=time.sleep):
    for attempt in range(5):
        try:
            reconcile(repo, tag, asset, sha, stable, rollout, run)
            return
        except GhError as exc:
            if not exc.retryable or attempt == 4:
                raise
            delay = (5, 15, 30, 60)[attempt]
            print(f'::warning::GitHub temporariamente indisponível ({exc.status or "rede"}); nova consulta em {delay}s.', flush=True)
            sleep(delay)


def main():
    version = re.search(r"^def bridgeVersion = '([^']+)'", Path('app/build.gradle').read_text(), re.M).group(1)
    if not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', version):
        raise ValueError('Versão inválida')
    tag = 'v' + version
    asset = Path(f'app/build/outputs/apk/release/MLOG-Scanner-Bridge-{tag}.apk')
    if not asset.is_file() or not asset.stat().st_size:
        raise RuntimeError('APK ausente')
    event = os.environ['GITHUB_EVENT_NAME']
    approved = event == 'workflow_dispatch' and os.environ.get('APPROVE') == 'true'
    legacy = event == 'push' and version in ('3.6.6', '3.6.8', '3.6.17', '3.6.18')
    rollout = os.environ.get('ROLLOUT', '100') if approved else '100'
    if rollout not in ('0', '10', '25', '50', '100'):
        raise ValueError('Rollout inválido')
    publish(os.environ['GITHUB_REPOSITORY'], tag, asset, os.environ['GITHUB_SHA'], approved or legacy, rollout)


if __name__ == '__main__':
    main()
