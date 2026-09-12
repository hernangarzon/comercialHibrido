package com.comercialhibrido.domain.enums;

public enum ConversationStatus {

    /** El bot responde automaticamente. Estado por defecto de toda conversacion nueva. */
    BOT_ACTIVO,

    /**
     * El bot detecto una oportunidad de venta o una pregunta que no puede
     * responder con confianza. Ya se notifico al comercial, pero el bot
     * puede seguir respondiendo preguntas de bajo riesgo hasta que el
     * humano tome control explicitamente.
     */
    ESCALADO_PENDIENTE,

    /**
     * El comercial tomo control manual. El bot no genera respuestas
     * automaticas mientras la conversacion este en este estado.
     */
    HUMANO_CONTROL,

    /**
     * La conversación fue cerrada/archivada por el comercial o por finalización de atención.
     * Si el cliente vuelve a escribir en el futuro, se reactiva automáticamente a BOT_ACTIVO.
     */
    ARCHIVADO
}
