import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from publish_apk_release import GhError, publish

ASSET = Path('MLOG-Scanner-Bridge-v3.6.24.apk')

class Fake:
    def __init__(self):
        self.release = None
        self.calls = []
        self.fail_reads = 0
        self.fail_create_after = False
        self.fail_create_before = False
        self.fail_upload_after = False
        self.target = 'sha'
    def __call__(self, *args):
        self.calls.append(args)
        if args[0] == 'api':
            if '/commits/' in args[1]: return json.dumps({'sha': self.target})
            if self.fail_reads:
                self.fail_reads -= 1
                raise GhError('HTTP 500')
            if self.release is None: raise GhError('HTTP 404')
            return json.dumps(self.release)
        if args[1] == 'create':
            if self.fail_create_before:
                self.fail_create_before = False
                raise GhError('HTTP 500')
            assert self.release is None
            self.release = {'draft': False, 'prerelease': '--prerelease' in args, 'assets': [self.asset()]}
            if self.fail_create_after:
                self.fail_create_after = False
                raise GhError('HTTP 500')
        elif args[1] == 'upload':
            assert '--clobber' not in args
            self.release['assets'].append(self.asset())
            if self.fail_upload_after:
                self.fail_upload_after = False
                raise GhError('HTTP 502')
        elif args[1] == 'edit':
            self.release['draft'] = False
            self.release['prerelease'] = '--prerelease=true' in args
        return ''
    def asset(self): return {'name': ASSET.name, 'state': 'uploaded', 'size': 100}
    def count(self, verb): return sum(a[:2] == ('release',verb) for a in self.calls)

class ReleaseTests(unittest.TestCase):
    def run_publish(self,f,stable=False):
        publish('org/repo','v3.6.24',ASSET,'sha',stable,'100',run=f,sleep=lambda _:None)
    def test_read_failure_not_treated_as_missing(self):
        f=Fake(); f.fail_reads=1; self.run_publish(f); self.assertEqual(f.count('create'),1); self.assertEqual(f.calls[0],f.calls[1])
    def test_create_failed_before_commit(self):
        f=Fake(); f.fail_create_before=True; self.run_publish(f); self.assertEqual(f.count('create'),2)
    def test_create_failed_after_commit(self):
        f=Fake(); f.fail_create_after=True; self.run_publish(f); self.assertEqual(f.count('create'),1); self.assertTrue(f.release['prerelease'])
    def test_partial_asset_recovers_without_overwrite(self):
        f=Fake(); f.release={'draft':False,'prerelease':True,'assets':[]}; f.fail_upload_after=True; self.run_publish(f); self.assertEqual(f.count('upload'),1)
    def test_promotion_preserves_asset(self):
        f=Fake(); f.release={'draft':False,'prerelease':True,'assets':[f.asset()]}; self.run_publish(f,True); self.assertEqual(f.count('upload'),0); self.assertFalse(f.release['prerelease'])
    def test_missing_asset_on_wrong_commit(self):
        f=Fake(); f.release={'assets':[]}; f.target='other';
        with self.assertRaises(RuntimeError): self.run_publish(f)
        self.assertEqual(f.count('upload'),0)
    def test_permanent_failure(self):
        def forbidden(*a): raise GhError('HTTP 403')
        with self.assertRaises(GhError): self.run_publish(forbidden)

if __name__=='__main__': unittest.main()
