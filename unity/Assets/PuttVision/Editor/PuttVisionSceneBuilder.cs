using PuttVision.Bootstrap;
using PuttVision.Simulation;
using PuttVision.Telemetry;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace PuttVision.Editor
{
    public static class PuttVisionSceneBuilder
    {
        private const string RootFolder = "Assets/PuttVision";
        private const string SceneFolder = RootFolder + "/Scenes";
        private const string MaterialFolder = RootFolder + "/Materials";
        private const string ScenePath = SceneFolder + "/PuttVisionPrototype.unity";

        [MenuItem("PuttVision/Build Prototype Scene")]
        public static void BuildPrototypeScene()
        {
            EnsureFolder(RootFolder, "Scenes");
            EnsureFolder(RootFolder, "Materials");

            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var root = new GameObject("_PuttVision");
            root.AddComponent<PuttVisionRuntimeSettings>();
            var surfaceSampler = root.AddComponent<GreenSurfaceSampler>();

            var green = CreateGreen();
            var ball = CreateBall();
            CreateCupAndFlag();
            CreateLighting();
            CreateCamera();

            var ballController = ball.AddComponent<PuttBallController>();
            ConfigureObjectReference(ballController, "ballVisual", ball.transform);
            ConfigureObjectReference(ballController, "surfaceSampler", surfaceSampler);

            var receiverObject = new GameObject(PuttVisionBootstrap.TelemetryReceiverObjectName);
            var receiver = receiverObject.AddComponent<PuttTelemetryReceiver>();

            var bootstrapObject = new GameObject("PuttVisionBootstrap");
            var bootstrap = bootstrapObject.AddComponent<PuttVisionBootstrap>();
            ConfigureObjectReference(bootstrap, "telemetryReceiver", receiver);
            ConfigureObjectReference(bootstrap, "ballController", ballController);

            green.transform.SetParent(root.transform, true);
            ball.transform.SetParent(root.transform, true);
            receiverObject.transform.SetParent(root.transform, true);
            bootstrapObject.transform.SetParent(root.transform, true);

            EditorSceneManager.MarkSceneDirty(scene);
            EditorSceneManager.SaveScene(scene, ScenePath);
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();

            Selection.activeGameObject = bootstrapObject;
            Debug.Log($"[PuttVision] Prototype scene created: {ScenePath}");
        }

        private static GameObject CreateGreen()
        {
            var green = GameObject.CreatePrimitive(PrimitiveType.Plane);
            green.name = "Green";
            green.transform.position = Vector3.zero;
            green.transform.localScale = new Vector3(0.45f, 1f, 1.2f); // 4.5 m x 12 m

            var renderer = green.GetComponent<MeshRenderer>();
            renderer.sharedMaterial = GetOrCreateMaterial(
                MaterialFolder + "/Green.mat",
                new Color(0.045f, 0.19f, 0.08f, 1f),
                0.28f,
                0f);

            return green;
        }

        private static GameObject CreateBall()
        {
            var ball = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            ball.name = "Ball";
            ball.transform.position = new Vector3(0f, 0.02135f, -3.5f);
            ball.transform.localScale = Vector3.one * 0.0427f;

            var renderer = ball.GetComponent<MeshRenderer>();
            renderer.sharedMaterial = GetOrCreateMaterial(
                MaterialFolder + "/Ball.mat",
                new Color(0.96f, 0.97f, 0.98f, 1f),
                0.62f,
                0f);

            return ball;
        }

        private static void CreateCupAndFlag()
        {
            var cup = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            cup.name = "CupMarker";
            cup.transform.position = new Vector3(0f, 0.001f, 4.0f);
            cup.transform.localScale = new Vector3(0.054f, 0.001f, 0.054f);
            cup.GetComponent<MeshRenderer>().sharedMaterial = GetOrCreateMaterial(
                MaterialFolder + "/Cup.mat",
                new Color(0.01f, 0.012f, 0.014f, 1f),
                0.05f,
                0f);

            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            pole.name = "FlagPole";
            pole.transform.position = new Vector3(0f, 0.75f, 4f);
            pole.transform.localScale = new Vector3(0.008f, 0.75f, 0.008f);
            pole.GetComponent<MeshRenderer>().sharedMaterial = GetOrCreateMaterial(
                MaterialFolder + "/FlagPole.mat",
                new Color(0.92f, 0.92f, 0.92f, 1f),
                0.4f,
                0.15f);

            var flag = GameObject.CreatePrimitive(PrimitiveType.Quad);
            flag.name = "Flag";
            flag.transform.position = new Vector3(0.20f, 1.30f, 4f);
            flag.transform.rotation = Quaternion.Euler(0f, 0f, 0f);
            flag.transform.localScale = new Vector3(0.38f, 0.22f, 1f);
            flag.GetComponent<MeshRenderer>().sharedMaterial = GetOrCreateMaterial(
                MaterialFolder + "/Flag.mat",
                new Color(0.72f, 0.035f, 0.04f, 1f),
                0.18f,
                0f);
        }

        private static void CreateLighting()
        {
            var lightObject = new GameObject("Key Light");
            var light = lightObject.AddComponent<Light>();
            light.type = LightType.Directional;
            light.intensity = 1.25f;
            light.shadows = LightShadows.Soft;
            lightObject.transform.rotation = Quaternion.Euler(48f, -28f, 0f);

            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = new Color(0.22f, 0.28f, 0.34f);
            RenderSettings.ambientEquatorColor = new Color(0.10f, 0.13f, 0.15f);
            RenderSettings.ambientGroundColor = new Color(0.035f, 0.045f, 0.04f);
        }

        private static void CreateCamera()
        {
            var cameraObject = new GameObject("Main Camera");
            cameraObject.tag = "MainCamera";
            var camera = cameraObject.AddComponent<Camera>();
            camera.fieldOfView = 34f;
            camera.nearClipPlane = 0.03f;
            camera.farClipPlane = 100f;
            camera.clearFlags = CameraClearFlags.SolidColor;
            camera.backgroundColor = new Color(0.012f, 0.018f, 0.022f, 1f);

            cameraObject.transform.position = new Vector3(0f, 2.45f, -5.7f);
            cameraObject.transform.LookAt(new Vector3(0f, 0.15f, 2.0f));
        }

        private static Material GetOrCreateMaterial(
            string path,
            Color color,
            float smoothness,
            float metallic)
        {
            var existing = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (existing != null)
                return existing;

            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            if (shader == null)
                throw new System.InvalidOperationException("No compatible Lit shader was found.");

            var material = new Material(shader)
            {
                color = color,
                name = System.IO.Path.GetFileNameWithoutExtension(path),
            };

            if (material.HasProperty("_Smoothness"))
                material.SetFloat("_Smoothness", smoothness);
            if (material.HasProperty("_Metallic"))
                material.SetFloat("_Metallic", metallic);

            AssetDatabase.CreateAsset(material, path);
            return material;
        }

        private static void ConfigureObjectReference(
            Object target,
            string propertyName,
            Object value)
        {
            var serializedObject = new SerializedObject(target);
            var property = serializedObject.FindProperty(propertyName);
            if (property == null)
                throw new System.InvalidOperationException(
                    $"Serialized property '{propertyName}' was not found on {target.GetType().Name}.");

            property.objectReferenceValue = value;
            serializedObject.ApplyModifiedPropertiesWithoutUndo();
        }

        private static void EnsureFolder(string parent, string child)
        {
            var path = parent + "/" + child;
            if (!AssetDatabase.IsValidFolder(path))
                AssetDatabase.CreateFolder(parent, child);
        }
    }
}
