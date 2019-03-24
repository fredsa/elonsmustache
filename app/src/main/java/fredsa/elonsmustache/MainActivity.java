/*
 * Copyright 2019 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fredsa.elonsmustache;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableDefinition;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.rendering.Vertex;
import com.google.ar.sceneform.ux.AugmentedFaceNode;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final double MIN_OPENGL_VERSION = 3.0;

    private FaceArFragment arFragment;

//    private ModelRenderable faceRegionsRenderable;
//    private Texture faceMeshTexture;

    private Texture mustacheTexture;
    private Material mustacheMaterial;
    private Renderable mustacheRenderable;

    private final HashMap<AugmentedFace, AugmentedFaceNode> faceNodeMap = new HashMap<>();

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        setContentView(R.layout.activity_face_mesh);
        arFragment = (FaceArFragment) getSupportFragmentManager().findFragmentById(R.id.face_fragment);

        ArSceneView sceneView = arFragment.getArSceneView();

        // This is important to make sure that the camera stream renders first so that
        // the face mesh occlusion works correctly.
        sceneView.setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST);


        // Elon's Mustache
        Texture.builder()
                .setSource(this, R.drawable.mustache)
                .build()
                .thenAccept(texture -> {
                    mustacheTexture = texture;

                    MaterialFactory.makeTransparentWithTexture(this, mustacheTexture)
                            .thenAccept(material -> {
                                mustacheMaterial = material;

                                float mustacheWidth = .075f;
                                mustacheRenderable = makePlane(
                                        new Vector3(mustacheWidth, mustacheWidth / 3f, 0f),
                                        mustacheMaterial);

                                mustacheRenderable.setShadowCaster(false);
                                mustacheRenderable.setShadowReceiver(false);
                            });
                });


        Scene scene = sceneView.getScene();

        scene.addOnUpdateListener(
                (FrameTime frameTime) -> {
                    if (mustacheRenderable == null || mustacheTexture == null) {
                        return;
                    }

                    Collection<AugmentedFace> faceList =
                            sceneView.getSession().getAllTrackables(AugmentedFace.class);

                    // Make new AugmentedFaceNodes for any new faces.
                    for (AugmentedFace face : faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            AugmentedFaceNode faceNode = new AugmentedFaceNode(face);
                            faceNode.setParent(scene);
                            faceNodeMap.put(face, faceNode);

                            Node node = new Node();
                            node.setName("mustache");
                            node.setParent(faceNode);
                            node.setRenderable(mustacheRenderable);
                        }
                    }

                    // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
                    Iterator<Map.Entry<AugmentedFace, AugmentedFaceNode>> iter =
                            faceNodeMap.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<AugmentedFace, AugmentedFaceNode> entry = iter.next();
                        AugmentedFace face = entry.getKey();
                        AugmentedFaceNode faceNode = entry.getValue();
                        switch (face.getTrackingState()) {
                            case STOPPED:
                                faceNode.setParent(null);
                                faceNode.setEnabled(false);
                                iter.remove();
                                break;
                            case PAUSED:
                                faceNode.setEnabled(false);
                                break;
                            case TRACKING:
                                faceNode.setEnabled(true);
                                FloatBuffer vertices = face.getMeshVertices();
                                
                                // Right below the nose.
                                Vector3 pos164 = getPos(vertices, 164);

                                // Right above the upper lip.
                                Vector3 pos0 = getPos(vertices, 0);

                                // Mustache location.
                                Vector3 pos = Vector3.add(pos164, pos0).scaled(.5f);

                                Node mustacheNode = faceNode.getChildren().get(2);
                                mustacheNode.setLocalPosition(pos);
                                break;
                        }
                    }
                });
    }


    private static ModelRenderable makePlane(Vector3 size, Material material) {
        Vector3 extents = size.scaled(0.5F);
        Vector3 p0 = new Vector3(-extents.x, -extents.y, 0f);
        Vector3 p1 = new Vector3(extents.x, -extents.y, 0f);
        Vector3 p4 = new Vector3(-extents.x, extents.y, 0f);
        Vector3 p5 = new Vector3(extents.x, extents.y, 0f);
        Vector3 forward = Vector3.forward();
        Vertex.UvCoordinate uv00 = new Vertex.UvCoordinate(0.0F, 0.0F);
        Vertex.UvCoordinate uv10 = new Vertex.UvCoordinate(1.0F, 0.0F);
        Vertex.UvCoordinate uv01 = new Vertex.UvCoordinate(0.0F, 1.0F);
        Vertex.UvCoordinate uv11 = new Vertex.UvCoordinate(1.0F, 1.0F);
        ArrayList<Vertex> vertices = new ArrayList(Arrays.asList(
                Vertex.builder().setPosition(p4).setNormal(forward).setUvCoordinate(uv01).build(),
                Vertex.builder().setPosition(p5).setNormal(forward).setUvCoordinate(uv11).build(),
                Vertex.builder().setPosition(p1).setNormal(forward).setUvCoordinate(uv10).build(),
                Vertex.builder().setPosition(p0).setNormal(forward).setUvCoordinate(uv00).build()
        ));
        ArrayList<Integer> triangleIndices = new ArrayList(6);
        triangleIndices.add(3);
        triangleIndices.add(1);
        triangleIndices.add(0);
        triangleIndices.add(3);
        triangleIndices.add(2);
        triangleIndices.add(1);

        RenderableDefinition.Submesh submesh = RenderableDefinition.Submesh.builder()
                .setTriangleIndices(triangleIndices).setMaterial(material).build();

        RenderableDefinition renderableDefinition = RenderableDefinition.builder()
                .setVertices(vertices).setSubmeshes(Arrays.asList(submesh)).build();

        CompletableFuture future = ModelRenderable.builder()
                .setSource(renderableDefinition).build();

        ModelRenderable result;
        try {
            result = (ModelRenderable) future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("Error creating renderable.", e);
        }

        if (result == null) {
            throw new AssertionError("Error creating renderable.");
        } else {
            return result;
        }
    }

    private Vector3 getPos(FloatBuffer vertices, int vertex) {
        vertices.position(3 * vertex);
        float[] pos = new float[3];
        vertices.get(pos, 0, 3);
        return new Vector3(pos[0], pos[1], pos[2]);
    }

    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * <p>Finishes the activity if Sceneform can not run
     */
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (ArCoreApk.getInstance().checkAvailability(activity)
                == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
            Log.e(TAG, "Augmented Faces requires ARCore.");
            Toast.makeText(activity, "Augmented Faces requires ARCore", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }
}
