import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/prestamo.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_prestamo.dart';
import '../../tema/tema.dart';
import '../comunes/componentes.dart';
import '../comunes/fichas.dart';

/// Caso de uso: Consultar UN préstamo por su código.
class ConsultarPrestamo extends StatefulWidget {
  const ConsultarPrestamo({super.key});

  @override
  State<ConsultarPrestamo> createState() => _ConsultarPrestamoState();
}

class _ConsultarPrestamoState extends State<ConsultarPrestamo> {
  final _codigo = TextEditingController();
  Prestamo? _prestamo;
  String? _sinResultado;
  bool _cargando = false;
  String? _error;

  @override
  void dispose() {
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _buscar() async {
    FocusManager.instance.primaryFocus?.unfocus();
    final codigo = _codigo.text.trim();
    setState(() {
      _cargando = true;
      _error = null;
      _sinResultado = null;
      _prestamo = null;
    });
    try {
      final resultado = await buscarPrestamo(codigo);
      if (!mounted) return;
      setState(() {
        _prestamo = resultado;
        if (resultado == null) _sinResultado = 'No hay ningún préstamo con el código «$codigo».';
      });
    } on ErrorGraphQL catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Préstamos')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e4, Espacio.e4, Espacio.e8),
        children: [
          const EncabezadoPantalla(titulo: 'Consultar préstamo', descripcion: 'Busca un préstamo por su código.'),
          BuscadorClave(
            controller: _codigo,
            etiqueta: 'Código del préstamo',
            pista: 'PR-001',
            onBuscar: _buscar,
            cargando: _cargando,
          ),
          if (_error != null) Aviso(_error!, titulo: 'No se pudo consultar'),
          if (_sinResultado != null)
            EstadoVacio(icono: LucideIcons.searchX, titulo: 'Sin resultados', descripcion: _sinResultado),
          if (_prestamo != null) FichaPrestamo(prestamo: _prestamo!),
        ],
      ),
    );
  }
}
