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
