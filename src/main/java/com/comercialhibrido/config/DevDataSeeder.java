package com.comercialhibrido.config;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Customer;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.entity.Product;
import com.comercialhibrido.domain.entity.SalesLead;
import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.DeliveryStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.repository.CustomerRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.ProductRepository;
import com.comercialhibrido.repository.SalesLeadRepository;
import com.comercialhibrido.repository.UserRepository;
import com.comercialhibrido.security.PasswordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Carga datos de demostración en el perfil dev: dos empresas (para probar el
 * aislamiento multiempresa), un usuario por empresa y conversaciones en cada estado.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    public static final String DEMO_PASSWORD = "demo1234";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProductRepository productRepository;
    private final PasswordService passwordService;
    private final SalesLeadRepository salesLeadRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (companyRepository.count() > 0) {
            return;
        }

        Company laboratorio = companyRepository.save(Company.builder()
            .name("Laboratorio Dental Demo")
            .whatsappPhoneNumberId("dev-phone-1")
            .knowledgeBase("Envíos a todo el país en 48 h. Pagos por transferencia o tarjeta. "
                + "Atención humana de lunes a viernes de 8:00 a 18:00.")
            .build());
        Company optica = companyRepository.save(Company.builder()
            .name("Óptica Demo")
            .whatsappPhoneNumberId("dev-phone-2")
            .build());

        crearUsuario(laboratorio, "admin@demo.com", "Carla Comercial", "ADMIN");
        crearUsuario(optica, "otra@demo.com", "Óscar Óptica", "ADMIN");
        crearUsuario(laboratorio, "comercial@demo.com", "Diego Ventas", "COMERCIAL");

        crearProducto(laboratorio, "Corona de zirconio", "Prótesis fija", "Zirconio", "180.00", 5);
        crearProducto(laboratorio, "Placa de descanso", "Ortodoncia", "Acrílico", "65.00", 3);
        crearProducto(laboratorio, "Prótesis removible", "Prótesis removible", "Acrílico", "240.00", 7);

        crearConversacion(laboratorio, "+573001110001", "Dra. Ana Pérez", ConversationStatus.BOT_ACTIVO, 35,
            msg(MessageSender.CLIENTE, "Hola, ¿cuánto cuesta una corona de zirconio?"),
            msg(MessageSender.BOT, "¡Hola Dra. Ana! La corona de zirconio cuesta $180 USD y la entregamos en 5 días hábiles. "
                + "¿Desea que le agende la recogida del modelo?"));
        Conversation luis = crearConversacion(laboratorio, "+573001110002", "Dr. Luis Gómez", ConversationStatus.ESCALADO_PENDIENTE, 85,
            msg(MessageSender.CLIENTE, "Necesito 12 coronas para un caso completo, ¿me hacen precio especial?"),
            msg(MessageSender.BOT, "¡Excelente caso, Dr. Luis! Para pedidos de volumen un asesor le preparará "
                + "una cotización personalizada. Le escribe en unos minutos."));
        luis.setSummary("Odontólogo con un caso de rehabilitación completa: pide 12 coronas de zirconio "
            + "y precio por volumen. Listo para cotizar esta semana.");
        conversationRepository.save(luis);
        crearConversacion(laboratorio, "+573001110003", "Clínica Sonrisa", ConversationStatus.HUMANO_CONTROL, 70,
            msg(MessageSender.CLIENTE, "¿Puedo pagar la mitad ahora y la mitad al recibir?"),
            msg(MessageSender.COMERCIAL, "Claro que sí, le envío los datos para el anticipo."));
        crearConversacion(laboratorio, "+573001110004", "Dr. Pedro Ruiz", ConversationStatus.ARCHIVADO, 20,
            msg(MessageSender.CLIENTE, "Gracias, ya recibí el pedido."),
            msg(MessageSender.BOT, "¡Gracias a usted, Dr. Pedro! Quedamos atentos."));
        // Ventana de 24 h cerrada: el cliente escribió hace 2 días; solo se le puede escribir con plantilla.
        Conversation marcela = crearConversacion(laboratorio, "+573001110006", "Dra. Marcela Ríos", ConversationStatus.HUMANO_CONTROL, 60);
        Instant haceDosDias = Instant.now().minus(Duration.ofDays(2));
        for (Message m : List.of(
            msgEn(MessageSender.CLIENTE, "¿Me envían la cotización de 4 carillas?", haceDosDias),
            msgEn(MessageSender.COMERCIAL, "Claro, la preparo y te la envío.", haceDosDias.plus(Duration.ofMinutes(10))))) {
            m.setConversation(marcela);
            messageRepository.save(m);
        }

        // XSS de prueba: el panel debe mostrarlo como texto, sin ejecutarlo.
        crearConversacion(laboratorio, "+573001110005", "<b>Cliente HTML</b>", ConversationStatus.BOT_ACTIVO, 10,
            msg(MessageSender.CLIENTE, "<img src=x onerror=\"alert('xss')\"> hola"));

        crearConversacion(optica, "+573002220001", "Cliente de la Óptica", ConversationStatus.ESCALADO_PENDIENTE, 90,
            msg(MessageSender.CLIENTE, "Quiero 3 pares de lentes progresivos."));

        sembrarHistorial(laboratorio);

        salesLeadRepository.save(SalesLead.builder().name("Marcela Pinto").companyName("Ortodoncia Pinto")
            .email("marcela@ortopinto.com").phone("+57 310 555 1234")
            .message("Tenemos 3 sedes y queremos automatizar las cotizaciones por WhatsApp.").build());
        salesLeadRepository.save(SalesLead.builder().name("Jorge Salas").companyName("Ferretería El Tornillo")
            .email("jorge@eltornillo.co").phone("+57 301 555 9876").status(SalesLead.CONTACTADA).build());

        log.info("Datos demo cargados. Login (clave {}): admin@demo.com y comercial@demo.com (Laboratorio), otra@demo.com (Óptica).",
            DEMO_PASSWORD);
    }

    private void crearUsuario(Company company, String email, String name, String role) {
        userRepository.save(User.builder()
            .company(company)
            .email(email)
            .name(name)
            .role(role)
            .passwordHash(passwordService.hashPassword(DEMO_PASSWORD))
            .build());
    }

    private void crearProducto(Company company, String name, String category, String material,
                               String price, int deliveryDays) {
        productRepository.save(Product.builder()
            .company(company)
            .name(name)
            .category(category)
            .material(material)
            .price(new BigDecimal(price))
            .deliveryTimeDays(deliveryDays)
            .build());
    }

    private Conversation crearConversacion(Company company, String phone, String name, ConversationStatus status,
                                   int leadScore, Message... mensajes) {
        Customer customer = customerRepository.save(Customer.builder()
            .phoneNumber(phone)
            .displayName(name)
            .build());
        Conversation conversation = conversationRepository.save(Conversation.builder()
            .company(company)
            .customer(customer)
            .status(status)
            .leadScore(leadScore)
            .build());
        for (Message m : mensajes) {
            m.setConversation(conversation);
            messageRepository.save(m);
        }
        return conversation;
    }

    private static final String[] NOMBRES = {
        "Dra. Sofía Ramírez", "Dr. Andrés Castillo", "Clínica Dental Norte", "Dra. Valentina Ruiz", "Dr. Camilo Herrera",
        "Odontología Integral", "Dra. Laura Méndez", "Dr. Felipe Ortiz", "Centro Oral Sonríe", "Dra. Paula Jiménez",
        "Dr. Mateo Vargas", "Dra. Daniela Torres", "Clínica Bucal Sur", "Dr. Santiago Rojas", "Dra. Natalia Gil",
    };
    private static final String[] PREGUNTAS = {
        "Hola, ¿cuánto cuesta una corona de zirconio?",
        "¿Hacen placas de descanso? ¿En cuánto tiempo?",
        "Necesito cotizar una prótesis removible",
        "¿Tienen envíos a Medellín?",
        "¿Qué garantía tienen las coronas?",
        "Quiero hacer un pedido grande para mi clínica",
        "¿Aceptan pago con tarjeta?",
    };
    private static final String[] RESPUESTAS_BOT = {
        "¡Hola! La corona de zirconio cuesta $180 USD y está lista en 5 días hábiles. ¿Le agendo la recogida?",
        "Sí, la placa de descanso cuesta $65 USD y se entrega en 3 días hábiles.",
        "Con gusto. La prótesis removible parte de $240 USD; ¿me comparte el caso para afinar la cotización?",
        "Enviamos a todo el país en 48 horas. ¿A qué dirección lo despachamos?",
        "Todas nuestras coronas tienen 12 meses de garantía.",
        "¡Excelente! Para pedidos de volumen un asesor le prepara una cotización especial.",
        "Sí, recibimos transferencia y tarjeta.",
    };

    /**
     * 30 días de actividad simulada (determinística) para que el dashboard tenga
     * historia en una demo: unas 3 conversaciones por día, ~30 % escaladas y la
     * mayoría respondidas por un asesor entre 2 y 40 minutos después.
     */
    private void sembrarHistorial(Company company) {
        Random rnd = new Random(42);
        Instant ahora = Instant.now();
        int n = 0;
        for (int dia = 29; dia >= 0; dia--) {
            int conversaciones = 1 + rnd.nextInt(5);
            for (int i = 0; i < conversaciones; i++, n++) {
                Instant inicio = ahora.minus(Duration.ofDays(dia))
                    .minus(Duration.ofMinutes(30 + rnd.nextInt(dia == 0 ? 300 : 600)));
                int pregunta = rnd.nextInt(PREGUNTAS.length);
                boolean escalada = pregunta == 5 || rnd.nextInt(100) < 25;
                boolean respondida = escalada && rnd.nextInt(100) < 85;
                boolean reciente = dia <= 1;
                int score = escalada ? 65 + rnd.nextInt(35) : 10 + rnd.nextInt(60);

                List<Message> mensajes = new ArrayList<>();
                mensajes.add(msgEn(MessageSender.CLIENTE, PREGUNTAS[pregunta], inicio));
                Instant t = inicio.plus(Duration.ofSeconds(20 + rnd.nextInt(40)));
                mensajes.add(msgEn(MessageSender.BOT, RESPUESTAS_BOT[pregunta], t));
                if (rnd.nextBoolean()) {
                    t = t.plus(Duration.ofMinutes(1 + rnd.nextInt(8)));
                    mensajes.add(msgEn(MessageSender.CLIENTE, "Perfecto, ¿y el tiempo de entrega?", t));
                    t = t.plus(Duration.ofSeconds(30));
                    mensajes.add(msgEn(MessageSender.BOT, "Entre 3 y 7 días hábiles según el trabajo.", t));
                }
                Instant escaladaEn = escalada ? t.plus(Duration.ofSeconds(5)) : null;
                if (respondida) {
                    t = escaladaEn.plus(Duration.ofMinutes(2 + rnd.nextInt(38)));
                    mensajes.add(msgEn(MessageSender.COMERCIAL, "Hola, soy Carla del equipo comercial. Ya reviso su caso.", t));
                }

                ConversationStatus estado = !reciente ? ConversationStatus.ARCHIVADO
                    : respondida ? ConversationStatus.HUMANO_CONTROL
                    : escalada ? ConversationStatus.ESCALADO_PENDIENTE
                    : ConversationStatus.BOT_ACTIVO;

                Customer customer = customerRepository.save(Customer.builder()
                    .phoneNumber(String.format("+57300555%04d", n))
                    .displayName(NOMBRES[n % NOMBRES.length])
                    .build());
                Conversation conversation = conversationRepository.save(Conversation.builder()
                    .company(company)
                    .customer(customer)
                    .status(estado)
                    .leadScore(score)
                    .escalatedAt(escaladaEn)
                    .createdAt(inicio)
                    .updatedAt(t)
                    .build());
                for (Message m : mensajes) {
                    m.setConversation(conversation);
                    messageRepository.save(m);
                }
            }
        }
    }

    private static Message msgEn(MessageSender sender, String content, Instant at) {
        Message m = msg(sender, content);
        m.setCreatedAt(at);
        return m;
    }

    private static Message msg(MessageSender sender, String content) {
        return Message.builder()
            .sender(sender)
            .content(content)
            .deliveryStatus(sender == MessageSender.CLIENTE ? null : DeliveryStatus.DELIVERED)
            .build();
    }
}
