import { FormEvent, useState } from "react";
import { Check } from "lucide-react";
import { EstadoPrestamo, Prestamo, PrestamoInput } from "../types";
import { mensajeDeError } from "../graphql/clienteGraphQL";
import { AreaTexto } from "./ui/AreaTexto";
import { Banner } from "./ui/Banner";
import { Boton } from "./ui/Boton";
import { Campo } from "./ui/Campo";
import { Selector } from "./ui/Selector";

const ESTADOS: { valor: EstadoPrestamo; texto: string }[] = [
  { valor: "ACTIVO", texto: "Activo" },
  { valor: "DEVUELTO", texto: "Devuelto" },
  { valor: "CANCELADO", texto: "Cancelado" }
];

function valoresIniciales(prestamo?: Prestamo) {
  const ahora = new Date();
  const fechaLocal = new Date(ahora.getTime() - ahora.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
  return {
    codigo: prestamo?.codigo ?? "", codigoEjemplar: prestamo?.codigoEjemplar ?? "",
    prestatario: prestamo?.prestatario ?? "", fechaPrestamo: prestamo?.fechaPrestamo.slice(0, 16) ?? fechaLocal,
    diasPrestamo: String(prestamo?.diasPrestamo ?? 7), multa: String(prestamo?.multa ?? 0),
    estado: prestamo?.estado ?? "ACTIVO" as EstadoPrestamo, observaciones: prestamo?.observaciones ?? ""
  };
}

export default function FormularioPrestamo({ inicial, alGuardar }: {
  inicial?: Prestamo; alGuardar: (input: PrestamoInput) => Promise<void>;
}) {
  const [datos, setDatos] = useState(() => valoresIniciales(inicial));
  const [error, setError] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const cambiar = (nombre: keyof typeof datos, valor: string) => setDatos((actual) => ({ ...actual, [nombre]: valor }));

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const dias = Number(datos.diasPrestamo);
    const multa = Number(datos.multa);
    if (!datos.codigo.trim() || !datos.codigoEjemplar.trim() || !datos.prestatario.trim()) {
      setError("Completa el código, el ejemplar y el prestatario.");
      return;
    }
    if (!/^PR-\d{3,6}$/i.test(datos.codigo.trim())) {
      setError("El código del préstamo debe tener el formato PR-000.");
      return;
    }
    if (!/^EJ-\d{3,6}$/i.test(datos.codigoEjemplar.trim())) {
      setError("El código del ejemplar debe tener el formato EJ-000.");
      return;
    }
    if (new Date(datos.fechaPrestamo).getTime() > Date.now()) {
      setError("La fecha del préstamo no puede ser futura.");
      return;
    }
    if (!Number.isInteger(dias) || dias < 1 || dias > 365 || !Number.isFinite(multa) || multa < 0) {
      setError("Los días deben ser un entero entre 1 y 365 y la multa debe ser mayor o igual a cero.");
      return;
    }
    setError(null);
    setOcupado(true);
    try {
      await alGuardar({
        ...datos, codigo: datos.codigo.trim().toUpperCase(), codigoEjemplar: datos.codigoEjemplar.trim().toUpperCase(),
        prestatario: datos.prestatario.trim(), diasPrestamo: dias, multa,
        fechaPrestamo: datos.fechaPrestamo.length === 16 ? `${datos.fechaPrestamo}:00` : datos.fechaPrestamo,
        observaciones: datos.observaciones.trim() || null, version: inicial?.version ?? null
      });
      if (!inicial) setDatos(valoresIniciales());
    } catch (fallo) {
      setError(mensajeDeError(fallo));
    } finally {
      setOcupado(false);
    }
  }

  return (
    <form className="panel formulario-secciones" onSubmit={guardar}>
      <section className="seccion-formulario">
        <h2>Datos del préstamo</h2>
        <div className="rejilla-campos">
          <Campo etiqueta="Código del préstamo" value={datos.codigo} onChange={(e) => cambiar("codigo", e.target.value)}
                 maxLength={9} placeholder="PR-001" required readOnly={!!inicial} disabled={ocupado} />
          <Campo etiqueta="Código del ejemplar" value={datos.codigoEjemplar} onChange={(e) => cambiar("codigoEjemplar", e.target.value)}
                 placeholder="EJ-001" required readOnly={!!inicial} disabled={ocupado} ayuda="Usa el código de un ejemplar registrado." />
          <Campo etiqueta="Prestatario" value={datos.prestatario} onChange={(e) => cambiar("prestatario", e.target.value)}
                 maxLength={120} required disabled={ocupado} />
          <Campo etiqueta="Fecha del préstamo" type="datetime-local" value={datos.fechaPrestamo}
                 onChange={(e) => cambiar("fechaPrestamo", e.target.value)} required disabled={ocupado} />
          <Campo etiqueta="Días del préstamo" type="number" min={1} max={365} step={1} value={datos.diasPrestamo}
                 onChange={(e) => cambiar("diasPrestamo", e.target.value)} required disabled={ocupado} />
          <Campo etiqueta="Multa" type="number" min={0} step="0.01" prefijo="$" value={datos.multa}
                 onChange={(e) => cambiar("multa", e.target.value)} required disabled={ocupado} />
          <Selector etiqueta="Estado" valor={datos.estado} opciones={ESTADOS}
                    alCambiar={(estado) => { if (estado) cambiar("estado", estado); }} />
          <AreaTexto etiqueta="Observaciones" value={datos.observaciones} maximo={500} maxLength={500}
                     onChange={(e) => cambiar("observaciones", e.target.value)} disabled={ocupado} />
        </div>
      </section>
      {error && <Banner titulo="No se pudo guardar el préstamo">{error}</Banner>}
      <div className="acciones acciones-pie"><Boton type="submit" variante="primario" icono={Check} cargando={ocupado}>
        {inicial ? "Guardar cambios" : "Adicionar préstamo"}
      </Boton></div>
    </form>
  );
}
