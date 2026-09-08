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
        private const float VisualUvTilesPerMeter = 0.75f;

        [SerializeField] private MeshFilter greenMeshFilter;
        [SerializeField] private MeshCollider greenCollider;
        [SerializeField] private Transform flagPole;
        [SerializeField] private Transform flag;
        [SerializeField, Range(48, 160)] private int lateralSegments = 120;
        [SerializeField, Range(96, 256)] private int longitudinalSegments = 200;

        private Mesh _greenMesh;
        private Mesh _collisionMesh;
        private GameObject _cupLiner;
        private Material _cupMaterial;
        private PuttTelemetry _shot;
        private PuttSurfaceGrid _surfaceGrid;

        private void Awake()
        {
            if (greenMeshFilter == null) greenMeshFilter = GetComponent<MeshFilter>();
            if (greenCollider == null) greenCollider = GetComponent<MeshCollider>();
            if (greenCollider == null) greenCollider = gameObject.AddComponent<MeshCollider>();
            if (flagPole == null) flagPole = GameObject.Find("FlagPole")?.transform;
            if (flag == null) flag = GameObject.Find("Flag")?.transform;

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
            PuttSurfaceGridReceiver.SurfaceGridReceived += ApplySurfaceGrid;
        }

        private void OnDisable()
        {
            PuttTelemetryReceiver.ShotReceived -= RebuildForShot;
            PuttSurfaceGridReceiver.SurfaceGridReceived -= ApplySurfaceGrid;
        }

        private void OnDestroy()
        {
            if (_greenMesh != null) Destroy(_greenMesh);
            if (_collisionMesh != null) Destroy(_collisionMesh);
            if (_cupLiner != null) Destroy(_cupLiner);
        }

        public void RebuildForShot(PuttTelemetry shot)
        {
            if (shot == null || greenMeshFilter == null) return;
            _shot = shot;
            _surfaceGrid = null;
            Rebuild();
        }

        private void ApplySurfaceGrid(PuttSurfaceGrid grid)
        {
            if (grid == null || !grid.IsDecoded) return;
            _surfaceGrid = grid;
            if (_shot != null) Rebuild();
        }

        private void Rebuild()
        {
            var shot = _shot;
            if (shot == null) return;

            float minX;
            float maxX;
            float minY;
            float maxY;
            if (_surfaceGrid != null && _surfaceGrid.IsDecoded)
            {
                minX = _surfaceGrid.minXM;
                maxX = _surfaceGrid.maxXM;
                minY = _surfaceGrid.minYM;
                maxY = _surfaceGrid.maxYM;
            }
            else
            {
                var distance = Mathf.Max(2f, shot.holeDistanceM);
                var halfWidth = Mathf.Max(2.5f, distance * 0.55f, Mathf.Abs(shot.startXM) + 1.5f);
                minX = -halfWidth;
                maxX = halfWidth;
                minY = Mathf.Min(-1.5f, shot.startYM - 1.2f);
                maxY = Mathf.Max(distance + 2.0f, shot.startYM + 2.0f);
            }

            BuildGreenMesh(shot, minX, maxX, minY, maxY);
            PositionCupAndFlag(shot);
        }

        private float HeightAt(PuttTelemetry shot, float x, float nativeY)
        {
            if (_surfaceGrid != null && _surfaceGrid.Covers(x, nativeY))
                return _surfaceGrid.Sample(x, nativeY);
            return PuttGreenSurfaceMath.EffectiveHeightAt(shot, x, nativeY);
        }

        private void BuildGreenMesh(PuttTelemetry shot, float minX, float maxX, float minNativeY, float maxNativeY)
        {
            var xCount = lateralSegments + 1;
            var yCount = longitudinalSegments + 1;
            var vertices = new Vector3[xCount * yCount];
            var uvs = new Vector2[vertices.Length];
            var visualTriangles = new List<int>(lateralSegments * longitudinalSegments * 6);
            var collisionTriangles = new List<int>(lateralSegments * longitudinalSegments * 6);
            var dx = (maxX - minX) / lateralSegments;
            var dy = (maxNativeY - minNativeY) / longitudinalSegments;
            var cupY = shot.holeDistanceM;

            for (var iy = 0; iy < yCount; iy++)
            {
                var nativeY = minNativeY + dy * iy;
                for (var ix = 0; ix < xCount; ix++)
                {
                    var x = minX + dx * ix;
                    var index = iy * xCount + ix;
                    vertices[index] = new Vector3(x, HeightAt(shot, x, nativeY), nativeY);
                    uvs[index] = WorldUvAt(x, nativeY, minX, minNativeY);
                }
            }

            for (var iy = 0; iy < longitudinalSegments; iy++)
            {
                var cellY = minNativeY + dy * (iy + 0.5f);
                for (var ix = 0; ix < lateralSegments; ix++)
                {
                    var cellX = minX + dx * (ix + 0.5f);
                    var cupDx = cellX;
                    var cupDy = cellY - cupY;
                    if (cupDx * cupDx + cupDy * cupDy < CupRadiusM * CupRadiusM)
                        continue;

                    var a = iy * xCount + ix;
                    var b = a + 1;
                    var c = a + xCount;
                    var d = c + 1;
                    AddQuad(collisionTriangles, a, b, c, d);

                    if (IsInsideVisualFootprint(cellX, cellY, minX, maxX, minNativeY, maxNativeY) ||
                        IsInsideGameplayCorridor(shot, cellX, cellY))
                    {
                        AddQuad(visualTriangles, a, b, c, d);
                    }
                }
            }

            if (_greenMesh == null)
            {
                _greenMesh = new Mesh { name = "PuttVision Visual Green" };
                _greenMesh.MarkDynamic();
            }
            else _greenMesh.Clear();

            _greenMesh.vertices = vertices;
            _greenMesh.uv = uvs;
            _greenMesh.SetTriangles(visualTriangles, 0, true);
            _greenMesh.RecalculateNormals();
            _greenMesh.RecalculateBounds();
            greenMeshFilter.sharedMesh = _greenMesh;

            if (greenCollider != null)
            {
                if (_collisionMesh == null)
                {
                    _collisionMesh = new Mesh { name = "PuttVision Authoritative Green Collision" };
                    _collisionMesh.MarkDynamic();
                }
                else _collisionMesh.Clear();

                _collisionMesh.vertices = vertices;
                _collisionMesh.SetTriangles(collisionTriangles, 0, true);
                _collisionMesh.RecalculateBounds();
                greenCollider.sharedMesh = null;
                greenCollider.sharedMesh = _collisionMesh;
            }
        }

        private static void AddQuad(List<int> triangles, int a, int b, int c, int d)
        {
            triangles.Add(a); triangles.Add(c); triangles.Add(b);
            triangles.Add(b); triangles.Add(c); triangles.Add(d);
        }

        internal static Vector2 WorldUvAt(float x, float nativeY, float minX, float minNativeY)
        {
            return new Vector2(
                (x - minX) * VisualUvTilesPerMeter,
                (nativeY - minNativeY) * VisualUvTilesPerMeter);
        }

        internal static bool IsInsideVisualFootprint(
            float x,
            float nativeY,
            float minX,
            float maxX,
            float minNativeY,
            float maxNativeY)
        {
            var halfWidth = Mathf.Max(0.01f, (maxX - minX) * 0.5f);
            var halfLength = Mathf.Max(0.01f, (maxNativeY - minNativeY) * 0.5f);
            var centerX = (minX + maxX) * 0.5f;
            var centerY = (minNativeY + maxNativeY) * 0.5f;
            var normalizedY = (nativeY - centerY) / halfLength;

            if (Mathf.Abs(normalizedY) > 1f)
                return false;

            // A small deterministic side drift avoids the raw rectangular data-slab look
            // while a fourth-power superellipse only trims the visually awkward corners.
            var sideDrift = Mathf.Sin((normalizedY + 1f) * 2.35f) * halfWidth * 0.055f;
            var normalizedX = (x - centerX - sideDrift) / halfWidth;
            var x2 = normalizedX * normalizedX;
            var y2 = normalizedY * normalizedY;
            return x2 * x2 + y2 * y2 <= 1f;
        }

        internal static bool IsInsideGameplayCorridor(PuttTelemetry shot, float x, float nativeY)
        {
            if (shot == null) return false;

            var start = new Vector2(shot.startXM, shot.startYM);
            var cup = new Vector2(0f, shot.holeDistanceM);
            var point = new Vector2(x, nativeY);
            var axis = cup - start;
            var axisLengthSq = axis.sqrMagnitude;
            var t = axisLengthSq > 1e-6f
                ? Mathf.Clamp01(Vector2.Dot(point - start, axis) / axisLengthSq)
                : 0f;
            var nearest = start + axis * t;
            return (point - nearest).sqrMagnitude <= 0.75f * 0.75f;
        }

        private void PositionCupAndFlag(PuttTelemetry shot)
        {
            var cupSurfaceY = HeightAt(shot, 0f, shot.holeDistanceM);
            EnsureCupLiner();
            if (_cupLiner != null)
                _cupLiner.transform.SetPositionAndRotation(new Vector3(0f, cupSurfaceY, shot.holeDistanceM), Quaternion.identity);
            if (flagPole != null) flagPole.position = new Vector3(0f, cupSurfaceY + 0.75f, shot.holeDistanceM);
            if (flag != null) flag.position = new Vector3(0.20f, cupSurfaceY + 1.30f, shot.holeDistanceM);
        }

        private void EnsureCupLiner()
        {
            if (_cupLiner != null) return;
            _cupLiner = new GameObject("CupLiner");
            _cupLiner.transform.SetParent(transform.parent, true);
            var filter = _cupLiner.AddComponent<MeshFilter>();
            var renderer = _cupLiner.AddComponent<MeshRenderer>();
            filter.sharedMesh = BuildCupLinerMesh(48);

            if (_cupMaterial != null) renderer.sharedMaterial = _cupMaterial;
            else
            {
                var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
                if (shader != null)
                {
                    _cupMaterial = new Material(shader) { name = "PuttVision Cup Liner Runtime", color = new Color(0.012f, 0.014f, 0.016f, 1f) };
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
                triangles.Add(top); triangles.Add(bottom); triangles.Add(nextTop);
                triangles.Add(nextTop); triangles.Add(bottom); triangles.Add(nextBottom);
                triangles.Add(bottomCenter); triangles.Add(nextBottom); triangles.Add(bottom);
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
            if (green != null && green.GetComponent<PuttGreenMeshPresenter>() == null)
                green.AddComponent<PuttGreenMeshPresenter>();
        }
    }
}
