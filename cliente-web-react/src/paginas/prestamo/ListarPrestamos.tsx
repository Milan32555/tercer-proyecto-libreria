import { FormEvent, useEffect, useState } from "react";
import { Handshake, Plus, RotateCw, Search } from "lucide-react";
import { Boton, BotonEnlace } from "../../componentes/ui/Boton";
import { Campo } from "../../componentes/ui/Campo";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { Paginador } from "../../componentes/ui/Paginador";
import { Selector } from "../../componentes/ui/Selector";
import { Columna, TablaDatos } from "../../componentes/ui/TablaDatos";
import { formatearFecha, formatearPrecio, textoEstadoPrestamo } from "../../formato";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import { listarPrestamos } from "../../graphql/servicioPrestamo";
import { EstadoPrestamo, Pagina, Prestamo } from "../../types";

const COLUMNAS: Columna<Prestamo>[] = [
  { titulo: "Código", celda: (p) => p.codigo },
  { titulo: "Ejemplar", celda: (p) => p.codigoEjemplar },
  { titulo: "Prestatario", celda: (p) => p.prestatario },
  { titulo: "Título del libro", celda: (p) => p.tituloLibro },
  { titulo: "Autor del libro", celda: (p) => p.autorLibro },
  { titulo: "Fecha", celda: (p) => formatearFecha(p.fechaPrestamo) },
  { titulo: "Días", celda: (p) => p.diasPrestamo, numerica: true },
  { titulo: "Multa", celda: (p) => formatearPrecio(p.multa), numerica: true },
  { titulo: "Estado", celda: (p) => textoEstadoPrestamo(p.estado) },
  { titulo: "Observaciones", celda: (p) => p.observaciones || "Sin observaciones" },
  { titulo: "ID", celda: (p) => <code>{p.id}</code>, apagada: true },
  { titulo: "Versión", celda: (p) => p.version, numerica: true }
];
const ESTADOS: { valor: EstadoPrestamo | ""; texto: string }[] = [
  { valor: "", texto: "Todos" }, { valor: "ACTIVO", texto: "Activo" },
  { valor: "DEVUELTO", texto: "Devuelto" }, { valor: "CANCELADO", texto: "Cancelado" }
];

export default function ListarPrestamos() {
  const [resultado, setResultado] = useState<Pagina<Prestamo>>({ contenido: [], pagina: 0, tamanio: 10, totalElementos: 0, totalPaginas: 0 });
  const [codigo, setCodigo] = useState("");
  const [estado, setEstado] = useState<EstadoPrestamo | "">("");
  const [filtro, setFiltro] = useState({ codigo: "", estado: "" as EstadoPrestamo | "" });
  const [pagina, setPagina] = useState(0);
  const [tamanio, setTamanio] = useState(10);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(true);
  const [cargado, setCargado] = useState(false);

  useEffect(() => {
    let vigente = true;
    setOcupado(true); setError(null);
    listarPrestamos(pagina, tamanio, filtro.codigo, filtro.estado).then((datos) => {
      if (!vigente) return;
      if (pagina > 0 && datos.contenido.length === 0 && datos.totalPaginas > 0) setPagina(datos.totalPaginas - 1);
      else setResultado(datos);
    }).catch((fallo: unknown) => {
      if (vigente) setError(mensajeDeError(fallo));
    }).finally(() => {
      if (vigente) { setOcupado(false); setCargado(true); }
    });
    return () => { vigente = false; };
  }, [pagina, tamanio, filtro, revision]);

  function filtrar(evento: FormEvent) {
    evento.preventDefault(); setPagina(0); setFiltro({ codigo, estado });
  }

  return <>
    <Encabezado seccion="Préstamos" titulo="Listar préstamos" descripcion="Consulta los préstamos y los datos del libro asociado."
      acciones={<BotonEnlace to="/prestamos/agregar" variante="primario" icono={Plus}>Adicionar préstamo</BotonEnlace>} />
    <section className="panel" aria-label="Préstamos">
      <form className="barra-filtros" role="search" onSubmit={filtrar}>
        <Campo etiqueta="Código del ejemplar" value={codigo} onChange={(e) => setCodigo(e.target.value)} placeholder="Opcional" />
        <Selector etiqueta="Estado" valor={estado} opciones={ESTADOS} alCambiar={setEstado} />
        <Boton type="submit" icono={Search} disabled={ocupado}>Filtrar</Boton>
        <Boton icono={RotateCw} onClick={() => setRevision((actual) => actual + 1)} disabled={ocupado}>Actualizar lista</Boton>
      </form>
      <p className="conteo" aria-live="polite">{resultado.totalElementos} préstamos</p>
      <TablaDatos etiqueta="Préstamos de la librería" columnas={COLUMNAS} filas={resultado.contenido}
        claveFila={(p) => p.codigo} cargandoInicial={!cargado} ocupada={ocupado} error={error} ancha
        alReintentar={() => setRevision((actual) => actual + 1)}
        vacio={{ icono: Handshake, titulo: "No hay préstamos para mostrar", descripcion: "Puedes adicionar un préstamo o cambiar los filtros." }} />
      <Paginador pagina={pagina} totalPaginas={resultado.totalPaginas} tamanio={tamanio} ocupado={ocupado}
        alCambiarPagina={setPagina} alCambiarTamanio={(nuevo) => { setPagina(0); setTamanio(nuevo); }} />
    </section>
  </>;
}
