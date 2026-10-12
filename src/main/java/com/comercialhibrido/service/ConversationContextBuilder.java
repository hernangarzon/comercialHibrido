package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.entity.Product;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.dto.openai.OpenAiChatDtos.ChatMessage;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConversationContextBuilder {

    private final MessageRepository messageRepository;
    private final ProductRepository productRepository;

    public List<ChatMessage> construir(Conversation conversation) {
        Company company = conversation.getCompany();
        String customerName = conversation.getCustomer() != null && conversation.getCustomer().getDisplayName() != null
            ? conversation.getCustomer().getDisplayName()
            : "Doctor/a (Cliente)";

        // 1. Obtener productos activos de la empresa desde la base de datos
        List<Product> productos = (company != null && company.getId() != null)
            ? productRepository.findByCompanyIdAndAvailableTrueOrderByNameAsc(company.getId())
            : List.of();

        String catalogoFormateado = formatearCatalogoProductos(productos);
        String systemPrompt = construirSystemPrompt(company, customerName, catalogoFormateado);

        List<ChatMessage> mensajes = new ArrayList<>();
        mensajes.add(new ChatMessage("system", systemPrompt));

        // Si ya se avisó a un asesor, el bot sigue atendiendo sin repetir la escalación.
        if (conversation.getStatus() == ConversationStatus.ESCALADO_PENDIENTE) {
            mensajes.add(new ChatMessage("system",
                "Ya se avisó a un asesor humano que confirmará el pedido. Sigue atendiendo al cliente con normalidad: "
                    + "responde sus dudas y, si pregunta por el asesor, dile que ya fue notificado y lo contactará pronto. "
                    + "No vuelvas a marcar requiereEscalamiento salvo que haya un pedido o reclamo nuevo."));
        }

        // 2. Inyectar resumen histórico previo si existe
        if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
            mensajes.add(new ChatMessage(
                "system",
                "Resumen del pedido o caso registrado para el asesor: " + conversation.getSummary()
            ));
        }

        // 3. Obtener los últimos 15 mensajes en orden cronológico
        List<Message> recientes = messageRepository
            .findTop15ByConversationIdOrderByCreatedAtDesc(conversation.getId());
        Collections.reverse(recientes);

        for (Message m : recientes) {
            String role = m.getSender() == MessageSender.CLIENTE ? "user" : "assistant";
            mensajes.add(new ChatMessage(role, m.getContent()));
        }

        return mensajes;
    }

    /**
     * Contexto para el probador del panel: mismo prompt y catálogo que en producción,
     * con un historial simulado en lugar de una conversación real.
     */
    public List<ChatMessage> construirPrueba(Company company, List<ChatMessage> historial) {
        List<Product> productos = productRepository.findByCompanyIdAndAvailableTrueOrderByNameAsc(company.getId());
        List<ChatMessage> mensajes = new ArrayList<>();
        mensajes.add(new ChatMessage("system",
            construirSystemPrompt(company, "Cliente de prueba", formatearCatalogoProductos(productos))));
        mensajes.addAll(historial);
        return mensajes;
    }

    private String formatearCatalogoProductos(List<Product> productos) {
        if (productos == null || productos.isEmpty()) {
            return "No hay productos cargados en el inventario actualmente.";
        }

        StringBuilder sb = new StringBuilder();
        for (Product p : productos) {
            sb.append("• ").append(p.getName()).append(":\n");
            sb.append("  - Precio: $").append(p.getPrice()).append(" ").append(p.getCurrency()).append("\n");
            if (p.getCategory() != null) sb.append("  - Categoría: ").append(p.getCategory()).append("\n");
            if (p.getMaterial() != null) sb.append("  - Material: ").append(p.getMaterial()).append("\n");
            if (p.getDeliveryTimeDays() != null) sb.append("  - Tiempo de elaboración: ").append(p.getDeliveryTimeDays()).append(" días hábiles\n");
            if (p.getWarrantyMonths() != null) sb.append("  - Garantía: ").append(p.getWarrantyMonths()).append(" meses\n");
            if (p.getDescription() != null) sb.append("  - Detalles/Indicaciones: ").append(p.getDescription()).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    private String construirSystemPrompt(Company company, String customerName, String catalogo) {
        String companyName = (company != null && company.getName() != null) ? company.getName() : "nuestra Empresa";
        String politicasGenerales = (company != null && company.getKnowledgeBase() != null && !company.getKnowledgeBase().isBlank())
            ? company.getKnowledgeBase()
            : "Atención comercial 24/7. Envíos disponibles y múltiples medios de pago.";

        String customPrompt = (company != null && company.getCustomPrompt() != null) 
            ? company.getCustomPrompt() 
            : "Atiende con amabilidad, responde dudas sobre los productos y orienta al cliente a concretar su compra.";

        return """
            Eres el Asesor Comercial Experto de %s atendiendo vía WhatsApp.
            Cliente actual: %s
            
            DIRECTRICES ESPECÍFICAS DE ESTA EMPRESA:
            %s
            
            ==================================================
            CATÁLOGO OFICIAL DE PRODUCTOS (PRECIOS Y TIEMPOS):
            ==================================================
            %s
            ==================================================
            INFORMACIÓN GENERAL, ENVÍOS Y POLÍTICAS:
            ==================================================
            %s
            ==================================================
            
            INSTRUCCIONES CLAVE DE VENTA:
            1. TÚ ATIENDES LA CONVERSACIÓN: responde siempre y lleva la venta lo más lejos que puedas. El cliente nunca debe quedar sin respuesta.
            2. VERACIDAD: Usa ÚNICAMENTE los precios, materiales, tiempos y características del catálogo y la información de arriba. NUNCA inventes productos, precios, plazos ni condiciones. Si no tienes un dato, dilo con honestidad, ofrece lo que sí sabes y sigue ayudando.
            3. TONO: Cercano, profesional, empático y conciso (recuerda que es WhatsApp).
            4. AVANCE COMERCIAL: Responde con claridad y haz una pregunta de avance para concretar: cantidad, fecha, datos de entrega o forma de pago.
            5. ESCALAMIENTO A UN ASESOR HUMANO. Marca "requiereEscalamiento": true ÚNICAMENTE en estos casos:
               a) VENTA CONCRETA: el cliente expresa que quiere comprar o hacer un pedido. Señales claras: dice "quiero pedir", "lo quiero", "me lo llevo", "hagamos el pedido"; indica cantidades o fecha para su pedido; pregunta cómo pagar, a qué cuenta transferir o a dónde enviar; o confirma tras una cotización. Aunque tú respondas sus dudas (precio, plazo, medios de pago), MARCA el escalamiento para que un asesor cierre la venta.
               b) El cliente pide expresamente hablar con una persona.
               c) El cliente presenta un reclamo o una queja.
               En cualquier otro caso (preguntas de precio, dudas, interés general, información que no tienes) NO escales: sigue atendiendo tú.
               Cuando escales, en "respuesta" dile al cliente que un asesor confirmará su pedido en breve y sigue disponible para sus dudas; en "resumenParaAsesor" resume en una o dos frases qué quiere, cantidades, fecha y qué falta. Si no escalas, deja "resumenParaAsesor" vacío.
            6. LEAD SCORE: Califica de 0 a 100 el interés real de compra del cliente.
            """.formatted(companyName, customerName, customPrompt, catalogo, politicasGenerales);
    }
    
}