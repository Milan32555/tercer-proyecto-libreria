import { FormEvent, ReactNode, useEffect, useState } from "react";
import { Library, LogIn } from "lucide-react";
import { URL_GRAPHQL } from "../graphql/clienteGraphQL";
import { EVENTO_SESION, iniciarSesion, usuarioAutenticado } from "../sesion";
import { Banner } from "./ui/Banner";
import { Boton } from "./ui/Boton";
import { Campo } from "./ui/Campo";

export default function Acceso({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState(usuarioAutenticado);
  const [usuario, setUsuario] = useState("");
  const [clave, setClave] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  useEffect(() => {
    const actualizar = () => setSesion(usuarioAutenticado());
    window.addEventListener(EVENTO_SESION, actualizar);
    return () => window.removeEventListener(EVENTO_SESION, actualizar);
  }, []);

  async function entrar(evento: FormEvent) {
    evento.preventDefault();
    setError(null);
    setOcupado(true);
    try {
      await iniciarSesion(URL_GRAPHQL, usuario, clave);
      setClave("");
    } catch (fallo) {
      setError(fallo instanceof Error ? fallo.message : "No se pudo iniciar sesión.");
    } finally {
      setOcupado(false);
    }
  }

  if (sesion) return <>{children}</>;

  return (
    <main className="acceso-pagina">
      <section className="panel acceso-panel" aria-labelledby="acceso-titulo">
        <div className="marca acceso-marca"><Library size={24} aria-hidden="true" /><span className="marca-nombre">Librería</span></div>
        <h1 id="acceso-titulo">Iniciar sesión</h1>
        <p className="apagado">Accede al inventario y los préstamos.</p>
        <form className="pila" onSubmit={entrar}>
          <Campo etiqueta="Usuario" value={usuario} onChange={(e) => setUsuario(e.target.value)}
                 autoComplete="username" required disabled={ocupado} />
          <Campo etiqueta="Contraseña" type="password" value={clave} onChange={(e) => setClave(e.target.value)}
                 autoComplete="current-password" required disabled={ocupado} />
          {error && <Banner titulo="No se pudo acceder">{error}</Banner>}
          <Boton type="submit" variante="primario" icono={LogIn} cargando={ocupado}>Entrar</Boton>
        </form>
      </section>
    </main>
  );
}
