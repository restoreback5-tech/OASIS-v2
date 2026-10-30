package com.oasis.app

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppLauncherModule(
    private val context: Context,
    private val sound: SoundModule
) {
    private val pm: PackageManager = context.packageManager
    private var flashlightOn = false

    fun launchApp(appName: String): Boolean {
        val normalized = TextMatch.normalize(appName)

        // 1. Casos especiales con lógica dedicada (texto sin acentos)
        when {
            normalized.contains("linterna") || normalized.contains("flashlight") ->
                return toggleFlashlight()

            normalized.contains("camara") ->
                return launchCameraApp()

            normalized.contains("calculadora") ->
                return launchCalculatorApp()

            normalized.contains("archivos") || normalized.contains("file manager") ->
                return launchFilesApp()

            normalized.contains("ajustes") || normalized.contains("settings") ||
                    normalized.contains("configuracion") ->
                return launchSettingsApp()

            normalized.contains("calendario") || normalized.contains("calendar") ->
                return launchCalendarApp()
        }

        // 2. Apps conocidas (registro central, alias por palabra completa)
        val knownPackage = AppRegistry.packageFor(normalized)
        if (knownPackage != null && launchPackage(knownPackage)) return true

        // 3. Búsqueda por nombre visible (tolera errores del reconocimiento de voz)
        return searchAndLaunchByLabel(normalized)
    }

    private fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                sound.play(R.raw.apps_launch)
                true
            } else false
        } catch (e: Exception) {
            Log.w("AppLauncher", "launchPackage '$packageName' falló: ${e.message}")
            false
        }
    }

    private fun launchCameraApp(): Boolean {
        return try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCalculatorApp(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else {
                // Fallback: buscar por label
                searchAndLaunchByLabel("calculadora")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun launchFilesApp(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else {
                // Fallback: DocumentsUI
                launchPackage("com.google.android.documentsui") ||
                        launchPackage("com.android.documentsui")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun launchSettingsApp(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCalendarApp(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else {
                searchAndLaunchByLabel("calendario")
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Enciende o apaga la linterna. Retorna true si la acción tuvo éxito.
     */
    private fun toggleFlashlight(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: run {
                Toast.makeText(context, "Este dispositivo no tiene linterna", Toast.LENGTH_SHORT).show()
                return false
            }

            val newState = !flashlightOn
            cm.setTorchMode(cameraId, newState)
            flashlightOn = newState
            Toast.makeText(
                context,
                if (newState) "Linterna encendida" else "Linterna apagada",
                Toast.LENGTH_SHORT
            ).show()
            true
        } catch (e: Exception) {
            Log.w("AppLauncher", "Linterna falló: ${e.message}")
            Toast.makeText(context, "No pude controlar la linterna", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Busca entre las apps instaladas: nombre exacto, luego que contenga el texto,
     * y al final el más parecido (p. ej. "netflis" -> "Netflix").
     */
    private fun searchAndLaunchByLabel(name: String): Boolean {
        return try {
            val target = TextMatch.normalize(name)
            if (target.length < 2) return false

            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val labeled = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map { it to TextMatch.normalize(it.loadLabel(pm).toString()) }

            val limit = TextMatch.fuzzyLimit(target.length)
            val match = labeled.firstOrNull { it.second == target }
                ?: labeled.firstOrNull { (_, label) ->
                    target.length >= 3 && label.length >= 3 &&
                        (label.contains(target) || target.contains(label))
                }
                ?: labeled
                    .map { it to TextMatch.levenshtein(it.second, target) }
                    .filter { it.second <= limit }
                    .minByOrNull { it.second }
                    ?.first

            if (match != null) {
                val ai = match.first.activityInfo
                launchComponent(ComponentName(ai.packageName, ai.name))
            } else false
        } catch (e: Exception) {
            Log.w("AppLauncher", "searchAndLaunchByLabel falló: ${e.message}")
            false
        }
    }

    private var drawerDialog: Dialog? = null

    /** MainActivity se suscribe para refrescar las apps fijadas en inicio. */
    var onPinsChanged: (() -> Unit)? = null

    fun launchComponent(cn: ComponentName): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            sound.play(R.raw.apps_launch)
            true
        } catch (e: Exception) {
            Log.w("AppLauncher", "launchComponent falló: ${e.message}")
            false
        }
    }

    private fun togglePin(cn: ComponentName) {
        val msg = when {
            PinnedApps.isPinned(context, cn) -> { PinnedApps.unpin(context, cn); "Quitada de inicio" }
            PinnedApps.pin(context, cn) -> "Fijada en inicio"
            else -> "Inicio lleno (máximo ${PinnedApps.MAX})"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        onPinsChanged?.invoke()
    }

    /** Cajón de aplicaciones: A-Z, con buscador. Se abre con el botón Apps o deslizando hacia arriba. */
    fun showAllApps() {
        if (drawerDialog?.isShowing == true) return
        try {
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != context.packageName }

            val view = LayoutInflater.from(context).inflate(R.layout.dialog_all_apps, null)
            val dialog = Dialog(context, R.style.Theme_OASIS_AppDrawer)
            val appsAdapter = AppListAdapter(
                context = context,
                apps = apps,
                packageManager = pm,
                isPinned = { PinnedApps.isPinned(context, it) },
                onTogglePin = { togglePin(it) },
                onLaunched = { dialog.dismiss() }
            )

            view.findViewById<RecyclerView>(R.id.rv_apps).apply {
                layoutManager = GridLayoutManager(context, 4)
                adapter = appsAdapter
            }
            view.findViewById<EditText>(R.id.et_search_apps).apply {
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { appsAdapter.filter(s?.toString().orEmpty()) }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
                setOnEditorActionListener { _, _, _ -> appsAdapter.launchSingleMatch() }
            }
            view.findViewById<View>(R.id.btn_close_apps).setOnClickListener { dialog.dismiss() }
            view.findViewById<View>(R.id.drawer_dismiss_area).setOnClickListener { dialog.dismiss() }

            dialog.setContentView(view)
            dialog.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            dialog.setOnDismissListener { if (drawerDialog === dialog) drawerDialog = null }
            drawerDialog = dialog

            sound.play(R.raw.apps_launch)
            dialog.show()
        } catch (e: Exception) {
            Log.e("AppLauncher", "showAllApps falló: ${e.message}")
            Toast.makeText(context, "No pude abrir la lista de apps", Toast.LENGTH_SHORT).show()
        }
    }

    /** Cierra el cajón si está abierto (p. ej. al pulsar Inicio). */
    fun dismissAllApps() {
        try { drawerDialog?.dismiss() } catch (_: Exception) {}
        drawerDialog = null
    }
}
