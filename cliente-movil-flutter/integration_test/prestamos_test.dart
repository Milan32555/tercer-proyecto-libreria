// Login y CRUD de Préstamo en el celular real, contra los dos microservicios reales.
// Usar una base de prueba, porque crea y elimina registros:
//
//   adb reverse tcp:8081 tcp:<puerto del servidor de prueba>
//   flutter test integration_test/prestamos_test.dart -d <id-del-celular> \
//     --dart-define=URL_GRAPHQL=http://localhost:8081/graphql

import 'package:cliente_movil_libreria/main.dart';
import 'package:cliente_movil_libreria/servicios/cliente_graphql.dart';
import 'package:cliente_movil_libreria/servicios/servicio_ejemplar.dart';
import 'package:cliente_movil_libreria/servicios/servicio_libro.dart';
import 'package:cliente_movil_libreria/servicios/servicio_prestamo.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  final numero = DateTime.now().millisecondsSinceEpoch % 100000;
  final isbn = '${8000 + numero % 1000}';
  final ejemplar = 'EJ-9${numero.toString().padLeft(5, '0')}';
  final codigo = 'PR-9${numero.toString().padLeft(5, '0')}';

  testWidgets('login y CRUD de préstamos en el celular', (tester) async {
    MaterialLocalizations textos() => MaterialLocalizations.of(tester.element(find.byType(Scaffold).last));

    Future<void> esperar(Finder objetivo, {int segundos = 20}) async {
      final limite = DateTime.now().add(Duration(seconds: segundos));
      while (objetivo.evaluate().isEmpty) {
        if (DateTime.now().isAfter(limite)) throw TestFailure('No apareció a tiempo: $objetivo');
        await tester.pump(const Duration(milliseconds: 200));
      }
      await tester.pumpAndSettle();
    }

    /// Lleva a la vista un elemento de una lista larga (aunque todavía no esté construido).
    Future<void> mostrar(Finder objetivo) async {
      if (objetivo.evaluate().isEmpty) {
        await tester.scrollUntilVisible(objetivo, 300, scrollable: find.byType(Scrollable).first, maxScrolls: 40);
      }
      await tester.ensureVisible(objetivo.first);
      await tester.pumpAndSettle();
    }

    Future<void> tocar(Finder objetivo) async {
      await mostrar(objetivo);
      await tester.tap(objetivo.first);
      await tester.pumpAndSettle();
    }

    // Se escribe por el controlador: el teclado del sistema puede pisar el texto simulado.
    Future<void> escribir(String etiqueta, String texto) async {
      final campo = find.widgetWithText(TextField, etiqueta);
      await mostrar(campo);
      tester.widget<TextField>(campo.first).controller!.text = texto;
      await tester.pumpAndSettle();
    }

    Future<void> irA(String opcion) async {
      while (find.byTooltip(textos().backButtonTooltip).evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip(textos().backButtonTooltip).first);
        await tester.pumpAndSettle();
      }
      await tester.tap(find.byTooltip(textos().openAppDrawerTooltip));
      await tester.pumpAndSettle();
      final menu = find.byType(NavigationDrawer);
      await tester.scrollUntilVisible(
        find.descendant(of: menu, matching: find.text('Acerca de...')),
        300,
        scrollable: find.descendant(of: menu, matching: find.byType(Scrollable)).first,
      );
      await tester.pumpAndSettle();
      await tocar(find.descendant(of: menu, matching: find.text(opcion)).last);
    }

    Future<void> buscar() async {
      await escribir('Código del préstamo', codigo);
      await tocar(find.widgetWithText(FilledButton, 'Buscar'));
      await esperar(find.text('Prestatario'));
    }

    ClienteGraphQL().cerrarSesion();
    await tester.pumpWidget(const AplicacionLibreria());
    await esperar(find.text('Iniciar sesión'));

    await escribir('Usuario', 'profesor');
    await escribir('Contraseña', 'incorrecta');
    await tocar(find.widgetWithText(FilledButton, 'Entrar'));
    await esperar(find.text('Usuario o contraseña incorrectos.'));

    await escribir('Contraseña', 'libreria');
    await tocar(find.widgetWithText(FilledButton, 'Entrar'));
    await esperar(find.textContaining('copias · calculado por el servidor'));

    await crearLibro(
      isbn: isbn,
      titulo: 'Libro en el celular',
      autor: 'Autora Móvil',
      precio: 1000,
      numeroPaginas: 10,
      fechaPublicacion: '2020-01-01T00:00:00',
      genero: 'CIENCIA',
    );
    await crearEjemplar(
      codigo: ejemplar,
      estado: 'DISPONIBLE',
      ubicacion: 'Estante C',
      fechaIngreso: '2024-01-01T00:00:00',
      precioReposicion: 500,
      numeroPrestamos: 0,
      isbnLibro: isbn,
    );

    await irA('Adicionar');
    await escribir('Código del préstamo', codigo);
    await escribir('Código del ejemplar', 'EJ-999999');
    await escribir('Prestatario', 'Ana Pérez');
    await tocar(find.widgetWithText(FilledButton, 'Adicionar préstamo'));
    await esperar(find.textContaining('No existe un ejemplar'));
    await escribir('Código del ejemplar', ejemplar);
    await tocar(find.widgetWithText(FilledButton, 'Adicionar préstamo'));
    await esperar(find.textContaining('Préstamo adicionado'));

    await irA('Consultar');
    await buscar();
    expect(find.text('Libro en el celular'), findsWidgets);
    expect(find.text('Autora Móvil'), findsWidgets);

    await irA('Listar');
    await esperar(find.text(codigo));
    expect(find.text('Libro en el celular'), findsOneWidget);
    expect(find.text('Autora Móvil'), findsOneWidget);
    await tocar(find.widgetWithText(ChoiceChip, 'Cancelado'));
    await esperar(find.text('Ningún préstamo coincide con el filtro'));

    await irA('Actualizar');
    await buscar();
    await escribir('Multa', '2500');
    await tocar(find.widgetWithText(DropdownButtonFormField<String>, 'Estado'));
    await tocar(find.text('Devuelto').last);
    await tocar(find.widgetWithText(FilledButton, 'Guardar cambios'));
    await esperar(find.textContaining('Préstamo actualizado'));
    final guardado = await buscarPrestamo(codigo);
    expect(guardado!.multa, 2500);
    expect(guardado.estado, 'DEVUELTO');

    await irA('Eliminar');
    await buscar();
    await tocar(find.widgetWithText(FilledButton, 'Eliminar préstamo'));
    await tocar(find.widgetWithText(FilledButton, 'Eliminar').last);
    await esperar(find.textContaining('Préstamo eliminado'));
    expect(await buscarPrestamo(codigo), isNull);

    await eliminarEjemplar(ejemplar);
    await eliminarLibro(isbn);
  });
}
