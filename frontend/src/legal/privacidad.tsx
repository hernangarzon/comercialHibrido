import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '../index.css'
import { LEGAL } from './company'
import { LegalLayout, Section } from './LegalLayout'

function Privacidad() {
  return (
    <LegalLayout
      title="Política de privacidad y tratamiento de datos"
      intro={`Esta política explica qué datos personales trata ${LEGAL.product}, para qué los usa, con quién los comparte y cómo puedes ejercer tus derechos. Se rige por la Ley 1581 de 2012, el Decreto 1377 de 2013 y demás normas de protección de datos personales de ${LEGAL.country}.`}
    >
      <Section title="1. Responsable del tratamiento">
        <p>
          <strong>{LEGAL.owner}</strong>, titular del servicio {LEGAL.product}, con domicilio en {LEGAL.country}.
          <br />
          Correo de contacto para asuntos de datos personales: <a className="text-brand-700 underline" href={`mailto:${LEGAL.email}`}>{LEGAL.email}</a>.
        </p>
      </Section>

      <Section title="2. A quién aplica">
        <p>{LEGAL.product} es una plataforma que permite a empresas atender a sus clientes por WhatsApp con un asistente automático y su equipo comercial. Esta política cubre tres grupos de personas:</p>
        <ul>
          <li><strong>Visitantes de este sitio</strong> que solicitan una demostración.</li>
          <li><strong>Usuarios del panel</strong>: personas de las empresas clientes que ingresan para atender conversaciones o configurar el servicio.</li>
          <li>
            <strong>Clientes finales</strong> que escriben por WhatsApp a una empresa que usa {LEGAL.product}. Respecto de estos datos, la empresa a la que escribes es la
            responsable del tratamiento y {LEGAL.product} actúa como <em>encargado</em>, tratándolos solo por cuenta y según las instrucciones de esa empresa.
          </li>
        </ul>
      </Section>

      <Section title="3. Datos que tratamos">
        <ul>
          <li><strong>Solicitudes de demostración:</strong> nombre, empresa, correo, teléfono y el mensaje que escribas.</li>
          <li><strong>Usuarios del panel:</strong> nombre, correo, rol y contraseña (guardada cifrada de forma irreversible, nunca en texto legible).</li>
          <li>
            <strong>Conversaciones de WhatsApp:</strong> número de teléfono, nombre de perfil, el contenido de los mensajes, imágenes o documentos enviados, fechas y
            estados de entrega, y una calificación automática del interés de compra.
          </li>
          <li><strong>Datos técnicos:</strong> dirección IP y registros de uso necesarios para la seguridad y el funcionamiento del servicio.</li>
        </ul>
        <p>No solicitamos datos sensibles. Si los compartes en una conversación, se tratarán solo para atender tu solicitud.</p>
      </Section>

      <Section title="4. Finalidades">
        <ul>
          <li>Responder solicitudes de demostración y preparar propuestas comerciales.</li>
          <li>Prestar el servicio: recibir y responder mensajes de WhatsApp, mostrarlos al equipo de la empresa y generar respuestas automáticas con información aprobada por ella.</li>
          <li>Calcular métricas de atención para la empresa (volumen de conversaciones, tiempos de respuesta).</li>
          <li>Proteger la plataforma contra accesos indebidos, abuso y envíos masivos.</li>
          <li>Cumplir obligaciones legales.</li>
        </ul>
        <p>No vendemos datos personales ni los usamos para publicidad de terceros.</p>
      </Section>

      <Section title="5. Proveedores (encargados) y transferencias internacionales">
        <p>Para prestar el servicio usamos proveedores que tratan datos por nuestra cuenta, algunos con servidores fuera de {LEGAL.country}:</p>
        <ul>
          <li><strong>Meta Platforms (WhatsApp Business):</strong> transporte de los mensajes de WhatsApp.</li>
          <li><strong>Supabase:</strong> base de datos donde se almacena la información del servicio.</li>
          <li><strong>Railway:</strong> servidores donde se ejecuta la aplicación.</li>
          <li>
            <strong>Proveedor de modelos de inteligencia artificial</strong> (actualmente Groq): procesa el texto de las conversaciones para generar las respuestas
            automáticas. No usamos tus conversaciones para entrenar modelos propios.
          </li>
        </ul>
        <p>Al aceptar esta política autorizas la transferencia de tus datos a estos proveedores, que aplican medidas de seguridad acordes con su función.</p>
      </Section>

      <Section title="6. Tus derechos">
        <p>Como titular de los datos puedes:</p>
        <ul>
          <li>Conocer, actualizar y rectificar tus datos.</li>
          <li>Solicitar prueba de la autorización que otorgaste.</li>
          <li>Ser informado sobre el uso que se les ha dado.</li>
          <li>Revocar la autorización y pedir la supresión de tus datos cuando no exista un deber legal o contractual de conservarlos.</li>
          <li>Acceder gratuitamente a tus datos.</li>
          <li>Presentar quejas ante la Superintendencia de Industria y Comercio.</li>
        </ul>
        <p>
          Escríbenos a <a className="text-brand-700 underline" href={`mailto:${LEGAL.email}`}>{LEGAL.email}</a> indicando tu nombre, tu número o correo y lo que
          solicitas. Respondemos consultas en máximo 10 días hábiles y reclamos en máximo 15 días hábiles. Si tu solicitud se refiere a conversaciones con una empresa
          cliente, la trasladaremos a esa empresa, que es la responsable de esos datos.
        </p>
      </Section>

      <Section id="eliminacion" title="7. Cómo solicitar la eliminación de tus datos">
        <ul>
          <li>
            Envía un correo a <a className="text-brand-700 underline" href={`mailto:${LEGAL.email}`}>{LEGAL.email}</a> con el asunto «Eliminación de datos», indicando
            el número de WhatsApp o el correo asociado.
          </li>
          <li>Verificaremos que la solicitud provenga del titular y eliminaremos los datos de nuestros sistemas en máximo 15 días hábiles, salvo los que debamos conservar por ley.</li>
          <li>Te confirmaremos por el mismo medio cuando la eliminación esté completa.</li>
        </ul>
        <p>También puedes dejar de escribir a la empresa en cualquier momento o bloquear su número en WhatsApp.</p>
      </Section>

      <Section title="8. Conservación y seguridad">
        <p>
          Conservamos los datos mientras exista la relación con la empresa cliente o mientras sean necesarios para las finalidades descritas, y luego los eliminamos o
          anonimizamos. Aplicamos medidas como cifrado de contraseñas, conexiones cifradas (HTTPS), control de acceso por empresa y por rol, y verificación de la
          autenticidad de los mensajes que recibimos de WhatsApp.
        </p>
      </Section>

      <Section title="9. Menores de edad">
        <p>El servicio está dirigido a empresas y sus clientes adultos. No recolectamos intencionalmente datos de menores de edad.</p>
      </Section>

      <Section title="10. Cambios a esta política">
        <p>Podemos actualizar esta política. Publicaremos la nueva versión en esta página con su fecha de actualización.</p>
      </Section>
    </LegalLayout>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Privacidad />
  </StrictMode>,
)
