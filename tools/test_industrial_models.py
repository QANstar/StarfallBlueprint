"""Geometry regressions for visible coplanar overlaps, including the reported tank lid."""
import json
import unittest
from itertools import combinations
from generate_industrial_models import ART, FACE_AXES, element, exposed_faces, subtract_rectangle


def coplanar_overlaps(parts):
    surfaces=[]
    for part in parts:
        for face,attributes in part['faces'].items():
            if attributes['texture']=='#glass':continue
            axis,positive,u,v=FACE_AXES[face]
            surfaces.append((face,part['to' if positive else 'from'][axis],
                             (part['from'][u],part['from'][v],part['to'][u],part['to'][v])))
    for (face,a,r),(other,b,s) in combinations(surfaces,2):
        if face==other and abs(a-b)<1e-7 and min(r[2],s[2])-max(r[0],s[0])>1e-7 and min(r[3],s[3])-max(r[1],s[1])>1e-7:
            yield face,a,r,s


class IndustrialGeometryTests(unittest.TestCase):
    def test_reported_tank_lid_has_one_visible_surface(self):
        parts=[element((2,3,3),(8,15,12),'ivory'),element((1.7,14,2.7),(8.3,15,12.3),'steel')]
        self.assertTrue(list(coplanar_overlaps(parts)))
        fixed=exposed_faces(parts)
        self.assertFalse(list(coplanar_overlaps(fixed)))
        tops=[p for p in fixed if 'up' in p['faces']]
        self.assertEqual(len(tops),1)
        self.assertEqual(tops[0]['faces']['up']['texture'],'#steel')
        self.assertAlmostEqual((tops[0]['to'][0]-tops[0]['from'][0])*(tops[0]['to'][2]-tops[0]['from'][2]),6.6*9.6)

    def test_partial_overlap_preserves_exposed_area(self):
        pieces=subtract_rectangle((0,0,10,10),(3,2,7,8))
        self.assertEqual(sum((c-a)*(d-b) for a,b,c,d in pieces),76)
        self.assertEqual(subtract_rectangle((0,0,1,1),(1,0,2,1)),[(0,0,1,1)])

    def test_glass_keeps_geometry_visible_behind_cutouts(self):
        fixed=exposed_faces([element((0,0,0),(4,4,4),'green'),element((0,0,0),(4,4,4),'glass')])
        self.assertTrue(any(f['texture']=='#green' for p in fixed for f in p['faces'].values()))

    def test_all_generated_models_are_free_of_coplanar_duplicates(self):
        checked=0
        for path in (ART/'models/block').glob('*.json'):
            text=path.read_text(encoding='utf-8')
            if 'industrial/'not in text and 'block/aggregate/'not in text:continue
            with self.subTest(model=path.name):
                data=json.loads(path.read_text(encoding='utf-8'))
                self.assertFalse(list(coplanar_overlaps(data.get('elements',[]))))
                checked+=1
        self.assertGreaterEqual(checked,60)


if __name__=='__main__':unittest.main()
