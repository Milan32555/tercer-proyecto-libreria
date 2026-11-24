import FormularioPrestamo from "../../componentes/FormularioPrestamo";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { useAvisos } from "../../componentes/ui/Avisos";
import { crearPrestamo } from "../../graphql/servicioPrestamo";
import { PrestamoInput } from "../../types";

export default function AgregarPrestamo() {
  const avisar = useAvisos();
  async function guardar(input: PrestamoInput) {
    const creado = await crearPrestamo(input);
    avisar({ titulo: "Préstamo registrado", descripcion: `Se creó el préstamo ${creado.codigo}.` });
  }
  return <>
    <Encabezado seccion="Préstamos" titulo="Adicionar préstamo" descripcion="Registra el préstamo de un ejemplar existente." />
    <FormularioPrestamo alGuardar={guardar} />
  </>;
}
