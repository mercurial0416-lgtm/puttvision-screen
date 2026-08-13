from pathlib import Path
p=Path('app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt')
t=p.read_text(encoding='utf-8')
if 'val customGreenHash: Int' not in t:
    old='''private data class V18TerrainKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val qualityTier: V24RenderTier
)'''
    new='''private data class V18TerrainKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customGreenHash: Int,
    val qualityTier: V24RenderTier
)'''
    if t.count(old)!=1: raise SystemExit('V18 terrain key marker')
    t=t.replace(old,new,1)
    old='''            (settings.sideSlopePct * 100).toInt(),
            (settings.longSlopePct * 100).toInt(),
            tier
        )'''
    new='''            (settings.sideSlopePct * 100).toInt(),
            (settings.longSlopePct * 100).toInt(),
            V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile),
            tier
        )'''
    if t.count(old)!=1: raise SystemExit('V18 terrain key value marker')
    t=t.replace(old,new,1)
    p.write_text(t,encoding='utf-8'); print('V28 3D terrain cache: patched')
else:
    print('V28 3D terrain cache: current')

fm=Path('FEATURE_MATRIX.json')
import json
d=json.loads(fm.read_text(encoding='utf-8'))
d.setdefault('features',{})['custom_green_3d_mesh_cache_key']=True
fm.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
