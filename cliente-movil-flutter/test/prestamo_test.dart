import 'package:cliente_movil_libreria/main.dart';
import 'package:cliente_movil_libreria/servicios/cliente_graphql.dart';
import 'package:cliente_movil_libreria/utilidades/validaciones.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'servidor_simulado.dart';

Map<String, dynamic> prestamoJson({String estado = 'ACTIVO', int version = 0, double multa = 1500}) => {
  'id': '65f000000000000000000001',
  'codigo': 'PR-001',
  'codigoEjemplar': 'EJ-001',
  'prestatario': 'Ana Pérez',
  'fechaPrestamo': '2026-01-10T08:30:00',
  'diasPrestamo': 7,
  'multa': multa,
  'estado': estado,
  'observaciones': null,
  'version': version,
  'tituloLibro': 'Cien años de soledad',
  'autorLibro': 'Gabriel García Márquez',
};

Future<void> abrir(WidgetTester tester, String ruta) async {
  tester.view.physicalSize = const Size(1200, 2600);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(AplicacionLibreria(rutaInicial: ruta));
  await tester.pumpAndSettle();
}

Future<void> tocar(WidgetTester tester, Finder objetivo) async {
  await tester.ensureVisible(objetivo);
  await tester.pumpAndSettle();
  await tester.tap(objetivo);
  await tester.pumpAndSettle();
}

Future<void> escribir(WidgetTester tester, String etiqueta, String texto) async {
  final campo = find.widgetWithText(TextField, etiqueta).first;
  await tester.ensureVisible(campo);
  await tester.enterText(campo, texto);
  await tester.pump();
}

Future<void> buscar(WidgetTester tester, String codigo) async {
  await escribir(tester, 'Código del préstamo', codigo);
  await tocar(tester, find.widgetWithText(FilledButton, 'Buscar'));
}

void main() {
  group('Login', () {
    setUp(() => ClienteGraphQL().cerrarSesion());
    tearDown(() => ClienteGraphQL().usuario.value = 'profesor');

    testWidgets('sin sesión muestra el acceso y las credenciales malas no entran', (tester) async {
      ClienteGraphQL().clienteHttp = MockClient((_) async => http.Response('', 401));
      await abrir(tester, '/');

      expect(find.text('Iniciar sesión'), findsOneWidget);
      await tester.enterText(find.widgetWithText(TextFormField, 'Usuario'), 'profesor');
      await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'mala');
      await tester.tap(find.text('Entrar'));
      await tester.pumpAndSettle();

      expect(find.text('Usuario o contraseña incorrectos.'), findsOneWidget);
      expect(ClienteGraphQL().usuario.value, isNull);
    });

    test('envía usuario y contraseña por HTTP Basic', () async {
      String? autorizacion;
      ClienteGraphQL().clienteHttp = MockClient((peticion) async {
        autorizacion = peticion.headers['Authorization'];
        return http.Response('{"usuario":"profesor"}', 200);
      });

      await ClienteGraphQL().iniciarSesion(' profesor ', 'libreria');

      expect(autorizacion, 'Basic cHJvZmVzb3I6bGlicmVyaWE=');
      expect(ClienteGraphQL().usuario.value, 'profesor');
    });
  });

  group('Préstamos', () {
    setUp(() => ClienteGraphQL().usuario.value = 'profesor');

    testWidgets('listar muestra dos atributos de la clase A y filtra por estado', (tester) async {
      final servidor = ServidorSimulado({
        'prestamos': (_) => {
          'contenido': [prestamoJson()],
          'pagina': 0,
          'tamanio': 10,
          'totalElementos': 1,
          'totalPaginas': 1,
        },
      })..instalar();
      await abrir(tester, '/prestamos/listar');

      expect(find.text('Cien años de soledad'), findsOneWidget);
      expect(find.text('Gabriel García Márquez'), findsOneWidget);
      expect(find.text('1 préstamo'), findsOneWidget);

      await tocar(tester, find.widgetWithText(ChoiceChip, 'Devuelto'));
      expect(servidor.variablesDe('prestamos').last['estado'], 'DEVUELTO');
    });

    testWidgets('adicionar valida el formato y envía los datos', (tester) async {
      final servidor = ServidorSimulado({'crearPrestamo': (_) => prestamoJson()})..instalar();
      await abrir(tester, '/prestamos/agregar');

      await escribir(tester, 'Código del préstamo', 'X1');
      await tocar(tester, find.widgetWithText(FilledButton, 'Adicionar préstamo'));
      expect(find.textContaining('PR-000'), findsOneWidget);
      expect(servidor.variablesDe('crearPrestamo'), isEmpty);

      await escribir(tester, 'Código del préstamo', 'pr-001');
      await escribir(tester, 'Código del ejemplar', 'ej-001');
      await escribir(tester, 'Prestatario', 'Ana Pérez');
      await tocar(tester, find.widgetWithText(FilledButton, 'Adicionar préstamo'));

      final input = servidor.variablesDe('crearPrestamo').single['input'] as Map<String, dynamic>;
      expect(input['codigo'], 'PR-001');
      expect(input['codigoEjemplar'], 'EJ-001');
      expect(input['diasPrestamo'], 7);
    });

    testWidgets('adicionar muestra el rechazo de integridad del servidor', (tester) async {
      ServidorSimulado({'crearPrestamo': (_) => throw ErrorSimulado('No existe un ejemplar con el código EJ-999.')}).instalar();
      await abrir(tester, '/prestamos/agregar');

      await escribir(tester, 'Código del préstamo', 'PR-001');
      await escribir(tester, 'Código del ejemplar', 'EJ-999');
      await escribir(tester, 'Prestatario', 'Ana');
      await tocar(tester, find.widgetWithText(FilledButton, 'Adicionar préstamo'));

      expect(find.text('No existe un ejemplar con el código EJ-999.'), findsOneWidget);
    });

    testWidgets('consultar muestra un solo préstamo o "sin resultados"', (tester) async {
      ServidorSimulado({'prestamoPorCodigo': (v) => v['codigo'] == 'PR-001' ? prestamoJson() : null}).instalar();
      await abrir(tester, '/prestamos/consultar');

      await buscar(tester, 'PR-404');
      expect(find.text('Sin resultados'), findsOneWidget);

      await buscar(tester, 'PR-001');
      expect(find.text('Ana Pérez'), findsOneWidget);
      expect(find.text('Título del libro'), findsOneWidget);
    });

    testWidgets('actualizar busca primero y envía la versión leída', (tester) async {
      final servidor = ServidorSimulado({
        'prestamoPorCodigo': (_) => prestamoJson(version: 4),
        'actualizarPrestamo': (_) => prestamoJson(version: 5, estado: 'DEVUELTO'),
      })..instalar();
      await abrir(tester, '/prestamos/actualizar');

      expect(find.text('Guardar cambios'), findsNothing);
      await buscar(tester, 'PR-001');
      expect(find.text('Préstamo a actualizar'), findsOneWidget);

      await tocar(tester, find.widgetWithText(FilledButton, 'Guardar cambios'));

      final input = servidor.variablesDe('actualizarPrestamo').single['input'] as Map<String, dynamic>;
      expect(input['version'], 4);
      expect(input['codigo'], 'PR-001');
    });

    testWidgets('eliminar busca, muestra toda la información y confirma', (tester) async {
      final servidor = ServidorSimulado({
        'prestamoPorCodigo': (_) => prestamoJson(),
        'eliminarPrestamo': (_) => prestamoJson(),
        'resumenInventario': (_) => resumenJson(),
        'revisionLibros': (_) => 1,
        'revisionEjemplares': (_) => 1,
      })..instalar();
      await abrir(tester, '/prestamos/eliminar');

      await buscar(tester, 'PR-001');
      expect(find.text('Préstamo a eliminar'), findsOneWidget);
      expect(find.text('Gabriel García Márquez'), findsWidgets);

      await tocar(tester, find.widgetWithText(FilledButton, 'Eliminar préstamo'));
      await tocar(tester, find.widgetWithText(FilledButton, 'Eliminar').last);

      expect(servidor.variablesDe('eliminarPrestamo').single['codigo'], 'PR-001');
    });
  });

  test('validaciones de préstamo', () {
    expect(validarCodigoPrestamo('PR-001'), isNull);
    expect(validarCodigoPrestamo('pr-123456'), isNull);
    expect(validarCodigoPrestamo('PR-1'), isNotNull);
    expect(validarCodigoPrestamo(''), isNotNull);
    expect(validarDiasPrestamo('365'), isNull);
    expect(validarDiasPrestamo('366'), isNotNull);
    expect(validarDiasPrestamo('0'), isNotNull);
    expect(validarDecimalNoNegativo('0'), isNull);
    expect(validarDecimalNoNegativo('-1'), isNotNull);
    expect(validarDecimalNoNegativo('abc'), isNotNull);
    expect(validarDecimalNoNegativo(''), isNotNull);
  });
}
