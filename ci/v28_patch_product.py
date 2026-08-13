from pathlib import Path
import json

def patch(path, old, new, label):
    p=Path(path); t=p.read_text(encoding='utf-8')
    if new in t: print(label+': current'); return
    if t.count(old)!=1: raise SystemExit(label+' marker')
    p.write_text(t.replace(old,new,1),encoding='utf-8'); print(label+': patched')

patch('app/src/main/java/com/puttvision/screen/GreenReadAdvisor.kt',
'''    val startY100: Int,
    val pace100: Int
)''','''    val startY100: Int,
    val pace100: Int,
    val customGreenHash: Int
)''','green key field')
patch('app/src/main/java/com/puttvision/screen/GreenReadAdvisor.kt',
'''            (start.second * 100.0).toInt(),
            (targetCupSpeedMps * 100.0).toInt()
        )''','''            (start.second * 100.0).toInt(),
            (targetCupSpeedMps * 100.0).toInt(),
            V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile)
        )''','green key value')
patch('app/src/main/java/com/puttvision/screen/V13Reliability.kt',
'''        key.startX100, key.startY100, key.pace100
    ).joinToString(":")''','''        key.startX100, key.startY100, key.pace100, key.customGreenHash
    ).joinToString(":")''','disk green key')

p=Path('app/src/main/java/com/puttvision/screen/V22CustomGreen.kt'); t=p.read_text(encoding='utf-8')
if 'GreenReadRuntime.clearRuntimeCache()' not in t:
    old='''            normalized.forEachIndexed { i, n ->
                putFloat("side_$i", n.sidePct.toFloat())
                putFloat("long_$i", n.longPct.toFloat())
            }
        }.apply()
    }'''
    new='''            normalized.forEachIndexed { i, n ->
                putFloat("side_$i", n.sidePct.toFloat())
                putFloat("long_$i", n.longPct.toFloat())
            }
        }.apply()
        GreenReadRuntime.clearRuntimeCache()
    }'''
    if t.count(old)!=1: raise SystemExit('custom green save marker')
    p.write_text(t.replace(old,new,1),encoding='utf-8'); print('custom green cache invalidate: patched')
else: print('custom green cache invalidate: current')

p=Path('app/src/main/java/com/puttvision/screen/V26GreenVisuals.kt'); t=p.read_text(encoding='utf-8')
old=':${V22CustomGreenRuntime.label()}"'
new=':${V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile)}"'
if new not in t:
    if t.count(old)!=1: raise SystemExit('contour cache marker')
    p.write_text(t.replace(old,new,1),encoding='utf-8'); print('contour cache signature: patched')
else: print('contour cache signature: current')

p=Path('app/src/main/java/com/puttvision/screen/ProductSetupDialog.kt'); t=p.read_text(encoding='utf-8')
if 'CUSTOM GREEN SHARE' not in t:
    old='''    addAction(action("CUSTOM GREEN", V22CustomGreenRuntime.label()) {
        showV22CustomGreenDialog(context)
    })
'''
    new=old+'''\n    addAction(action("CUSTOM GREEN SHARE", "JSON 공유 · 불러오기") {
        V28CustomGreenShare.show(context)
    })
'''
    if t.count(old)!=1: raise SystemExit('product custom green marker')
    p.write_text(t.replace(old,new,1),encoding='utf-8'); print('product green share: patched')
else: print('product green share: current')

p=Path('app/src/main/java/com/puttvision/screen/ImpactReplayView.kt'); t=p.read_text(encoding='utf-8')
if '"SHARE" to "SHARE"' not in t:
    old='''            "LINE" to "LINE", "CIRCLE" to "CIRCLE", "ANGLE" to "ANGLE",
            "UNDO" to "UNDO", "CLEAR" to "CLEAR"
        )'''
    new='''            "LINE" to "LINE", "CIRCLE" to "CIRCLE", "ANGLE" to "ANGLE",
            "UNDO" to "UNDO", "CLEAR" to "CLEAR", "SHARE" to "SHARE"
        )'''
    if t.count(old)!=1: raise SystemExit('replay toolbar marker')
    t=t.replace(old,new,1)
    old='''            "CLEAR" -> { setPaused(true); annotations.clear() }
        }'''
    new='''            "CLEAR" -> { setPaused(true); annotations.clear() }
            "SHARE" -> { setPaused(true); V28ReplayShare.share(context, this) }
        }'''
    if t.count(old)!=1: raise SystemExit('replay share action marker')
    t=t.replace(old,new,1)
    p.write_text(t,encoding='utf-8'); print('replay share: patched')
else: print('replay share: current')

p=Path('FEATURE_MATRIX.json'); d=json.loads(p.read_text(encoding='utf-8')); d['version']='v28-development'
f=d.setdefault('features',{}); f.update({'custom_green_json_export_import':True,'custom_green_shape_cache_key':True,'annotated_replay_png_share':True,'companion_session_code_pairing':True,'companion_clock_sync':True,'companion_stale_measurement_rejection':True})
v=d.setdefault('validation',{}); v.update({'v28_custom_green_roundtrip_test':True,'v28_custom_green_cache_signature_test':True,'v28_companion_protocol_test':True})
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8'); print('matrix: v28')
