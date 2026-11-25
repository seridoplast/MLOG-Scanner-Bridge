"""No-network smoke test for the public releases.json schema."""
import importlib.util
import os
from pathlib import Path
import tempfile
from unittest.mock import patch

os.environ.setdefault('GITHUB_TOKEN','test-only-not-real')
spec=importlib.util.spec_from_file_location('feed',str(Path(__file__).resolve().parents[1]/'scripts/build_release_feed.py'))
feed=importlib.util.module_from_spec(spec)
spec.loader.exec_module(feed)

def sample(tag, is_test=False):
    name=f'MLOG-Scanner-Bridge-{tag}.apk'
    return {'tag_name':tag,'name':tag,'draft':False,'prerelease':is_test,
     'assets':[{'name':name,'digest':'sha256:'+'a'*64,
                'browser_download_url':f'https://github.com/{feed.REPO}/releases/download/{tag}/{name}'}],
     'body': 'rollout=0' if is_test else 'rollout=100'}

with tempfile.TemporaryDirectory() as d:
    prior=os.getcwd()
    try:
        os.chdir(d)
        latest=sample('v3.6.6')
        newest=sample('v3.6.8',True)
        with patch.object(feed,'api',side_effect=lambda path: latest if path=='/releases/latest' else [newest,latest]):
            feed.generate()
        import json
        data=json.loads(Path('site/releases.json').read_text())
        assert data['schema']==1
        assert data['latest']['tag_name']=='v3.6.6' and not data['latest']['prerelease']
        assert data['releases'][0]['tag_name']=='v3.6.8'
        assert len(data['releases'])==2
    finally:
        os.chdir(prior)
print('Release feed smoke test OK')

# A promotion changes the channel, not the signed asset or tag.
with tempfile.TemporaryDirectory() as d:
    prior=os.getcwd()
    try:
        os.chdir(d)
        pre=sample('v3.6.19',True)
        pre['body']='rollout=100'
        old=sample('v3.6.18')
        with patch.object(feed,'api',side_effect=lambda path: old if path=='/releases/latest' else [pre,old]):
            feed.generate()
        before=json.loads(Path('site/releases.json').read_text())
        stable=dict(pre,prerelease=False)
        with patch.object(feed,'api',side_effect=lambda path: stable if path=='/releases/latest' else [stable,old]):
            feed.generate()
        after=json.loads(Path('site/releases.json').read_text())
        assert before['releases'][0]['prerelease'] is True
        assert after['latest']['tag_name']==pre['tag_name']
        assert after['latest']['prerelease'] is False
        assert after['latest']['body']=='rollout=100'
        assert after['latest']['assets']==before['releases'][0]['assets']
        paused=dict(stable,body='rollout=0')
        with patch.object(feed,'api',side_effect=lambda path: paused if path=='/releases/latest' else [paused]):
            feed.generate()
        assert json.loads(Path('site/releases.json').read_text())['latest']['body']=='rollout=0'
    finally:
        os.chdir(prior)
print('Promotion and intentional rollout pause OK')

with tempfile.TemporaryDirectory() as d:
    prior=os.getcwd()
    try:
        os.chdir(d)
        stable=sample('v3.6.18')
        tests=[sample('v3.6.'+str(n),True) for n in range(19,26)]
        with patch.object(feed,'api',side_effect=lambda path: stable if path=='/releases/latest' else tests+[stable]):
            feed.generate()
        data=json.loads(Path('site/releases.json').read_text())
        assert len(data['releases'])==4 and all(x['prerelease'] for x in data['releases'])
        assert [x['tag_name'] for x in data['stable_releases']]==['v3.6.18']
    finally:
        os.chdir(prior)
print('Stable-only list survives multiple prereleases OK')
