import { useState } from "react";
import FichaPrestamo from "../../componentes/FichaPrestamo";
import { useAvisos } from "../../componentes/ui/Avisos";
import { Banner } from "../../componentes/ui/Banner";
import { BuscadorClave } from "../../componentes/ui/BuscadorClave";
import { ConfirmarEliminacion } from "../../componentes/ui/ConfirmarEliminacion";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { SinResultados } from "../../componentes/ui/SinResultados";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import { buscarPrestamo, eliminarPrestamo } from "../../graphql/servicioPrestamo";
import { Prestamo } from "../../types";

export default function EliminarPrestamo() {
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
  async function confirmar() {
    if (!prestamo) return;
    setOcupado(true); setError(null);
    try {
      await eliminarPrestamo(prestamo.codigo);
      avisar({ titulo: "Préstamo eliminado", descripcion: `Se eliminó el préstamo ${prestamo.codigo}.` });
      setPrestamo(null); setBuscado(false);
    } catch (fallo) { setError(mensajeDeError(fallo)); }
    finally { setOcupado(false); }
  }
  return <>
    <Encabezado seccion="Préstamos" titulo="Eliminar préstamo" descripcion="Busca el préstamo y confirma su eliminación después de revisar sus datos." />
    <div className="pila">
      <BuscadorClave etiqueta="Código del préstamo" cargando={ocupado} alBuscar={(codigo) => void buscar(codigo)} />
      {error && <Banner titulo="No se pudo completar la operación">{error}</Banner>}
      {buscado && !prestamo && <SinResultados titulo="Sin resultados">No existe un préstamo con ese código.</SinResultados>}
      {prestamo && <>
        <FichaPrestamo prestamo={prestamo} />
        <div className="acciones"><ConfirmarEliminacion textoBoton="Eliminar préstamo"
          titulo={`¿Eliminar el préstamo ${prestamo.codigo}?`} descripcion="Se eliminará el registro del préstamo."
          cargando={ocupado} alConfirmar={() => void confirmar()} /></div>
      </>}
    </div>
  </>;
}
