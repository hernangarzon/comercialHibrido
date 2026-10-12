package com.comercialhibrido.service;

/** Convenciones para las notas de voz guardadas como mensajes. */
public final class VoiceNotes {

    public static final String MEDIA_TYPE = "AUDIO";
    /** Contenido mientras el audio espera su transcripción. */
    public static final String PENDING = "[Nota de voz: transcribiendo…]";
    /** Contenido cuando no se pudo transcribir (audio vacío, ilegible o falla del proveedor). */
    public static final String FAILED = "[Nota de voz sin transcribir]";

    private VoiceNotes() {}

    public static boolean isTranscribed(String content) {
        return content != null && !content.equals(PENDING) && !content.equals(FAILED);
    }
}
