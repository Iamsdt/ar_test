package com.example.artest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.artest.databinding.ActivityMainBinding
import com.example.artest.databinding.SheetModelsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch

private const val MODEL_DIR = "models"

/**
 * POC: pick a .glb from assets/models/, tap a detected floor plane to place it.
 *
 * Models attach to an ARCore Anchor rather than a fixed world coordinate. ARCore
 * re-corrects an anchor's pose every frame as its map of the room improves -- that is
 * what keeps an object planted when you walk around it. Setting a raw position instead
 * is the usual cause of "my model drifts away".
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Every .glb found under assets/models/ at startup. Drop files in, they appear. */
    private val availableModels: List<String> by lazy {
        assets.list(MODEL_DIR).orEmpty()
            .filter { it.endsWith(".glb", ignoreCase = true) }
            .sorted()
    }

    private var selectedModel: String? = null
    private val placedNodes = mutableListOf<AnchorNode>()
    private var planeFound = false
    private var sceneReady = false
    private var failureReason: TrackingFailureReason = TrackingFailureReason.NONE

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setupScene() else explainPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedModel = availableModels.firstOrNull()

        binding.modelsButton.setOnClickListener { showModelSheet() }
        binding.clearButton.setOnClickListener { clearAll() }

        if (hasCamera()) setupScene() else requestCamera.launch(Manifest.permission.CAMERA)
    }

    // --- Permission -------------------------------------------------------------

    private fun hasCamera() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Denied. If the system will still show the OS prompt, offer to ask again. If it
     * won't (permanent denial), the only route left is app settings.
     */
    private fun explainPermission() {
        val canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perm_title)
            .setMessage(if (canAskAgain) R.string.perm_body else R.string.perm_body_settings)
            .setCancelable(false)
            .setNegativeButton(R.string.perm_quit) { _, _ -> finish() }
            .setPositiveButton(
                if (canAskAgain) R.string.perm_grant else R.string.perm_settings
            ) { _, _ ->
                if (canAskAgain) {
                    requestCamera.launch(Manifest.permission.CAMERA)
                } else {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                }
            }
            .show()
    }

    /** Coming back from Settings, the grant only takes effect now. */
    override fun onResume() {
        super.onResume()
        if (!sceneReady && hasCamera()) setupScene()
    }

    // --- Scene ------------------------------------------------------------------

    private fun setupScene() {
        if (sceneReady) return
        sceneReady = true

        with(binding.sceneView) {
            lifecycle = this@MainActivity.lifecycle

            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

                // Depth lets real objects occlude the model, and lets us hit-test
                // against a depth map when no plane has been found yet.
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                }

                // Autofocus sharpens the frame, which yields more trackable feature
                // points. On a blank tiled floor that is most of the battle.
                config.focusMode = Config.FocusMode.AUTO

                // Last-resort placement: puts the model down immediately at a guessed
                // depth and corrects it once tracking catches up.
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            }

            planeRenderer.isEnabled = true

            onSessionUpdated = { _, frame ->
                if (!planeFound) {
                    val tracking = frame.getUpdatedTrackables(Plane::class.java).any {
                        it.trackingState == TrackingState.TRACKING
                    }
                    if (tracking) {
                        planeFound = true
                        failureReason = TrackingFailureReason.NONE
                        updateHint()
                    } else {
                        // Tell the user *why* nothing is being found. An empty office
                        // with uniform tiles reports INSUFFICIENT_FEATURES all day.
                        val reason = frame.camera.trackingFailureReason
                        if (reason != failureReason) {
                            failureReason = reason
                            updateHint()
                        }
                    }
                }
            }

            setOnGestureListener(
                onSingleTapConfirmed = { motionEvent, _ ->
                    placeModelAt(motionEvent.x, motionEvent.y)
                }
            )
        }

        updateHint()
    }

    /**
     * Find something to anchor to, best surface first.
     *
     * A plane anchor is the only one ARCore keeps re-solving against real geometry, so
     * it is the only one that truly holds still. The fallbacks below trade accuracy for
     * being able to place anything at all on a featureless floor -- fine for judging how
     * a chair looks, not fine for deciding where a pipe is.
     */
    private fun findHit(frame: Frame, xPx: Float, yPx: Float): HitResult? {
        val hits = frame.hitTest(xPx, yPx)

        // 1. A real horizontal plane, tap inside its polygon. Stable.
        hits.firstOrNull { hit ->
            val t = hit.trackable
            t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                t.isPoseInPolygon(hit.hitPose)
        }?.let { return it }

        // 2. Depth map. Works with no plane at all, needs DepthMode.AUTOMATIC.
        hits.firstOrNull { it.trackable is DepthPoint }?.let { return it }

        // 3. A feature point that knows which way the surface faces.
        hits.firstOrNull { hit ->
            val t = hit.trackable
            t is Point && t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
        }?.let { return it }

        // 4. Nothing tracked. Guess ~1.5m out and let ARCore correct it later.
        return frame.hitTestInstantPlacement(xPx, yPx, 1.5f).firstOrNull()
    }

    /** Hit-test the tap, anchor the selected model to whatever we found. */
    private fun placeModelAt(xPx: Float, yPx: Float) {
        val model = selectedModel ?: return

        val frame = binding.sceneView.frame ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val hit = findHit(frame, xPx, yPx) ?: return

        // createAnchor() -- not a fixed Vector3. This is the whole trick.
        val node = AnchorNode(binding.sceneView.engine, hit.createAnchor())

        lifecycleScope.launch {
            val instance = binding.sceneView.modelLoader
                .loadModelInstance("$MODEL_DIR/$model") ?: return@launch

            // No scaleToUnits: glTF authors geometry in metres, so the model already
            // knows its real size. Normalising every model to a 1m longest axis would
            // make a 6cm avocado a metre tall and shrink 3.7m scaffolding to a toy.
            node.addChildNode(
                ModelNode(modelInstance = instance).apply {
                    isEditable = false
                }
            )
            binding.sceneView.addChildNode(node)
            placedNodes += node

            binding.sceneView.planeRenderer.isEnabled = false
            binding.clearButton.visibility = View.VISIBLE
            binding.hintText.setText(R.string.hint_placed)
        }
    }

    private fun clearAll() {
        placedNodes.forEach {
            binding.sceneView.removeChildNode(it)
            it.destroy()
        }
        placedNodes.clear()
        binding.sceneView.planeRenderer.isEnabled = true
        binding.clearButton.visibility = View.GONE
        updateHint()
    }

    // --- Model picker -----------------------------------------------------------

    private fun showModelSheet() {
        val sheet = SheetModelsBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this).apply { setContentView(sheet.root) }

        if (availableModels.isEmpty()) {
            sheet.modelList.addView(sheetLabel(getString(R.string.no_models)))
        } else {
            availableModels.forEach { model ->
                sheet.modelList.addView(
                    MaterialButton(this).apply {
                        text = model.removeSuffix(".glb").removeSuffix(".GLB")
                        isAllCaps = false
                        // Highlight whichever model taps will currently place.
                        if (model == selectedModel) {
                            setIconResource(android.R.drawable.checkbox_on_background)
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 8 }
                        setOnClickListener {
                            selectedModel = model
                            updateHint()
                            dialog.dismiss()
                        }
                    }
                )
            }
        }
        dialog.show()
    }

    private fun sheetLabel(text: String) = android.widget.TextView(this).apply {
        this.text = text
        setPadding(0, 16, 0, 16)
    }

    private fun updateHint() {
        val model = selectedModel
        binding.hintText.text = when {
            model == null -> getString(R.string.no_models)
            planeFound -> getString(R.string.hint_tap, model.removeSuffix(".glb"))
            else -> getString(coachingMessage())
        }
    }

    /** Turn ARCore's failure reason into something a person can act on. */
    private fun coachingMessage() = when (failureReason) {
        TrackingFailureReason.INSUFFICIENT_FEATURES -> R.string.coach_features
        TrackingFailureReason.INSUFFICIENT_LIGHT -> R.string.coach_light
        TrackingFailureReason.EXCESSIVE_MOTION -> R.string.coach_motion
        TrackingFailureReason.CAMERA_UNAVAILABLE -> R.string.coach_camera
        TrackingFailureReason.BAD_STATE -> R.string.coach_bad_state
        else -> R.string.hint_searching
    }
}
