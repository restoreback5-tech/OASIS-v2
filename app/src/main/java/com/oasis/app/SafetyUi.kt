package com.oasis.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.CountDownTimer
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Pantallas de seguridad: SOS, contactos de emergencia, ubicación, casa, datos médicos y cuenta regresiva de caídas. */
object SafetyUi {

    private fun dp(a: Activity, v: Int) = (v * a.resources.displayMetrics.density).toInt()

    private fun field(a: Activity, hint: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(a).apply { this.hint = hint; setText(value); inputType = type; setSingleLine(false) }

    private fun form(a: Activity, vararg fields: EditText): ScrollView {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(a, 20), dp(a, 8), dp(a, 20), 0) }
        fields.forEach { box.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        return ScrollView(a).apply { addView(box) }
    }

    // ---------- contactos de emergencia ----------
    fun editContacts(a: Activity, onDone: () -> Unit = {}) {
        val cur = Safety.contacts(a)
        val names = (0 until 3).map { field(a, "Nombre ${it + 1}" + (if (it == 0) " (cuidador)" else ""), cur.getOrNull(it)?.name ?: "") }
        val nums = (0 until 3).map { field(a, "Teléfono ${it + 1}", cur.getOrNull(it)?.number ?: "", InputType.TYPE_CLASS_PHONE) }
        val all = names.indices.flatMap { listOf(names[it], nums[it]) }.toTypedArray()
        AlertDialog.Builder(a).setTitle("Contactos de emergencia")
            .setMessage("A ellos llegará tu ubicación cuando pidas ayuda. El primero también recibe avisos de medicinas y batería.")
            .setView(form(a, *all))
            .setPositiveButton("Guardar") { _, _ ->
                Safety.saveContacts(a, names.indices.map { Safety.Contact(names[it].text.toString().trim(), nums[it].text.toString().trim()) })
                onDone()
            }.setNegativeButton("Cancelar", null).show()
    }

    // ---------- SOS ----------
    /** Confirma y pide ayuda: SMS con ubicación a todos, llamada al primero, luz y sirena. */
    fun runSos(a: Activity, call: (String) -> Unit) {
        val list = Safety.contacts(a)
        if (list.isEmpty()) {
            Toast.makeText(a, "Primero agrega tus contactos de emergencia", Toast.LENGTH_LONG).show()
            editContacts(a)
            return
        }
        AlertDialog.Builder(a).setTitle("🆘 ¿Pedir ayuda ahora?")
            .setMessage("Se avisará a ${list.joinToString(", ") { it.name.ifBlank { it.number } }} con tu ubicación y se llamará a ${list.first().name.ifBlank { list.first().number }}.")
            .setPositiveButton("SÍ, PEDIR AYUDA") { _, _ -> doSos(a, call) }
            .setNegativeButton("Cancelar", null).show()
    }

    /** Sin confirmar (la usa la cuenta regresiva de caídas). */
    fun doSos(a: Activity, call: (String) -> Unit) {
        val list = Safety.contacts(a)
        if (list.isEmpty()) return
        if (!Safety.ensureSms(a)) { Toast.makeText(a, "Permite enviar SMS y vuelve a tocar SOS", Toast.LENGTH_LONG).show(); return }
        val sent = Safety.sendSosSms(a)
        Toast.makeText(a, if (sent > 0) "Aviso enviado a $sent contacto(s)" else "No se pudo enviar el SMS", Toast.LENGTH_LONG).show()
        Siren.startTorchSos(a)
        call(list.first().number)
    }

    // ---------- ubicación y casa ----------
    fun shareLocation(a: Activity) {
        val link = Safety.locationLink(a)
        if (link == null) {
            Toast.makeText(a, "No tengo tu ubicación. Permite la ubicación y activa el GPS.", Toast.LENGTH_LONG).show()
            androidx.core.app.ActivityCompat.requestPermissions(a, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), Safety.REQ_LOCATION)
            return
        }
        a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Estoy aquí: $link"), "Compartir mi ubicación"))
    }

    fun goHome(a: Activity) {
        val prefs = a.getSharedPreferences("oasis_settings", Activity.MODE_PRIVATE)
        val addr = prefs.getString("home_address", "") ?: ""
        if (addr.isBlank()) { editHome(a) { goHome(a) }; return }
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(addr))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { a.startActivity(nav) } catch (e: Exception) {
            try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(addr))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e2: Exception) { Toast.makeText(a, "No encontré una app de mapas", Toast.LENGTH_LONG).show() }
        }
    }

    fun editHome(a: Activity, onDone: () -> Unit = {}) {
        val prefs = a.getSharedPreferences("oasis_settings", Activity.MODE_PRIVATE)
        val et = field(a, "Calle, número, colonia, ciudad", prefs.getString("home_address", "") ?: "")
        AlertDialog.Builder(a).setTitle("¿Dónde está tu casa?").setView(form(a, et))
            .setPositiveButton("Guardar") { _, _ -> prefs.edit().putString("home_address", et.text.toString().trim()).apply(); onDone() }
            .setNegativeButton("Cancelar", null).show()
    }

    // ---------- datos médicos ----------
    fun showMedical(a: Activity) {
        val t = Safety.medicalText(a)
        AlertDialog.Builder(a).setTitle("🩺 Mis datos médicos")
            .setMessage(if (t.isBlank()) "Todavía no hay datos. Toca «Editar» para agregarlos." else t)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Editar") { _, _ -> editMedical(a) }.show()
    }

    fun editMedical(a: Activity) {
        val p = a.getSharedPreferences("oasis_settings", Activity.MODE_PRIVATE)
        val fields = Safety.MED_FIELDS.map { (k, label) -> k to field(a, label, p.getString(k, "") ?: "") }
        AlertDialog.Builder(a).setTitle("Datos médicos").setView(form(a, *fields.map { it.second }.toTypedArray()))
            .setPositiveButton("Guardar") { _, _ ->
                val e = p.edit()
                fields.forEach { (k, et) -> e.putString(k, et.text.toString().trim()) }
                e.apply()
            }.setNegativeButton("Cancelar", null).show()
    }

    // ---------- PIN de cuidador ----------
    fun setPinFlow(a: Activity, onDone: () -> Unit = {}) {
        CaregiverPin.require(a, onOk = {
            val et = field(a, "Nuevo PIN (4 a 8 números)", "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            AlertDialog.Builder(a).setTitle("Crear PIN de cuidador").setMessage("Protege Ajustes y desinstalar apps.").setView(form(a, et))
                .setPositiveButton("Guardar") { _, _ ->
                    val pin = et.text.toString().trim()
                    if (pin.length in 4..8) { CaregiverPin.set(a, pin); Toast.makeText(a, "PIN guardado", Toast.LENGTH_SHORT).show(); onDone() }
                    else Toast.makeText(a, "El PIN debe tener de 4 a 8 números", Toast.LENGTH_LONG).show()
                }.setNegativeButton("Cancelar", null).show()
        })
    }

    // ---------- caída detectada ----------
    private var fallDialog: AlertDialog? = null

    fun fallCountdown(a: Activity, call: (String) -> Unit) {
        if (fallDialog?.isShowing == true || Safety.contacts(a).isEmpty()) return
        val msg = TextView(a).apply { textSize = 18f; setPadding(dp(a, 24), dp(a, 16), dp(a, 24), 0) }
        var timer: CountDownTimer? = null
        val d = AlertDialog.Builder(a).setTitle("⚠️ ¿Te caíste?").setView(msg).setCancelable(false)
            .setPositiveButton("ESTOY BIEN") { _, _ -> timer?.cancel() }
            .create()
        fallDialog = d
        d.show()
        timer = object : CountDownTimer(30_000, 1000) {
            override fun onTick(ms: Long) { msg.text = "Si no respondes, aviso a tus contactos en ${ms / 1000} segundos."; Siren.tick() }
            override fun onFinish() { if (d.isShowing) { d.dismiss(); doSos(a, call) } }
        }.start()
        d.setOnDismissListener { timer?.cancel() }
    }
}
