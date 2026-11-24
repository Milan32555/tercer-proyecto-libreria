const estadosPrestamo = ['ACTIVO', 'DEVUELTO', 'CANCELADO'];

/// Estado de un préstamo en texto legible.
String etiquetaEstadoPrestamo(String estado) => switch (estado) {
  'ACTIVO' => 'Activo',
  'DEVUELTO' => 'Devuelto',
  'CANCELADO' => 'Cancelado',
  _ => estado,
};

class Prestamo {
  final String id;
  final String codigo;
  final String codigoEjemplar;
  final String prestatario;
  final String fechaPrestamo;
  final int diasPrestamo;
  final double multa;
  final String estado;
  final String? observaciones;
  final int version;
  final String? tituloLibro;
  final String? autorLibro;

  Prestamo.desdeJson(Map<String, dynamic> json)
      : id = json['id'] as String,
        codigo = json['codigo'] as String,
        codigoEjemplar = json['codigoEjemplar'] as String,
        prestatario = json['prestatario'] as String,
        fechaPrestamo = json['fechaPrestamo'] as String,
        diasPrestamo = (json['diasPrestamo'] as num).toInt(),
        multa = (json['multa'] as num).toDouble(),
        estado = json['estado'] as String,
        observaciones = json['observaciones'] as String?,
        version = (json['version'] as num).toInt(),
        tituloLibro = json['tituloLibro'] as String?,
        autorLibro = json['autorLibro'] as String?;
}
