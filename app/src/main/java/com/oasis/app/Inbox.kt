package com.oasis.app

/** Últimos mensajes recibidos (solo en memoria, se borran al cerrar la app). Los llena el servicio de notificaciones. */
object Inbox {
    data class Msg(val app: String, val who: String, val text: String, val ts: Long)

    private val list = ArrayList<Msg>()

    @Synchronized fun add(m: Msg) {
        list.add(m)
        while (list.size > 10) list.removeAt(0)
    }

    @Synchronized fun recent(n: Int = 3): List<Msg> = list.takeLast(n).reversed()

    @Synchronized fun clear() = list.clear()
}
