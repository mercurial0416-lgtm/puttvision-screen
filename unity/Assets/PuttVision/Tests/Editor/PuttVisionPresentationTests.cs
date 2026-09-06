using System;
using NUnit.Framework;
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
        public void UniformGreenUsesSameGlobalSlopeHeightConventionAsNative()
        {
            var shot = new PuttTelemetry { terrainProfileId = -1, holeDistanceM = 5f, sideSlopePct = 2f, longSlopePct = -1f };
            Assert.That(PuttGreenSurfaceMath.EffectiveHeightAt(shot, 0.5f, 2f), Is.EqualTo(0.01f).Within(1e-6f));
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
