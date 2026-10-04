package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.entity.Product;
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

        // 2. Inyectar resumen histórico previo si existe
        if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
            mensajes.add(new ChatMessage(
                "system",
                "Resumen de conversaciones anteriores con este cliente: " + conversation.getSummary()
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
            1. VERACIDAD: Usa ÚNICAMENTE los precios, materiales, tiempos y características del catálogo oficial provisto arriba. NUNCA inventes productos ni precios.
            2. TONO: Cercano, profesional, empático y conciso (recuerda que es WhatsApp).
            3. AVANCE COMERCIAL: Responde la consulta con claridad y haz una pregunta de avance orientada a concretar el pedido o agendar el servicio.
            4. ESCALAMIENTO A HUMANO:
               Marca "requiereEscalamiento": true si el cliente solicita hablar con un asesor humano, pide un presupuesto personalizado no listado o presenta un reclamo.
            5. LEAD SCORE: Califica de 0 a 100 el interés real de compra del cliente.
            """.formatted(companyName, customerName, customPrompt, catalogo, politicasGenerales);
    }
    
}