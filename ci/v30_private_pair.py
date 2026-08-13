from pathlib import Path
import json

p=Path('app/src/main/java/com/puttvision/screen/V29NsdDiscovery.kt')
t=p.read_text(encoding='utf-8')
t=t.replace('''    val port: Int,
    val pairCode: String
) {
    val label: String get() = "$serviceName · $host:$port · PAIR $pairCode"
}''','''    val port: Int
) {
    val label: String get() = "$serviceName · $host:$port"
}''',1)
t=t.replace('fun advertise(context: Context, pairCode: String, port: Int): Boolean {','fun advertise(context: Context, port: Int): Boolean {',1)
t=t.replace('            setAttribute("pair", pairCode)\n','',1)
t=t.replace('            setAttribute("pv", "29")','            setAttribute("pv", "30")',1)
old='''                            val pair = resolved.attributes["pair"]?.toString(Charsets.UTF_8)?.trim()?.uppercase() ?: return
                            if (pair.length < 6 || resolved.port <= 0) return
                            found[resolved.serviceName] = V29DiscoveredHost(resolved.serviceName, address, resolved.port, pair)'''
new='''                            if (resolved.port <= 0) return
                            found[resolved.serviceName] = V29DiscoveredHost(resolved.serviceName, address, resolved.port)'''
if old not in t: raise SystemExit('V30 NSD pair marker')
t=t.replace(old,new,1)
p.write_text(t,encoding='utf-8'); print('V30 NSD PAIR privacy patched')

p=Path('app/src/main/java/com/puttvision/screen/V16CompanionUi.kt')
t=p.read_text(encoding='utf-8')
t=t.replace('V29NsdRuntime.advertise(context, code, created.status().port)','V29NsdRuntime.advertise(context, created.status().port)',1)
old='''                                hostInput.setText(found.host)
                                codeInput.setText(found.pairCode)
                                Toast.makeText(context, "${found.serviceName} 선택됨", Toast.LENGTH_SHORT).show()'''
new='''                                hostInput.setText(found.host)
                                codeInput.setText("")
                                Toast.makeText(context, "주소 자동입력 완료 · 메인폰 PAIR 코드를 입력하세요", Toast.LENGTH_LONG).show()'''
if old not in t: raise SystemExit('V30 auto fill marker')
t=t.replace(old,new,1)
p.write_text(t,encoding='utf-8'); print('V30 companion UI privacy patched')

p=Path('FEATURE_MATRIX.json'); d=json.loads(p.read_text(encoding='utf-8')); d['version']='v30-development'
f=d.setdefault('features',{}); f['companion_auto_fill_pair_code']=False; f['companion_pair_code_not_advertised']=True; f['companion_nsd_auto_discovery']=True
v=d.setdefault('validation',{}); v['v30_pair_privacy_source_gate']=True
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
