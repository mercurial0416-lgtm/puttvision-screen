from pathlib import Path
import json

p=Path('app/src/main/java/com/puttvision/screen/V16CompanionUi.kt')
t=p.read_text(encoding='utf-8')
if 'fun startHost(context: Context)' not in t:
    t=t.replace('''    fun startHost(): Boolean {
        stop()''','''    fun startHost(context: Context): Boolean {
        stop()''',1)
    t=t.replace('''        role = V16CompanionRole.HOST
        host = null
        V15CompanionRuntime.clear()''','''        role = V16CompanionRole.HOST
        host = null
        V29NsdRuntime.advertise(context, code, created.status().port)
        V15CompanionRuntime.clear()''',1)
    t=t.replace('''        runCatching { client?.close() }
        server = null''','''        runCatching { client?.close() }
        V29NsdRuntime.stopAdvertising()
        server = null''',1)
    t=t.replace('''            val ok = V16CompanionLinkRuntime.startHost()''','''            val ok = V16CompanionLinkRuntime.startHost(context)''',1)
    t=t.replace('''Toast.makeText(context, if (ok) "메인폰 서버 시작 · ${V16CompanionLinkRuntime.hostAddressLabel()}" else "메인폰 서버 시작 실패", Toast.LENGTH_LONG).show()''','''Toast.makeText(context, if (ok) "메인폰 시작 · 보조폰에서 자동검색 가능" else "메인폰 서버 시작 실패", Toast.LENGTH_LONG).show()''',1)

    marker='''    root.addView(codeInput, LinearLayout.LayoutParams(-1, context.pvDp(50)))

    val views'''
    replacement='''    root.addView(codeInput, LinearLayout.LayoutParams(-1, context.pvDp(50)))

    val discover = TextView(context).apply {
        text = "같은 Wi‑Fi에서 메인폰 자동검색"
        textSize = context.pvSp(10f)
        setTextColor(Pv.primary)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(context.pvDp(8), context.pvDp(8), context.pvDp(8), context.pvDp(8))
        setOnClickListener {
            text = "검색 중…"
            V29NsdRuntime.discover(context) { hosts ->
                post {
                    text = "같은 Wi‑Fi에서 메인폰 자동검색"
                    if (hosts.isEmpty()) {
                        Toast.makeText(context, "메인폰을 못 찾음 · 수동 주소/PAIR 입력 가능", Toast.LENGTH_LONG).show()
                    } else {
                        AlertDialog.Builder(context)
                            .setTitle("발견된 PuttVision")
                            .setItems(hosts.map { it.label }.toTypedArray()) { _, which ->
                                val found = hosts[which]
                                hostInput.setText(found.host)
                                codeInput.setText(found.pairCode)
                                Toast.makeText(context, "${found.serviceName} 선택됨", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("취소", null)
                            .show()
                    }
                }
            }
        }
    }
    root.addView(discover, LinearLayout.LayoutParams(-1, context.pvDp(48)))

    val views'''
    if t.count(marker)!=1: raise SystemExit('V29 join auto discovery marker')
    t=t.replace(marker,replacement,1)
    p.write_text(t,encoding='utf-8'); print('V29 companion UI discovery patched')
else:
    print('V29 companion UI discovery current')

p=Path('FEATURE_MATRIX.json')
d=json.loads(p.read_text(encoding='utf-8')); d['version']='v29-development'
f=d.setdefault('features',{}); f.update({'companion_nsd_auto_discovery':True,'companion_manual_ip_fallback':True,'companion_auto_fill_pair_code':True})
v=d.setdefault('validation',{}); v['v29_nsd_compile_gate']=True
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
