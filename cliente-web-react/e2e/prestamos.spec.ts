import { Page, TestInfo, expect, test } from "@playwright/test";
import {
  abrirMenu, capturar, codigoUnico, conSesion, crearEjemplar, crearLibro, elegir, esperarAviso, graphql, isbnUnico
} from "./utilidades";

// Login y CRUD de la clase C: web -> GraphQL (8081) -> REST (8082) -> su propia base MongoDB.

let contador = 0;

function codigoPrestamo(info: TestInfo): string {
  contador++;
  const numero = ((Date.now() % 10_000) * 10 + (contador % 10)) % 100_000;
  return `PR-${info.project.name === "celular" ? "8" : "7"}${String(numero).padStart(5, "0")}`;
}

async function llenarPrestamo(page: Page, codigo: string, ejemplar: string) {
  await page.getByLabel("Código del préstamo").fill(codigo);
  await page.getByLabel("Código del ejemplar").fill(ejemplar);
  await page.getByLabel("Prestatario").fill("Ana Pérez");
  await page.getByLabel("Multa").fill("1500");
}

async function buscar(page: Page, codigo: string) {
  await page.getByLabel("Código del préstamo").first().fill(codigo);
  await page.getByRole("button", { name: "Buscar" }).click();
  await expect(page.getByRole("article", { name: `Préstamo ${codigo}` })).toBeVisible();
}

test.describe("Login", () => {
  test("rechaza la clave mala, entra con la buena, sobrevive a recargar y cierra sesión", async ({ page }, info) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();
    await capturar(page, info, "00-login");

    await page.getByLabel("Usuario").fill("profesor");
    await page.getByLabel("Contraseña").fill("incorrecta");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByText("Usuario o contraseña incorrectos.")).toBeVisible();

    await page.getByLabel("Contraseña").fill("libreria");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByRole("region", { name: "Inventario" })).toBeVisible();

    await page.reload();
    await expect(page.getByRole("region", { name: "Inventario" })).toBeVisible();

    const menu = await abrirMenu(page);
    await menu.getByRole("button", { name: "Cerrar sesión" }).click();
    await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();
  });
});

test.describe("Préstamo (clase C)", () => {
  test.beforeEach(async ({ context }) => {
    await conSesion(context);
  });

  test("insertar, consultar, listar con filtro, actualizar y eliminar", async ({ page, request }, info) => {
    const isbn = isbnUnico(info);
    const ejemplar = codigoUnico(info);
    const codigo = codigoPrestamo(info);
    await crearLibro(request, isbn, "Libro con préstamo");
    await crearEjemplar(request, ejemplar, isbn);

    await page.goto("/prestamos/agregar");
    await llenarPrestamo(page, codigo, ejemplar);
    await page.getByRole("button", { name: "Adicionar préstamo" }).click();
    await esperarAviso(page, "Préstamo registrado");
    await capturar(page, info, "70-prestamo-adicionar", true);

    await page.goto("/prestamos/consultar");
    await buscar(page, codigo);
    const ficha = page.getByRole("article", { name: `Préstamo ${codigo}` });
    await expect(ficha.getByText("Libro con préstamo").first()).toBeVisible();
    await expect(ficha.getByText("Autora E2E").first()).toBeVisible();
    await capturar(page, info, "71-prestamo-consultar", true);

    await page.goto("/prestamos/listar");
    await page.getByLabel("Código del ejemplar").fill(ejemplar);
    await page.getByRole("button", { name: "Filtrar" }).click();
    await expect(page.getByText("1 préstamos")).toBeVisible();
    await expect(page.getByRole("cell", { name: "Libro con préstamo" })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Autora E2E" })).toBeVisible();
    await capturar(page, info, "72-prestamo-listar", true);

    await page.goto("/prestamos/actualizar");
    await buscar(page, codigo);
    await page.getByLabel("Multa").fill("2500");
    await elegir(page, "Estado", "Devuelto");
    await page.getByRole("button", { name: "Guardar cambios" }).click();
    await esperarAviso(page, "Préstamo actualizado");
    const { prestamoPorCodigo: guardado } = await graphql<{ prestamoPorCodigo: { multa: number; estado: string } }>(
      request, `query ($codigo: ID!) { prestamoPorCodigo(codigo: $codigo) { multa estado } }`, { codigo }
    );
    expect(guardado).toEqual({ multa: 2500, estado: "DEVUELTO" });
    await capturar(page, info, "73-prestamo-actualizar", true);

    await page.goto("/prestamos/eliminar");
    await buscar(page, codigo);
    await page.getByRole("button", { name: "Eliminar préstamo" }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Eliminar", exact: true }).click();
    await esperarAviso(page, "Préstamo eliminado");
    const { prestamoPorCodigo: borrado } = await graphql<{ prestamoPorCodigo: unknown }>(
      request, `query ($codigo: ID!) { prestamoPorCodigo(codigo: $codigo) { codigo } }`, { codigo }
    );
    expect(borrado).toBeNull();
  });

  test("integridad referencial: exige un ejemplar real y protege al ejemplar con préstamos", async ({ page, request }, info) => {
    const isbn = isbnUnico(info);
    const ejemplar = codigoUnico(info);
    const codigo = codigoPrestamo(info);
    await crearLibro(request, isbn, "Libro con integridad");
    await crearEjemplar(request, ejemplar, isbn);

    await page.goto("/prestamos/agregar");
    await llenarPrestamo(page, codigo, "EJ-999999");
    await page.getByRole("button", { name: "Adicionar préstamo" }).click();
    await expect(page.getByText(/No existe un ejemplar con el código EJ-999999/)).toBeVisible();

    await page.getByLabel("Código del ejemplar").fill(ejemplar);
    await page.getByRole("button", { name: "Adicionar préstamo" }).click();
    await esperarAviso(page, "Préstamo registrado");

    const eliminarEjemplar = `mutation ($codigo: ID!) { eliminarEjemplar(codigo: $codigo) { codigo } }`;
    await expect(graphql(request, eliminarEjemplar, { codigo: ejemplar })).rejects.toThrow(/préstamo\(s\)/);

    await graphql(request, `mutation ($codigo: ID!) { eliminarPrestamo(codigo: $codigo) { codigo } }`, { codigo });
    await graphql(request, eliminarEjemplar, { codigo: ejemplar });
  });
});
