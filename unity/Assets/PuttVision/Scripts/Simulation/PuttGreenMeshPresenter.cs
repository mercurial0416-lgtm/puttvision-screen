using System.Collections.Generic;
using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Simulation
{
    [DisallowMultipleComponent]
    public sealed class PuttGreenMeshPresenter : MonoBehaviour
    {
        private const float CupRadiusM = 0.054f;
        private const float CupDepthM = 0.1016f;

        [SerializeField] private MeshFilter greenMeshFilter;
        [SerializeField] private MeshCollider greenCollider;
        [SerializeField] private Transform flagPole;
        [SerializeField] private Transform flag;
        [SerializeField, Range(48, 160)] private int lateralSegments = 120;
        [SerializeField, Range(96, 256)] private int longitudinalSegments = 200;

        private Mesh _greenMesh;
        private GameObject _cupLiner;
        private Material _cupMaterial;

        private void Awake()
        {
            if (greenMeshFilter == null)
                greenMeshFilter = GetComponent<MeshFilter>();

            if (greenCollider == null)
                greenCollider = GetComponent<MeshCollider>();

            if (greenCollider == null)
                greenCollider = gameObject.AddComponent<MeshCollider>();

            if (flagPole == null)
                flagPole = GameObject.Find("FlagPole")?.transform;
            if (flag == null)
                flag = GameObject.Find("Flag")?.transform;

            var legacyCupMarker = GameObject.Find("CupMarker");
            if (legacyCupMarker != null)
            {
                var renderer = legacyCupMarker.GetComponent<Renderer>();
                if (renderer != null)
                {
                    _cupMaterial = renderer.sharedMaterial;
                    renderer.enabled = false;
                }
            }

            transform.localScale = Vector3.one;
        }

        private void OnEnable()
        {
            PuttTelemetryReceiver.ShotReceived += RebuildForShot;
        }

        private void OnDisable()
        {
            PuttTelemetryReceiver.ShotReceived -= RebuildForShot;
        }

        private void OnDestroy()
        {
            if (_greenMesh != null)
                Destroy(_greenMesh);
            if (_cupLiner != null)
                Destroy(_cupLiner);
        }

        public void RebuildForShot(PuttTelemetry shot)
        {
            if (shot == null || greenMeshFilter == null)
                return;

            var distance = Mathf.Max(2f, shot.holeDistanceM);
            var halfWidth = Mathf.Max(2.5f, distance * 0.55f, Mathf.Abs(shot.startXM) + 1.5f);
            var minNativeY = Mathf.Min(-1.5f, shot.startYM - 1.2f);
            var maxNativeY = Mathf.Max(distance + 2.0f, shot.startYM + 2.0f);

            BuildGreenMesh(shot, halfWidth, minNativeY, maxNativeY);
            PositionCupAndFlag(shot);
        }

        private void BuildGreenMesh(PuttTelemetry shot, float halfWidth, float minNativeY, float maxNativeY)
        {
            var xCount = lateralSegments + 1;
            var yCount = longitudinalSegments + 1;
            var vertices = new Vector3[xCount * yCount];
            var uvs = new Vector2[vertices.Length];
            var triangles = new List<int>(lateralSegments * longitudinalSegments * 6);

            var dx = (halfWidth * 2f) / lateralSegments;
            var dy = (maxNativeY - minNativeY) / longitudinalSegments;
            var cupY = shot.holeDistanceM;

            for (var iy = 0; iy < yCount; iy++)
            {
                var nativeY = minNativeY + dy * iy;
                var v = iy / (float)longitudinalSegments;

                for (var ix = 0; ix < xCount; ix++)
                {
                    var x = -halfWidth + dx * ix;
                    var u = ix / (float)lateralSegments;
                    var height = PuttGreenSurfaceMath.EffectiveHeightAt(shot, x, nativeY);
                    var index = iy * xCount + ix;
                    vertices[index] = new Vector3(x, height, nativeY);
                    uvs[index] = new Vector2(u * 3f, v * 6f);
                }
            }

            for (var iy = 0; iy < longitudinalSegments; iy++)
            {
                var cellY = minNativeY + dy * (iy + 0.5f);
                for (var ix = 0; ix < lateralSegments; ix++)
                {
                    var cellX = -halfWidth + dx * (ix + 0.5f);
                    var cupDistance = Mathf.Sqrt(cellX * cellX + (cellY - cupY) * (cellY - cupY));
                    if (cupDistance < CupRadiusM)
                        continue;

                    var a = iy * xCount + ix;
                    var b = a + 1;
                    var c = a + xCount;
                    var d = c + 1;

                    // Viewed from above, normals face +Y in Unity.
                    triangles.Add(a);
                    triangles.Add(c);
                    triangles.Add(b);
                    triangles.Add(b);
                    triangles.Add(c);
                    triangles.Add(d);
                }
            }

            if (_greenMesh == null)
            {
                _greenMesh = new Mesh { name = "PuttVision Native-Compatible Green" };
                _greenMesh.MarkDynamic();
            }
            else
            {
                _greenMesh.Clear();
            }

            _greenMesh.vertices = vertices;
            _greenMesh.uv = uvs;
            _greenMesh.SetTriangles(triangles, 0, true);
            _greenMesh.RecalculateNormals();
            _greenMesh.RecalculateBounds();

            greenMeshFilter.sharedMesh = _greenMesh;
            if (greenCollider != null)
            {
                greenCollider.sharedMesh = null;
                greenCollider.sharedMesh = _greenMesh;
            }
        }

        private void PositionCupAndFlag(PuttTelemetry shot)
        {
            var cupSurfaceY = PuttGreenSurfaceMath.EffectiveHeightAt(shot, 0f, shot.holeDistanceM);
            EnsureCupLiner();
            if (_cupLiner != null)
                _cupLiner.transform.SetPositionAndRotation(
                    new Vector3(0f, cupSurfaceY, shot.holeDistanceM),
                    Quaternion.identity);

            if (flagPole != null)
                flagPole.position = new Vector3(0f, cupSurfaceY + 0.75f, shot.holeDistanceM);
            if (flag != null)
                flag.position = new Vector3(0.20f, cupSurfaceY + 1.30f, shot.holeDistanceM);
        }

        private void EnsureCupLiner()
        {
            if (_cupLiner != null)
                return;

            _cupLiner = new GameObject("CupLiner");
            _cupLiner.transform.SetParent(transform.parent, true);

            var filter = _cupLiner.AddComponent<MeshFilter>();
            var renderer = _cupLiner.AddComponent<MeshRenderer>();
            filter.sharedMesh = BuildCupLinerMesh(48);

            if (_cupMaterial != null)
            {
                renderer.sharedMaterial = _cupMaterial;
            }
            else
            {
                var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
                if (shader != null)
                {
                    _cupMaterial = new Material(shader)
                    {
                        name = "PuttVision Cup Liner Runtime",
                        color = new Color(0.012f, 0.014f, 0.016f, 1f),
                    };
                    renderer.sharedMaterial = _cupMaterial;
                }
            }
        }

        private static Mesh BuildCupLinerMesh(int segments)
        {
            var vertices = new List<Vector3>(segments * 2 + 1);
            var triangles = new List<int>(segments * 9);

            for (var i = 0; i < segments; i++)
            {
                var angle = i / (float)segments * Mathf.PI * 2f;
                var x = Mathf.Cos(angle) * CupRadiusM;
                var z = Mathf.Sin(angle) * CupRadiusM;
                vertices.Add(new Vector3(x, 0f, z));
                vertices.Add(new Vector3(x, -CupDepthM, z));
            }

            var bottomCenter = vertices.Count;
            vertices.Add(new Vector3(0f, -CupDepthM, 0f));

            for (var i = 0; i < segments; i++)
            {
                var next = (i + 1) % segments;
                var top = i * 2;
                var bottom = top + 1;
                var nextTop = next * 2;
                var nextBottom = nextTop + 1;

                // Inner-facing wall.
                triangles.Add(top);
                triangles.Add(bottom);
                triangles.Add(nextTop);
                triangles.Add(nextTop);
                triangles.Add(bottom);
                triangles.Add(nextBottom);

                // Up-facing bottom.
                triangles.Add(bottomCenter);
                triangles.Add(nextBottom);
                triangles.Add(bottom);
            }

            var mesh = new Mesh { name = "PuttVision Cup Liner" };
            mesh.SetVertices(vertices);
            mesh.SetTriangles(triangles, 0, true);
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();
            return mesh;
        }

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void InstallOnPrototypeGreen()
        {
            var green = GameObject.Find("Green");
            if (green == null || green.GetComponent<PuttGreenMeshPresenter>() != null)
                return;

            green.AddComponent<PuttGreenMeshPresenter>();
        }
    }
}
