using System;
using System.Reflection;
using NUnit.Framework;
using PuttVision.Presentation;
using PuttVision.Simulation;
using PuttVision.Telemetry;
using UnityEngine;

namespace PuttVision.Tests
{
    public sealed class PuttVisionPresentationTests
    {
        [Test]
        public void IdentityNativeQuaternionRemainsIdentityInUnityBasis()
        {
            var frame = new PuttPhysicsFrame { orientationW = 1f };
            var rotation = PuttAuthoritativeBallPresenter.NativeQuaternionToUnity(frame);
            Assert.That(Quaternion.Angle(Quaternion.identity, rotation), Is.LessThan(0.001f));
        }

        [Test]
        public void NativePositiveYawMapsToUnityNegativeYawAfterBasisChange()
        {
            var half = Mathf.PI * 0.25f;
            var frame = new PuttPhysicsFrame { orientationW = Mathf.Cos(half), orientationZ = Mathf.Sin(half) };
            var rotation = PuttAuthoritativeBallPresenter.NativeQuaternionToUnity(frame);
            Assert.That(Quaternion.Angle(Quaternion.AngleAxis(-90f, Vector3.up), rotation), Is.LessThan(0.01f));
        }

        [Test]
        public void NativeQuaternionConversionStaysStableForCompoundRotationWithoutPreNormalizingBasisVectors()
        {
            var native = Quaternion.Euler(23f, -37f, 61f);
            var frame = new PuttPhysicsFrame
            {
                orientationW = native.w * 3.5f,
                orientationX = native.x * 3.5f,
                orientationY = native.y * 3.5f,
                orientationZ = native.z * 3.5f,
            };

            var rotation = PuttAuthoritativeBallPresenter.NativeQuaternionToUnity(frame);

            var x = native.x;
            var y = native.y;
            var z = native.z;
            var w = native.w;
            var expectedForward = new Vector3(
                2f * (x * y - w * z),
                2f * (y * z + w * x),
                1f - 2f * (x * x + z * z));
            var expectedUp = new Vector3(
                2f * (x * z + w * y),
                1f - 2f * (x * x + y * y),
                2f * (y * z - w * x));

            Assert.That(Vector3.Angle(rotation * Vector3.forward, expectedForward), Is.LessThan(0.01f));
            Assert.That(Vector3.Angle(rotation * Vector3.up, expectedUp), Is.LessThan(0.01f));
        }

        [Test]
        public void PhysicsFrameRejectsNonFiniteOrNegativePresentationInputs()
        {
            Assert.That(new PuttPhysicsFrame().IsUsable, Is.True);
            Assert.That(new PuttPhysicsFrame { vxMps = float.NaN }.IsUsable, Is.False);
            Assert.That(new PuttPhysicsFrame { surfaceNormalZ = float.PositiveInfinity }.IsUsable, Is.False);
            Assert.That(new PuttPhysicsFrame { elapsedSec = -0.01f }.IsUsable, Is.False);
            Assert.That(new PuttPhysicsFrame { slipSpeedMps = -0.01f }.IsUsable, Is.False);
        }

        [Test]
        public void TrailSpacingUsesSquaredDistanceWithoutChangingBoundarySemantics()
        {
            var method = typeof(PuttTrailPresenter).GetMethod(
                "ShouldAppendPoint",
                BindingFlags.Static | BindingFlags.NonPublic);
            Assert.That(method, Is.Not.Null);

            bool ShouldAppend(Vector3 next) => (bool)method.Invoke(
                null,
                new object[] { Vector3.zero, next, 0.012f });

            Assert.That(ShouldAppend(new Vector3(0.011f, 0f, 0f)), Is.False);
            Assert.That(ShouldAppend(new Vector3(0.012f, 0f, 0f)), Is.True);
            Assert.That(ShouldAppend(new Vector3(0.009f, 0f, 0.009f)), Is.True);
        }

        [Test]
        public void FullTrailCompactionRetainsRecentHalfInsteadOfShiftingEveryFrame()
        {
            var method = typeof(PuttTrailPresenter).GetMethod(
                "RetainedPointCountAfterCompaction",
                BindingFlags.Static | BindingFlags.NonPublic);
            Assert.That(method, Is.Not.Null);

            int Retained(int count, int max) => (int)method.Invoke(null, new object[] { count, max });

            Assert.That(Retained(2048, 2048), Is.EqualTo(1024));
            Assert.That(Retained(32, 32), Is.EqualTo(16));
            Assert.That(Retained(1, 32), Is.EqualTo(1));
            Assert.That(Retained(0, 32), Is.EqualTo(0));
        }

        [Test]
        public void UniformGreenUsesSameGlobalSlopeHeightConventionAsNative()
        {
            var shot = new PuttTelemetry { terrainProfileId = -1, holeDistanceM = 5f, sideSlopePct = 2f, longSlopePct = -1f };
            Assert.That(PuttGreenSurfaceMath.EffectiveHeightAt(shot, 0.5f, 2f), Is.EqualTo(0.01f).Within(1e-6f));
        }

        [Test]
        public void VisualGreenFootprintTrimsDataSlabCornersButKeepsCenter()
        {
            var method = typeof(PuttGreenMeshPresenter).GetMethod(
                "IsInsideVisualFootprint",
                BindingFlags.Static | BindingFlags.NonPublic);
            Assert.That(method, Is.Not.Null);

            bool Inside(float x, float y) => (bool)method.Invoke(
                null,
                new object[] { x, y, -4f, 4f, 0f, 10f });

            Assert.That(Inside(0f, 5f), Is.True);
            Assert.That(Inside(-4f, 0f), Is.False);
            Assert.That(Inside(4f, 10f), Is.False);
        }

        [Test]
        public void GreenUvUsesSameWorldScaleOnBothAxes()
        {
            var method = typeof(PuttGreenMeshPresenter).GetMethod(
                "WorldUvAt",
                BindingFlags.Static | BindingFlags.NonPublic);
            Assert.That(method, Is.Not.Null);

            Vector2 Uv(float x, float y) => (Vector2)method.Invoke(
                null,
                new object[] { x, y, -2f, 1f });

            var origin = Uv(-2f, 1f);
            var oneMeterRight = Uv(-1f, 1f);
            var oneMeterForward = Uv(-2f, 2f);

            Assert.That(oneMeterRight.x - origin.x, Is.EqualTo(0.75f).Within(1e-6f));
            Assert.That(oneMeterForward.y - origin.y, Is.EqualTo(0.75f).Within(1e-6f));
            Assert.That(oneMeterRight.y, Is.EqualTo(origin.y).Within(1e-6f));
            Assert.That(oneMeterForward.x, Is.EqualTo(origin.x).Within(1e-6f));
        }

        [Test]
        public void VisualGreenAlwaysKeepsGameplayCorridor()
        {
            var method = typeof(PuttGreenMeshPresenter).GetMethod(
                "IsInsideGameplayCorridor",
                BindingFlags.Static | BindingFlags.NonPublic);
            Assert.That(method, Is.Not.Null);

            var shot = new PuttTelemetry { startXM = 1.25f, startYM = -0.5f, holeDistanceM = 6f };
            bool Inside(float x, float y) => (bool)method.Invoke(
                null,
                new object[] { shot, x, y });

            Assert.That(Inside(1.25f, -0.5f), Is.True);
            Assert.That(Inside(0f, 6f), Is.True);
            Assert.That(Inside(2.5f, 3f), Is.False);
        }

        [Test]
        public void BuiltInProfileZeroMatchesNativeOriginHeight()
        {
            var shot = new PuttTelemetry { terrainProfileId = 0, holeDistanceM = 5f };
            Assert.That(PuttGreenSurfaceMath.EffectiveHeightAt(shot, 0f, 0f), Is.EqualTo(0.0004f).Within(1e-7f));
        }

        [Test]
        public void SurfaceGridDecodesLittleEndianAndBilinearlyInterpolates()
        {
            var values = new[] { 0f, 1f, 2f, 3f };
            var bytes = new byte[values.Length * 4];
            for (var i = 0; i < values.Length; i++)
            {
                var raw = BitConverter.SingleToInt32Bits(values[i]);
                var o = i * 4;
                bytes[o] = (byte)raw;
                bytes[o + 1] = (byte)(raw >> 8);
                bytes[o + 2] = (byte)(raw >> 16);
                bytes[o + 3] = (byte)(raw >> 24);
            }

            var grid = new PuttSurfaceGrid
            {
                schemaVersion = 1,
                width = 2,
                height = 2,
                minXM = -1f,
                maxXM = 1f,
                minYM = 0f,
                maxYM = 2f,
                heightF32LeBase64 = Convert.ToBase64String(bytes),
            };

            Assert.That(grid.TryDecode(), Is.True);
            Assert.That(grid.Covers(0f, 1f), Is.True);
            Assert.That(grid.Sample(0f, 1f), Is.EqualTo(1.5f).Within(1e-6f));
        }

        [Test]
        public void SurfaceGridRejectsWrongPayloadLength()
        {
            var grid = new PuttSurfaceGrid
            {
                schemaVersion = 1,
                width = 2,
                height = 2,
                minXM = 0f,
                maxXM = 1f,
                minYM = 0f,
                maxYM = 1f,
                heightF32LeBase64 = Convert.ToBase64String(new byte[4]),
            };
            Assert.That(grid.TryDecode(), Is.False);
        }
    }
}
