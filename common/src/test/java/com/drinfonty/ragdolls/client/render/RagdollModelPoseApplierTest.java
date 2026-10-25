package com.drinfonty.ragdolls.client.render;

import com.drinfonty.ragdolls.client.compat.emf.EMFCompat;
import com.drinfonty.ragdolls.physics.RagdollPose;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagdollModelPoseApplierTest {

	@Test
	void testEMFCompatInitSafeWithoutEMF() {
		assertDoesNotThrow(EMFCompat::init);
	}

	@Test
	void testApplyToGenericModel() {
		ModelPart head = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);

		ModelPart body = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);

		ModelPart leftArm = new ModelPart(
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

		ModelPart crossedArms = new ModelPart(
			Collections.emptyList(),
			Collections.emptyMap()
		);
		crossedArms.visible = true;

		Map<String, ModelPart> rootChildren = new HashMap<>();
		rootChildren.put("head", head);
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

		// 1. Head received head transform
		assertEquals(headTr.x(), head.x, 1e-4f);
		assertEquals(headTr.y(), head.y, 1e-4f);
		assertEquals(headTr.z(), head.z, 1e-4f);
		assertEquals(headTr.xRot(), head.xRot, 1e-4f);
		assertEquals(headTr.yRot(), head.yRot, 1e-4f);
		assertEquals(headTr.zRot(), head.zRot, 1e-4f);

		// 2. Arms and legs received transforms
		assertEquals(leftArmTr.x(), leftArm.x, 1e-4f);
		assertEquals(rightArmTr.x(), rightArm.x, 1e-4f);
		assertEquals(leftLegTr.x(), leftLeg.x, 1e-4f);
		assertEquals(rightLegTr.x(), rightLeg.x, 1e-4f);
		assertEquals(bodyTr.x(), body.x, 1e-4f);

		// 3. Crossed arms part should be hidden for illager models
		assertFalse(crossedArms.visible);
	}
}
