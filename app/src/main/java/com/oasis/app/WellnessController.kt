package com.oasis.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Calendar

/** Tarjetas de la página Widgets: seguridad, pasos, hábitos, medicinas, lista del súper, cumpleaños y chat para probar la IA. */
class WellnessController(private val act: AppCompatActivity) {

    companion object {
        const val REQ_NOTIF = 207
        val CELLS = listOf(
            R.id.w_cell_safety to "Seguridad y ayuda (SOS)",
            R.id.w_cell_steps to "Pasos",
            R.id.w_cell_habits to "Hábitos",
            R.id.w_cell_meds to "Medicinas",
            R.id.w_cell_list to "Lista del súper",
            R.id.w_cell_bday to "Cumpleaños",
            R.id.w_cell_ai to "Prueba la IA"
        )
    }

    private val main get() = act as? MainActivity
    private var fg = 0xFF1C1C1E.toInt()
    private var steps: StepCounter? = null
    private val d = act.resources.displayMetrics.density
    private val chat = StringBuilder()

    private fun <T : View> v(id: Int): T = act.findViewById(id)
    private fun tv(id: Int): TextView = act.findViewById(id)

    private fun stepPermOk() = Build.VERSION.SDK_INT < 29 || Safety.has(act, Manifest.permission.ACTIVITY_RECOGNITION)

    // ====================== ciclo de vida ======================
    fun bind() {
        bindSafety(); bindSteps(); bindHabits(); bindMeds(); bindList(); bindAi()
        refreshAll()
    }

    fun onResume() {
        refreshAll()
        if (StepCounter.available(act) && stepPermOk()) {
            if (steps == null) steps = StepCounter(act) { n -> act.runOnUiThread { showSteps(n) } }
            steps?.start()
        }
        Safety.checkBattery(act)
    }

    fun onPause() { steps?.stop() }

    fun restyle(fg: Int, accent: Int) {
        this.fg = fg
        v<ProgressRing>(R.id.w_steps_ring).let { it.ink = fg; it.accent = accent }
        refreshAll()
    }

    fun onPermissionResult(code: Int) {
        if (code == Safety.REQ_ACTIVITY) { refreshSteps(); onResume() }
        if (code == Safety.REQ_SMS) Toast.makeText(act, "Listo. Vuelve a tocar el botón.", Toast.LENGTH_SHORT).show()
    }

    private fun refreshAll() { refreshSteps(); refreshHabits(); refreshMeds(); refreshList(); refreshBirthdays(); refreshSafetyLabels() }

    // ====================== filas ======================
    private fun row(text: String, onClick: () -> Unit, onLong: (() -> Unit)? = null): TextView = TextView(act).apply {
        this.text = text; textSize = 16f; setTextColor(fg)
        setPadding((4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt())
        setOnClickListener { onClick() }
        if (onLong != null) setOnLongClickListener { onLong(); true }
        Fx.pressable(this, 0.98f)
    }

    private fun askText(title: String, hint: String, type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, ok: (String) -> Unit) {
        val et = EditText(act).apply { this.hint = hint; inputType = type; setSingleLine() }
        val box = LinearLayout(act).apply { setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0); addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        AlertDialog.Builder(act).setTitle(title).setView(box)
            .setPositiveButton("Guardar") { _, _ -> et.text.toString().trim().takeIf { it.isNotEmpty() }?.let(ok) }
            .setNegativeButton("Cancelar", null).show()
    }

    // ====================== seguridad ======================
    private fun bindSafety() {
        tv(R.id.w_sos).apply {
            setTextColor(0xFFFFFFFF.toInt()); backgroundTintList = ColorStateList.valueOf(0xFFE53935.toInt())
            setOnClickListener { SafetyUi.runSos(act) { n -> main?.dial(n) } }
        }
        tv(R.id.w_loc).setOnClickListener { SafetyUi.shareLocation(act) }
        tv(R.id.w_home).apply {
            setOnClickListener { SafetyUi.goHome(act) }
            setOnLongClickListener { SafetyUi.editHome(act); true }
        }
        tv(R.id.w_medical).setOnClickListener { SafetyUi.showMedical(act) }
        tv(R.id.w_breathe).setOnClickListener { Breathing.show(act) { main?.say(it) } }
        tv(R.id.w_torch).setOnClickListener {
            if (Siren.isTorchOn()) Siren.stopTorchSos() else Siren.startTorchSos(act)
            v<View>(R.id.w_torch).postDelayed({ refreshSafetyLabels() }, 300)
        }
        tv(R.id.w_siren).setOnClickListener {
            if (Siren.isSirenOn()) Siren.stopSiren() else Siren.startSiren()
            refreshSafetyLabels()
        }
        tv(R.id.w_contacts).setOnClickListener { SafetyUi.editContacts(act) }
        tv(R.id.w_pin).setOnClickListener { SafetyUi.setPinFlow(act) { refreshSafetyLabels() } }
        listOf(R.id.w_sos, R.id.w_loc, R.id.w_home, R.id.w_medical, R.id.w_breathe, R.id.w_torch, R.id.w_siren, R.id.w_contacts, R.id.w_pin)
            .forEach { Fx.pressable(v(it), 0.94f) }
    }

    private fun refreshSafetyLabels() {
        tv(R.id.w_torch).text = if (Siren.isTorchOn()) "🔦\nApagar luz" else "🔦\nLuz SOS"
        tv(R.id.w_siren).text = if (Siren.isSirenOn()) "🔇\nApagar sirena" else "🔊\nSirena"
        tv(R.id.w_pin).text = if (CaregiverPin.isSet(act)) "🔒\nPIN activo" else "🔒\nPIN cuidador"
    }

    // ====================== pasos ======================
    private fun bindSteps() {
        tv(R.id.w_tv_steps_sub).setOnClickListener {
            askText("Meta de pasos al día", "Ej. 6000", InputType.TYPE_CLASS_NUMBER) { s ->
                s.toIntOrNull()?.let { StepCounter.setGoal(act, it); refreshSteps() }
            }
        }
        tv(R.id.w_steps_enable).setOnClickListener {
            ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), Safety.REQ_ACTIVITY)
        }
    }

    private fun refreshSteps() {
        val sensor = StepCounter.available(act)
        tv(R.id.w_steps_enable).visibility = if (sensor && !stepPermOk()) View.VISIBLE else View.GONE
        if (!sensor) { tv(R.id.w_tv_steps).text = "Sin contador"; tv(R.id.w_tv_steps_sub).text = "Este teléfono no tiene sensor de pasos"; return }
        showSteps(StepCounter.lastKnown(act))
    }

    private fun showSteps(n: Int) {
        val goal = StepCounter.goal(act)
        val ring = v<ProgressRing>(R.id.w_steps_ring)
        ring.big = n.toString(); ring.small = "pasos"
        ring.setFraction(n / goal.toFloat())
        tv(R.id.w_tv_steps).text = "$n pasos"
        tv(R.id.w_tv_steps_sub).text = "Meta $goal · ${(n * 100 / goal).coerceAtMost(999)}% · toca para cambiarla"
    }

    // ====================== hábitos ======================
    private fun bindHabits() {
        tv(R.id.w_habit_add).setOnClickListener { askText("Nuevo hábito", "Ej. Leer 10 minutos") { HabitStore.add(act, it); refreshHabits() } }
    }

    private fun refreshHabits() {
        val box = v<LinearLayout>(R.id.w_habits_box)
        box.removeAllViews()
        HabitStore.list(act).forEach { h ->
            val done = HabitStore.doneToday(act, h)
            val streak = HabitStore.streak(act, h)
            box.addView(row((if (done) "✅  " else "⬜  ") + h + (if (streak > 0) "   🔥 $streak" else ""),
                { HabitStore.toggleToday(act, h); refreshHabits() },
                { AlertDialog.Builder(act).setTitle("¿Quitar «$h»?").setPositiveButton("Quitar") { _, _ -> HabitStore.remove(act, h); refreshHabits() }.setNegativeButton("No", null).show() }))
        }
        tv(R.id.w_habit_add).setTextColor(fg)
    }

    // ====================== medicinas ======================
    private fun bindMeds() {
        tv(R.id.w_med_add).setOnClickListener {
            askText("¿Qué medicina?", "Ej. Pastilla de la presión") { name ->
                val c = Calendar.getInstance()
                TimePickerDialog(act, { _, h, m ->
                    val med = MedStore.add(act, name, h, m)
                    MedScheduler.schedule(act, med)
                    if (Build.VERSION.SDK_INT >= 33 && !Safety.has(act, Manifest.permission.POST_NOTIFICATIONS))
                        ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
                    if (Safety.contacts(act).isEmpty()) Toast.makeText(act, "Tip: agrega un contacto SOS para avisarle si olvidas una dosis", Toast.LENGTH_LONG).show()
                    refreshMeds()
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
            }
        }
    }

    private fun refreshMeds() {
        val box = v<LinearLayout>(R.id.w_meds_box)
        box.removeAllViews()
        val meds = MedStore.list(act)
        if (meds.isEmpty()) box.addView(TextView(act).apply { text = "Aún no hay medicinas."; setTextColor(fg); textSize = 14f; alpha = 0.7f })
        meds.forEach { m ->
            val taken = MedStore.isTakenToday(act, m.id)
            box.addView(row("%02d:%02d   💊 %s".format(m.hour, m.minute, m.name) + (if (taken) "   ✔ tomada hoy" else ""),
                {
                    if (!taken) { MedStore.markTaken(act, m.id); MedScheduler.cancelMissed(act, m.id); refreshMeds() }
                },
                { AlertDialog.Builder(act).setTitle("¿Quitar «${m.name}»?").setPositiveButton("Quitar") { _, _ -> MedScheduler.cancel(act, m); MedStore.remove(act, m.id); refreshMeds() }.setNegativeButton("No", null).show() }))
        }
        tv(R.id.w_med_add).setTextColor(fg)
    }

    // ====================== lista del súper ======================
    private fun bindList() {
        tv(R.id.w_list_add).setOnClickListener { askText("Agregar a la lista", "Ej. Leche") { ListStore.add(act, it); refreshList() } }
    }

    private fun refreshList() {
        val box = v<LinearLayout>(R.id.w_list_box)
        box.removeAllViews()
        val items = ListStore.items(act)
        if (items.isEmpty()) box.addView(TextView(act).apply { text = "La lista está vacía."; setTextColor(fg); textSize = 14f; alpha = 0.7f })
        items.forEach { it -> box.addView(row("•  $it", { ListStore.remove(act, it); refreshList() })) }
        tv(R.id.w_list_add).setTextColor(fg)
    }

    // ====================== cumpleaños ======================
    private fun refreshBirthdays() {
        Thread {
            val list = Birthdays.upcoming(act)
            act.runOnUiThread {
                val box = v<LinearLayout>(R.id.w_bday_box)
                box.removeAllViews()
                list.forEach { b -> box.addView(TextView(act).apply { text = "🎂  " + Birthdays.describe(b); textSize = 15f; setTextColor(fg); setPadding(4, (8 * d).toInt(), 4, (8 * d).toInt()) }) }
                tv(R.id.w_bday_empty).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    // ====================== chat para probar la IA ======================
    private fun bindAi() {
        val input = v<EditText>(R.id.w_ai_input)
        fun send() {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return
            input.setText("")
            aiLog("Tú: $q")
            val engine = main?.assistantEngine() ?: return
            engine.handle(q) { r ->
                aiLog("OASIS: ${r.text}" + (r.act?.let { "\n   ↳ acción: ${describe(it)} (no se ejecuta aquí)" } ?: ""))
            }
        }
        tv(R.id.w_ai_send).setOnClickListener { send() }
        input.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
        tv(R.id.w_ai_test).setOnClickListener {
            aiLog("Probando la conexión…")
            main?.assistantEngine()?.ping { aiLog(it) }
        }
        tv(R.id.w_ai_clear).setOnClickListener { chat.setLength(0); tv(R.id.w_ai_log).text = "" }
    }

    private fun describe(a: AssistantEngine.Act): String = when (a) {
        is AssistantEngine.Act.OpenApp -> "abrir ${a.app}"
        is AssistantEngine.Act.Call -> "llamar a ${a.contact}"
        is AssistantEngine.Act.Message -> "mensaje a ${a.contact}"
        is AssistantEngine.Act.Timer -> "temporizador de ${a.minutes} min"
        is AssistantEngine.Act.AlarmAt -> "alarma ${a.hour}:${"%02d".format(a.minute)} ${a.label}"
        is AssistantEngine.Act.SendSms -> "SMS a ${a.name}: ${a.body}"
        else -> a::class.java.simpleName
    }

    private fun aiLog(line: String) {
        act.runOnUiThread {
            chat.append(line).append("\n\n")
            tv(R.id.w_ai_log).text = chat.toString().trimEnd()
            (tv(R.id.w_ai_log).parent as? ScrollView)?.post { (tv(R.id.w_ai_log).parent as ScrollView).fullScroll(View.FOCUS_DOWN) }
        }
    }
}
