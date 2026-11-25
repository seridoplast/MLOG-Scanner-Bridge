#!/usr/bin/env python3
"""Publish the static feed on its own branch; mirror meaningful changes for old APKs.

Use a temporary Git index, never overwrite main source or force-push. A failed
push is retried against the new remote tip with fresh release metadata.
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time

from build_release_feed import generate

ROOT = Path(__file__).resolve().parents[1]
GIT_ENV = dict(os.environ, GIT_AUTHOR_NAME='github-actions[bot]',
               GIT_AUTHOR_EMAIL='41898282+github-actions[bot]@users.noreply.github.com',
               GIT_COMMITTER_NAME='github-actions[bot]',
               GIT_COMMITTER_EMAIL='41898282+github-actions[bot]@users.noreply.github.com')


def git(*args, env=None, check=True, data=None):
    return subprocess.run(['git', *args], cwd=ROOT, env=env or GIT_ENV,
                          input=data, text=True, capture_output=True, check=check)


def equivalent(old, new, heartbeat=False):
    if not old:
        return False
    try:
        a, b = json.loads(old), json.loads(new)
        old_time, new_time = int(a.pop('generated_at')), int(b.pop('generated_at'))
        return a == b and (not heartbeat or 0 <= new_time - old_time < 3600)
    except (ValueError, KeyError, TypeError):
        return False


def publish(branch, heartbeat=False):
    for attempt in range(4):
        ref = 'refs/heads/' + branch
        remote = git('ls-remote', '--heads', 'origin', ref).stdout.strip()
        if remote:
            git('fetch', '--no-tags', 'origin', ref)
            base = git('rev-parse', 'FETCH_HEAD').stdout.strip()
            old = git('show', base + ':releases.json', check=False)
            old_text = old.stdout if old.returncode == 0 else None
        else:
            if branch == 'main':
                raise RuntimeError('Missing main branch; refusing to publish')
            base, old_text = None, None
        with tempfile.TemporaryDirectory() as tmp:
            # Generate after obtaining the remote tip, and repeat on races.
            prior = os.getcwd()
            try:
                os.chdir(tmp)
                generate()
                text = Path('site/releases.json').read_text(encoding='utf-8')
            finally:
                os.chdir(prior)
            if equivalent(old_text, text, heartbeat):
                print(branch, 'catalogo sem alteracoes relevantes')
                return
            env = dict(GIT_ENV, GIT_INDEX_FILE=str(Path(tmp) / 'index'))
            if base:
                git('read-tree', base, env=env)
            else:
                git('read-tree', '--empty', env=env)
            blob = git('hash-object', '-w', '--stdin', data=text).stdout.strip()
            git('update-index', '--add', '--cacheinfo', '100644,' + blob + ',releases.json', env=env)
            tree = git('write-tree', env=env).stdout.strip()
            args = ['commit-tree', tree]
            if base:
                args += ['-p', base]
            args += ['-m', 'chore: atualizar catalogo de releases [skip ci]']
            commit = git(*args).stdout.strip()
            push = git('push', 'origin', commit + ':' + ref, check=False)
            if push.returncode == 0:
                print(branch, 'catalogo publicado')
                return
            print(branch, 'conflito ou falha no push; tentativa', attempt + 1, push.stderr[-500:])
        time.sleep(2 + attempt * 2)
    raise RuntimeError('Nao foi possivel publicar catalogo em ' + branch)


if __name__ == '__main__':
    publish('release-catalog', heartbeat=True)
    # Devices <= 3.6.18 still use the main branch. Do not update it merely
    # because the generated_at timestamp changed.
    publish('main')
