import '../modelos/pagina.dart';
import '../modelos/prestamo.dart';
import 'cliente_graphql.dart';

const camposPrestamo = '''id codigo codigoEjemplar prestatario fechaPrestamo diasPrestamo multa
  estado observaciones version tituloLibro autorLibro''';

Future<Pagina<Prestamo>> listarPrestamos({int pagina = 0, int tamanio = 10, String? codigoEjemplar, String? estado}) async {
  final data = await ClienteGraphQL().ejecutar(
    '''query (\$pagina: Int!, \$tamanio: Int!, \$codigoEjemplar: String, \$estado: EstadoPrestamo) {
      prestamos(pagina: \$pagina, tamanio: \$tamanio, codigoEjemplar: \$codigoEjemplar, estado: \$estado) {
        contenido { $camposPrestamo } pagina tamanio totalElementos totalPaginas
      }
    }''',
    variables: {'pagina': pagina, 'tamanio': tamanio, 'codigoEjemplar': codigoEjemplar, 'estado': estado},
  );
  return Pagina.desdeJson(data['prestamos'] as Map<String, dynamic>, Prestamo.desdeJson);
}

Future<Prestamo?> buscarPrestamo(String codigo) async {
  final data = await ClienteGraphQL().ejecutar(
    'query (\$codigo: ID!) { prestamoPorCodigo(codigo: \$codigo) { $camposPrestamo } }',
    variables: {'codigo': codigo.trim()},
  );
  final encontrado = data['prestamoPorCodigo'];
  return encontrado == null ? null : Prestamo.desdeJson(encontrado as Map<String, dynamic>);
}

Future<Prestamo> crearPrestamo(Map<String, dynamic> input) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$input: PrestamoInput!) { crearPrestamo(input: \$input) { $camposPrestamo } }',
    variables: {'input': {...input, 'version': null}},
  );
  return Prestamo.desdeJson(data['crearPrestamo'] as Map<String, dynamic>);
}

Future<Prestamo> actualizarPrestamo(Map<String, dynamic> input) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$input: PrestamoInput!) { actualizarPrestamo(input: \$input) { $camposPrestamo } }',
    variables: {'input': input},
  );
  return Prestamo.desdeJson(data['actualizarPrestamo'] as Map<String, dynamic>);
}

Future<Prestamo> eliminarPrestamo(String codigo) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$codigo: ID!) { eliminarPrestamo(codigo: \$codigo) { $camposPrestamo } }',
    variables: {'codigo': codigo},
  );
  return Prestamo.desdeJson(data['eliminarPrestamo'] as Map<String, dynamic>);
}
