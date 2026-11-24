import { useState } from "react";
import FichaPrestamo from "../../componentes/FichaPrestamo";
import FormularioPrestamo from "../../componentes/FormularioPrestamo";
import { useAvisos } from "../../componentes/ui/Avisos";
import { Banner } from "../../componentes/ui/Banner";
import { BuscadorClave } from "../../componentes/ui/BuscadorClave";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { SinResultados } from "../../componentes/ui/SinResultados";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import { actualizarPrestamo, buscarPrestamo } from "../../graphql/servicioPrestamo";
import { Prestamo, PrestamoInput } from "../../types";

export default function ActualizarPrestamo() {
  const [prestamo, setPrestamo] = useState<Prestamo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [buscado, setBuscado] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  const avisar = useAvisos();
  async function buscar(codigo: string) {
    setError(null); setPrestamo(null); setBuscado(false); setOcupado(true);
    try { setPrestamo(await buscarPrestamo(codigo)); setBuscado(true); }
    catch (fallo) { setError(mensajeDeError(fallo)); }
    finally { setOcupado(false); }
  }
  async function guardar(input: PrestamoInput) {
    setPrestamo(await actualizarPrestamo(input));
    avisar({ titulo: "Préstamo actualizado", descripcion: "Se guardaron los cambios." });
  }
  return <>
    <Encabezado seccion="Préstamos" titulo="Actualizar préstamo" descripcion="Busca el préstamo y revisa sus datos antes de editar." />
    <div className="pila">
      <BuscadorClave etiqueta="Código del préstamo" cargando={ocupado} alBuscar={(codigo) => void buscar(codigo)} />
      {error && <Banner titulo="No se pudo buscar">{error}</Banner>}
      {buscado && !prestamo && <SinResultados titulo="Sin resultados">No existe un préstamo con ese código.</SinResultados>}
      {prestamo && <>
        <FichaPrestamo prestamo={prestamo} />
        <FormularioPrestamo key={`${prestamo.codigo}-${prestamo.version}`} inicial={prestamo} alGuardar={guardar} />
      </>}
    </div>
  </>;
}
