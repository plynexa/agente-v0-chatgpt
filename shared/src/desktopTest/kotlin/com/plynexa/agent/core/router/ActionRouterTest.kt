package com.plynexa.agent.core.router

import kotlin.test.Test
import kotlin.test.assertEquals

class ActionRouterTest {
    private val router = ActionRouter()

    @Test
    fun routesLocalIntentWithoutExternalAi() {
        assertEquals(ActionRoute.SKILL, router.route("Status do agente").route)
        assertEquals("system-status", router.route("Status do agente").skillId)
        assertEquals(ActionRoute.MEMORY, router.route("Quem é Yasmin?").route)
        assertEquals(ActionRoute.SKILL, router.route("Me lembra amanhã às 15h").route)
        assertEquals(ActionRoute.IMAGE_PROVIDER, router.route("Gere uma imagem").route)
    }
}
