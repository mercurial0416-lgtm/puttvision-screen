from pathlib import Path
import json

p=Path('app/src/main/java/com/puttvision/screen/V18OpenGlSimulator.kt')
t=p.read_text(encoding='utf-8')
old='''        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                vec3 light = normalize(vec3(-0.35, -0.45, 0.82));
                float d = max(dot(normalize(aNormal), light), 0.0);
                float shade = 0.60 + d * 0.48;
                vColor = vec4(aColor.rgb * shade, aColor.a);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 vColor;
            void main() { gl_FragColor = vColor; }
        """'''
new='''        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            varying vec4 vBaseColor;
            varying vec3 vNormal;
            varying vec3 vLocalPos;
            varying float vFog;
            void main() {
                vec4 clip = uMvp * vec4(aPosition, 1.0);
                gl_Position = clip;
                vBaseColor = aColor;
                vNormal = normalize(aNormal);
                vLocalPos = aPosition;
                vFog = clamp((clip.w - 2.5) / 22.0, 0.0, 1.0);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 vBaseColor;
            varying vec3 vNormal;
            varying vec3 vLocalPos;
            varying float vFog;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 sun = normalize(vec3(-0.35, -0.45, 0.82));
                float diffuse = max(dot(n, sun), 0.0);
                float hemi = 0.5 + 0.5 * clamp(n.z, -1.0, 1.0);

                float greenLead = vBaseColor.g - max(vBaseColor.r, vBaseColor.b);
                float greenMask = smoothstep(0.06, 0.24, greenLead) * smoothstep(0.55, 0.90, n.z);
                float micro = sin(vLocalPos.x * 61.0 + sin(vLocalPos.y * 23.0)) * 0.5
                            + sin(vLocalPos.y * 47.0 - vLocalPos.x * 17.0) * 0.25;
                float mowing = sin(vLocalPos.y * 4.2) * 0.5;
                float materialGain = 1.0 + greenMask * (micro * 0.045 + mowing * 0.020);
                vec3 material = vBaseColor.rgb * materialGain;

                float lightGain = 0.31 + diffuse * 0.56 + hemi * 0.17;
                float softHighlight = pow(diffuse, 6.0) * 0.045;
                vec3 lit = material * lightGain + vec3(1.0, 0.93, 0.76) * softHighlight;
                vec3 atmospheric = vec3(0.62, 0.76, 0.83);
                vec3 finalColor = mix(lit, atmospheric, vFog * 0.14);
                gl_FragColor = vec4(finalColor, vBaseColor.a);
            }
        """'''
if new in t:
    print('V29 TV material current')
elif t.count(old)==1:
    p.write_text(t.replace(old,new,1),encoding='utf-8'); print('V29 TV material patched')
else:
    raise SystemExit('V29 shader marker')

p=Path('FEATURE_MATRIX.json')
d=json.loads(p.read_text(encoding='utf-8')); d['version']='v29-development'
f=d.setdefault('features',{}); f.update({'procedural_grass_micro_material':True,'hemisphere_ambient_lighting':True,'distance_atmospheric_fog':True})
v=d.setdefault('validation',{}); v['v29_opengl_shader_source_gate']=True
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
