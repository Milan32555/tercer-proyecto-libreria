import { useState } from "react";
import FichaPrestamo from "../../componentes/FichaPrestamo";
import { Banner } from "../../componentes/ui/Banner";
import { BuscadorClave } from "../../componentes/ui/BuscadorClave";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { SinResultados } from "../../componentes/ui/SinResultados";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import { buscarPrestamo } from "../../graphql/servicioPrestamo";
import { Prestamo } from "../../types";

export default function ConsultarPrestamo() {
  const [prestamo, setPrestamo] = useState<Prestamo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [buscado, setBuscado] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  async function buscar(codigo: string) {
    setError(null); setPrestamo(null); setBuscado(false); setOcupado(true);
    try { setPrestamo(await buscarPrestamo(codigo)); setBuscado(true); }
    catch (fallo) { setError(mensajeDeError(fallo)); }
    finally { setOcupado(false); }
  }
  return <>
    <Encabezado seccion="Préstamos" titulo="Consultar préstamo" descripcion="Busca un préstamo por su código." />
    <div className="pila">
      <BuscadorClave etiqueta="Código del préstamo" cargando={ocupado} alBuscar={(codigo) => void buscar(codigo)} />
      {error && <Banner titulo="No se pudo consultar">{error}</Banner>}
      {buscado && !prestamo && <SinResultados titulo="Sin resultados">No existe un préstamo con ese código.</SinResultados>}
      {prestamo && <FichaPrestamo prestamo={prestamo} />}
    </div>
  </>;
}
