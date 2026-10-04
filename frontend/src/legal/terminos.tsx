import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '../index.css'
import { LEGAL } from './company'
import { LegalLayout, Section } from './LegalLayout'

function Terminos() {
  return (
    <LegalLayout
      title="Términos de uso"
      intro={`Estos términos regulan el uso de ${LEGAL.product}, servicio prestado por ${LEGAL.owner} (${LEGAL.country}). Al usar el sitio o el panel aceptas estos términos.`}
    >
      <Section title="1. El servicio">
        <p>
          {LEGAL.product} permite a empresas atender a sus clientes por WhatsApp mediante un asistente automático que responde con la información que la empresa
          configura, y un panel para que su equipo comercial tome el control de las conversaciones. Las condiciones comerciales (alcance, precio y vigencia) se
          acuerdan con cada empresa por separado.
        </p>
      </Section>

      <Section title="2. Cuentas y acceso">
        <ul>
          <li>Cada empresa es responsable de quiénes tienen acceso a su panel y de mantener sus contraseñas en reserva.</li>
          <li>Los administradores de la empresa pueden invitar, desactivar y cambiar el rol de sus usuarios.</li>
          <li>Podemos suspender una cuenta ante uso indebido, riesgo de seguridad o incumplimiento de las condiciones comerciales.</li>
        </ul>
      </Section>

      <Section title="3. Responsabilidades de la empresa cliente">
        <ul>
          <li>Cumplir las políticas de WhatsApp Business y de Meta, incluidas las reglas sobre mensajes y plantillas.</li>
          <li>Ser la responsable del tratamiento de los datos de sus clientes y contar con las autorizaciones que exige la ley.</li>
          <li>Mantener actualizada y veraz la información del catálogo y la base de conocimiento que usa el asistente.</li>
          <li>No usar el servicio para spam, fraude, contenido ilegal ni comunicaciones no solicitadas.</li>
        </ul>
      </Section>

      <Section title="4. Respuestas automáticas">
        <p>
          El asistente responde con base en la información que la empresa carga y está instruido para no inventar precios ni condiciones y para pasar a una persona
          las consultas que no puede resolver. Aun así, sus respuestas pueden contener errores; la empresa es responsable de supervisar las conversaciones y de las
          ofertas que finalmente haga a sus clientes.
        </p>
      </Section>

      <Section title="5. Disponibilidad">
        <p>
          Buscamos mantener el servicio disponible de forma continua, pero puede haber interrupciones por mantenimiento, fallas de proveedores (por ejemplo, de
          WhatsApp) o causas ajenas a nuestro control.
        </p>
      </Section>

      <Section title="6. Propiedad intelectual">
        <p>
          El software y la marca {LEGAL.product} son de su titular. La información, el catálogo y las conversaciones de cada empresa le pertenecen a esa empresa.
        </p>
      </Section>

      <Section title="7. Limitación de responsabilidad">
        <p>
          En la medida que permita la ley, no respondemos por pérdidas indirectas, lucro cesante ni por decisiones comerciales tomadas con base en respuestas
          automáticas o métricas del servicio.
        </p>
      </Section>

      <Section title="8. Datos personales">
        <p>
          El tratamiento de datos personales se rige por nuestra <a className="text-brand-700 underline" href="/privacidad">Política de privacidad</a>.
        </p>
      </Section>

      <Section title="9. Cambios y contacto">
        <p>
          Podemos actualizar estos términos y publicaremos la nueva versión en esta página. Para cualquier consulta escríbenos a{' '}
          <a className="text-brand-700 underline" href={`mailto:${LEGAL.email}`}>{LEGAL.email}</a>. Estos términos se rigen por las leyes de {LEGAL.country}.
        </p>
      </Section>
    </LegalLayout>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Terminos />
  </StrictMode>,
)
