package com.drinfonty.ragdolls.client.render;

import com.drinfonty.ragdolls.client.compat.emf.EMFCompat;
import com.drinfonty.ragdolls.physics.RagdollPose;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagdollModelPoseApplierTest {

	@Test
	void testRagdollRenderingThreadLocalState() {
		assertFalse(RagdollModelPoseApplier.isRagdollRendering());
		RagdollModelPoseApplier.setRagdollRendering(true);
		assertTrue(RagdollModelPoseApplier.isRagdollRendering());
		RagdollModelPoseApplier.setRagdollRendering(false);
		assertFalse(RagdollModelPoseApplier.isRagdollRendering());
	}

	@Test
	void testEMFCompatInitSafeWithoutEMF() {
		assertDoesNotThrow(EMFCompat::init);
	}

	@Test
	void testApplyToGenericModelWithCEMHierarchy() {
		// Create submodel child for left arm with an initial pose
		ModelPart leftArmSub = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);
		leftArmSub.setInitialPose(PartPose.offsetAndRotation(0.0f, 2.0f, 0.0f, 0.1f, 0.0f, 0.0f));
		leftArmSub.resetPose();
		// Simulate dynamic animation offset
		leftArmSub.setPos(5.0f, 5.0f, 5.0f);
		leftArmSub.setRotation(1.5f, 1.5f, 1.5f);

		Map<String, ModelPart> leftArmChildren = new HashMap<>();
		leftArmChildren.put("left_arm2", leftArmSub);

		ModelPart leftArm = new ModelPart(
			Collections.emptyList(),
			leftArmChildren
		);
		leftArm.visible = false; // Fresh Animations hides vanilla limb

		ModelPart crossedArms = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);
		crossedArms.visible = true;

		ModelPart head2 = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);

		ModelPart body = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);

		ModelPart rightArm = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);

		ModelPart leftLeg = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);

		ModelPart rightLeg = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);

		Map<String, ModelPart> rootChildren = new HashMap<>();
		rootChildren.put("head2", head2);
		rootChildren.put("body", body);
		rootChildren.put("left_arm", leftArm);
		rootChildren.put("right_arm", rightArm);
		rootChildren.put("left_leg", leftLeg);
		rootChildren.put("right_leg", rightLeg);
		rootChildren.put("arms", crossedArms);

		ModelPart root = new ModelPart(
			Collections.emptyList(),
			rootChildren
		);

		Model<Object> dummyModel = new Model<>(root, id -> null) {
			@Override
			public void setupAnim(Object state) {
			}
		};

		RagdollPose.LimbTransform headTr = new RagdollPose.LimbTransform(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f);
		RagdollPose.LimbTransform bodyTr = new RagdollPose.LimbTransform(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f);
		RagdollPose.LimbTransform leftArmTr = new RagdollPose.LimbTransform(2.0f, 2.1f, 2.2f, 2.3f, 2.4f, 2.5f);
		RagdollPose.LimbTransform rightArmTr = new RagdollPose.LimbTransform(3.0f, 3.1f, 3.2f, 3.3f, 3.4f, 3.5f);
		RagdollPose.LimbTransform leftLegTr = new RagdollPose.LimbTransform(4.0f, 4.1f, 4.2f, 4.3f, 4.4f, 4.5f);
		RagdollPose.LimbTransform rightLegTr = new RagdollPose.LimbTransform(5.0f, 5.1f, 5.2f, 5.3f, 5.4f, 5.5f);

		RagdollPose pose = new RagdollPose(
			0.0, 0.0, 0.0,
			headTr, bodyTr, leftArmTr, rightArmTr, leftLegTr, rightLegTr
		);

		RagdollModelPoseApplier.apply(dummyModel, pose);

		// 1. Head2 should have received head transform
		assertEquals(headTr.x(), head2.x, 1e-4f);
		assertEquals(headTr.y(), head2.y, 1e-4f);
		assertEquals(headTr.z(), head2.z, 1e-4f);
		assertEquals(headTr.xRot(), head2.xRot, 1e-4f);
		assertEquals(headTr.yRot(), head2.yRot, 1e-4f);
		assertEquals(headTr.zRot(), head2.zRot, 1e-4f);
		assertTrue(head2.visible);

		// 2. Left arm should have been made visible and received leftArm transform
		assertTrue(leftArm.visible);
		assertEquals(leftArmTr.x(), leftArm.x, 1e-4f);
		assertEquals(leftArmTr.y(), leftArm.y, 1e-4f);
		assertEquals(leftArmTr.z(), leftArm.z, 1e-4f);
		assertEquals(leftArmTr.xRot(), leftArm.xRot, 1e-4f);
		assertEquals(leftArmTr.yRot(), leftArm.yRot, 1e-4f);
		assertEquals(leftArmTr.zRot(), leftArm.zRot, 1e-4f);

		// 3. Left arm submodel should have reset its dynamic pose to its initialPose
		assertEquals(0.0f, leftArmSub.x, 1e-4f);
		assertEquals(2.0f, leftArmSub.y, 1e-4f);
		assertEquals(0.0f, leftArmSub.z, 1e-4f);
		assertEquals(0.1f, leftArmSub.xRot, 1e-4f);
		assertEquals(0.0f, leftArmSub.yRot, 1e-4f);
		assertEquals(0.0f, leftArmSub.zRot, 1e-4f);

		// 4. Crossed arms part should be hidden
		assertFalse(crossedArms.visible);

		// 5. Body and right limbs received their transforms
		assertEquals(bodyTr.x(), body.x, 1e-4f);
		assertEquals(rightArmTr.x(), rightArm.x, 1e-4f);
		assertEquals(leftLegTr.x(), leftLeg.x, 1e-4f);
		assertEquals(rightLegTr.x(), rightLeg.x, 1e-4f);
	}
}
