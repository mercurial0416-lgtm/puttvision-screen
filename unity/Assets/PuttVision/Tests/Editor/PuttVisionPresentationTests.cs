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
            var frame = new PuttPhysicsFrame
            {
                orientationW = 1f,
                orientationX = 0f,
                orientationY = 0f,
                orientationZ = 0f,
            };

            var rotation = PuttAuthoritativeBallPresenter.NativeQuaternionToUnity(frame);
            Assert.That(Quaternion.Angle(Quaternion.identity, rotation), Is.LessThan(0.001f));
        }

        [Test]
        public void NativePositiveYawMapsToUnityNegativeYawAfterBasisChange()
        {
            var half = Mathf.PI * 0.25f;
            var frame = new PuttPhysicsFrame
            {
                orientationW = Mathf.Cos(half),
                orientationZ = Mathf.Sin(half),
            };

            var rotation = PuttAuthoritativeBallPresenter.NativeQuaternionToUnity(frame);
            var expected = Quaternion.AngleAxis(-90f, Vector3.up);
            Assert.That(Quaternion.Angle(expected, rotation), Is.LessThan(0.01f));
        }

        [Test]
        public void UniformGreenUsesSameGlobalSlopeHeightConventionAsNative()
        {
            var shot = new PuttTelemetry
            {
                terrainProfileId = -1,
                holeDistanceM = 5f,
                sideSlopePct = 2f,
                longSlopePct = -1f,
            };

            var height = PuttGreenSurfaceMath.EffectiveHeightAt(shot, 0.5f, 2f);
            Assert.That(height, Is.EqualTo(0.01f).Within(1e-6f));
        }

        [Test]
        public void BuiltInProfileZeroMatchesNativeOriginHeight()
        {
            var shot = new PuttTelemetry
            {
                terrainProfileId = 0,
                holeDistanceM = 5f,
            };

            var height = PuttGreenSurfaceMath.EffectiveHeightAt(shot, 0f, 0f);
            Assert.That(height, Is.EqualTo(0.0004f).Within(1e-7f));
        }
    }
}
