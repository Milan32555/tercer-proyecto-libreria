import { formatearFecha, formatearPrecio, textoEstadoPrestamo } from "../formato";
import { Prestamo } from "../types";
import { Dato, Opcional } from "./FichaLibro";

export default function FichaPrestamo({ prestamo }: { prestamo: Prestamo }) {
  return (
    <article className="panel" aria-label={`Préstamo ${prestamo.codigo}`}>
      <header className="ficha-cabecera"><div>
        <p className="ficha-tipo">Préstamo</p>
        <h2 className="ficha-titulo">{prestamo.codigo}</h2>
        <p className="ficha-subtitulo">{prestamo.tituloLibro} · {prestamo.autorLibro}</p>
      </div></header>
      <dl className="ficha-datos">
        <Dato termino="Código" valor={prestamo.codigo} />
        <Dato termino="Código del ejemplar" valor={prestamo.codigoEjemplar} />
        <Dato termino="Prestatario" valor={prestamo.prestatario} />
        <Dato termino="Fecha del préstamo" valor={formatearFecha(prestamo.fechaPrestamo)} />
        <Dato termino="Días del préstamo" valor={prestamo.diasPrestamo} />
        <Dato termino="Multa" valor={formatearPrecio(prestamo.multa)} />
        <Dato termino="Estado" valor={textoEstadoPrestamo(prestamo.estado)} />
        <Dato termino="Título del libro" valor={prestamo.tituloLibro} />
        <Dato termino="Autor del libro" valor={prestamo.autorLibro} />
        <Dato termino="Observaciones" valor={prestamo.observaciones || <Opcional />} ancho />
        <Dato termino="ID" valor={<code>{prestamo.id}</code>} />
        <Dato termino="Versión" valor={prestamo.version} />
      </dl>
    </article>
  );
}
