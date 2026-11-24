const CLAVE_AUTORIZACION = "libreria.autorizacion";
const CLAVE_USUARIO = "libreria.usuario";

/** La sesión dura mientras la pestaña esté abierta: sobrevive a recargar la página. */
function leer(clave: string): string | null {
  try {
    return window.sessionStorage.getItem(clave);
  } catch {
    return null;
  }
}

function guardar(usuario: string | null, credencial: string | null): void {
  try {
    if (usuario && credencial) {
      window.sessionStorage.setItem(CLAVE_USUARIO, usuario);
      window.sessionStorage.setItem(CLAVE_AUTORIZACION, credencial);
    } else {
      window.sessionStorage.removeItem(CLAVE_USUARIO);
      window.sessionStorage.removeItem(CLAVE_AUTORIZACION);
    }
  } catch {
    // Sin almacenamiento la sesión sigue funcionando en memoria.
  }
}

let autorizacion: string | null = leer(CLAVE_AUTORIZACION);
let usuarioActual: string | null = autorizacion ? leer(CLAVE_USUARIO) : null;
if (!usuarioActual) autorizacion = null;

export const EVENTO_SESION = "libreria-sesion";

export function usuarioAutenticado(): string | null {
  return usuarioActual;
}

export function encabezadoAutorizacion(): Record<string, string> {
  return autorizacion ? { Authorization: autorizacion } : {};
}

export function cerrarSesion(): void {
  autorizacion = null;
  usuarioActual = null;
  guardar(null, null);
  window.dispatchEvent(new Event(EVENTO_SESION));
}

export async function iniciarSesion(urlGraphQL: string, usuario: string, clave: string): Promise<void> {
  const bytes = new TextEncoder().encode(`${usuario.trim()}:${clave}`);
  const credencial = `Basic ${btoa(String.fromCharCode(...bytes))}`;
  let respuesta: Response;
  try {
    respuesta = await fetch(new URL("api/sesion", urlGraphQL), {
      headers: { Authorization: credencial, Accept: "application/json" },
      credentials: "omit"
    });
  } catch {
    throw new Error("No se pudo conectar con el servidor. Comprueba que esté en ejecución.");
  }
  if (respuesta.status === 401) throw new Error("Usuario o contraseña incorrectos.");
  if (!respuesta.ok) throw new Error("No se pudo iniciar sesión. Intenta nuevamente.");
  const datos = await respuesta.json() as { usuario: string };
  autorizacion = credencial;
  usuarioActual = datos.usuario;
  guardar(usuarioActual, autorizacion);
  window.dispatchEvent(new Event(EVENTO_SESION));
}
