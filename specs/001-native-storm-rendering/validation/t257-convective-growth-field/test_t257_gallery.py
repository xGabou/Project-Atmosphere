"""Capture/export plumbing tests; no changes to candidate geometry or preserved pictures."""
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('gallery',Path(__file__).with_name('t257_gallery.py'))
gallery=importlib.util.module_from_spec(spec);spec.loader.exec_module(gallery)

class GalleryTests(unittest.TestCase):
    def test_parity_coverage_is_explicit(self):
        self.assertEqual(len(gallery.expected_parity_shots()),16)
        self.assertTrue(gallery.expected_parity_shots().issubset(set(gallery.expected_shots())))

    def test_all_original_views(self):
        shots=gallery.expected_shots()
        self.assertEqual(len(shots),350)
        self.assertEqual(len(set(shots)),350)
        for t in gallery.TYPES:
            for seed in gallery.SEEDS:
                self.assertEqual(sum(s.startswith(f'T257_{t}__{seed}__') and not ('control_' in s or 'attribution_' in s) for s in shots),38)
    def test_pose_orientation_lens_and_conditions_must_match(self):
        a=dict(pose=dict(origin=[1,2,3],camera=[4,5,6],aim=[7,8,9]),ack=[90,0],captured=dict(main_target='1600x900'),
               effective='flat=1 history=off',conditions=dict(fov=110,fov_effect_scale=1))
        self.assertTrue(gallery.pair_matches(a,a))
        for k,v in [('ack',[89,0]),('effective','flat=0 history=off'),('conditions',dict(fov=100,fov_effect_scale=1))]:
            b=dict(a);b[k]=v;self.assertFalse(gallery.pair_matches(a,b))

    def test_baselines_are_explicit_and_strict_elsewhere(self):
        import json
        baselines=json.loads(gallery.BASELINES.read_text(encoding='utf-8'))
        self.assertEqual(set(baselines['baselines']),{'B-v2','B-v3'})
        manifest=dict(protected_files={n:'v2-'+n for n in baselines['versioned_files']}|{'other.java':'strict'})
        v2,d2,_=gallery.resolve_expectations(manifest,'B-v2',baselines)
        self.assertEqual(d2,[]);self.assertEqual(v2['other.java'],'strict')
        for n in baselines['versioned_files']:self.assertEqual(v2[n],baselines['baselines']['B-v2']['strict_files'][n])
        v3,d3,spec=gallery.resolve_expectations(manifest,'B-v3',baselines)
        self.assertEqual(v3['other.java'],'strict')
        self.assertEqual(len(d3),1);self.assertEqual(len(spec['geometry_fingerprints']),9)
        additive=[n for n in manifest['protected_files'] if n in spec.get('additive_files',{})]
        self.assertEqual(len(v3)+len(d3)+len(additive),len(manifest['protected_files']))
        with self.assertRaises(SystemExit):gallery.resolve_expectations(manifest,'B-v4',baselines)

    def test_additive_files_allow_insertions_only(self):
        import tempfile,hashlib
        L=lambda *xs: ''.join(x+'\n' for x in xs).encode()
        with tempfile.TemporaryDirectory() as d:
            d=Path(d);(d/'frozen.txt').write_bytes(L('a','b','c'))
            rule=dict(frozen_copy='frozen.txt',sha256_at_capture=hashlib.sha256(L('a','b','c')).hexdigest())
            for body,ok in [(L('a','b','c'),True),(L('a','X','b','c','Y'),True),(L('a','B','c'),False),(L('a','c'),False)]:
                (d/'cur.txt').write_bytes(body)
                self.assertEqual(gallery.verify_additive(d,'cur.txt',rule,d),ok,body)
            bad=dict(rule,sha256_at_capture='0'*64);(d/'cur.txt').write_bytes(L('a','b','c'))
            self.assertFalse(gallery.verify_additive(d,'cur.txt',bad,d))

    def test_fingerprint_parse_only_full_level(self):
        import tempfile,os
        with tempfile.NamedTemporaryFile('w',delete=False,suffix='.log') as f:
            f.write('T257_FINGERPRINT type=supercell seed=1 level=MESO version=3 signature=aa texture=bb size=1x1\n'
                    'T257_FINGERPRINT type=supercell seed=1 level=FULL version=3 signature=cc texture=dd size=1x1\n')
        try:self.assertEqual(gallery.parse_fingerprints(f.name),{'supercell/1':dict(signature='cc',texture_sha256='dd')})
        finally:os.unlink(f.name)

if __name__=='__main__':unittest.main()
