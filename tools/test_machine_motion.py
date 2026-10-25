"""Atlas compatibility, bounded animation cost and stopped-state regressions."""
import json,unittest
from PIL import Image
from generate_machine_motion import ART,FOLDER

class MachineMotionTests(unittest.TestCase):
    def test_shared_animation_pool_is_small_and_low_rate(self):
        metadata=list(FOLDER.glob('*.png.mcmeta'));self.assertEqual(len(metadata),4)
        decoded=0
        for path in metadata:
            animation=json.loads(path.read_text())['animation'];self.assertGreaterEqual(animation['frametime'],2);self.assertFalse(animation.get('interpolate',False))
            with Image.open(path.with_suffix('')) as im:
                size=animation['width'];self.assertEqual(size,animation['height']);self.assertLessEqual(size,64)
                self.assertEqual(im.width,size);self.assertEqual(im.height%size,0);self.assertGreater(im.height//size,1)
                decoded+=im.width*im.height*4
        self.assertLessEqual(decoded,256*1024,'Decoded frames are shared across all machines, under 256KiB before mipmaps')
    def test_idle_frames_match_the_animation_start_and_motion_is_visible(self):
        for path in FOLDER.glob('*_on.png'):
            with Image.open(path) as im,Image.open(path.with_name(path.name.replace('_on.png','_idle.png'))) as idle:
                frame=im.crop((0,0,im.width,im.width));self.assertEqual(frame.tobytes(),idle.tobytes())
                self.assertNotEqual(frame.tobytes(),im.crop((0,im.width*3,im.width,im.width*4)).tobytes())
        with Image.open(FOLDER/'wind_idle.png') as wind:self.assertEqual(wind.getpixel((0,0))[3],0)
    def test_running_and_stopped_models_choose_different_shared_sprites(self):
        for kind in ('quarry','crusher'):
            for part in range(4):
                off=json.loads((ART/f'models/block/{kind}_{part}.json').read_text());on=json.loads((ART/f'models/block/{kind}_{part}_on.json').read_text())
                for key in (('drill',)if kind=='quarry'else('roller_left','roller_right')):
                    self.assertTrue(off['textures'][key].endswith('_idle'));self.assertTrue(on['textures'][key].endswith('_on'))
        off=json.loads((ART/'models/block/g02.json').read_text());on=json.loads((ART/'models/block/g02_on.json').read_text())
        self.assertTrue(off['textures']['rotor'].endswith('wind_idle'));self.assertTrue(on['textures']['rotor'].endswith('wind_on'))
        self.assertEqual(on['render_type'],'minecraft:cutout')

if __name__=='__main__':unittest.main()
