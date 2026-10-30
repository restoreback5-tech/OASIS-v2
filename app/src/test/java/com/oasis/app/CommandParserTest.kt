package com.oasis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {

    private fun app(text: String) = CommandParser.parse(text)?.params?.get("app")
    private fun contact(text: String) = CommandParser.parse(text)?.params?.get("contact")

    @Test fun abreClaudeConservaElNombre() = assertEquals("claude", app("abre claude"))
    @Test fun abreTelegramConservaElNombre() = assertEquals("telegram", app("abre telegram"))
    @Test fun abreLaCalculadora() = assertEquals("calculadora", app("abre la calculadora"))
    @Test fun abreWhatsapp() = assertEquals("whatsapp", app("abre whatsapp"))
    @Test fun abreLaAplicacionDeNetflix() = assertEquals("netflix", app("abre la aplicación de netflix"))
    @Test fun sinNombreDevuelveDesconocido() = assertEquals("desconocido", app("abre"))

    @Test fun llamaAMariaConservaAcento() = assertEquals("maría", contact("llama a maría"))
    @Test fun llamarAAna() = assertEquals("ana", contact("llamar a ana"))
    @Test fun llamaAMama() = assertEquals("mamá", contact("llama a mamá"))
    @Test fun llamaAMiMama() = assertEquals("mamá", contact("llama a mi mamá"))
    @Test fun llamaANumero() = assertEquals("5512345678", contact("llama al 5512345678"))
    @Test fun numeroDictadoConEspacios() = assertEquals("5512345678", contact("llama al 55 12 34 56 78"))
    @Test fun llamarSinNombreDejaVacio() = assertEquals("", contact("llamar"))

    @Test fun mensajeAMariana() = assertEquals("mariana", contact("enviar mensaje a mariana"))
    @Test fun mandarMensajeAPapa() = assertEquals("papá", contact("mandar mensaje a papá"))

    @Test fun comandoEsCorrecto() {
        assertEquals("open_app", CommandParser.parse("abre claude")?.command)
        assertEquals("call", CommandParser.parse("llama a ana")?.command)
        assertEquals("message", CommandParser.parse("manda mensaje a ana")?.command)
        assertEquals("cancel", CommandParser.parse("cancelar")?.command)
        assertEquals("help", CommandParser.parse("qué puedes hacer")?.command)
        assertEquals("help", CommandParser.parse("ayuda")?.command)
    }

    @Test fun fraseSinComandoEsNula() {
        assertNull(CommandParser.parse("hace mucho calor hoy"))
        assertNull(CommandParser.parse(""))
    }
}
