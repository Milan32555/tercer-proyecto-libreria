import { ClienteGraphQL } from "./clienteGraphQL";
import { EstadoPrestamo, Pagina, Prestamo, PrestamoInput } from "../types";

export const CAMPOS_PRESTAMO = `id codigo codigoEjemplar prestatario fechaPrestamo diasPrestamo multa
  estado observaciones version tituloLibro autorLibro`;

export async function listarPrestamos(pagina: number, tamanio: number, codigoEjemplar: string, estado: EstadoPrestamo | ""): Promise<Pagina<Prestamo>> {
  const data = await ClienteGraphQL.getInstancia().ejecutar<{ prestamos: Pagina<Prestamo> }>(
    `query ($pagina: Int!, $tamanio: Int!, $codigoEjemplar: String, $estado: EstadoPrestamo) {
      prestamos(pagina: $pagina, tamanio: $tamanio, codigoEjemplar: $codigoEjemplar, estado: $estado) {
        contenido { ${CAMPOS_PRESTAMO} } pagina tamanio totalElementos totalPaginas
      }
    }`, { pagina, tamanio, codigoEjemplar: codigoEjemplar.trim() || null, estado: estado || null }
  );
  return data.prestamos;
}

export async function buscarPrestamo(codigo: string): Promise<Prestamo | null> {
  const data = await ClienteGraphQL.getInstancia().ejecutar<{ prestamoPorCodigo: Prestamo | null }>(
    `query ($codigo: ID!) { prestamoPorCodigo(codigo: $codigo) { ${CAMPOS_PRESTAMO} } }`, { codigo: codigo.trim() }
  );
  return data.prestamoPorCodigo;
}

export async function crearPrestamo(input: PrestamoInput): Promise<Prestamo> {
  const data = await ClienteGraphQL.getInstancia().ejecutar<{ crearPrestamo: Prestamo }>(
    `mutation ($input: PrestamoInput!) { crearPrestamo(input: $input) { ${CAMPOS_PRESTAMO} } }`, { input: { ...input, version: null } }
  );
  return data.crearPrestamo;
}

export async function actualizarPrestamo(input: PrestamoInput): Promise<Prestamo> {
  const data = await ClienteGraphQL.getInstancia().ejecutar<{ actualizarPrestamo: Prestamo }>(
    `mutation ($input: PrestamoInput!) { actualizarPrestamo(input: $input) { ${CAMPOS_PRESTAMO} } }`, { input }
  );
  return data.actualizarPrestamo;
}

export async function eliminarPrestamo(codigo: string): Promise<Prestamo> {
  const data = await ClienteGraphQL.getInstancia().ejecutar<{ eliminarPrestamo: Prestamo }>(
    `mutation ($codigo: ID!) { eliminarPrestamo(codigo: $codigo) { ${CAMPOS_PRESTAMO} } }`, { codigo }
  );
  return data.eliminarPrestamo;
}
