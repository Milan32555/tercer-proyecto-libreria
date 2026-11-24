import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/prestamo.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_prestamo.dart';
import '../../tema/tema.dart';
import '../comunes/componentes.dart';
import '../comunes/fichas.dart';
import 'formulario_prestamo.dart';

/// Caso de uso: Actualizar Préstamo. Primero se busca y se muestra TODA su información.
class ActualizarPrestamo extends StatefulWidget {
  const ActualizarPrestamo({super.key});

  @override
  State<ActualizarPrestamo> createState() => _ActualizarPrestamoState();
}

class _ActualizarPrestamoState extends State<ActualizarPrestamo> {
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

  Future<void> _guardar(Map<String, dynamic> input) async {
    final actualizado = await actualizarPrestamo(input);
    if (!mounted) return;
    setState(() => _prestamo = actualizado);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Préstamo actualizado: ${actualizado.codigo}.')));
  }

  @override
  Widget build(BuildContext context) {
    final prestamo = _prestamo;
    return Scaffold(
      appBar: AppBar(title: const Text('Préstamos')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e4, Espacio.e4, Espacio.e8),
        children: [
          const EncabezadoPantalla(
            titulo: 'Actualizar préstamo',
            descripcion: 'Busca el préstamo y revisa toda su información antes de editar.',
          ),
          BuscadorClave(
            controller: _codigo,
            etiqueta: 'Código del préstamo',
            pista: 'PR-001',
            onBuscar: _buscar,
            cargando: _cargando,
          ),
          if (_error != null) Aviso(_error!, titulo: 'No se pudo buscar el préstamo'),
          if (_sinResultado != null)
            EstadoVacio(icono: LucideIcons.searchX, titulo: 'Sin resultados', descripcion: _sinResultado),
          if (prestamo != null) ...[
            FichaPrestamo(prestamo: prestamo, titulo: 'Préstamo a actualizar'),
            const SizedBox(height: Espacio.e4),
            FormularioPrestamo(
              key: ValueKey('${prestamo.codigo}-${prestamo.version}'),
              inicial: prestamo,
              textoBoton: 'Guardar cambios',
              tituloError: 'No se pudo actualizar el préstamo',
              alGuardar: _guardar,
            ),
          ],
        ],
      ),
    );
  }
}
